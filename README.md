# Local Runtime Agent

Spring Boot 기반 PC 단위 Agent. 로컬 Python AI 모델 프로세스를 자동으로 기동/중지/재시작·모니터링하고,
중앙 DB와 폴링 동기화하며, 중앙 서버 REST API를 통해 상태 조회 및 명령을 처리한다.
Windows Service / Linux systemd 로 상시 구동하도록 설계되었다.

## 주요 기능

1. **프로세스 관리** — `ProcessBuilder` 기반 start/stop/restart, 크로스플랫폼 PID 추출
2. **헬스 모니터링** — PID liveness 체크 + 비정상 종료(crash) 감지
3. **DB 명령 폴링** — 기본 30초 주기로 대기 명령(START/STOP/RESTART) 처리
4. **파라미터 관리** — JSON 기반 동적 파라미터, 재시작 시 적용
5. **상태 머신** — STOPPED → STARTING → RUNNING → STOPPING, 이상 시 CRASHED
6. **자동 재시작** — crash 감지 시 재시작 횟수 한도 내 자동 복구

## 기술 스택

| 항목 | 선택 |
|------|------|
| 언어 | Java 21 (LTS) |
| 프레임워크 | Spring Boot 3.4.1 |
| 빌드 도구 | Maven Wrapper (`./mvnw`, 3.9+) |
| DB 스키마 관리 | 배포자가 수동 SQL로 관리 |
| 데이터베이스 | MariaDB 11.8 (운영) / H2 (테스트) |

## 패키지 구조

```
com.lra
├── LocalRuntimeAgentApplication   # Spring Boot 엔트리포인트
├── agent    # 프로세스 관리, 상태 머신, 헬스체크, DB 동기화, 파라미터, 메인 루프
├── server   # 중앙 서버 REST API
├── common   # 공용 DTO/enum/상수/예외/유틸
└── db       # JPA 엔티티 및 리포지토리
```

## 빌드 및 실행

```bash
# 빌드
./mvnw clean package        # Windows: mvnw.cmd clean package

# 실행
./mvnw spring-boot:run
```

### 테스트

```bash
./mvnw test        # 69 tests, 0 failures (4 disabled) (H2 인메모리 사용, MariaDB 11.8 불필요)
```

단위 테스트와 E2E 통합 테스트(`SystemIntegrationTest`, 4개 시나리오: 상태 등록/폴링, 명령 발행/실행,
파라미터 변경 후 재시작, crash 감지/자동 재시작)를 포함한다.

## 설정 (Configuration)

Spring 프로필별 설정 파일을 제공한다. 프로필 미지정 시 `application.yml`(기본값)만 적용된다.

| 파일 | 프로필 | 용도 |
|------|--------|------|
| `application.yml` | (공통) | 기본값, 환경변수 바인딩 |
| `application-dev.yml` | `dev` | 로컬 MariaDB 11.8, SQL 로깅, DEBUG |
| `application-prod.yml` | `prod` | 환경변수 필수, TLS 옵션, INFO |
| `application-test.yml` | `test` | H2 인메모리, Hibernate 테스트 스키마 |

### 프로필 활성화

```bash
# 환경변수
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# 또는 JVM 인자
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### 환경변수

`.env.example`를 `.env`로 복사해 값을 채운다(`.env`는 git-ignore 대상).

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | (없음) | 활성 프로필 (`dev`/`prod`/`test`) |
| `DB_HOST` | `localhost` | MariaDB 11.8 호스트 |
| `DB_PORT` | `3306` | MariaDB 11.8 포트 |
| `DB_NAME` | `agent_db` | 데이터베이스명 |
| `DB_USER` | (필수) | DB 사용자 |
| `DB_PASSWORD` | (필수) | DB 비밀번호 |
| `TB_M26_AGENT.IP_ADDRESS` | - | 로컬 IP와 일치하는 Agent 식별자 자동 조회 |
| `AGENT_POLLING_INTERVAL_SEC` | `30` | DB 폴링 주기(초) |
| `AGENT_HEALTH_CHECK_INTERVAL_SEC` | `30` | 헬스체크 주기(초) |
| `SERVER_PORT` | `8080` | HTTP 포트 |

운영(prod)에서는 `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`가 필수이며 기본값이 없다.
TLS는 `SERVER_SSL_ENABLED=true`와 키스토어 관련 변수로 활성화한다. 전체 목록은 `.env.example` 참고.

### 로그

기본 로그 파일은 `logs/application.log`(`LOG_FILE`로 변경 가능). 콘솔에도 함께 출력된다.

## 배포 (Deployment)

서비스로 배포하는 방법(Linux systemd 포함)은 [`DEPLOYMENT.md`](DEPLOYMENT.md)를 참고한다.

```bash
./mvnw clean package
sudo scripts/linux/install-service.sh
systemctl status local-runtime-agent
```

- **Linux**: `scripts/linux/install-service.sh` (systemd) — 구현 완료
- **Windows**: Windows Service (WinSW 래퍼) — 설계 완료, 스크립트는 향후 제공 (`DEPLOYMENT.md` 참고)

## 아키텍처

전체 설계는 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) 참고 (12개 섹션: 개요, Loop Engineering 철학,
시스템 아키텍처, 컴포넌트 설계, DB 스키마, API 명세, 상태/명령 흐름, Hook/Adapter 확장, 배포·운영,
장애·보안, MVP 범위, 로드맵). 문서 상단에 Quick Start 요약이 있다.

## 개발 상태

MVP 기능 구현 완료 — 프로세스 관리·모니터링·자동 재시작·DB 폴링·파라미터 관리 및 통합 테스트(4/4 시나리오).
테스트 70건 전부 통과. 목표 기간 6주. 진행 현황은 태스크 보드 참고.

## 팀

- **Architecture Lead** — 설계 및 의사결정
- **Backend Implementers** — 프로세스/상태/동기화/파라미터/서버 API 병렬 구현
- **DevOps** — 배포(systemd) 및 테스트
