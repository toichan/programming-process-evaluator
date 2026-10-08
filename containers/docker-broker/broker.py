import http.client
import json
import logging
import os
import re
import select
import socket
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


API_VERSION = "1.41"
MAX_CHILDREN = 4
MAX_BODY_BYTES = 256 * 1024
MAX_SOURCE_BYTES = 64 * 1024
CHILD_LIFETIME_SECONDS = 70
NAMESPACE_LABEL = "ppe.execution.namespace"
ID_LABEL = "ppe.execution.id"
WRAPPER = (
    "import os; "
    "exec(compile(os.environ['PPE_STUDENT_CODE'], '<student>', 'exec'))"
)
COMMAND = ["python3", "-I", "-S", "-B", "-u", "-c", WRAPPER]
ID_PATTERN = re.compile(r"[0-9a-f]{64}")
EXECUTION_PATTERN = re.compile(r"[0-9a-f]{32}")


class BrokerError(Exception):
    def __init__(self, status=403, message="Docker broker policy denied the request."):
        super().__init__(message)
        self.status = status


def _same(actual, expected):
    # Python bool equals int; security-sensitive comparisons must not coerce.
    if type(actual) is not type(expected):
        return False
    if isinstance(expected, dict):
        return (actual.keys() == expected.keys()
                and all(_same(actual[key], value) for key, value in expected.items()))
    if isinstance(expected, list):
        return (len(actual) == len(expected)
                and all(_same(left, right) for left, right in zip(actual, expected)))
    return actual == expected


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise BrokerError(400, "Duplicate JSON fields are not allowed.")
        result[key] = value
    return result


def _json(body):
    try:
        return json.loads(body, object_pairs_hook=_unique_object,
                          parse_constant=lambda _: (_ for _ in ()).throw(BrokerError(400)))
    except (ValueError, UnicodeError) as error:
        raise BrokerError(400, "Invalid JSON.") from error


def _fields(value, required, defaults):
    if not isinstance(value, dict) or not required.keys() <= value.keys():
        raise BrokerError()
    for key, actual in value.items():
        if key in required:
            if not _same(actual, required[key]):
                raise BrokerError()
        elif key not in defaults or not any(_same(actual, candidate)
                                           for candidate in defaults[key]):
            raise BrokerError()


