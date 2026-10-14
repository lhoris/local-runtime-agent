# Local Python Process Test

Set `PYTHON_PROJECT_PATH` to this directory:

```text
C:/workspace/github/local-runtime-agent/local-test/python-process
```

일반 Python 프로젝트의 `start.bat`, `stop.bat`, `restart.bat` 작성 규칙은
[`docs/PROCESS_CONTROL_SCRIPTS.md`](../../docs/PROCESS_CONTROL_SCRIPTS.md)를
참고합니다.

The Agent controls the process with:

- `start.bat`: starts `test_process.py` in the foreground
- `stop.bat <PID>`: stops the Python process by the PID passed by the Agent
- `restart.bat <PID>`: stops the old PID and starts the Python process again

The Python process writes its own PID and heartbeat messages to:

- `.agent.pid`
- `python_process.log`

Run the files manually first if needed:

```bat
start.bat
```

In another terminal:

```bat
type .agent.pid
type python_process.log
```

Stop it with:

```bat
stop.bat 12345
```

The Python process writes its actual PID to `.agent.pid`. The Agent reads and
validates that file first after `start.bat` starts.
When it executes `stop.bat` or `restart.bat`, it passes that PID as the first
argument. Running `stop.bat` without an argument still falls back to the local
`.agent.pid` file for manual testing.

`start.bat` uses `py -3` instead of the bare `python` command. On Windows,
the bare command can resolve to the Microsoft Store execution alias instead
of the installed Python interpreter.

Run `command-test.sql` from this folder to enqueue a command with one INSERT
statement. Change the IP address, model process ID, and command type first.
