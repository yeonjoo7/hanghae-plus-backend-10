# 환경 변수 설정 가이드

## 개요
데이터베이스 접속 정보와 같은 민감한 정보를 보호하기 위해 환경 변수를 사용합니다.

## 환경 변수 목록
- `DB_URL`: 데이터베이스 접속 URL
- `DB_USERNAME`: 데이터베이스 사용자명
- `DB_PASSWORD`: 데이터베이스 비밀번호

## 설정 방법

### 1. IntelliJ IDEA에서 실행할 때
Run Configuration에서 환경 변수 설정:
1. Run > Edit Configurations 메뉴 선택
2. Environment variables 필드에 입력
   ```
   DB_URL=jdbc:mysql://localhost:3306/ecommerce?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8;DB_USERNAME=your_username;DB_PASSWORD=your_password
   ```

### 2. 터미널에서 Gradle로 실행할 때
```bash
export DB_URL="jdbc:mysql://localhost:3306/ecommerce?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
export DB_USERNAME="your_username"
export DB_PASSWORD="your_password"
./gradlew bootRun
```

### 3. .env 파일 사용 (추천)
1. `.env.example` 파일을 복사하여 `.env` 파일 생성
   ```bash
   cp .env.example .env
   ```
2. `.env` 파일에서 실제 값으로 수정
3. IntelliJ IDEA에서 EnvFile 플러그인 설치하여 .env 파일 자동 로드

### 4. JAR 파일 실행 시
```bash
java -jar build/libs/ecommerce-api.jar \
  --DB_URL="jdbc:mysql://localhost:3306/ecommerce?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8" \
  --DB_USERNAME="your_username" \
  --DB_PASSWORD="your_password"
```

## 주의사항
- `.env` 파일은 절대로 Git에 커밋하지 마세요 (이미 .gitignore에 추가됨)
- 프로덕션 환경에서는 AWS Secrets Manager, HashiCorp Vault 등의 시크릿 관리 도구 사용을 권장합니다