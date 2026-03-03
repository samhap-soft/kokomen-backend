# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kokomen (꼬꼬면) is an AI-powered mock interview platform for developers. The name comes from "꼬리에 꼬리를 무는 면접 질문" (follow-up questions that chain together).

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

### Key Technologies
- Java 17, Spring Boot 3.x
- MySQL 8.0 (Primary DB), Redis/Valkey (Session & Cache, Redisson for distributed locks)
- OpenAI GPT-4 / AWS Bedrock for AI features
- Supertone for TTS (voice mode)
- Kakao/Google OAuth for authentication
- Flyway for DB migrations (`src/main/resources/db/migration/`)
- Spring REST Docs for API documentation
- Micrometer + Prometheus for metrics (management port: 8081)

### Domain Packages
```
src/main/java/com/samhap/kokomen/
├── admin/        # Admin operations (root question voice management)
├── answer/       # Interview answers, likes, memos
├── auth/         # OAuth login (Kakao, Google)
├── category/     # Interview categories
├── global/       # Cross-cutting concerns (AOP, config, exceptions, fixtures)
├── interview/    # Core interview domain (start, proceed, questions, resume-based)
├── member/       # User profiles and scores
├── payment/      # Tosspayments integration (payments, webhooks, refunds)
├── product/      # Purchasable products
├── recruit/      # Job recruitment listings
├── resume/       # Resume/portfolio upload and AI evaluation
└── token/        # Interview tokens (purchase, consumption)
```

### Domain Package Convention
```
{domain}/
├── controller/
├── service/
│   └── dto/       # Request/Response DTOs (suffixed with Request or Response)
├── repository/
│   └── dto/       # Query projections
├── entity/        # JPA entities
├── domain/        # Domain logic & enums
├── tool/          # Utility classes
└── external/      # External API clients
```

### Interview Service Sub-Packages
The interview domain uses sub-packages to organize its service layer. Facade services and InterviewQueryService remain at the root for controller access:
```
interview/service/
├── InterviewStartFacadeService        # Orchestrates interview start flow
├── InterviewProceedFacadeService      # Orchestrates answer submission flow
├── InterviewQueryService              # Read-only query delegation for controllers
├── core/       # InterviewService, InterviewProceedService
├── question/   # QuestionService, RootQuestionService, QuestionGeneration*
├── resume/     # ResumeBasedInterviewService, ResumeContentService
├── social/     # InterviewLikeService, InterviewViewCountService
├── infra/      # InterviewSchedulerService, InterviewProceedBedrockFlowAsyncService
└── dto/
```

### Cross-Cutting Patterns
- **Facade pattern**: Domains with complex orchestration use `*FacadeService` classes. Other domains should depend on facade services, not internal services directly.
- **Custom annotations**: `@DistributedLock` (Redis-based), `@ExecutionTimer`, `@RedisExceptionWrapper`
- **Base entity**: `BaseEntity` with `@CreatedDate createdAt`

## Code Conventions (from docs/convention.md)

### Style Guide
- Follows Woowacourse Java Style Guide (based on Google Java Style)
- Column limit: **120 characters**
- Indent: 4 spaces, continuation indent: +8 spaces minimum

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

### Exception Handling
- Custom exceptions: `BadRequestException`, `UnauthorizedException`, `ForbiddenException`, etc.
- Validation: `@Valid` in DTO, entity-level validation in constructors
- Business validation that needs external data goes in service layer

## Testing

### Test Conventions
- Test method names in **Korean**
- No `@DisplayName` annotation
- Controller tests: MockMvc + real beans (integration test, generates RestDocs)
- Service tests: integration with repository
- Domain tests: unit tests
- Test isolation: `MySQLDatabaseCleaner` truncates all tables (not `@Transactional`)
- Fixtures: `global/fixture/{domain}/XxxFixtureBuilder` — static `builder()`, fluent setters, sensible defaults

### Test Infrastructure
Tests require MySQL and Redis containers:
- MySQL: port 13306 (database: kokomen-test, password: root)
- Redis: port 16379

Start with: `docker compose -f test.yml up -d`

### Test Base Classes
- **`BaseTest`**: `@SpringBootTest` with `@ActiveProfiles("test")`, mocks external services (GPT, S3, Supertone, Tosspayments, OAuth clients, Bedrock), spies on Redis. Uses real MySQL container + `MySQLDatabaseCleaner`.
- **`BaseControllerTest`**: Extends BaseTest, adds MockMvc with RestDocs configuration.
- **`DocsTest`**: `@ActiveProfiles("docs")`, uses H2 in-memory DB with `@Transactional`. For lightweight REST Docs generation without Docker.

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
- `test`: Test environment (real MySQL + Redis containers)
- `docs`: REST Docs generation (H2 in-memory, no Docker needed)
