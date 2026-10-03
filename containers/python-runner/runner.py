import codecs
import json
import os
import subprocess
import threading
import time
import urllib.parse
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


MAX_REQUEST_BYTES = 512 * 1024
MAX_SOURCE_BYTES = 64 * 1024
MAX_INPUT_BYTES = 8 * 1024
MAX_OUTPUT_BYTES = 32 * 1024
EXECUTION_TIMEOUT_SECONDS = 60
INPUT_IDLE_TIMEOUT_SECONDS = 30
DOCKER_CLEANUP_TIMEOUT_SECONDS = 2
SESSION_RETENTION_SECONDS = 120
MAX_SESSION_RECORDS = 32
RUNTIME_IMAGE = os.environ.get("PYTHON_RUNTIME_IMAGE", "python:3.12-alpine")
EXECUTION_WRAPPER = (
    "import os; "
    "exec(compile(os.environ['PPE_STUDENT_CODE'], '<student>', 'exec'))"
)
MAX_CONCURRENT_EXECUTIONS = threading.BoundedSemaphore(4)
SESSIONS = {}
SESSIONS_LOCK = threading.Lock()


class BoundedOutput:
    def __init__(self):
        self.data = bytearray()
        self.lock = threading.Lock()
        self.truncated = threading.Event()

    def drain(self, stream):
        while True:
            chunk = stream.read(4096)
            if not chunk:
                return
            with self.lock:
                remaining = MAX_OUTPUT_BYTES - len(self.data)
                if remaining > 0:
                    self.data.extend(chunk[:remaining])
                if len(chunk) > remaining:
                    self.truncated.set()

    def text(self):
        with self.lock:
            return bytes(self.data).decode("utf-8", errors="replace")


def _write_stdin(stream, value):
    try:
        stream.write(value)
        stream.flush()
    except OSError:
        pass
    finally:
        stream.close()


def _container_command(container_name, source):
    return [
        "docker", "run", "--rm", "--interactive", "--quiet", "--name", container_name,
        "--pull=missing",
        "--network=none",
        "--memory=128m",
        "--memory-swap=128m",
        "--cpus=0.5",
        "--pids-limit=32",
        "--read-only",
        "--cap-drop=ALL",
        "--security-opt=no-new-privileges",
        "--user=65532:65532",
        "--workdir=/tmp",
        "--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=8m",
        "--ulimit=cpu=4:4",
        "--ulimit=nofile=64:64",
        "--ulimit=fsize=1048576:1048576",
        "--env=PYTHONDONTWRITEBYTECODE=1",
        "--env=PPE_STUDENT_CODE=" + source,
        RUNTIME_IMAGE,
        "python3", "-I", "-S", "-B", "-u", "-c", EXECUTION_WRAPPER,
    ]


def _run_container(source, standard_input):
    container_name = "ppe-python-" + uuid.uuid4().hex
    command = _container_command(container_name, source)
    try:
        process = subprocess.Popen(
            command,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=0,
        )
    except OSError:
        return {
            "status": "unavailable",
            "exitCode": None,
            "standardOutput": "",
            "standardError": "",
            "standardOutputTruncated": False,
            "standardErrorTruncated": False,
            "errorCode": "runner_unavailable",
        }, 503

    stdout = BoundedOutput()
    stderr = BoundedOutput()
    threads = [
        threading.Thread(target=stdout.drain, args=(process.stdout,), daemon=True),
        threading.Thread(target=stderr.drain, args=(process.stderr,), daemon=True),
        threading.Thread(
            target=_write_stdin,
            args=(process.stdin, standard_input.encode("utf-8")),
            daemon=True,
        ),
    ]
    for thread in threads:
        thread.start()

    deadline = time.monotonic() + EXECUTION_TIMEOUT_SECONDS
    timed_out = False
    output_limited = False
    while process.poll() is None:
        if stdout.truncated.is_set() or stderr.truncated.is_set():
            output_limited = True
            process.terminate()
            break
        if time.monotonic() >= deadline:
            timed_out = True
            process.terminate()
            break
        time.sleep(0.02)

    wait_failed = False
    try:
        process.wait(timeout=1)
    except subprocess.TimeoutExpired:
        process.kill()
        try:
            process.wait(timeout=1)
        except subprocess.TimeoutExpired:
            wait_failed = True

    cleanup_failed = wait_failed
    if timed_out or output_limited or process.returncode == 125:
        try:
            cleanup = subprocess.run(
                ["docker", "rm", "--force", container_name],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=DOCKER_CLEANUP_TIMEOUT_SECONDS,
                check=False,
            )
            if cleanup.returncode != 0 and b"No such container" not in cleanup.stderr:
                cleanup_failed = True
        except (OSError, subprocess.TimeoutExpired):
            cleanup_failed = True

    for thread in threads:
        thread.join(timeout=1)

    if cleanup_failed:
        status = "unavailable"
        error_code = "runner_cleanup_failed"
    elif timed_out:
        status = "timed_out"
        error_code = "execution_timeout"
    elif output_limited:
        status = "failed"
        error_code = "output_limit"
    elif process.returncode == 125:
        status = "unavailable"
        error_code = "runner_unavailable"
    else:
        status = "succeeded" if process.returncode == 0 else "failed"
        error_code = None if process.returncode == 0 else "runtime_error"

    result = {
        "status": status,
        "exitCode": process.returncode,
        "standardOutput": stdout.text(),
        "standardError": stderr.text(),
        "standardOutputTruncated": stdout.truncated.is_set(),
        "standardErrorTruncated": stderr.truncated.is_set(),
        "errorCode": error_code,
    }
    return result, 503 if status == "unavailable" else 200


