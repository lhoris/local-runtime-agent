# Local Runtime Agent

Spring Boot 기반 PC 단위 Agent. Python AI 모델 프로세스를 자동으로 기동/중지/모니터링하고,
중앙 DB와 폴링 동기화하며, 중앙 서버 REST API를 통해 상태 조회 및 명령을 처리한다.

> 부트스트랩 README입니다. 상세 문서는 Task #16에서 확장됩니다.

## 기술 스택

| 항목 | 선택 |
|------|------|
| 언어 | Java 21 (LTS) |
| 프레임워크 | Spring Boot 3.4.1 |
| 빌드 도구 | Maven (3.9+) |
| DB 마이그레이션 | Flyway |
| 데이터베이스 | PostgreSQL (운영) / H2 (테스트) |

## 패키지 구조

```
com.lra
├── LocalRuntimeAgentApplication   # Spring Boot 엔트리포인트
├── agent    # 프로세스 관리, 상태 머신, 헬스체크, DB 동기화, 파라미터, 메인 루프
├── server   # 중앙 서버 REST API
├── common   # 공용 DTO/enum/상수/예외/유틸
└── db       # JPA 엔티티 및 리포지토리 (스키마는 Flyway로 관리)
```

## 빌드 및 실행

```bash
# 빌드
./mvnw clean package        # Windows: mvnw.cmd clean package

# 실행
./mvnw spring-boot:run
```

## 개발 상태

MVP 개발 중 (목표: 6주). 진행 현황은 태스크 보드 참고.
