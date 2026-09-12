#!/usr/bin/env python3
"""Long-running test process for integration tests.

Runs an idle loop until it receives SIGTERM/SIGINT, then exits cleanly with
code 0. Accepts arbitrary --key value arguments (e.g. model parameters) and
echoes them once at startup so tests can assert that parameters were applied.
"""
import signal
import sys
import time

_running = True


def _handle_stop(signum, frame):
    global _running
    _running = False


def main():
    signal.signal(signal.SIGTERM, _handle_stop)
    signal.signal(signal.SIGINT, _handle_stop)

    # Echo launch args so tests can verify parameter application.
    if len(sys.argv) > 1:
        print("ARGS " + " ".join(sys.argv[1:]), flush=True)
    print("STARTED", flush=True)

    while _running:
        time.sleep(0.1)

    print("STOPPED", flush=True)
    sys.exit(0)


if __name__ == "__main__":
    main()