class Policy:
    def __init__(self, namespace, runtime_image):
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,31}", namespace):
            raise ValueError("A unique Docker broker namespace must be configured.")
        self.namespace = namespace
        self.runtime_image = runtime_image
        self.prefix = "ppe-python-" + namespace + "-"

    def labels_for_name(self, name):
        if not name.startswith(self.prefix):
            raise BrokerError()
        execution_id = name[len(self.prefix):]
        if not EXECUTION_PATTERN.fullmatch(execution_id):
            raise BrokerError()
        return {NAMESPACE_LABEL: self.namespace, ID_LABEL: execution_id}

    def create_config(self, name, value, image_id):
        if not isinstance(value, dict):
            raise BrokerError()
        environment = value.get("Env")
        if (not isinstance(environment, list) or len(environment) != 2
                or any(not isinstance(item, str) for item in environment)
                or environment.count("PYTHONDONTWRITEBYTECODE=1") != 1):
            raise BrokerError()
        sources = [item[len("PPE_STUDENT_CODE="):] for item in environment
                   if item.startswith("PPE_STUDENT_CODE=")]
        if len(sources) != 1 or "\x00" in sources[0]:
            raise BrokerError()
        try:
            if len(sources[0].encode("utf-8")) > MAX_SOURCE_BYTES:
                raise BrokerError(413)
        except UnicodeError as error:
            raise BrokerError(400) from error
        labels = self.labels_for_name(name)
        host = {
            "NetworkMode": "none", "Memory": 128 * 1024 * 1024,
            "MemorySwap": 128 * 1024 * 1024, "NanoCpus": 500_000_000,
            "PidsLimit": 32, "ReadonlyRootfs": True, "CapDrop": ["ALL"],
            "SecurityOpt": ["no-new-privileges"], "AutoRemove": True,
            "Tmpfs": {"/tmp": "rw,noexec,nosuid,nodev,size=8m"},
            "Ulimits": [
                {"Name": "cpu", "Soft": 4, "Hard": 4},
                {"Name": "nofile", "Soft": 64, "Hard": 64},
                {"Name": "fsize", "Soft": 1048576, "Hard": 1048576},
            ],
        }
        host_defaults = {
            "Binds": [None, []], "ContainerIDFile": [""],
            "LogConfig": [{"Type": "", "Config": {}}, {"Type": "", "Config": None}],
            "PortBindings": [None, {}],
            "RestartPolicy": [{"Name": "no", "MaximumRetryCount": 0}],
            "VolumeDriver": [""], "VolumesFrom": [None, []],
            "ConsoleSize": [[0, 0]], "CapAdd": [None, []],
            "CgroupnsMode": ["", "private"], "IpcMode": ["", "private"],
            "Cgroup": [""], "PidMode": [""], "UTSMode": [""], "UsernsMode": [""],
            "Privileged": [False], "PublishAllPorts": [False],
            "ShmSize": [0, 67108864], "Runtime": [""], "Isolation": [""],
            "MaskedPaths": [None], "ReadonlyPaths": [None],
        }
        for key in ("Dns", "DnsOptions", "DnsSearch", "ExtraHosts", "GroupAdd", "Links",
                    "Devices", "DeviceCgroupRules", "DeviceRequests",
                    "BlkioWeightDevice", "BlkioDeviceReadBps", "BlkioDeviceWriteBps",
                    "BlkioDeviceReadIOps", "BlkioDeviceWriteIOps"):
            host_defaults[key] = [None, []]
        for key in ("StorageOpt", "Sysctls"):
            host_defaults[key] = [None, {}]
        for key in ("OomScoreAdj", "CpuShares", "BlkioWeight", "CpuPeriod", "CpuQuota",
                    "CpuRealtimePeriod", "CpuRealtimeRuntime", "MemoryReservation",
                    "CpuCount", "CpuPercent", "IOMaximumIOps", "IOMaximumBandwidth"):
            host_defaults[key] = [0]
        for key in ("CgroupParent", "CpusetCpus", "CpusetMems"):
            host_defaults[key] = [""]
        host_defaults["MemorySwappiness"] = [None, -1]
        host_defaults["OomKillDisable"] = [None, False]
        actual_host = value.get("HostConfig")
        if isinstance(actual_host, dict):
            actual_host = dict(actual_host)
            limits = actual_host.get("Ulimits")
            if (isinstance(limits, list)
                    and all(isinstance(item, dict) and isinstance(item.get("Name"), str)
                            for item in limits)):
                actual_host["Ulimits"] = sorted(limits, key=lambda item: item["Name"])
        checked_host = dict(host, Ulimits=sorted(host["Ulimits"], key=lambda item: item["Name"]))
        _fields(actual_host, checked_host, host_defaults)
        required = {
            "User": "65532:65532", "AttachStdin": True, "AttachStdout": True,
            "AttachStderr": True, "Tty": False, "OpenStdin": True, "StdinOnce": True,
            "Env": environment, "Cmd": COMMAND, "Image": self.runtime_image,
            "WorkingDir": "/tmp", "Labels": labels, "HostConfig": value["HostConfig"],
        }
        _fields(value, required, {
            "Hostname": [""], "Domainname": [""], "ExposedPorts": [None, {}],
            "Volumes": [None, {}], "Entrypoint": [None, []], "NetworkDisabled": [False, True],
            "MacAddress": [""], "StopSignal": [""], "StopTimeout": [None],
            "Healthcheck": [None], "ArgsEscaped": [False],
            "OnBuild": [None, []], "Shell": [None, []],
            "NetworkingConfig": [None, {}, {"EndpointsConfig": {}}],
        })
        # Rebuild, never forward caller JSON. Pin the immutable local image ID,
        # clear inherited entrypoints/volumes, and disable daemon disk logging.
        result = {key: required[key] for key in required if key != "HostConfig"}
        result.update(Image=image_id, Entrypoint=[], NetworkDisabled=True)
        result["HostConfig"] = dict(host, LogConfig={"Type": "none", "Config": {}},
                                    IpcMode="private", ShmSize=8 * 1024 * 1024,
                                    Privileged=False, PublishAllPorts=False,
                                    CapAdd=[], Binds=[], Devices=[], VolumesFrom=[],
                                    RestartPolicy={"Name": "no", "MaximumRetryCount": 0})
        return result


