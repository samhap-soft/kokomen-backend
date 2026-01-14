# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kokomen (꼬꼬면) is an AI-powered mock interview platform for developers. The name comes from "꼬리에 꼬리를 무는 면접 질문" (follow-up questions that chain together).

## Build & Development Commands

```bash
# Full build
./gradlew clean build

# Run API server
./gradlew :api:bootRun

# Run Consumer service (separate terminal)
./gradlew :consumer:bootRun

# Run all tests
./gradlew test

# Run module-specific tests
./gradlew :api:test
./gradlew :consumer:test

# Run single test class
./gradlew :api:test --tests "com.samhap.kokomen.interview.service.InterviewServiceTest"

# Run single test method
./gradlew :api:test --tests "com.samhap.kokomen.interview.service.InterviewServiceTest.메소드명"

# Start test infrastructure (MySQL + Redis)
cd api && docker compose -f test.yml up -d

# Or use the helper script
cd api && ./run-test-mysql-redis.sh
```

## Architecture

### Multi-Module Structure
```
kokomen-backend/
├── api/        # REST API server (Spring Boot application)
├── consumer/   # Kafka event consumer service
└── common/     # Shared domain models, entities, repositories, exceptions
```

- **api**: Main REST API with controllers, services, external client integrations (GPT, Bedrock, Supertone TTS, S3)
- **consumer**: Kafka Streams consumer for event processing (e.g., InterviewLikeEvent)
- **common**: JPA entities, repositories, shared exceptions, Flyway migrations, Redis configuration

### Key Technologies
- Java 17, Spring Boot 3.x
- MySQL 8.0 (Primary DB), Redis/Valkey (Session & Cache)
- Apache Kafka for async event processing
- OpenAI GPT-4 / AWS Bedrock for AI features
- Supertone for TTS (voice mode)
- Kakao/Google OAuth for authentication
- Flyway for DB migrations (`common/src/main/resources/db/migration/`)

### Domain Package Structure
```
domain/
├── controller/
├── service/
│   └── dto/     # Request/Response DTOs
├── repository/
├── domain/      # Entities (in common module)
└── external/    # External API clients
```

## Code Conventions (from docs/convention.md)

### Style Guide
- Follows Woowacourse Java Style Guide (based on Google Java Style)
- Line limit: 160 characters
- Indent: 4 spaces

### Naming
- Methods: `행위 + 도메인` (e.g., `saveMember()`)
- `read-` prefix: value must exist, throws exception if not found
- `find-` prefix: value may not exist, returns Optional or empty list
- Don't use `get-` for non-getter methods
- DTOs end with `Request` or `Response`

### Annotation Order
- Lombok → Spring annotations (more important annotations go below)
```java
@Lombok
@SpringAnnotation
public void example() {}
```

### Method Declaration Order
1. Constructor
2. Static factory methods
3. Business methods (CRUD order, private methods after their calling public method)
4. Override methods (equals, hashCode, toString)

### Testing
- Test method names in **Korean**
- No `@DisplayName` annotation
- Controller tests: MockMvc + real beans (integration test, generates RestDocs)
- Service tests: integration with repository
- Domain tests: unit tests
- Test isolation: `MySQLDatabaseCleaner` (not `@Transactional`)
- Fixtures: `global/fixture/XxxFixtureBuilder` classes
- Tests use real MySQL container (not H2)

### Exception Handling
- Custom exceptions: `BadRequestException`, `UnauthorizedException`, `ForbiddenException`, etc.
- Validation: `@Valid` in DTO, entity-level validation in constructors
- Business validation that needs external data goes in service layer

## Test Infrastructure

Tests require MySQL and Redis containers:
- MySQL: port 13306 (database: kokomen-test, password: root)
- Redis: port 16379

Start with: `cd api && docker compose -f test.yml up -d`

Test base classes:
- `BaseTest`: `@SpringBootTest` with mock beans for external services (GPT, Kafka, S3, etc.)
- `BaseControllerTest`: Extends BaseTest, adds MockMvc with RestDocs configuration

## API Documentation

- Generated via Spring REST Docs
- Build generates docs into `api/build/docs/`
- Access at: `http://localhost:8080/docs/index.html`

## Environment Variables

Required for local development:
```
OPEN_AI_API_KEY
KAKAO_CLIENT_ID
KAKAO_CLIENT_SECRET
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
SUPERTONE_API_TOKEN
```

## Profiles
- `local`: Local development
- `dev`: Development server
- `prod`: Production
- `load-test`: Load testing
- `test`: Test environment (used by tests)