class ExecutionSession:
    def __init__(self, source):
        self.session_id = uuid.uuid4().hex
        self.container_name = "ppe-python-" + self.session_id
        self.started_at = time.monotonic()
        self.last_activity_at = self.started_at
        self.lock = threading.RLock()
        self.input_lock = threading.Lock()
        self.sequence = 0
        self.events = []
        self.output_data = {"stdout": bytearray(), "stderr": bytearray()}
        self.output_truncated = {"stdout": False, "stderr": False}
        self.input_bytes = 0
        self.status = "running"
        self.exit_code = None
        self.error_code = None
        self.finished_at = None
        self.termination_reason = None
        self.process = subprocess.Popen(
            _container_command(self.container_name, source),
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=0,
        )
        self.readers = [
            threading.Thread(
                target=self._drain_stream,
                args=("stdout", self.process.stdout),
                daemon=True,
            ),
            threading.Thread(
                target=self._drain_stream,
                args=("stderr", self.process.stderr),
                daemon=True,
            ),
        ]
        for reader in self.readers:
            reader.start()
        threading.Thread(target=self._monitor, daemon=True).start()

    def _append_event(self, stream, text):
        if not text:
            return
        self.sequence += 1
        self.events.append({"id": self.sequence, "stream": stream, "text": text})
        self.last_activity_at = time.monotonic()

    def _drain_stream(self, stream_name, pipe):
        decoder = codecs.getincrementaldecoder("utf-8")(errors="replace")
        while True:
            chunk = pipe.read(4096)
            if not chunk:
                break
            with self.lock:
                remaining = MAX_OUTPUT_BYTES - len(self.output_data[stream_name])
                allowed = chunk[:max(0, remaining)]
                if allowed:
                    self.output_data[stream_name].extend(allowed)
                    self._append_event(stream_name, decoder.decode(allowed))
                if len(chunk) > max(0, remaining):
                    self.output_truncated[stream_name] = True
                    self.last_activity_at = time.monotonic()
        tail = decoder.decode(b"", final=True)
        if tail:
            with self.lock:
                self._append_event(stream_name, tail)

    def send_input(self, value):
        encoded = value.encode("utf-8") + b"\n"
        with self.input_lock:
            with self.lock:
                if self.status != "running":
                    raise ValueError("execution_not_running")
                if self.input_bytes + len(encoded) > MAX_INPUT_BYTES:
                    raise ValueError("input_too_large")
                try:
                    self.process.stdin.write(encoded)
                    self.process.stdin.flush()
                except (BrokenPipeError, OSError) as error:
                    raise ValueError("execution_not_waiting") from error
                self.input_bytes += len(encoded)
                self._append_event("input", value + "\n")

    def cancel(self):
        with self.lock:
            if self.status != "running":
                return
            self.termination_reason = "cancelled"
            try:
                self.process.terminate()
            except OSError:
                pass

    def _monitor(self):
        while self.process.poll() is None:
            now = time.monotonic()
            with self.lock:
                if any(self.output_truncated.values()):
                    self.termination_reason = "output_limit"
                elif now - self.started_at >= EXECUTION_TIMEOUT_SECONDS:
                    self.termination_reason = "execution_timeout"
                elif now - self.last_activity_at >= INPUT_IDLE_TIMEOUT_SECONDS:
                    self.termination_reason = "input_timeout"
                reason = self.termination_reason
            if reason:
                try:
                    self.process.terminate()
                except OSError:
                    pass
                break
            time.sleep(0.02)

        wait_failed = False
        try:
            self.process.wait(timeout=1)
        except subprocess.TimeoutExpired:
            self.process.kill()
            try:
                self.process.wait(timeout=1)
            except subprocess.TimeoutExpired:
                wait_failed = True

        with self.lock:
            reason = self.termination_reason
        cleanup_failed = wait_failed
        if reason or self.process.returncode == 125:
            try:
                cleanup = subprocess.run(
                    ["docker", "rm", "--force", self.container_name],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    timeout=DOCKER_CLEANUP_TIMEOUT_SECONDS,
                    check=False,
                )
                if cleanup.returncode != 0 and b"No such container" not in cleanup.stderr:
                    cleanup_failed = True
            except (OSError, subprocess.TimeoutExpired):
                cleanup_failed = True

        for reader in self.readers:
            reader.join(timeout=1)

        if cleanup_failed:
            status, error_code = "unavailable", "runner_cleanup_failed"
        elif reason == "cancelled":
            status, error_code = "cancelled", "execution_cancelled"
        elif reason == "input_timeout":
            status, error_code = "timed_out", "input_timeout"
        elif reason == "execution_timeout":
            status, error_code = "timed_out", "execution_timeout"
        elif reason == "output_limit":
            status, error_code = "failed", "output_limit"
        elif self.process.returncode == 125:
            status, error_code = "unavailable", "runner_unavailable"
        else:
            status = "succeeded" if self.process.returncode == 0 else "failed"
            error_code = None if self.process.returncode == 0 else "runtime_error"

        with self.lock:
            self.status = status
            self.exit_code = self.process.returncode
            self.error_code = error_code
            self.finished_at = time.monotonic()
        MAX_CONCURRENT_EXECUTIONS.release()

    def snapshot(self, cursor):
        with self.lock:
            finished = self.status != "running"
            return {
                "sessionId": self.session_id,
                "status": self.status,
                "exitCode": self.exit_code,
                "errorCode": self.error_code,
                "events": [event for event in self.events if event["id"] > cursor],
                "nextCursor": self.sequence,
                "standardInput": "".join(
                    event["text"] for event in self.events if event["stream"] == "input"
                ) if finished else "",
                "standardOutput": bytes(self.output_data["stdout"]).decode(
                    "utf-8", errors="replace"
                ) if finished else "",
                "standardError": bytes(self.output_data["stderr"]).decode(
                    "utf-8", errors="replace"
                ) if finished else "",
                "standardOutputTruncated": self.output_truncated["stdout"],
                "standardErrorTruncated": self.output_truncated["stderr"],
                "durationMilliseconds": int((time.monotonic() - self.started_at) * 1000),
            }


