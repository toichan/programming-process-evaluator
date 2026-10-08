import unittest

import broker


def valid_request():
    return {
        "User": "65532:65532", "AttachStdin": True, "AttachStdout": True,
        "AttachStderr": True, "Tty": False, "OpenStdin": True, "StdinOnce": True,
        "Env": ["PYTHONDONTWRITEBYTECODE=1", "PPE_STUDENT_CODE=print(2)"],
        "Cmd": broker.COMMAND, "Image": "python:3.12-alpine",
        "WorkingDir": "/tmp",
        "Labels": {broker.NAMESPACE_LABEL: "test", broker.ID_LABEL: "a" * 32},
        "HostConfig": {
            "NetworkMode": "none", "Memory": 134217728, "MemorySwap": 134217728,
            "NanoCpus": 500000000, "PidsLimit": 32, "ReadonlyRootfs": True,
            "CapDrop": ["ALL"], "SecurityOpt": ["no-new-privileges"],
            "AutoRemove": True, "Tmpfs": {"/tmp": "rw,noexec,nosuid,nodev,size=8m"},
            "Ulimits": [
                {"Name": "cpu", "Soft": 4, "Hard": 4},
                {"Name": "nofile", "Soft": 64, "Hard": 64},
                {"Name": "fsize", "Soft": 1048576, "Hard": 1048576},
            ],
        },
    }


class PolicyTest(unittest.TestCase):
    def setUp(self):
        self.policy = broker.Policy("test", "python:3.12-alpine")
        self.name = "ppe-python-test-" + "a" * 32

    def admit(self, value):
        return self.policy.create_config(self.name, value, "sha256:" + "b" * 64)

    def test_rebuilds_and_pins_safe_configuration(self):
        result = self.admit(valid_request())
        self.assertEqual("sha256:" + "b" * 64, result["Image"])
        self.assertEqual([], result["Entrypoint"])
        self.assertTrue(result["NetworkDisabled"])
        self.assertEqual("none", result["HostConfig"]["LogConfig"]["Type"])
        self.assertEqual([], result["HostConfig"]["Binds"])

    def test_cli_limit_order_and_unset_swappiness_are_safe(self):
        request = valid_request()
        request["HostConfig"]["Ulimits"].reverse()
        request["HostConfig"]["MemorySwappiness"] = -1
        self.admit(request)
        request["HostConfig"]["Ulimits"][0]["Hard"] = -1
        with self.assertRaises(broker.BrokerError):
            self.admit(request)

    def test_denies_every_host_escape_or_limit_change(self):
        hostile = {
            "Binds": ["/:/host"], "Privileged": True, "CapAdd": ["SYS_ADMIN"],
            "NetworkMode": "host", "PidMode": "host", "IpcMode": "host",
            "Devices": [{"PathOnHost": "/dev/sda"}],
            "VolumesFrom": ["database"], "SecurityOpt": ["seccomp=unconfined"],
            "ReadonlyRootfs": False, "Memory": 0, "MemorySwap": -1,
            "NanoCpus": 0, "PidsLimit": -1,
            "Tmpfs": {"/host": "rw,size=1g"}, "Ulimits": [],
            "Mounts": [{"Type": "bind", "Source": "/", "Target": "/host"}],
            "PortBindings": {"22/tcp": [{"HostPort": "22"}]},
            "DeviceRequests": [{"Capabilities": [["gpu"]]}],
        }
        for key, value in hostile.items():
            with self.subTest(field=key):
                request = valid_request()
                request["HostConfig"][key] = value
                with self.assertRaises(broker.BrokerError):
                    self.admit(request)

    def test_denies_arbitrary_image_command_env_and_namespace(self):
        for key, value in {
            "Image": "ubuntu", "Cmd": ["sh"], "User": "0:0",
            "Entrypoint": ["sh"], "Env": ["PPE_STUDENT_CODE=print(2)", "SECRET=x"],
            "Labels": {}, "Volumes": {"/host": {}}, "Healthcheck": {"Test": ["CMD", "sh"]},
        }.items():
            with self.subTest(field=key):
                request = valid_request()
                request[key] = value
                with self.assertRaises(broker.BrokerError):
                    self.admit(request)
        with self.assertRaises(broker.BrokerError):
            self.policy.create_config("foreign", valid_request(), "image")

    def test_cannot_coerce_boolean_to_integer(self):
        value = valid_request()
        value["HostConfig"]["Memory"] = True
        with self.assertRaises(broker.BrokerError):
            self.admit(value)

    def test_duplicate_json_and_encoded_paths_are_denied(self):
        with self.assertRaises(broker.BrokerError):
            broker._json(b'{"Image":"safe","Image":"unsafe"}')
        for path in ("/%2e%2e/containers/json", "http://docker/containers/json",
                     "/containers//x/start", "/containers/../info",
                     "/containers/create?name=a&name=b"):
            with self.subTest(path=path), self.assertRaises(broker.BrokerError):
                broker._route(path)

    def test_unknown_containers_are_never_forwarded(self):
        class NoEngineCalls:
            def request(self, *_):
                raise AssertionError("A foreign container reached the engine.")
        service = broker.Broker(NoEngineCalls(), "test", "python:3.12-alpine")
        service.ready = True
        for operation in ("json", "start", "wait", None):
            with self.subTest(operation=operation), self.assertRaises(broker.BrokerError):
                service.operation("POST", "foreign", operation, {})


if __name__ == "__main__":
    unittest.main()
