# Local Python Process Test

Set `PYTHON_PROJECT_PATH` to this directory:

```text
C:/workspace/github/local-runtime-agent/local-test/python-process
```

The Agent controls the process with:

- `run.bat`: starts `test_process.py` in the foreground
- `stop.bat`: reads `python_process.pid` and stops the Python process
- `restart.bat`: stops and starts the Python process again

The Python process writes its own PID and heartbeat messages to:

- `python_process.pid`
- `python_process.log`

Run the files manually first if needed:

```bat
run.bat
```

In another terminal:

```bat
type python_process.pid
type python_process.log
```

Stop it with:

```bat
stop.bat
```

The Agent stores the PID of the foreground `cmd.exe` wrapper started for
`run.bat`. Because the Python process stays in the foreground, the wrapper
also exits when the Python process exits, so PID liveness is useful for the
Agent's local smoke test.