def _find_session(session_id):
    now = time.monotonic()
    with SESSIONS_LOCK:
        expired = [
            key for key, session in SESSIONS.items()
            if session.finished_at is not None
            and now - session.finished_at > SESSION_RETENTION_SECONDS
        ]
        for key in expired:
            del SESSIONS[key]
        return SESSIONS.get(session_id)


def _create_session(source):
    if not MAX_CONCURRENT_EXECUTIONS.acquire(blocking=False):
        return None, "runner_busy"
    try:
        session = ExecutionSession(source)
    except OSError:
        MAX_CONCURRENT_EXECUTIONS.release()
        return None, "runner_unavailable"

    with SESSIONS_LOCK:
        expired = [
            key for key, existing in SESSIONS.items()
            if existing.finished_at is not None
            and time.monotonic() - existing.finished_at > SESSION_RETENTION_SECONDS
        ]
        for key in expired:
            del SESSIONS[key]
        if len(SESSIONS) >= MAX_SESSION_RECORDS:
            completed = sorted(
                (
                    (existing.finished_at, key)
                    for key, existing in SESSIONS.items()
                    if existing.finished_at is not None
                )
            )
            if completed:
                del SESSIONS[completed[0][1]]
            else:
                session.cancel()
                return None, "runner_busy"
        SESSIONS[session.session_id] = session
    return session, None


