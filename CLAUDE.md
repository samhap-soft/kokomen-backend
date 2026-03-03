# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kokomen (꼬꼬면) is an AI-powered mock interview platform for developers. The name comes from "꼬리에 꼬리를 무는 면접 질문" (follow-up
questions that chain together).

## Build & Development Commands

```bash
# Full build
./gradlew clean build

# Run server
./gradlew bootRun

# Run all tests
./gradlew test

# Run single test class
./gradlew test --tests "com.samhap.kokomen.interview.service.core.InterviewServiceTest"

# Run single test method
./gradlew test --tests "com.samhap.kokomen.interview.service.core.InterviewServiceTest.메소드명"

# Start test infrastructure (MySQL + Redis)
docker compose -f test.yml up -d

# Or use the helper script
./run-test-mysql-redis.sh
```

## Architecture

### Project Structure

```
kokomen-backend/
├── src/main/java/com/samhap/kokomen/
│   ├── admin/
│   ├── answer/
│   ├── auth/
│   ├── category/
│   ├── global/
│   ├── interview/
│   ├── member/
│   ├── product/
│   ├── recruit/
│   ├── resume/
│   └── token/
├── src/main/resources/
│   ├── db/migration/     # Flyway migrations
│   ├── application.yml   # Common config
│   └── application-{profile}.yml
├── src/test/
├── src/docs/asciidoc/    # REST Docs
├── docker/               # Deployment configs
└── build.gradle
```

### Key Technologies

- Java 17, Spring Boot 3.x
- MySQL 8.0 (Primary DB), Redis/Valkey (Session & Cache)
- OpenAI GPT-4 / AWS Bedrock for AI features
- Supertone for TTS (voice mode)
- Kakao/Google OAuth for authentication
- Flyway for DB migrations (`src/main/resources/db/migration/`)

### Domain Package Structure

```
{domain}/
├── controller/
├── service/
│   └── dto/     # Request/Response DTOs
├── repository/
│   └── dto/     # Query projections
├── entity/      # JPA entities
├── domain/      # Domain logic & enums
├── tool/        # Utility classes
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

Start with: `docker compose -f test.yml up -d`

Test base classes:

- `BaseTest`: `@SpringBootTest` with mock beans for external services (GPT, S3, etc.)
- `BaseControllerTest`: Extends BaseTest, adds MockMvc with RestDocs configuration

## API Documentation

- Generated via Spring REST Docs
- Build generates docs into `build/docs/`
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

# currentDate

Today's date is 2026-02-24.
