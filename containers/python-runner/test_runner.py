import io
import sys
import time
import unittest
from subprocess import CompletedProcess, TimeoutExpired
from unittest.mock import patch

import runner


class BoundedOutputTest(unittest.TestCase):
    def capture(self, value, limit):
        with patch.object(runner, "MAX_OUTPUT_BYTES", limit):
            output = runner.BoundedOutput()
            output.drain(io.BytesIO(value))
            return output.text(), output.truncated.is_set()

    def test_clips_at_utf8_character_boundary(self):
        text, truncated = self.capture("\u3042".encode() * 20, 32)
        self.assertEqual("\u3042" * 10, text)
        self.assertTrue(truncated)
        self.assertLessEqual(len(text.encode()), 32)

    def test_keeps_valid_multibyte_output_across_read_boundaries(self):
        value = "\u3042" * 2000
        text, truncated = self.capture(value.encode(), 8192)
        self.assertEqual(value, text)
        self.assertFalse(truncated)

    def test_preserves_invalid_byte_replacement_with_bounded_encoded_text(self):
        text, truncated = self.capture(b"\xff" * 32, 32)
        self.assertEqual("\ufffd" * 10, text)
        self.assertTrue(truncated)
        self.assertLessEqual(len(text.encode()), 32)
        self.assertEqual(("\ufffd", False), self.capture(b"\xff", 32))

    def test_incomplete_actual_eof_is_replaced_when_not_truncated(self):
        self.assertEqual(("\ufffd", False), self.capture(b"\xe3\x81", 32))

    def test_does_not_append_later_text_after_decoded_limit(self):
        with patch.object(runner, "MAX_OUTPUT_BYTES", 8):
            output = runner.BoundedOutput()
            output.append(b"\xff" * 3)
            output.append(b"a")
            output.finish()
        self.assertEqual("\ufffd" * 2, output.text())
        self.assertTrue(output.truncated.is_set())

    def test_synchronous_execution_uses_same_decoding_and_limit(self):
        with patch.object(runner, "MAX_OUTPUT_BYTES", 32):
            with patch.object(runner, "_container_command",
                              return_value=[sys.executable, "-u", "-c", "import sys;sys.stdout.buffer.write(b'\\xff'*32)"]):
                result, status = runner._run_container("synthetic", "")
        self.assertEqual(200, status)
        self.assertEqual("output_limit", result["errorCode"])
        self.assertEqual("\ufffd" * 10, result["standardOutput"])
        self.assertTrue(result["standardOutputTruncated"])

    def test_drain_closes_stream_on_read_failure(self):
        stream = io.BytesIO(b"synthetic")
        with patch.object(stream, "read", side_effect=OSError("synthetic read failure")):
            with self.assertRaisesRegex(OSError, "synthetic read failure"):
                runner.BoundedOutput().drain(stream)
        self.assertTrue(stream.closed)


class SynchronousPipeOwnershipTest(unittest.TestCase):
    def test_closes_all_pipes_after_success_failure_and_timeout(self):
        original_popen = runner.subprocess.Popen
        for source, expected in [
            ("print('done')", "succeeded"),
            ("raise ValueError('synthetic')", "failed"),
            ("import time; time.sleep(5)", "timed_out"),
        ]:
            with self.subTest(status=expected):
                processes = []

                def start(*args, **kwargs):
                    process = original_popen(*args, **kwargs)
                    processes.append(process)
                    return process

                with patch.object(runner, "_container_command",
                                  return_value=[sys.executable, "-u", "-c", source]), \
                        patch.object(runner.subprocess, "Popen", side_effect=start), \
                        patch.object(runner, "_cleanup_container", return_value=True), \
                        patch.object(runner, "EXECUTION_TIMEOUT_SECONDS", 0.15):
                    result, status = runner._run_container("synthetic", "")
                self.assertEqual(200, status)
                self.assertEqual(expected, result["status"])
                self.assertEqual(1, len(processes))
                process = processes[0]
                self.assertIsNotNone(process.poll())
                for stream in (process.stdin, process.stdout, process.stderr):
                    self.assertTrue(stream.closed)


class ContainerCleanupTest(unittest.TestCase):
    def test_confirms_absence_after_removal_in_progress(self):
        responses = [
            CompletedProcess([], 1, b"", b"removal of container fixture is already in progress"),
            CompletedProcess([], 0, b"fixture\n", b""),
            CompletedProcess([], 1, b"", b"error: no such object: fixture"),
        ]
        with patch.object(runner.subprocess, "run", side_effect=responses) as command:
            self.assertTrue(runner._cleanup_container("fixture"))
        self.assertEqual(3, command.call_count)
        self.assertEqual(["docker", "inspect", "--type=container", "--format", "{{.Id}}", "fixture"],
                         command.call_args.args[0])

    def test_does_not_accept_daemon_failure_as_absence(self):
        with patch.object(runner.subprocess, "run",
                          return_value=CompletedProcess([], 1, b"", b"Cannot connect to Docker daemon")):
            with self.assertLogs("runner", level="WARNING"):
                self.assertFalse(runner._cleanup_container("fixture"))

    def test_does_not_accept_inspect_failure_as_absence(self):
        with patch.object(runner.subprocess, "run", side_effect=[
            CompletedProcess([], 1, b"", b"removal of container fixture is already in progress"),
            CompletedProcess([], 1, b"", b"permission denied"),
        ]):
            with self.assertLogs("runner", level="WARNING"):
                self.assertFalse(runner._cleanup_container("fixture"))

    def test_removal_still_present_is_bounded_by_original_deadline(self):
        with patch.object(runner, "DOCKER_CLEANUP_TIMEOUT_SECONDS", .05):
            with patch.object(runner.subprocess, "run", side_effect=[
                CompletedProcess([], 1, b"", b"removal of container fixture is already in progress"),
                CompletedProcess([], 0, b"fixture", b""),
            ]) as command:
                with self.assertLogs("runner", level="WARNING"):
                    self.assertFalse(runner._cleanup_container("fixture"))
        self.assertEqual(2, command.call_count)

    def test_subprocess_timeout_remains_failure(self):
        with patch.object(runner.subprocess, "run", side_effect=TimeoutExpired(["docker"], 2)):
            with self.assertLogs("runner", level="WARNING"):
                self.assertFalse(runner._cleanup_container("fixture"))


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
                self.assertIsNotNone(session.process.poll())
                for stream in (session.process.stdin, session.process.stdout, session.process.stderr):
                    self.assertTrue(stream.closed)
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

    def test_runtime_error_closes_pipes_and_rejects_late_input(self):
        session = self.start_session("raise ValueError('synthetic')")
        result = self.wait_for_completion(session)
        self.assertEqual("runtime_error", result["errorCode"])
        with self.assertRaisesRegex(ValueError, "execution_not_running"):
            session.send_input("late")

    def test_multibyte_output_events_match_bounded_terminal_snapshot(self):
        with patch.object(runner, "MAX_OUTPUT_BYTES", 32):
            with patch.object(runner.subprocess, "run",
                              return_value=CompletedProcess([], 0, b"", b"")):
                session = self.start_session(
                    "import sys,time\nsys.stdout.write('\\u3042'*100)\nsys.stdout.flush()\ntime.sleep(5)")
                result = self.wait_for_completion(session)
        streamed = "".join(e["text"] for e in result["events"] if e["stream"] == "stdout")
        self.assertEqual("\u3042" * 10, result["standardOutput"])
        self.assertEqual(result["standardOutput"], streamed)
        self.assertEqual("output_limit", result["errorCode"])


if __name__ == "__main__":
    unittest.main()