class UnixConnection(http.client.HTTPConnection):
    def __init__(self, socket_path, timeout=5):
        super().__init__("localhost", timeout=timeout)
        self.socket_path = socket_path

    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect(self.socket_path)


class Engine:
    def __init__(self, socket_path):
        self.socket_path = socket_path

    def request(self, method, path, body=None, timeout=5):
        connection = UnixConnection(self.socket_path, timeout)
        try:
            connection.request(method, path, body=body,
                               headers={"Content-Type": "application/json"})
            response = connection.getresponse()
            data = response.read(1024 * 1024 + 1)
            if len(data) > 1024 * 1024:
                raise BrokerError(503, "Docker engine response exceeded its limit.")
            return response.status, data
        except (OSError, http.client.HTTPException) as error:
            raise BrokerError(503, "Docker engine is unavailable.") from error
        finally:
            connection.close()

    def attach(self, path):
        upstream = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        try:
            upstream.settimeout(5)
            upstream.connect(self.socket_path)
            upstream.sendall((
                "POST " + path + " HTTP/1.1\r\nHost: localhost\r\n"
                "Connection: Upgrade\r\nUpgrade: tcp\r\nContent-Length: 0\r\n\r\n"
            ).encode("ascii"))
            response = http.client.HTTPResponse(upstream)
            response.fp.close()
            response.fp = upstream.makefile("rb", buffering=0)
            response.begin()
            return upstream, response
        except (OSError, http.client.HTTPException):
            upstream.close()
            raise BrokerError(503, "Docker engine is unavailable.")


