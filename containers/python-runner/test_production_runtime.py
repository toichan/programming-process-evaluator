"""Runs inside the dedicated production runner; never contacts another project."""
import concurrent.futures
import json
import os
import time
import unittest
import urllib.error
import urllib.request


class RuntimeTest(unittest.TestCase):
    def request(self, path, payload=None, authorized=True):
        token = runner_token()
        headers = {"Authorization": "Bearer " + token} if authorized else {}
        body = None if payload is None else json.dumps(payload).encode()
        if body is not None:
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request("http://127.0.0.1:8090" + path, body, headers)
        try:
            response = urllib.request.urlopen(request, timeout=68)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, json.load(response)

    def execute(self, source, standard_input=""):
        status, result = self.request("/execute", {"source": source, "standardInput": standard_input})
        self.assertEqual(200, status, result.get("errorCode"))
        return result

    def test_authentication_and_size_limits(self):
        for path, payload in [("/execute", {"source": "print(1)"}),
                              ("/sessions", {"source": "print(1)"}),
                              ("/sessions/" + "a" * 32, None),
                              ("/sessions/" + "a" * 32 + "/cancel", {})]:
            self.assertEqual(401, self.request(path, payload, authorized=False)[0])
        self.assertEqual(413, self.request("/execute", {"source": "x" * 65537})[0])

    def test_normal_error_output_and_filesystem_isolation(self):
        result = self.execute("print(int(input()) * 2)", "3\n")
        self.assertEqual("succeeded", result["status"])
        self.assertEqual("6\n", result["standardOutput"])
        self.assertEqual("runtime_error", self.execute("raise ValueError('synthetic')")["errorCode"])
        isolated = self.execute("""
import os, socket, resource
assert os.getuid() == 65532
assert resource.getrlimit(resource.RLIMIT_CPU) == (4, 4)
assert resource.getrlimit(resource.RLIMIT_NOFILE) == (64, 64)
assert resource.getrlimit(resource.RLIMIT_FSIZE) == (1048576, 1048576)
assert open('/sys/fs/cgroup/memory.max').read().strip() == '134217728'
assert open('/sys/fs/cgroup/pids.max').read().strip() == '32'
status = open('/proc/self/status').read()
assert 'CapEff:\\t0000000000000000' in status
assert 'NoNewPrivs:\\t1' in status
assert not os.path.exists('/var/run/docker.sock')
assert not os.path.exists('/run/secrets')
assert not os.path.exists('/host')
try:
    open('/escape', 'w')
    raise AssertionError('root filesystem writable')
except OSError:
    pass
try:
    socket.create_connection(('1.1.1.1', 80), timeout=1)
    raise AssertionError('network available')
except OSError:
    pass
print('isolated')
""")
        self.assertEqual("succeeded", isolated["status"])
        self.assertEqual("isolated\n", isolated["standardOutput"])
        limited = self.execute("print('x' * 100000)")
        self.assertEqual("output_limit", limited["errorCode"])
        self.assertLessEqual(len(limited["standardOutput"].encode()), 32768)

    def test_interactive_input_and_concurrency(self):
        status, initial = self.request("/sessions", {"source": "print('ready'); print(input())"})
        self.assertEqual(201, status, initial)
        path = "/sessions/" + initial["sessionId"]
        self.assertEqual(202, self.request(path + "/input", {"line": "synthetic"})[0])
        result = self.wait(path)
        self.assertEqual("succeeded", result["status"])
        self.assertIn("synthetic", result["standardOutput"])
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(lambda _: self.execute("import time; time.sleep(1); print(2)"),
                                    range(4)))
        self.assertTrue(all(item["status"] == "succeeded" for item in results))
        held = []
        try:
            for _ in range(4):
                status, session = self.request("/sessions", {"source": "input()"})
                self.assertEqual(201, status, session)
                held.append("/sessions/" + session["sessionId"])
            self.assertEqual(429, self.request("/sessions", {"source": "print(1)"})[0])
        finally:
            for path in held:
                self.request(path + "/cancel", {})
                self.wait(path)

    def wait(self, path):
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            status, result = self.request(path)
            self.assertEqual(200, status, result)
            if result["status"] != "running":
                return result
            time.sleep(.05)
        self.fail("Interactive execution exceeded the test deadline.")

    def test_wall_timeout(self):
        self.assertEqual("execution_timeout", self.execute("import time; time.sleep(65)")["errorCode"])


def runner_token():
    with open(os.environ["PYTHON_RUNNER_TOKEN_FILE"], encoding="utf-8") as secret:
        return secret.read().strip()


if __name__ == "__main__":
    unittest.main()
