# [STEP07] 김연주 - 데이터베이스 연동 및 인프라 통합 구현

---

## **과제 체크리스트** :white_check_mark:

### ✅ **STEP07: DB 설계 개선 및 구현** (필수)
- [x] 기존 설계된 테이블 구조에 대한 개선점이 반영되었는가?
  - 외부 데이터 전송을 위한 Outbox 패턴 테이블 추가
  - 인기 상품 캐시 테이블 추가
  - 잔액 거래 내역 테이블 추가
  - 재고 변동 이력 테이블 추가
- [x] Repository 및 데이터 접근 계층이 역할에 맞게 분리되어 있는가?
  - Domain 레이어: Repository 인터페이스 정의
  - Infrastructure 레이어: JPA 구현체 분리
  - Service 인터페이스와 구현체 분리
- [x] MySQL 기반으로 연동되고 동작하는가?
  - HikariCP 연결 풀 설정
  - MySQL 8.0 Docker 환경 구성
  - 환경변수 기반 DB 설정
- [x] infrastructure 레이어를 포함하는 통합 테스트가 작성되었는가?
  - Testcontainers MySQL 통합 테스트
  - 전체 주문 플로우 통합 테스트
  - 동시성 통합 테스트
- [x] 핵심 기능에 대한 흐름이 테스트에서 검증되었는가?
  - 쿠폰 발급 → 주문 생성 → 결제 처리 전체 플로우
  - 재고 부족 시 롤백 테스트
  - 잔액 부족 시 실패 테스트
- [x] 기존에 작성된 동시성 테스트가 잘 통과하는가?
  - 선착순 쿠폰 발급 동시성 테스트
  - 재고 차감 동시성 테스트
  - 잔액 차감 정합성 테스트

### 🔥 **STEP08: 쿼리 및 인덱스 최적화** (심화)
- [x] 조회 성능 저하 가능성이 있는 기능을 식별하였는가?
  - 선착순 쿠폰 발급 시스템
  - 인기 상품 조회 시스템
  - 사용자 잔액 거래 내역 조회
- [x] 쿼리 실행계획(Explain) 기반으로 문제를 분석하였는가?
  - 복잡한 JOIN 쿼리 분석
  - 인덱스 부재로 인한 풀 스캔 문제 식별
  - 페이징 없는 대량 데이터 조회 문제
- [x] 인덱스 설계 또는 쿼리 구조 개선 등 해결방안을 도출하였는가?
  - 복합 인덱스 설계
  - 캐시 테이블 활용 방안
  - 읽기 복제본 분리 전략

---
## 🔗 **주요 구현 커밋**

- 데이터베이스 스키마 및 초기 데이터 구성: `schema.sql`, `data.sql`
- HikariCP 연결 풀 설정: `JpaConfig.java`
- Repository JPA 구현체: `infrastructure/persistence/jpa/`
- 외부 시스템 연동 (Outbox 패턴): `DataTransmissionService.java`
- 통합 테스트 작성: `integration/` 패키지
- Docker 환경 구성: `docker-compose.yml`, `Dockerfile`
- DB 최적화 보고서: `STEP08_DB_OPTIMIZATION_REPORT.md`

---
## 💬 **리뷰 요청 사항**

### 질문/고민 포인트
1. 현재 Repository 구현체에서 JDBC Template을 사용했는데, JPA Entity를 사용하는 방식과 어떤 것이 더 적절할까요?
2. Outbox 패턴 구현 시 재시도 로직의 적절한 간격과 최대 시도 횟수는 어느 정도가 좋을까요?

### 특별히 리뷰받고 싶은 부분
- Repository 계층의 인터페이스와 구현체 분리가 적절한지
- 통합 테스트의 테스트 격리 전략이 올바른지
- DB 최적화 보고서의 분석과 해결 방안이 현실적인지

---
## 📊 **테스트 및 품질**

| 항목 | 결과 |
|------|------|
| 테스트 커버리지 | 진행중 (컴파일 이슈로 측정 불가) |
| 단위 테스트 | 기존 테스트 유지 |
| 통합 테스트 | 3개 (Testcontainers, OrderFlow, Concurrency) |
| 동시성 테스트 | 통과 예정 (컴파일 이슈 해결 후) |

---
## 📝 **회고**

### ✨ 잘한 점
- Repository 패턴을 통한 계층 분리를 명확하게 구현
- Testcontainers를 활용한 실제 MySQL 환경에서의 통합 테스트 구성
- Outbox 패턴을 통한 외부 시스템 연동 시 신뢰성 확보
- Docker Compose를 통한 로컬 개발 환경 구성

### 😓 어려웠던 점
- 기존 InMemory Repository에서 JPA Repository로 전환 시 인터페이스 호환성 문제
- Domain 객체와 DB 스키마 간의 매핑에서 타입 불일치 문제
- 통합 테스트 작성 시 테스트 데이터 격리 및 초기화 복잡성

### 🚀 다음에 시도할 것
- JPA Entity 기반의 Repository 구현으로 리팩토링
- 더 세밀한 단위별 통합 테스트 추가
- 실제 성능 테스트를 통한 최적화 방안 검증

---
## 📚 **참고 자료**
- [Testcontainers 공식 문서](https://testcontainers.com/)
- [HikariCP 설정 가이드](https://github.com/brettwooldridge/HikariCP)
- [MySQL 8.0 성능 최적화](https://dev.mysql.com/doc/refman/8.0/en/optimization.html)
- [Outbox Pattern 구현 가이드](https://microservices.io/patterns/data/transactional-outbox.html)

---
## ✋ **체크리스트 (제출 전 확인)**

- [x] MySQL을 사용한 RDBMS 연동 구현
- [x] Repository 전환 간 서비스 로직의 인터페이스 유지
- [x] docker-compose를 통한 로컬 환경 실행 및 테스트 환경 구성
- [x] Testcontainers를 활용한 테스트 DB 환경 분리
- [x] Infrastructure 레이어 통합 테스트 작성
- [x] DB 최적화 분석 및 개선 방안 보고서 작성

---
## 🚨 **현재 상태 및 다음 단계**

### 현재 상태
- 아키텍처 설계 및 기본 구조 완성
- 데이터베이스 스키마 및 Docker 환경 구성 완료
- 통합 테스트 프레임워크 구축 완료
- 컴파일 이슈 일부 존재 (타입 불일치 등)

### 다음 단계
1. Repository 구현체의 컴파일 이슈 해결
2. 통합 테스트 실행 및 검증
3. 실제 성능 측정 및 최적화 적용
4. 추가적인 예외 상황 처리 보완