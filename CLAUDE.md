# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a TDD (Test-Driven Development) practice project for implementing a user point management system. The project is a Spring Boot application using Java 17 with an in-memory database simulation for managing user points (charging and using points).

**Project Name**: hhplus-tdd-jvm
**Base Package**: io.hhplus.tdd
**Spring Boot Version**: 3.2.0

## Build and Development Commands

**Important**: This project uses Java 21. If you don't have Java in your PATH, use `./gradle.sh` instead of `./gradlew`. The `gradle.sh` script automatically sets the correct Java path.

### Building the Project
```bash
./gradle.sh build
# or if Java 21 is in your PATH:
./gradlew build
```

### Running Tests
```bash
./gradle.sh test
# Run specific test class:
./gradle.sh test --tests "PointServiceTest"
# Run specific test method:
./gradle.sh test --tests "PointServiceTest.chargePoint_Success"
```

Note: Tests are configured with `ignoreFailures = true` in [build.gradle.kts:50](build.gradle.kts#L50), so the build will continue even if tests fail.

### Running the Application
```bash
./gradle.sh bootRun
```

### Code Coverage
The project uses JaCoCo for code coverage:
```bash
./gradle.sh test jacocoTestReport
```

### Java Setup
- **Required**: Java 21 (OpenJDK 21)
- **gradle.properties** sets `org.gradle.java.home=/opt/homebrew/opt/openjdk@21` for Gradle
- **gradle.sh** wrapper script sets PATH for convenience

## Architecture

### Core Components

**Controller Layer** ([io.hhplus.tdd.point](src/main/java/io/hhplus/tdd/point/))
- [PointController.java](src/main/java/io/hhplus/tdd/point/PointController.java): REST API endpoints for point operations
  - GET `/point/{id}` - Query user points
  - GET `/point/{id}/histories` - Query user point transaction history
  - PATCH `/point/{id}/charge` - Charge user points
  - PATCH `/point/{id}/use` - Use user points

**Service Layer** ([io.hhplus.tdd.point](src/main/java/io/hhplus/tdd/point/))
- [PointService.java](src/main/java/io/hhplus/tdd/point/PointService.java): Service interface
- [PointServiceImpl.java](src/main/java/io/hhplus/tdd/point/PointServiceImpl.java): Business logic implementation
  - Validates amount (must be > 0)
  - Checks balance before using points
  - Records all transactions to history

**Database Layer** ([io.hhplus.tdd.database](src/main/java/io/hhplus/tdd/database/))
- [UserPointTable.java](src/main/java/io/hhplus/tdd/database/UserPointTable.java): In-memory storage for user points
  - `selectById(Long id)` - Retrieve user point data
  - `insertOrUpdate(long id, long amount)` - Create or update user points
  - **Do not modify this class** - use only the public API
  - Contains artificial throttling (200-300ms random delay) to simulate database latency

- [PointHistoryTable.java](src/main/java/io/hhplus/tdd/database/PointHistoryTable.java): In-memory storage for transaction history
  - `insert(userId, amount, type, updateMillis)` - Insert transaction record
  - `selectAllByUserId(long userId)` - Retrieve all transactions for a user
  - **Do not modify this class** - use only the public API
  - Contains artificial throttling (300ms random delay)

**Domain Models** ([io.hhplus.tdd.point](src/main/java/io/hhplus/tdd/point/))
- [UserPoint.java](src/main/java/io/hhplus/tdd/point/UserPoint.java): Java record representing user point data (id, point, updateMillis)
- [PointHistory.java](src/main/java/io/hhplus/tdd/point/PointHistory.java): Java record for transaction history
- [TransactionType.java](src/main/java/io/hhplus/tdd/point/TransactionType.java): Enum with CHARGE and USE types

**Exception Handling**
- [ApiControllerAdvice.java](src/main/java/io/hhplus/tdd/ApiControllerAdvice.java): Global exception handler
  - `InsufficientPointException` → 400 Bad Request
  - `InvalidAmountException` → 400 Bad Request
  - All other exceptions → 500 Internal Server Error
- [ErrorResponse.java](src/main/java/io/hhplus/tdd/ErrorResponse.java): Error response DTO
- Custom Exceptions ([io.hhplus.tdd.point.exception](src/main/java/io/hhplus/tdd/point/exception/)):
  - [InsufficientPointException.java](src/main/java/io/hhplus/tdd/point/exception/InsufficientPointException.java): Thrown when balance is insufficient
  - [InvalidAmountException.java](src/main/java/io/hhplus/tdd/point/exception/InvalidAmountException.java): Thrown when amount is <= 0

### Design Constraints

1. **Database Tables**: The `UserPointTable` and `PointHistoryTable` classes simulate database behavior with in-memory storage and should not be modified. Only use their public APIs.

2. **TDD Approach**: This is a TDD practice project. Test coverage includes:
   - Unit Tests: [PointServiceTest.java](src/test/java/io/hhplus/tdd/point/PointServiceTest.java) - 8 tests using Mockito
   - Integration Tests: [PointControllerTest.java](src/test/java/io/hhplus/tdd/point/PointControllerTest.java) - 13 tests using real in-memory objects
   - All 21 tests passing

3. **Test Patterns Used**:
   - **Mock**: Mockito for isolating PointService unit tests
   - **Fake**: Real in-memory UserPointTable and PointHistoryTable for integration tests
   - **Stub**: Test data setup in @BeforeEach
   - **Dummy**: Simple test data objects

4. **Concurrency Considerations**: The in-memory tables use basic collections (HashMap, ArrayList) without synchronization. This implementation does not consider distributed environments or concurrent access.
