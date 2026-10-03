import sys
import time
import unittest
from subprocess import CompletedProcess
from unittest.mock import patch

import runner


class InteractiveExecutionSessionTest(unittest.TestCase):
    def start_session(self, source):
        with patch.object(
            runner,
            "_container_command",
            return_value=[sys.executable, "-u", "-c", source],
        ):
            session, error = runner._create_session(source)
        self.assertIsNone(error)
        return session

    def wait_for_completion(self, session, timeout=3):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            snapshot = session.snapshot(0)
            if snapshot["status"] != "running":
                return snapshot
            time.sleep(0.01)
        self.fail("Execution session did not finish before the test deadline.")

    def test_accepts_multiple_inputs_and_streams_output(self):
        source = (
            "import sys\n"
            "print('first prompt')\n"
            "first = input()\n"
            "print('second prompt')\n"
            "second = input()\n"
            "print(first + '-' + second)\n"
            "print('warning', file=sys.stderr)\n"
        )
        session = self.start_session(source)
        session.send_input("alpha")
        session.send_input("beta")
        result = self.wait_for_completion(session)

        self.assertEqual("succeeded", result["status"])
        self.assertEqual("alpha\nbeta\n", result["standardInput"])
        self.assertIn("first prompt", result["standardOutput"])
        self.assertIn("second prompt", result["standardOutput"])
        self.assertIn("alpha-beta", result["standardOutput"])
        self.assertIn("warning", result["standardError"])
        self.assertEqual(
            sorted(event["id"] for event in result["events"]),
            [event["id"] for event in result["events"]],
        )

    def test_idle_timeout_ends_waiting_process(self):
        with patch.object(runner, "INPUT_IDLE_TIMEOUT_SECONDS", 0.15):
            with patch.object(
                runner.subprocess,
                "run",
                return_value=CompletedProcess([], 0, b"", b""),
            ) as cleanup:
                session = self.start_session("input()")
                result = self.wait_for_completion(session)

        self.assertEqual("timed_out", result["status"])
        self.assertEqual("input_timeout", result["errorCode"])
        cleanup.assert_called_once()

    def test_total_timeout_ends_long_running_process(self):
        with patch.object(runner, "EXECUTION_TIMEOUT_SECONDS", 0.15):
            with patch.object(
                runner.subprocess,
                "run",
                return_value=CompletedProcess([], 0, b"", b""),
            ) as cleanup:
                session = self.start_session("import time\ntime.sleep(5)")
                result = self.wait_for_completion(session)

        self.assertEqual("timed_out", result["status"])
        self.assertEqual("execution_timeout", result["errorCode"])
        cleanup.assert_called_once()

    def test_output_limit_terminates_process_and_reports_truncation(self):
        with patch.object(runner, "MAX_OUTPUT_BYTES", 32):
            with patch.object(
                runner.subprocess,
                "run",
                return_value=CompletedProcess([], 0, b"", b""),
            ) as cleanup:
                session = self.start_session(
                    "import sys, time\nsys.stdout.write('x' * 256)\nsys.stdout.flush()\ntime.sleep(5)"
                )
                result = self.wait_for_completion(session)

        self.assertEqual("failed", result["status"])
        self.assertEqual("output_limit", result["errorCode"])
        self.assertTrue(result["standardOutputTruncated"])
        self.assertEqual(32, len(result["standardOutput"].encode("utf-8")))
        cleanup.assert_called_once()

    def test_cancel_ends_active_process(self):
        with patch.object(
            runner.subprocess,
            "run",
            return_value=CompletedProcess([], 0, b"", b""),
        ):
            session = self.start_session("import time\ntime.sleep(10)")
            session.cancel()
            result = self.wait_for_completion(session)

        self.assertEqual("cancelled", result["status"])
        self.assertEqual("execution_cancelled", result["errorCode"])


if __name__ == "__main__":
    unittest.main()