class Broker:
    def __init__(self, engine, namespace, runtime_image):
        self.engine = engine
        self.policy = Policy(namespace, runtime_image)
        self.children = {}
        self.lock = threading.RLock()
        self.failed = False
        self.ready = False
        self.image_id = None

    def initialize(self):
        status, body = self.engine.request("GET", "/version")
        version = _json(body) if status == 200 else {}
        try:
            if tuple(map(int, version["ApiVersion"].split("."))) < (1, 41):
                raise ValueError()
        except (KeyError, ValueError, TypeError):
            raise BrokerError(503, "Docker engine API 1.41 or newer is required.")
        image_path = "/images/" + urllib.parse.quote(self.policy.runtime_image, safe="") + "/json"
        status, body = self.engine.request("GET", image_path)
        image = _json(body) if status == 200 else {}
        if (not re.fullmatch(r"sha256:[0-9a-f]{64}", image.get("Id", ""))
                or not isinstance(image.get("Config"), dict)
                or image["Config"].get("Volumes")):
            raise BrokerError(503, "A pre-pulled runtime image without volumes is required.")
        self.image_id = image["Id"]
        filters = json.dumps({"label": [NAMESPACE_LABEL + "=" + self.policy.namespace]})
        status, body = self.engine.request("GET", "/containers/json?all=1&filters="
                                           + urllib.parse.quote(filters, safe=""))
        existing = _json(body) if status == 200 else None
        if not isinstance(existing, list):
            raise BrokerError(503, "Docker namespace reconciliation failed.")
        for child in existing:
            identifier = child.get("Id", "")
            names = child.get("Names", [])
            labels = child.get("Labels", {})
            if not ID_PATTERN.fullmatch(identifier) or len(names) != 1:
                raise BrokerError(503, "Docker namespace reconciliation failed.")
            name = names[0].removeprefix("/")
            if labels != self.policy.labels_for_name(name):
                raise BrokerError(503, "Docker namespace reconciliation failed.")
            status, _ = self.engine.request("DELETE", "/containers/" + identifier + "?force=1&v=0")
            if status not in (204, 404):
                raise BrokerError(503, "Docker namespace reconciliation failed.")
        self.ready = True

    def healthy(self):
        if not self.ready or self.failed:
            raise BrokerError(503, "Docker broker is unavailable.")

    def request(self, method, path, body=None, timeout=5):
        try:
            return self.engine.request(method, path, body, timeout)
        except BrokerError:
            self.failed = True
            raise

    def _inspect(self, identifier, child):
        status, data = self.request("GET", "/containers/" + identifier + "/json")
        if status == 404:
            self.children.pop(identifier, None)
            return None
        if status != 200:
            self.failed = True
            raise BrokerError(503)
        value = _json(data)
        if (value.get("Id") != identifier or value.get("Name") != "/" + child["name"]
                or value.get("Config", {}).get("Labels") != child["labels"]):
            self.failed = True
            raise BrokerError(503, "Docker namespace verification failed.")
        return value

    def reap(self):
        with self.lock:
            for identifier, child in list(self.children.items()):
                value = self._inspect(identifier, child)
                if value and time.monotonic() - child["created"] >= CHILD_LIFETIME_SECONDS:
                    status, _ = self.request("DELETE", "/containers/" + identifier + "?force=1&v=0")
                    if status in (204, 404):
                        self.children.pop(identifier, None)

    def create(self, name, value):
        config = self.policy.create_config(name, value, self.image_id)
        with self.lock:
            self.healthy()
            self.reap()
            if len(self.children) >= MAX_CHILDREN:
                raise BrokerError(429, "Docker broker child limit reached.")
            if any(child["name"] == name for child in self.children.values()):
                raise BrokerError(409, "Docker broker child name is already admitted.")
            status, body = self.request("POST", "/containers/create?name=" + name,
                                        json.dumps(config).encode())
            if status != 201:
                # A failed create may have reached the engine; never free an
                # uncertain reservation until startup namespace reconciliation.
                self.failed = True
                raise BrokerError(503, "Docker broker could not admit the child.")
            value = _json(body)
            identifier = value.get("Id", "")
            if not ID_PATTERN.fullmatch(identifier) or identifier in self.children:
                self.failed = True
                raise BrokerError(503)
            self.children[identifier] = {
                "name": name, "labels": self.policy.labels_for_name(name),
                "created": time.monotonic(), "attached": False, "waiting": False,
            }
            return 201, json.dumps({"Id": identifier, "Warnings": []}).encode()

    def owned(self, reference):
        with self.lock:
            self.healthy()
            if reference in self.children:
                return reference, self.children[reference]
            for identifier, child in self.children.items():
                if reference == child["name"]:
                    return identifier, child
        raise BrokerError(404, "No such container: " + reference)

    def operation(self, method, reference, operation, query):
        identifier, child = self.owned(reference)
        path = "/containers/" + identifier
        if method == "GET" and operation == "json" and not query:
            with self.lock:
                value = self._inspect(identifier, child)
            if value is None:
                raise BrokerError(404, "No such container: " + reference)
            return 200, json.dumps(value).encode()
        if method == "POST" and operation == "start" and not query:
            status, body = self.request(method, path + "/start")
        elif method == "POST" and operation == "wait" and query in (
                {}, {"condition": "not-running"}, {"condition": "next-exit"},
                {"condition": "removed"}):
            with self.lock:
                if child["waiting"]:
                    raise BrokerError(409)
                child["waiting"] = True
            try:
                suffix = "?condition=" + query.get("condition", "not-running")
                return self.request(method, path + "/wait" + suffix, timeout=70)
            finally:
                with self.lock:
                    child["waiting"] = False
        elif method == "DELETE" and not operation and query in (
                {"force": "1"}, {"force": "1", "v": "0"},
                {"force": "true", "v": "false"}):
            status, body = self.request(method, path + "?force=1&v=0")
            if status in (204, 404):
                with self.lock:
                    self.children.pop(identifier, None)
        else:
            raise BrokerError()
        if status == 404:
            with self.lock:
                self.children.pop(identifier, None)
        return status, body


