# Python 프로세스 제어 스크립트 가이드

Agent가 Python 프로젝트를 제어하려면 `PYTHON_PROJECT_PATH` 아래에 다음 세
파일이 있어야 합니다.

```text
<python-project>/
  start.bat
  stop.bat
  restart.bat
  main.py
  .agent.pid          # 실행 중일 때 Python이 생성
```

## Agent와 스크립트의 약속

1. `start.bat`은 Python 프로세스를 foreground로 실행해야 합니다.
2. Python 프로그램은 시작 직후 자신의 PID를 `.agent.pid`에 기록해야 합니다.
3. `stop.bat`은 Agent가 전달하는 첫 번째 인자 `%~1`을 PID로 사용해야 합니다.
4. `restart.bat`도 기존 Python PID를 첫 번째 인자로 받습니다.
5. 성공 시 배치 파일은 종료 코드 `0`을 반환해야 합니다.
6. 실패 시 배치 파일은 `0`이 아닌 종료 코드를 반환해야 합니다.

Agent는 `start.bat` 실행 후 `.agent.pid`를 우선 읽고, 해당 PID가 실제 살아
있는 Python 프로세스인지 검증합니다. PID 파일이 없으면 기존 프로세스 탐색
방식으로 보완하지만, 운영 환경에서는 PID 파일 사용을 권장합니다.

## start.bat

`start.bat`은 `start` 명령으로 별도 창이나 detached 프로세스를 만들지 말고
Python을 foreground로 실행해야 합니다.

```bat
@echo off
setlocal
cd /d "%~dp0"

py -3 main.py
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo Python process failed. exitCode=%EXIT_CODE%
    exit /b %EXIT_CODE%
)

endlocal
exit /b 0
```

Python 프로그램은 시작 시 PID를 기록합니다.

```python
from pathlib import Path
import os

PID_FILE = Path(__file__).resolve().parent / ".agent.pid"
PID_FILE.write_text(str(os.getpid()), encoding="ascii")

try:
    run_application()
finally:
    PID_FILE.unlink(missing_ok=True)
```

## stop.bat

Agent는 현재 관리 중인 Python PID를 첫 번째 인자로 전달합니다.

```bat
@echo off
setlocal
cd /d "%~dp0"

set "PYTHON_PID=%~1"
if not defined PYTHON_PID (
    echo Python PID argument is required.
    exit /b 2
)

taskkill /PID %PYTHON_PID% /T /F >nul 2>&1
if errorlevel 1 (
    echo Failed to stop Python process PID %PYTHON_PID%.
    exit /b 1
)

del /q ".agent.pid" >nul 2>&1
endlocal
exit /b 0
```

`taskkill` 실패를 숨긴 채 `exit /b 0`을 반환하면 Agent가 중지 실패를 정상
종료로 오인하므로, 반드시 종료 코드를 확인해야 합니다.

## restart.bat

재시작은 기존 PID로 중지한 뒤 잠시 기다리고 `start.bat`을 foreground로
호출합니다.

```bat
@echo off
setlocal
cd /d "%~dp0"

call stop.bat %~1
if errorlevel 1 (
    echo Restart failed during stop.
    exit /b %errorlevel%
)

timeout /t 3 /nobreak >nul
call start.bat
set "EXIT_CODE=%ERRORLEVEL%"
endlocal
exit /b %EXIT_CODE%
```

`start.bat`이 성공적으로 실행되면 Python이 새 PID를 `.agent.pid`에 기록하고,
Agent가 새 PID를 확인하여 `TB_M26_MODEL_PROCESS.PROCESS_PID`에 저장합니다.

## 수동 실행

수동 테스트 시에도 동일한 스크립트를 사용할 수 있습니다.

```bat
start.bat
type .agent.pid
stop.bat 49200
```

Agent가 실행하는 경우에는 다음과 같은 호출이 됩니다.

```text
cmd.exe /c start.bat
cmd.exe /c stop.bat 49200
cmd.exe /c restart.bat 49200
```