class RequestHandler(BaseHTTPRequestHandler):
    server_version = "PPEPythonRunner/1.0"

    def log_message(self, format_string, *args):
        return

    def _write_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urllib.parse.urlsplit(self.path)
        if parsed.path == "/health":
            self._write_json(200, {"status": "ok"})
            return
        parts = parsed.path.strip("/").split("/")
        if len(parts) == 2 and parts[0] == "sessions":
            try:
                query = urllib.parse.parse_qs(parsed.query, strict_parsing=True)
                cursor_values = query.get("after", ["0"])
                if len(cursor_values) != 1:
                    raise ValueError
                cursor = int(cursor_values[0])
                if cursor < 0:
                    raise ValueError
            except ValueError:
                self._write_json(400, {"error": "invalid_cursor"})
                return
            session = _find_session(parts[1])
            if session is None:
                self._write_json(404, {"error": "session_not_found"})
                return
            result = session.snapshot(cursor)
            self._write_json(503 if result["status"] == "unavailable" else 200, result)
            return
        self._write_json(404, {"error": "not_found"})

    def _read_payload(self):
        try:
            try:
                content_length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                return None, (400, "invalid_content_length")
            if content_length <= 0 or content_length > MAX_REQUEST_BYTES:
                return None, (413, "request_too_large")

            try:
                payload = json.loads(self.rfile.read(content_length))
            except (json.JSONDecodeError, UnicodeDecodeError):
                return None, (400, "invalid_json")
            if not isinstance(payload, dict):
                return None, (400, "invalid_request")
            return payload, None
        except OSError:
            return None, (400, "invalid_request")

    def do_POST(self):
        parsed = urllib.parse.urlsplit(self.path)
        parts = parsed.path.strip("/").split("/")
        if parsed.path == "/execute":
            self._handle_execute()
            return
        if parsed.path == "/sessions":
            self._handle_session_create()
            return
        if len(parts) == 3 and parts[0] == "sessions" and parts[2] in ("input", "cancel"):
            self._handle_session_action(parts[1], parts[2])
            return
        self._write_json(404, {"error": "not_found"})

    def _handle_execute(self):
        if not MAX_CONCURRENT_EXECUTIONS.acquire(blocking=False):
            self._write_json(429, {"error": "runner_busy"})
            return
        try:
            payload, error = self._read_payload()
            if error:
                self._write_json(error[0], {"error": error[1]})
                return
            source = payload.get("source")
            standard_input = payload.get("standardInput", "")
            if not isinstance(source, str) or not isinstance(standard_input, str):
                self._write_json(400, {"error": "invalid_request"})
                return
            if len(source.encode("utf-8")) > MAX_SOURCE_BYTES:
                self._write_json(413, {"error": "source_too_large"})
                return
            if "\x00" in source:
                self._write_json(400, {"error": "invalid_source"})
                return
            if len(standard_input.encode("utf-8")) > MAX_INPUT_BYTES:
                self._write_json(413, {"error": "input_too_large"})
                return
            result, status = _run_container(source, standard_input)
            self._write_json(status, result)
        finally:
            MAX_CONCURRENT_EXECUTIONS.release()

    def _handle_session_create(self):
        payload, error = self._read_payload()
        if error:
            self._write_json(error[0], {"error": error[1]})
            return
        source = payload.get("source")
        if not isinstance(source, str):
            self._write_json(400, {"error": "invalid_request"})
            return
        if len(source.encode("utf-8")) > MAX_SOURCE_BYTES:
            self._write_json(413, {"error": "source_too_large"})
            return
        if "\x00" in source:
            self._write_json(400, {"error": "invalid_source"})
            return
        session, error_code = _create_session(source)
        if error_code:
            status = 429 if error_code == "runner_busy" else 503
            self._write_json(status, {"error": error_code})
            return
        self._write_json(201, session.snapshot(0))

    def _handle_session_action(self, session_id, action):
        session = _find_session(session_id)
        if session is None:
            self._write_json(404, {"error": "session_not_found"})
            return
        if action == "cancel":
            session.cancel()
            result = session.snapshot(0)
            self._write_json(200, result)
            return

        payload, error = self._read_payload()
        if error:
            self._write_json(error[0], {"error": error[1]})
            return
        value = payload.get("line")
        if not isinstance(value, str) or "\x00" in value or "\n" in value or "\r" in value:
            self._write_json(400, {"error": "invalid_input"})
            return
        try:
            session.send_input(value)
        except ValueError as input_error:
            code = str(input_error)
            status = 413 if code == "input_too_large" else 409
            self._write_json(status, {"error": code})
            return
        self._write_json(202, {"accepted": True})


def main():
    port = int(os.environ.get("RUNNER_PORT", "8090"))
    server = ThreadingHTTPServer(("0.0.0.0", port), RequestHandler)
    server.daemon_threads = True
    server.serve_forever()


if __name__ == "__main__":
    main()