def _route(target):
    # Do not decode or normalize paths: encoded slashes, traversal, duplicate
    # query values and absolute-form proxy requests must never reach Docker.
    parsed = urllib.parse.urlsplit(target)
    if (parsed.scheme or parsed.netloc or parsed.fragment or "%" in parsed.path
            or not re.fullmatch(r"/[A-Za-z0-9_./-]*", parsed.path)
            or "//" in parsed.path or ".." in parsed.path):
        raise BrokerError()
    path = re.sub(r"^/v1\.(?:4[1-9]|5[0-9])(?=/)", "", parsed.path)
    try:
        pairs = urllib.parse.parse_qsl(parsed.query, strict_parsing=True,
                                      keep_blank_values=True)
    except ValueError as error:
        raise BrokerError(400) from error
    query = {}
    for key, value in pairs:
        if key in query:
            raise BrokerError()
        query[key] = value
    return path, query


class RequestHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "PPEDockerBroker/1.0"
    rbufsize = 0

    def setup(self):
        super().setup()
        self.connection.settimeout(5)

    def log_message(self, *_):
        return

    def _respond(self, status, body):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)
        self.close_connection = True

    def _body(self, allowed=False):
        lengths = self.headers.get_all("Content-Length", [])
        if self.headers.get_all("Transfer-Encoding") or len(lengths) > 1:
            raise BrokerError(400)
        length = lengths[0] if lengths else "0"
        if not re.fullmatch(r"[0-9]+", length):
            raise BrokerError(400)
        length = int(length)
        if length > MAX_BODY_BYTES:
            raise BrokerError(413)
        if length and not allowed:
            raise BrokerError()
        body = self.rfile.read(length)
        if len(body) != length:
            raise BrokerError(400)
        return body

    def _relay(self, reference, query):
        if query != {"stream": "1", "stdin": "1", "stdout": "1", "stderr": "1"}:
            raise BrokerError()
        if (self.headers.get_all("Upgrade", []) != ["tcp"]
                or self.headers.get("Connection", "").lower() != "upgrade"):
            raise BrokerError()
        broker = self.server.broker
        identifier, child = broker.owned(reference)
        with broker.lock:
            if child["attached"]:
                raise BrokerError(409)
            child["attached"] = True
        upstream = response = None
        upgraded = False
        try:
            upstream, response = broker.engine.attach(
                "/containers/" + identifier + "/attach?stream=1&stdin=1&stdout=1&stderr=1")
            if response.status != 101:
                raise BrokerError(503, "Docker engine could not attach the child.")
            self.send_response_only(101, "Switching Protocols")
            self.send_header("Connection", "Upgrade")
            self.send_header("Upgrade", "tcp")
            self.send_header("Content-Type", "application/vnd.docker.raw-stream")
            self.end_headers()
            self.wfile.flush()
            upgraded = True
            peers = [self.connection, upstream]
            deadline = time.monotonic() + 70
            while time.monotonic() < deadline:
                readable, _, _ = select.select(peers, [], [], 1)
                for peer in readable:
                    data = peer.recv(16384)
                    if not data:
                        if peer is upstream:
                            return
                        peers.remove(peer)
                        upstream.shutdown(socket.SHUT_WR)
                    else:
                        destination = upstream if peer is self.connection else self.connection
                        destination.sendall(data)
        except (OSError, http.client.HTTPException):
            if not upgraded:
                raise BrokerError(503, "Docker engine attach is unavailable.")
        finally:
            if response is not None:
                response.close()
            if upstream is not None:
                upstream.close()
            with broker.lock:
                child["attached"] = False
            self.close_connection = True

    def _wait(self, reference, query):
        if query not in ({}, {"condition": "not-running"}, {"condition": "next-exit"},
                         {"condition": "removed"}):
            raise BrokerError()
        broker = self.server.broker
        identifier, child = broker.owned(reference)
        with broker.lock:
            if child["waiting"]:
                raise BrokerError(409)
            child["waiting"] = True
        connection = UnixConnection(broker.engine.socket_path, timeout=70)
        headers_sent = False
        try:
            connection.request("POST", "/containers/" + identifier + "/wait?condition="
                               + query.get("condition", "not-running"), body=b"")
            response = connection.getresponse()
            # Docker CLI waits for registration headers before it starts the
            # container. Buffering the whole wait response would deadlock it.
            self.send_response(response.status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.flush()
            headers_sent = True
            total = 0
            while data := response.read(4096):
                total += len(data)
                if total > 1024 * 1024:
                    raise BrokerError(503, "Docker wait response exceeded its limit.")
                self.wfile.write(data)
        except (OSError, http.client.HTTPException, BrokerError) as error:
            if not headers_sent:
                raise BrokerError(503, "Docker wait is unavailable.") from error
            logging.warning("Docker wait stream could not complete.")
        finally:
            connection.close()
            with broker.lock:
                child["waiting"] = False
            self.close_connection = True

    def _handle(self):
        try:
            path, query = _route(self.path)
            broker = self.server.broker
            broker.healthy()
            body = self._body(self.command == "POST" and path == "/containers/create")
            if path == "/_ping" and self.command in ("GET", "HEAD") and not query:
                status, result = broker.request("GET", "/_ping")
                if status != 200 or result != b"OK":
                    raise BrokerError(503, "Docker engine health check failed.")
                self.send_response(200)
                self.send_header("API-Version", API_VERSION)
                self.send_header("Docker-Experimental", "false")
                self.send_header("OSType", "linux")
                self.send_header("Content-Length", "2")
                self.send_header("Connection", "close")
                self.end_headers()
                if self.command != "HEAD":
                    self.wfile.write(b"OK")
                self.close_connection = True
                return
            if path == "/version" and self.command == "GET" and not query:
                self._respond(200, json.dumps({
                    "ApiVersion": API_VERSION, "MinAPIVersion": API_VERSION,
                    "Version": "ppe-broker", "Os": "linux",
                }).encode())
                return
            if path == "/containers/create" and self.command == "POST" and query.keys() == {"name"}:
                status, result = broker.create(query["name"], _json(body))
                self._respond(status, result)
                return
            match = re.fullmatch(r"/containers/([a-z0-9-]+)(?:/(json|start|wait|attach))?", path)
            if not match:
                raise BrokerError()
            reference, operation = match.groups()
            if operation == "attach" and self.command == "POST":
                self._relay(reference, query)
            elif operation == "wait" and self.command == "POST":
                self._wait(reference, query)
            else:
                status, result = broker.operation(self.command, reference, operation, query)
                self._respond(status, result)
        except BrokerError as error:
            logging.warning("Broker rejection: %s", error)
            self._respond(error.status, json.dumps({"message": str(error)}).encode())
        except (OSError, ValueError, TypeError, KeyError, http.client.HTTPException):
            self.close_connection = True
            self._respond(503, b'{"message":"Docker broker is unavailable."}')

    do_GET = do_HEAD = do_POST = do_DELETE = do_PUT = do_PATCH = do_OPTIONS = _handle


class BrokerServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 32

    def __init__(self, address, broker):
        super().__init__(address, RequestHandler)
        self.broker = broker
        self.connections = threading.BoundedSemaphore(32)

    def process_request(self, request, client_address):
        if not self.connections.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.connections.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.connections.release()


def main():
    broker = Broker(Engine(os.environ.get("DOCKER_SOCKET", "/var/run/docker.sock")),
                    os.environ.get("PYTHON_BROKER_NAMESPACE", ""),
                    os.environ.get("PYTHON_RUNTIME_IMAGE", "python:3.12-alpine"))
    broker.initialize()
    server = BrokerServer(("0.0.0.0", 2375), broker)

    def monitor():
        while True:
            time.sleep(1)
            try:
                broker.reap()
            except BrokerError:
                broker.failed = True

    threading.Thread(target=monitor, daemon=True).start()
    server.serve_forever()


if __name__ == "__main__":
    main()
