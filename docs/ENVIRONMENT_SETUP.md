# 환경 변수 설정 가이드

이 프로젝트는 데이터베이스 연결 정보를 환경 변수로 관리합니다.

> [!IMPORTANT]
> 환경 변수는 **필수**입니다. `.env` 파일을 생성하지 않으면 애플리케이션이 시작되지 않습니다.

## 설정 방법

1. `.env.example` 파일을 `.env`로 복사:
   ```bash
   cp .env.example .env
   ```

2. `.env` 파일을 열어 **실제 데이터베이스 정보로 수정**:
   ```bash
   # 예시 - 실제 값으로 변경하세요!
   DB_URL=jdbc:mysql://localhost:3306/ecommerce?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
   DB_USERNAME=your_actual_username
   DB_PASSWORD=your_actual_password
   DB_TEST_URL=jdbc:mysql://localhost:3306/test_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false
   ```

## 환경 변수 목록

| 변수명 | 설명 | 필수 여부 |
|--------|------|-----------|
| `DB_URL` | 메인 데이터베이스 URL | ✅ 필수 |
| `DB_USERNAME` | 데이터베이스 사용자명 | ✅ 필수 |
| `DB_PASSWORD` | 데이터베이스 비밀번호 | ✅ 필수 |
| `DB_TEST_URL` | 테스트 데이터베이스 URL | ✅ 필수 |

## 주의사항

> [!WARNING]
> - `.env` 파일은 **절대 Git에 커밋하지 마세요**. 이미 `.gitignore`에 포함되어 있습니다.
> - `.env.example`의 값은 **플레이스홀더**입니다. 실제 값으로 변경해야 합니다.
> - 환경 변수가 설정되지 않으면 애플리케이션이 시작 시 오류가 발생합니다.

> [!TIP]
> - 실제 운영 환경에서는 시스템 레벨 환경 변수나 비밀 관리 도구를 사용하세요.
> - 테스트 데이터베이스는 자동으로 생성됩니다 (`createDatabaseIfNotExist=true`).
