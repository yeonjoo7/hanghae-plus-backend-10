# 이커머스 시스템 AWS 인프라 설계 (중소 규모)

## 목차
1. [개요](#개요)
2. [아키텍처 다이어그램](#아키텍처-다이어그램)
3. [네트워크 구성](#네트워크-구성)
4. [컴퓨팅 계층](#컴퓨팅-계층)
5. [데이터베이스 계층](#데이터베이스-계층)
6. [캐싱 계층](#캐싱-계층)
7. [스토리지](#스토리지)
8. [보안](#보안)
9. [모니터링 및 로깅](#모니터링-및-로깅)
10. [CI/CD](#cicd)
11. [비용 최적화](#비용-최적화)
12. [확장 계획](#확장-계획)

---

## 개요

### 설계 목표
- **비용 효율성**: 최소 비용으로 안정적인 서비스 운영
- **단순성**: 운영 복잡도 최소화
- **성능**: API 응답 시간 요구사항 충족 (상품 조회 100ms, 주문 처리 500ms)
- **확장성**: 트래픽 증가 시 쉽게 확장 가능한 구조
- **안정성**: 99% 이상의 서비스 가용성 보장

### 예상 트래픽 (중소 규모)
- 일일 활성 사용자: 1,000 ~ 5,000명
- 피크 시간 동시 접속자: 100 ~ 500명
- 초당 평균 요청: 10 ~ 50 TPS
- 피크 시간 최대 요청: 100 TPS
- 주문 처리: 10 ~ 20 TPS

---

## 아키텍처 다이어그램

```
                           [Route 53]
                               |
                         [CloudFront]
                               |
                          [WAF Basic]
                               |
                        [ALB - Public]
                               |
               +---------------+---------------+
               |                               |
    [Public Subnet - AZ1]          [Public Subnet - AZ2]
               |                               |
         [NAT Gateway]                         |
               |                               |
               +---------------+---------------+
                               |
               +---------------+---------------+
               |                               |
    [Private Subnet - AZ1]         [Private Subnet - AZ2]
               |                               |
        [EC2 Auto Scaling]                     |
        - Spring Boot App                      |
        - Min: 2, Max: 6                       |
               |                               |
               +---------------+---------------+
                               |
               +---------------+---------------+
               |                               |
          [RDS MySQL]                    [ElastiCache]
        - Single-AZ                      - Single Node
        - db.t4g.medium                  - cache.t4g.small
        - Automated Backup               - Daily Snapshot
               |                               |
               +---------------+---------------+
                               |
                       +-------+-------+
                       |               |
                  [S3 Bucket]    [CloudWatch]
                  - Backups      - Basic Monitoring
                  - Logs         - Essential Alarms
```

### 주요 간소화 사항
- **Single NAT Gateway**: 비용 절감 (HA는 추후 확장 시 고려)
- **Single-AZ RDS**: Multi-AZ 대신 자동 백업으로 대응
- **Single ElastiCache Node**: 클러스터 대신 단일 노드
- **CloudFront Optional**: 필요 시 추가 (정적 파일 많을 경우)
- **Internal ALB 제거**: 단순 구조로 불필요

---

## 네트워크 구성

### VPC 설계 (간소화)

```yaml
VPC Configuration:
  CIDR: 10.0.0.0/16
  Region: ap-northeast-2 (Seoul)
  Availability Zones: 2 (ap-northeast-2a, ap-northeast-2c)

Subnet Design:
  Public Subnets:
    - AZ1: 10.0.1.0/24  # ALB, NAT Gateway
    - AZ2: 10.0.2.0/24  # ALB (HA)

  Private Subnets (Application & Database):
    - AZ1: 10.0.11.0/24  # EC2, RDS, ElastiCache
    - AZ2: 10.0.12.0/24  # EC2 (Auto Scaling)

# 비용 절감: Database 전용 서브넷 제거, Internal 서브넷 제거
```

### 네트워크 구성 요소

#### Internet Gateway
```yaml
Internet Gateway:
  Purpose: Public Subnet의 인터넷 연결
  Attachment: VPC에 연결
  Cost: 무료
```

#### NAT Gateway (간소화)
```yaml
NAT Gateway:
  Count: 1 (비용 절감)
  Placement: Public Subnet AZ1 (10.0.1.0/24)
  Purpose: Private Subnet의 아웃바운드 인터넷 연결

  Cost Impact:
    - Single NAT: ~$32/월
    - Dual NAT (HA): ~$65/월
    - Savings: ~$33/월 (50% 절감)

  Limitation:
    - AZ1 장애 시 인터넷 아웃바운드 불가
    - 서비스 자체는 ALB + AZ2로 정상 동작

  Alternative (추가 비용 절감):
    - NAT Instance (t4g.nano): ~$3/월
    - 단, 성능 제한 및 관리 필요
```

#### Route Tables (간소화)
```yaml
Public Route Table:
  Routes:
    - Destination: 0.0.0.0/0
      Target: Internet Gateway
  Associated Subnets:
    - 10.0.1.0/24 (AZ1)
    - 10.0.2.0/24 (AZ2)

Private Route Table:
  Routes:
    - Destination: 0.0.0.0/0
      Target: NAT Gateway (AZ1만)
  Associated Subnets:
    - 10.0.11.0/24 (AZ1)
    - 10.0.12.0/24 (AZ2)
```

### VPC 엔드포인트 (선택적 비용 절감)

```yaml
VPC Endpoints (NAT Gateway 비용 절감):
  S3 Gateway Endpoint:
    Type: Gateway (무료)
    Purpose: S3 접근 시 NAT Gateway 우회
    Savings: 데이터 전송 비용 절감

  ECR VPC Endpoint (Optional):
    Type: Interface (~$7/월)
    Purpose: 컨테이너 이미지 pull 시 NAT 우회
    Consideration: NAT 데이터 전송 비용 vs VPC Endpoint 비용 비교

  Recommendation: S3만 우선 적용
```

---

## 컴퓨팅 계층

### Application Load Balancer (ALB)

```yaml
Public ALB:
  Type: Application Load Balancer
  Scheme: Internet-facing
  Subnets: Public Subnets (AZ1, AZ2)
  Security Groups: ALB-SG
  Cost: ~$16/월 (base) + ~$10/월 (처리량) ≈ $26/월

  Listeners:
    HTTPS:443:
      Protocol: HTTPS
      Certificate: ACM Certificate (무료)
      Default Action: Forward to Target Group

    HTTP:80:
      Protocol: HTTP
      Default Action: Redirect to HTTPS

  Target Groups:
    Application-TG:
      Protocol: HTTP
      Port: 8080
      Health Check:
        Path: /actuator/health
        Interval: 30s
        Timeout: 5s
        Healthy Threshold: 2
        Unhealthy Threshold: 2
      Deregistration Delay: 30s  # 빠른 배포
      Stickiness:
        Enabled: true
        Type: lb_cookie
        Duration: 3600s

  Settings:
    Idle Timeout: 60s
    Cross-Zone Load Balancing: Enabled
    Access Logs: Disabled (비용 절감, 필요시만 활성화)
```

### Auto Scaling Group (간소화)

```yaml
Launch Template:
  Name: ecommerce-app-lt
  AMI: Amazon Linux 2023 (최신 버전)
  Instance Type: t3.small  # 중소 규모로 축소

  User Data:
    #!/bin/bash
    yum update -y
    yum install -y java-17-amazon-corretto docker
    systemctl start docker
    systemctl enable docker

    # Pull application from ECR
    aws ecr get-login-password --region ap-northeast-2 | \
      docker login --username AWS --password-stdin <ECR_URI>
    docker pull <ECR_URI>/ecommerce-app:latest

    # Run application
    docker run -d \
      -p 8080:8080 \
      -e SPRING_PROFILES_ACTIVE=prod \
      -e DB_HOST=${DB_HOST} \
      -e REDIS_HOST=${REDIS_HOST} \
      --name ecommerce-app \
      <ECR_URI>/ecommerce-app:latest

  IAM Role: EC2-App-Role
  Security Groups: App-SG

  Monitoring:
    Detailed Monitoring: Disabled (비용 절감, 1분 → 5분 간격)
    CloudWatch Logs: Enabled

  Storage:
    Root Volume:
      Type: gp3
      Size: 20 GB  # 30GB → 20GB 축소
      Encrypted: true

Auto Scaling Group:
  Name: ecommerce-app-asg
  Launch Template: ecommerce-app-lt
  VPC Subnets: Private App Subnets (AZ1, AZ2)
  Target Group: Application-TG

  Capacity:
    Minimum: 2  # HA 보장
    Desired: 2  # 기본 2대 운영
    Maximum: 6  # 10 → 6으로 축소

  Health Check:
    Type: ELB
    Grace Period: 180s  # 300s → 180s 단축

  Scaling Policies (단순화):
    Target Tracking:
      - Metric: CPU Utilization
        Target: 60%  # 70% → 60% (더 빠른 대응)
        Cooldown: 180s

    Scheduled Scaling (Optional):
      - Name: business-hours-scale-up
        Schedule: 0 9 * * MON-FRI  # 평일 오전 9시
        Desired Capacity: 3

      - Name: after-hours-scale-down
        Schedule: 0 22 * * *  # 매일 오후 10시
        Desired Capacity: 2

  Termination Policies:
    - OldestInstance
    - Default

Cost Impact:
  Baseline (2 instances): ~$30/월
  Average (2.5 instances): ~$38/월
  Peak (4 instances): ~$60/월
```

### EC2 인스턴스 타입 선택 (비용 최적화)

```yaml
Production Environment:
  Instance Type: t3.small
  vCPU: 2
  Memory: 2 GiB
  Network: Up to 5 Gbps
  Cost: ~$15/month per instance
  Baseline: 2 instances = ~$30/월

  Justification:
    - 중소 규모 트래픽 처리 충분
    - Spring Boot 애플리케이션 실행 가능
    - Burstable 성능으로 순간 트래픽 대응
    - 비용 50% 절감 (vs t3.medium)

  Performance Considerations:
    - JVM Heap: -Xmx1g (2GB 메모리에서 적절)
    - 동시 처리: 50-100 TPS 충분
    - 모니터링 후 필요시 t3.medium으로 업그레이드

Development/Staging:
  Instance Type: t3.micro
  vCPU: 2
  Memory: 1 GiB
  Cost: ~$7.5/month per instance
  Count: 1 instance only

Reserved Instance Option (비용 절감):
  Type: Standard RI, 1-year, No Upfront
  Instance: t3.small
  Cost: ~$10/month (33% 절감)
  Savings: ~$5/월 × 2대 = ~$10/월

Savings Plan Option (유연성):
  Type: Compute Savings Plan
  Commitment: $20/월 (1-year)
  Savings: ~30%
  Flexibility: 인스턴스 타입 변경 가능
```

---

## 데이터베이스 계층

### RDS (MySQL) - 비용 최적화

```yaml
RDS Configuration:
  Engine: MySQL 8.0
  Deployment: Single-AZ (비용 절감)
  Instance Class: db.t4g.medium  # ARM 기반 (저렴)

  Specifications:
    vCPU: 2
    Memory: 4 GiB
    Network: Up to 5 Gbps
    Storage Type: gp3
    Storage Size: 50 GB (Auto Scaling up to 200 GB)
    IOPS: 3000 (기본 포함)
    Storage Encryption: Enabled (KMS)
    Cost: ~$50/월 (vs Multi-AZ db.r6g.large ~$290/월)

  High Availability Strategy:
    Multi-AZ: Disabled (비용 절감)
    Backup: 자동 백업으로 복구 대응
    Recovery Time: ~15-30분 (수동 복구)
    Point-in-Time Recovery: 5분 단위

  Backup Configuration:
    Automated Backup: Enabled
    Backup Retention: 7 days
    Backup Window: 03:00-04:00 UTC (12:00-13:00 KST)
    Snapshot: Daily automatic
    Manual Snapshots: 주요 변경 전
    Final Snapshot: Enabled

  Maintenance:
    Maintenance Window: Sun 04:00-05:00 UTC (13:00-14:00 KST)
    Auto Minor Version Upgrade: Enabled (보안 패치)

  Parameter Group (최적화):
    character_set_server: utf8mb4
    collation_server: utf8mb4_unicode_ci
    max_connections: 200  # 500 → 200 (중소 규모 충분)
    innodb_buffer_pool_size: 2684354560  # 2.5 GB (60% of RAM)
    innodb_log_file_size: 268435456      # 256 MB
    slow_query_log: 1
    long_query_time: 2
    binlog_format: ROW
    binlog_expire_logs_seconds: 259200   # 3 days (7일 → 3일)

  Performance Insights:
    Enabled: false  # 비용 절감 (필요시만 활성화)
    Alternative: CloudWatch Logs + Slow Query Log

  Monitoring:
    Enhanced Monitoring: Disabled  # 비용 절감
    CloudWatch Alarms (필수만):
      - CPU > 80%
      - Free Storage < 5 GB
      - Connection Count > 150

  Security:
    VPC: Private Subnets (10.0.11.0/24)
    Security Group: DB-SG
    Public Accessibility: Disabled
    Encryption at Rest: Enabled (KMS)
    Encryption in Transit: Enabled (SSL)

  Cost Optimization:
    - Single-AZ: 50% 절감
    - t4g (Graviton2): 20% 절감 vs t3
    - Storage 축소: 50GB로 시작
    - Performance Insights: Disabled
    - Enhanced Monitoring: Disabled

  Upgrade Path:
    트래픽 증가 시:
      1. db.t4g.large (8GB RAM) - $100/월
      2. Multi-AZ 전환 - $200/월
      3. Read Replica 추가 - $250/월
```

### Backup & Recovery Strategy (비용 효율)

```yaml
Automated Backup:
  Frequency: Daily
  Retention: 7 days (최소 권장)
  Window: 03:00-04:00 UTC (Low traffic)

Manual Snapshots:
  Frequency: Weekly (주말)
  Retention: 30 days
  Trigger:
    - Major deployment
    - Data migration
    - Schema changes

Recovery Scenarios:
  Database Failure:
    - RTO: 15-30 minutes (restore from snapshot)
    - RPO: 5 minutes (point-in-time recovery)

  Data Corruption:
    - Restore to point-in-time
    - Validate data integrity
    - Switch application connection

  Cost: Backup Storage ~$5/월 (50GB × $0.095/GB)
```

---

## 캐싱 계층

### ElastiCache for Redis (간소화)

```yaml
ElastiCache Configuration:
  Engine: Redis 7.0
  Deployment: Single Node (비용 절감)
  Node Type: cache.t4g.small  # ARM 기반

  Specifications:
    vCPU: 2
    Memory: 1.37 GiB
    Network Performance: Up to 5 Gbps
    Cost: ~$24/월 (vs cache.r6g.large 4 nodes ~$541/월)

  Cluster Configuration:
    Replicas: 0 (Single Node)
    Multi-AZ: Disabled
    Automatic Failover: N/A

  High Availability Strategy:
    Daily Snapshots: 캐시 재구성용
    Warm-up: 애플리케이션 시작 시 주요 데이터 로드
    Fallback: 캐시 장애 시 DB 직접 조회

  Network:
    VPC: Private Subnets (10.0.11.0/24)
    Subnet Group: elasticache-subnet-group
    Security Group: Redis-SG

  Parameter Group:
    maxmemory-policy: allkeys-lru
    timeout: 300
    tcp-keepalive: 300
    maxmemory-samples: 5
    notify-keyspace-events: Ex  # Expiration events

  Backup:
    Snapshot Retention: 3 days  # 7일 → 3일
    Snapshot Window: 03:00-05:00 UTC
    Final Snapshot: Enabled

  Maintenance:
    Maintenance Window: Sun 05:00-06:00 UTC

  Cost Optimization:
    - Single Node: 캐시 웜업으로 대응
    - t4g (Graviton2): 20% 저렴
    - 작은 메모리: 핵심 데이터만 캐싱
    - 96% 비용 절감 (vs 원안)

  Upgrade Path:
    트래픽 증가 시:
      1. cache.t4g.medium (3.09 GiB) - $48/월
      2. cache.m6g.large (12.93 GiB) - $110/월
      3. Replication 추가 (HA) - $220/월

Cache Strategy (핵심 기능만):
  Priority 1 (필수):
    1. 쿠폰 재고 (동시성 제어):
       Key: coupon:stock:{couponId}
       TTL: Until expires
       Size: ~1KB per coupon
       Purpose: 동시성 문제 해결

    2. 분산 락 (재고 차감):
       Key: lock:stock:{productId}
       TTL: 10 seconds
       Purpose: 재고 동시성 제어

  Priority 2 (성능 향상):
    3. 인기 상품 정보:
       Key: product:{id}
       TTL: 10 minutes
       Size: Top 100 products (~500KB)
       Purpose: DB 부하 감소

    4. 사용자 세션:
       Key: session:{userId}
       TTL: 30 minutes
       Purpose: 로그인 상태 유지

  Memory Allocation (1.37 GiB):
    - 쿠폰/재고 락: 100 MB (우선순위)
    - 인기 상품: 500 MB
    - 세션: 500 MB
    - 여유: 270 MB

Monitoring (간소화):
  CloudWatch Metrics:
    - Memory Utilization > 90%
    - Evictions > 1000/min (문제 징후)
    - CPU Utilization > 80%
```

---

## 스토리지

### S3 Buckets (간소화)

```yaml
Combined Bucket (통합):
  Name: ecommerce-data-{account-id}
  Purpose: 로그, 백업 통합 관리 (비용 절감)

  Folder Structure:
    /logs/application/
    /logs/alb/  (필요시만)
    /backups/rds/
    /backups/redis/

  Configuration:
    Versioning: Suspended (로그), Enabled (백업)
    Encryption: SSE-S3 (무료)

    Lifecycle Policies:
      Logs:
        - Delete after 30 days  (365일 → 30일)

      Backups:
        - Transition to Glacier: 30 days  (90일 → 30일)
        - Delete after 90 days  (7년 → 90일)

  Cost: ~$2/월 (10GB storage)

  IAM Policy:
    - EC2 instances (write to /logs/)
    - RDS (write to /backups/)
    - Admin users (read all)

Static Assets (Optional):
  Name: ecommerce-static-{account-id}
  Purpose: 상품 이미지 등 정적 파일

  Configuration:
    Versioning: Suspended
    Encryption: SSE-S3
    Public Access: Via ALB or CloudFront (추후)

  Alternative: 초기에는 DB BLOB 저장도 고려
  Cost: ~$1/월 (usage-based)

Cost Optimization:
  - Audit bucket 제거 (초기 불필요)
  - 짧은 보관 기간
  - 단일 버킷으로 통합
  - SSE-S3 (KMS 대신, 무료)
```

---

## 보안

### IAM 역할 및 정책 (간소화)

```yaml
EC2 Instance Role (EC2-App-Role):
  Managed Policies:
    - AmazonEC2ContainerRegistryReadOnly  # ECR pull
    - CloudWatchLogsFullAccess  # 간소화

  Inline Policies:
    S3Access:
      - Action: s3:PutObject
        Resource: arn:aws:s3:::ecommerce-data-*/logs/*

    SecretsAccess:
      - Action: secretsmanager:GetSecretValue
        Resource: arn:aws:secretsmanager:*:secret:ecommerce/*

    RDSConnect:
      - Action: rds-db:connect
        Resource: RDS ARN

  Trust Policy:
    Service: ec2.amazonaws.com

Cost: IAM 무료
```

### Security Groups (필수만)

```yaml
ALB-SG:
  Inbound:
    - Port 443 (HTTPS) from 0.0.0.0/0
    - Port 80 (HTTP) from 0.0.0.0/0
  Outbound:
    - Port 8080 to App-SG

App-SG:
  Inbound:
    - Port 8080 from ALB-SG
    - Port 22 from MyIP (SSH, Bastion 대신)
  Outbound:
    - All traffic to 0.0.0.0/0  # 간소화

DB-SG:
  Inbound:
    - Port 3306 from App-SG
    - Port 3306 from MyIP (직접 접속, Bastion 제거)
  Outbound:
    - None

Redis-SG:
  Inbound:
    - Port 6379 from App-SG
  Outbound:
    - None
```

### WAF (단계적 도입)

```yaml
Phase 1 (초기 - WAF 없이 시작):
  Alternative: Application-level rate limiting
  - Spring Boot @RateLimit annotation
  - Redis 기반 rate limiting
  Cost: $0

Phase 2 (트래픽 증가 시):
  AWS WAF:
    Association: ALB only
    Cost: ~$12/월 ($5 WebACL + $1/rule × 2 + requests)

    Managed Rules (필수만):
      - AWS Managed Rules - Core Rule Set ($0 with WebACL)

    Custom Rules (최소):
      - Rate Limiting: 100 req/5min per IP
      - Login Rate Limit: 10 req/min

    Logging: Disabled (비용 절감)

Shield Standard (무료):
  Protection: Basic DDoS (Layer 3/4)
  Coverage: ALB, Route 53
  Cost: Free
  Note: 중소 규모에 충분
```

### Secrets Management (간소화)

```yaml
AWS Secrets Manager (필수만):
  Secrets:
    - rds/ecommerce/password
    - jwt/signing-key

  Cost: $0.40/secret × 2 = $0.80/월

  Rotation: Disabled initially (수동 관리)

Parameter Store (무료 티어):
  Parameters:
    - /ecommerce/prod/db-host
    - /ecommerce/prod/redis-host
    - /ecommerce/prod/env-config

  Type: String
  Cost: Free (< 10,000 parameters)
```

### 암호화 (필수만)

```yaml
Encryption at Rest:
  RDS: Default KMS (AWS managed)
  EBS: Default KMS (AWS managed)
  S3: SSE-S3 (무료)
  ElastiCache: Disabled (성능 고려)

Encryption in Transit:
  ALB → Client: TLS 1.2+ (ACM 인증서, 무료)
  EC2 → RDS: SSL (optional)
  EC2 → Redis: No TLS (성능 우선, VPC 내부)
  EC2 → S3: HTTPS

Cost: KMS (AWS managed keys) - 무료
```

### 네트워크 보안 (최소)

```yaml
VPC Flow Logs (Optional):
  Enabled: 문제 발생 시에만
  Cost: ~$5/월 (활성화 시)
  Initial: Disabled

GuardDuty (Optional):
  Enabled: 추후 활성화
  Cost: ~$5-10/월
  Initial: Disabled

보안 모범 사례:
  - Security Groups로 기본 보안
  - CloudWatch Logs로 애플리케이션 모니터링
  - 정기적인 패치 및 업데이트
  - AWS Trusted Advisor (무료 체크)
```

---

## 모니터링 및 로깅 (간소화)

### CloudWatch (필수만)

```yaml
CloudWatch Dashboard (통합):
  Name: ecommerce-overview

  Widgets (필수 메트릭만):
    인프라:
      - ALB Request Count & Response Time
      - EC2 CPU Utilization (평균)
      - RDS CPU & Connection Count
      - Redis Memory Usage

    애플리케이션:
      - Error Count (Metric Filter)
      - Order Success Rate (Custom Metric)

  Cost: 대시보드 무료 (< 50 metrics)

CloudWatch Alarms (필수만):
  Critical (P1):
    - ALB Unhealthy Targets > 0
      Action: SNS → Email
      Cost: $0.10/alarm

    - RDS CPU > 85%
      Action: SNS → Email

    - Application Error Rate > 10/min
      Action: SNS → Email

  Warning (P2):
    - RDS Storage < 10 GB
      Action: SNS → Email

    - Redis Memory > 90%
      Action: SNS → Email

  Total Alarms: 5개
  Cost: ~$0.50/월

CloudWatch Logs:
  Log Groups (필수만):
    - /aws/ec2/ecommerce-app
      Retention: 7 days  (30일 → 7일)
      Cost: ~$1/월

    - /aws/rds/instance/ecommerce-db/error
      Retention: 7 days
      Cost: ~$0.50/월

  Metric Filters (핵심만):
    - Application Errors (ERROR pattern)
    - Failed Orders

  Total Cost: ~$2/월

Logs Insights: 필요 시 수동 쿼리 (무료 티어 내)
```

### 모니터링 전략

```yaml
Phase 1 (초기):
  - CloudWatch 기본 메트릭 (무료)
  - 필수 알람 5개
  - 애플리케이션 로그 7일 보관
  Cost: ~$3/월

Phase 2 (성장 시):
  - Custom Metrics 추가
  - X-Ray 활성화 (샘플링 5%)
  - 로그 보관 기간 30일
  Cost: ~$15/월

무료 도구 활용:
  - Spring Boot Actuator (/health, /metrics)
  - Prometheus + Grafana (EC2에 설치)
  - AWS Trusted Advisor (무료 체크)
```

---

## CI/CD (간소화)

### CI/CD 전략

```yaml
Option 1: GitHub Actions (추천 - 무료):
  Workflow:
    1. Build & Test on GitHub
    2. Build Docker image
    3. Push to ECR
    4. SSH to EC2 and pull/restart

  Cost: $0 (Public repo) or $0.008/min (Private repo)

  장점:
    - GitHub 통합
    - 무료 또는 저렴
    - 간단한 설정
    - 빠른 피드백

Option 2: AWS CodePipeline (간소화):
  Stages:
    1. Source: GitHub
    2. Build: CodeBuild (small instance)
    3. Deploy: Rolling deployment to ASG

  Cost: ~$1/month (pipeline) + $0.005/min (build)

  Simplified Deploy:
    - Rolling update (Blue/Green 제거)
    - No Lambda hooks
    - ALB health check only

Option 3: 수동 배포 (초기):
  Steps:
    1. Local build & Docker image
    2. Push to ECR
    3. SSH to instances
    4. Pull new image & restart
    5. Verify health

  Cost: $0
  Time: 10-15 minutes

권장: GitHub Actions으로 시작 → 성장 시 CodePipeline
```

### ECR (Container Registry)

```yaml
ECR Repository:
  Name: ecommerce-app

  Image Scanning: Disabled (비용 절감)

  Lifecycle Policy:
    Rules:
      - Keep last 5 images  (10 → 5)
      - Delete untagged after 3 days

  Cost: ~$1/월 (10GB storage)

Alternative (초기 비용 절감):
  Docker Hub Free Tier:
    - Public repository
    - Unlimited pulls
    Cost: $0
```

### Deployment Strategy (간소화)

```yaml
Rolling Deployment:
  Type: In-place update
  Target: Auto Scaling Group

  Process:
    1. ASG increases desired capacity (+1)
    2. New instance launches with new code
    3. Health check passes
    4. Old instance terminates
    5. Repeat

  Advantages:
    - No additional resources
    - Simple setup
    - Cost effective

  Downtime: None (rolling)
  Rollback: Deploy previous version

배포 빈도:
  - Dev: On every push
  - Prod: Weekly or on-demand
```

---

## 비용 최적화

### 월간 예상 비용 (중소 규모 - 최적화)

```yaml
Compute (EC2):
  Instance Type: t3.small
  Count: 2 instances (baseline)
  Cost: ~$15/instance × 2 = ~$30/월

  Auto Scaling (평균 2.5대):
    Average Cost: ~$38/월

  Reserved Instances (선택):
    Type: 1-year, No Upfront
    Savings: 33%
    Cost: ~$25/월 (2 instances)

Load Balancer (ALB):
  Base: $16/월
  Data Processing: ~$10/월
  Total: ~$26/월

RDS (MySQL):
  Instance: db.t4g.medium Single-AZ
  Compute: ~$50/월
  Storage (gp3, 50 GB): ~$11/월
  Backup Storage: ~$5/월
  Total: ~$66/월

ElastiCache (Redis):
  Instance: cache.t4g.small Single-Node
  Cost: ~$24/월

NAT Gateway:
  Count: 1 (Single AZ)
  Base: ~$32/월
  Data Processing: ~$10/월
  Total: ~$42/월

CloudWatch:
  Logs (7 days): ~$2/월
  Alarms (5개): ~$0.50/월
  Total: ~$3/월

S3:
  Storage (10 GB): ~$0.50/월
  Requests: ~$1/월
  Total: ~$2/월

Secrets Manager:
  Secrets: 2 × $0.40 = ~$0.80/월

Route 53:
  Hosted Zone: $0.50/월
  Queries: ~$1/월
  Total: ~$2/월

ECR:
  Image Storage: ~$1/월

Data Transfer:
  Estimated: ~$10/월

---

총 월간 비용 (On-Demand):
  EC2:              $38
  ALB:              $26
  RDS:              $66
  ElastiCache:      $24
  NAT Gateway:      $42
  CloudWatch:       $3
  S3:               $2
  Secrets Manager:  $1
  Route 53:         $2
  ECR:              $1
  Data Transfer:    $10
  ========================
  Total:            ~$215/월

총 월간 비용 (Reserved Instances 적용):
  EC2 (RI):         $25  (-$13)
  RDS (RI):         $50  (-$16, 1년 후)
  Others:           $140
  ========================
  Total:            ~$185/월 (14% 절감)

초기 3개월 (테스트/검증):
  - Reserved 없이 On-Demand
  - Cost: ~$215/월
  - Total: ~$645

안정화 후 (Reserved 적용):
  - Cost: ~$185/월
  - 연간: ~$2,220
```

### 비용 절감 전략 (중소 규모)

```yaml
즉시 적용 가능:
  1. Scheduled Scaling:
     - 야간/주말 EC2 1대로 축소
     - Savings: ~$15-20/월

  2. S3 Lifecycle:
     - 로그 30일 후 삭제
     - 백업 30일 후 Glacier
     - Savings: ~$5/월

  3. CloudWatch 로그 보관 기간:
     - 7일로 제한
     - Savings: ~$3/월

  4. RDS Storage 최적화:
     - 불필요한 데이터 정리
     - Auto Scaling으로 적정 크기 유지
     - Savings: ~$5/월

  5. VPC Endpoint (S3):
     - NAT Gateway 비용 절감
     - Savings: ~$5-10/월

3개월 후 적용:
  6. Reserved Instances:
     - EC2 2대 (1-year, No Upfront)
     - Savings: ~$13/월

  7. RDS Reserved Instance:
     - 1-year, Partial Upfront
     - Savings: ~$16/월

추가 절감 기회:
  8. NAT Instance로 전환:
     - t4g.nano (~$3/월)
     - Savings: ~$39/월
     - 단점: 관리 부담, 성능 제한

  9. CloudFront 제거:
     - 초기에는 불필요
     - Savings: 변동 비용

  10. Dev 환경 최적화:
      - t3.micro 사용
      - 업무 시간만 운영
      - Savings: ~$15/월

최대 절감 시나리오:
  Base: $215/월
  Immediate: -$28/월
  RI (3개월 후): -$29/월
  ========================
  Final: ~$158/월 (27% 절감)
```

### 비용 모니터링

```yaml
AWS Budgets (무료):
  Monthly Budget: $250
  Alerts:
    - 80% threshold: Email
    - 100% threshold: Email + SMS
    - Forecast exceeds: Email

Cost Explorer:
  Weekly Review: 비용 추세 확인
  Tag-based Analysis: 서비스별 비용

AWS Cost Anomaly Detection:
  Enabled: Yes
  Alert: Email on anomaly
```

---

## 확장 계획

### 트래픽 기반 확장 로드맵

```yaml
Phase 1: 현재 (0-5K DAU)
  Cost: ~$215/월
  Architecture:
    - EC2: 2 × t3.small
    - RDS: db.t4g.medium (Single-AZ)
    - Redis: cache.t4g.small (Single-Node)
    - NAT: 1 Gateway

  Performance: 50-100 TPS

Phase 2: 초기 성장 (5K-20K DAU)
  Cost: ~$350/월 (+$135)
  Upgrades:
    - EC2: 3-4 × t3.small (Auto Scaling)
    - RDS: db.t4g.large (4GB → 8GB)
    - Redis: cache.t4g.medium (1.37GB → 3.09GB)

  Performance: 100-200 TPS

Phase 3: 중간 성장 (20K-50K DAU)
  Cost: ~$600/월 (+$250)
  Upgrades:
    - EC2: 4-6 × t3.medium
    - RDS: db.r6g.large + Multi-AZ
    - Redis: cache.m6g.large + Replica
    - NAT: 2 Gateways (HA)
    - WAF 추가

  Performance: 200-500 TPS

Phase 4: 대규모 (50K+ DAU)
  Cost: ~$1,200/월 (+$600)
  Upgrades:
    - EC2: 6-10 × t3.large
    - RDS: Read Replica 추가
    - Redis: Cluster mode (샤딩)
    - CloudFront 추가
    - ElastiCache 노드 증설

  Performance: 500+ TPS
```

### 기능별 확장 우선순위

```yaml
우선순위 1 (성능 병목):
  Trigger: RDS CPU > 80% 지속
  Action: RDS 인스턴스 업그레이드
  Cost: +$50/월
  Timeline: 즉시

우선순위 2 (가용성):
  Trigger: 트래픽 2배 증가
  Action: RDS Multi-AZ 전환
  Cost: +$100/월
  Timeline: 1주일

우선순위 3 (캐시 최적화):
  Trigger: Redis Memory > 80%
  Action: Redis 업그레이드 or 데이터 정리
  Cost: +$24/월
  Timeline: 즉시

우선순위 4 (컴퓨팅):
  Trigger: EC2 CPU > 70% 지속
  Action: 인스턴스 증설 or 업그레이드
  Cost: +$15-30/월
  Timeline: Auto Scaling 자동

우선순위 5 (보안 강화):
  Trigger: 보안 위협 증가
  Action: WAF 추가, GuardDuty 활성화
  Cost: +$20/월
  Timeline: 1-2주
```

### 재해 복구 (간소화)

```yaml
RTO/RPO (중소 규모):
  RTO: 30분-1시간
  RPO: 5-15분

Backup 전략:
  RDS:
    - Automated Backup: 7일
    - Point-in-Time Recovery: 활성화
    - Manual Snapshot: 주요 변경 전

  ElastiCache:
    - Daily Snapshot: 3일 보관
    - 캐시 웜업 스크립트 준비

  Application:
    - Docker Image: ECR (latest 5 versions)
    - Infrastructure as Code: Git

장애 시나리오:
  1. EC2 Instance Failure:
     - Auto Scaling 자동 교체
     - Recovery: 5분

  2. RDS Failure:
     - Point-in-time restore
     - Recovery: 30-45분

  3. AZ Failure:
     - ALB가 다른 AZ로 라우팅
     - EC2 Auto Scaling으로 복구
     - Recovery: 10-15분

  4. Region Failure (Low Priority):
     - 수동 복구 (RDS snapshot restore)
     - Recovery: 2-4시간
     - Cost: Cross-region backup 불포함 (절감)
```

---

## 인프라 자동화 (IaC - 간소화)

### Terraform 구조 (단순화)

```
terraform/
├── main.tf           # 모든 리소스 정의
├── variables.tf      # 변수 정의
├── outputs.tf        # 출력 값
├── terraform.tfvars  # 환경 변수
└── backend.tf        # State 저장소 (S3)
```

### 핵심 리소스 (예시)

```hcl
# VPC
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  cidr = "10.0.0.0/16"
  azs  = ["ap-northeast-2a", "ap-northeast-2c"]

  public_subnets  = ["10.0.1.0/24", "10.0.2.0/24"]
  private_subnets = ["10.0.11.0/24", "10.0.12.0/24"]

  enable_nat_gateway = true
  single_nat_gateway = true  # 비용 절감

  tags = {
    Environment = "production"
    Project     = "ecommerce"
  }
}

# RDS
resource "aws_db_instance" "main" {
  identifier     = "ecommerce-db"
  engine         = "mysql"
  engine_version = "8.0"
  instance_class = "db.t4g.medium"

  allocated_storage = 50
  storage_type      = "gp3"

  multi_az = false  # Single-AZ

  backup_retention_period = 7
  skip_final_snapshot     = false
  final_snapshot_identifier = "ecommerce-db-final"

  tags = local.tags
}

# ElastiCache
resource "aws_elasticache_cluster" "main" {
  cluster_id      = "ecommerce-redis"
  engine          = "redis"
  node_type       = "cache.t4g.small"
  num_cache_nodes = 1

  parameter_group_name = "default.redis7"

  tags = local.tags
}
```

---

## 배포 체크리스트 (간소화)

### MVP 배포 (2주)

```yaml
Week 1 - 인프라 기본 구성:
  Day 1-2: 네트워크
    - [ ] VPC, Subnets, IGW 생성
    - [ ] NAT Gateway 생성
    - [ ] Security Groups 생성

  Day 3-4: 데이터베이스
    - [ ] RDS 인스턴스 생성
    - [ ] ElastiCache 생성
    - [ ] DB 초기화 스크립트 실행

  Day 5: ALB 및 EC2
    - [ ] ALB 생성 및 설정
    - [ ] Launch Template 생성
    - [ ] Auto Scaling Group 생성 (min 2)

Week 2 - 애플리케이션 및 모니터링:
  Day 1-2: 애플리케이션 배포
    - [ ] Docker 이미지 빌드
    - [ ] ECR에 푸시
    - [ ] EC2 인스턴스에 배포
    - [ ] Health Check 확인

  Day 3: 보안
    - [ ] IAM Role 설정
    - [ ] Secrets Manager 설정
    - [ ] SSL 인증서 (ACM) 설정

  Day 4: 모니터링
    - [ ] CloudWatch 대시보드 생성
    - [ ] 필수 알람 5개 설정
    - [ ] SNS Topic 생성

  Day 5: 최종 검증
    - [ ] 부하 테스트 (JMeter/k6)
    - [ ] 장애 복구 테스트
    - [ ] 문서 작성

배포 완료!
```

---

## 운영 가이드 (간소화)

### 일상 운영

```yaml
Daily (5분):
  - CloudWatch 대시보드 확인
  - 에러 알람 확인

Weekly (30분):
  - 비용 리포트 확인
  - 성능 메트릭 검토
  - 백업 상태 확인

Monthly (2시간):
  - 보안 패치 적용
  - 사용하지 않는 리소스 정리
  - 비용 최적화 검토
```

### 긴급 대응

```yaml
Critical (즉시):
  - 서비스 전체 다운
  - RDS 접근 불가
  - 에러율 > 10%

  대응: 슬랙/이메일 알람 → 즉시 조사

Warning (1시간 내):
  - 응답 시간 > 2s
  - RDS CPU > 85%
  - 디스크 공간 < 10%

  대응: 원인 파악 및 조치 계획
```

---

## 요약

### 비용 요약

```yaml
월간 비용:
  초기 (On-Demand):     ~$215/월
  최적화 후 (RI):       ~$185/월
  최대 절감:            ~$158/월

연간 비용:
  최적화 적용:          ~$2,220/년
  최대 절감:            ~$1,900/년
```

### 주요 특징

```yaml
강점:
  ✓ 비용 효율적 (~$200/월)
  ✓ 자동 확장 (Auto Scaling)
  ✓ 간단한 운영
  ✓ 단계적 확장 가능

제약사항:
  - Single-AZ (RDS, Redis)
  - Single NAT Gateway
  - 기본 모니터링만
  - WAF 미포함 (초기)

권장 확장 시점:
  - DAU 5K 초과: RDS 업그레이드
  - DAU 10K 초과: Multi-AZ 전환
  - DAU 20K 초과: Redis Replica 추가
  - DAU 50K 초과: 전체 HA 구성
```

### 다음 단계

```yaml
1. Terraform으로 인프라 구축
2. 애플리케이션 배포
3. 2주간 모니터링 및 튜닝
4. 성능 테스트 및 검증
5. Reserved Instance 구매 검토 (3개월 후)
```

---

## 변경 이력

| 버전 | 날짜 | 작성자 | 변경 내역 |
|------|------|--------|-----------|
| 2.0 | 2025-10-31 | Infrastructure Team | 중소 규모로 재설계 (비용 최적화) |
| 1.0 | 2025-10-31 | Infrastructure Team | 초기 인프라 설계 문서 작성 |
