"""Small long-running Python process for local Agent verification."""

from __future__ import annotations

import os
import signal
import sys
import time
from pathlib import Path


PROJECT_DIR = Path(__file__).resolve().parent
PID_FILE = PROJECT_DIR / ".agent.pid"
LOG_FILE = PROJECT_DIR / "python_process.log"
running = True


def stop_handler(signum: int, _frame: object) -> None:
    global running
    running = False
    write_log(f"stop signal received: {signum}")


def write_log(message: str) -> None:
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
    line = f"{timestamp} pid={os.getpid()} {message}"
    print(line, flush=True)
    with LOG_FILE.open("a", encoding="utf-8") as log_file:
        log_file.write(line + "\n")


def main() -> int:
    signal.signal(signal.SIGINT, stop_handler)
    if hasattr(signal, "SIGTERM"):
        signal.signal(signal.SIGTERM, stop_handler)

    PID_FILE.write_text(str(os.getpid()), encoding="ascii")
    write_log("test process started")

    try:
        while running:
            write_log("heartbeat")
            time.sleep(5)
    finally:
        PID_FILE.unlink(missing_ok=True)
        write_log("test process stopped")

    return 0


if __name__ == "__main__":
    sys.exit(main())
