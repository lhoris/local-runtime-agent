# Python 제어 배치 스크립트 작성 가이드

이 문서는 `local-runtime-agent`가 Python 프로세스를 정확하게 시작하고,
중지하고, 재시작할 수 있도록 `start.bat`, `stop.bat`, `restart.bat`을
작성하는 기준을 설명합니다.

## 1. 반드시 지켜야 하는 계약

Python 프로젝트 디렉터리는 다음 파일을 포함해야 합니다.

```text
<python-project>/
  start.bat
  stop.bat
  restart.bat
  <python-entrypoint>.py
  .agent.pid          # 실행 중에만 존재하는 실제 Python PID 파일
```

Agent는 다음 순서로 실제 Python PID를 확인합니다.

1. `.agent.pid`를 읽고 실제 살아 있는 `python.exe`인지 확인합니다.
2. `start.bat`에서 Python 실행 명령을 읽습니다.
3. `cmd.exe` 하위 프로세스 중 Python 명령줄이 일치하는 프로세스를 찾습니다.
4. 전체 Python 프로세스와 프로젝트 경로를 보조적으로 확인합니다.

`cmd.exe` PID, `start.bat` PID, Miniconda 활성화용 PID를
`PROCESS_PID`에 저장하면 안 됩니다. 저장 대상은 실제 `python.exe` PID입니다.

## 2. Miniconda start.bat 표준 예시

다음과 같이 Python을 foreground로 실행해야 합니다.

```bat
@echo off
setlocal
cd /d "%~dp0"

call C:\ProgramData\miniconda3\Scripts\activate.bat base
if errorlevel 1 (
    echo Miniconda environment activation failed. errorlevel=%ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

python "apps\std1_publish\run.py" --config "apps\std1_publish\config.json"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo Python process failed. exitCode=%EXIT_CODE%
)

endlocal
exit /b %EXIT_CODE%
```

다음 패턴은 사용하지 않습니다.

```bat
start python apps\std1_publish\run.py
start "Python" python apps\std1_publish\run.py
```

`start`는 별도 프로세스를 만들고 `start.bat`을 즉시 종료하므로 Agent가
부모-자식 관계를 추적하기 어려워집니다. Python 명령은 반드시 foreground로
실행해야 합니다.

## 3. Python 프로그램의 PID 파일

Python 프로그램은 시작 직후 자신의 PID를 기록하고, 종료할 때 파일을
삭제해야 합니다.

```python
import os
from pathlib import Path

PID_FILE = Path(__file__).resolve().parent / ".agent.pid"

def write_pid_file() -> None:
    PID_FILE.write_text(str(os.getpid()), encoding="ascii")

def remove_pid_file() -> None:
    PID_FILE.unlink(missing_ok=True)

def main() -> int:
    write_pid_file()
    try:
        run_application()
        return 0
    finally:
        remove_pid_file()
```

PID 파일에는 숫자 하나만 저장합니다.

```text
34156
```

로그 메시지나 여러 줄의 텍스트를 PID 파일에 저장하면 안 됩니다.

## 4. stop.bat 표준 예시

Agent는 현재 DB에 저장된 Python PID를 첫 번째 인자로 전달합니다.

```bat
@echo off
setlocal
cd /d "%~dp0"

set "PYTHON_PID=%~1"
if not defined PYTHON_PID (
    echo Python PID argument is required. Example: stop.bat 34156
    exit /b 2
)

echo Stopping Python process PID %PYTHON_PID%...
taskkill /PID %PYTHON_PID% /T /F >nul 2>&1
if errorlevel 1 (
    echo Failed to stop Python process PID %PYTHON_PID%.
    exit /b 1
)

del /q ".agent.pid" >nul 2>&1
endlocal
exit /b 0
```

종료코드는 반드시 지켜야 합니다.

| 결과 | 종료코드 |
|---|---:|
| 정상 중지 | `0` |
| PID 누락 | `2` |
| `taskkill` 실패 | `1` |

중지에 실패했는데 `exit /b 0`을 반환하면 Agent가 정상 중지로 오인합니다.

## 5. restart.bat 표준 예시

재시작은 Agent가 전달한 기존 PID를 먼저 중지한 뒤 새 Python 프로세스를
시작해야 합니다.

```bat
@echo off
setlocal
cd /d "%~dp0"

call stop.bat %~1
if errorlevel 1 (
    echo Restart failed during stop.
    exit /b %ERRORLEVEL%
)

timeout /t 3 /nobreak >nul
call start.bat
set "EXIT_CODE=%ERRORLEVEL%"

endlocal
exit /b %EXIT_CODE%
```

`restart.bat`은 새 PID를 직접 반환할 필요가 없습니다. `start.bat`과
Python 프로그램이 `.agent.pid`를 만들면 Agent가 새 PID를 탐색하여 DB에
저장합니다.

## 6. Agent 로그 확인

START 요청 후 다음 로그를 순서대로 확인합니다.

```text
Python PID discovery started
startBatPythonCommand=python "apps\std1_publish\run.py" --config "apps\std1_publish\config.json"
Python PID discovery waiting
cmd child Python candidate
Python command-line candidate
cmd child Python matched
pythonPid=34156
```

정상적으로 찾으면 다음 값이 실제 Python PID와 같아야 합니다.

```text
pythonPid=34156
PROCESS_PID=34156
```

다음과 같은 값은 저장 대상이 아닙니다.

```text
cmdPid=19644
conda.exe PID
activate.bat PID
```

탐색에 실패하면 `Python PID discovery timed out` 로그와 함께 명령이
실패합니다. 이 경우 다음 항목을 확인합니다.

1. `startBatPythonCommand`에 실제 Python 실행 라인이 표시되는지 확인합니다.
2. `run.py`와 `config.json` 경로가 실제 프로세스 command line과 같은지 확인합니다.
3. `start.bat`에서 `start` 명령을 사용하지 않았는지 확인합니다.
4. Python이 `.agent.pid`를 프로젝트 디렉터리에 생성하는지 확인합니다.
5. `PROCESS_PID`에 `cmd.exe` PID가 이미 저장되어 있으면 START 전에 초기화합니다.

## 7. 수동 테스트

프로젝트 디렉터리에서 다음 순서로 테스트합니다.

```bat
start.bat
type .agent.pid
tasklist /FI "PID eq 34156"
stop.bat 34156
if errorlevel 1 echo STOP FAILED
```

Agent 명령 테스트 후에는 다음 SQL로 결과를 확인합니다.

```sql
SELECT MODEL_PROCESS_ID,
       PYTHON_PROJECT_PATH,
       PYTHON_COMMAND,
       PROCESS_STATUS,
       PROCESS_PID,
       ERROR_MESSAGE
FROM TB_M26_MODEL_PROCESS
WHERE MODEL_PROCESS_ID = 2;
```

정상 시작 결과는 `PROCESS_STATUS = 'RUNNING'`이고,
`PROCESS_PID`는 `tasklist`에서 확인한 실제 Python PID와 같아야 합니다.
