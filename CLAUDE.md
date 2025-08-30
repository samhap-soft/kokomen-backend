# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

꼬꼬면 (Kokomen) is an interview preparation backend system that provides AI-powered mock interview functionality with
question-and-answer sessions. The system uses multi-module architecture with Spring Boot and includes real-time event
processing via Kafka.

## Project Structure

This is a multi-module Gradle project with the following modules:

- **api** - Main REST API server with Spring Boot
- **consumer** - Kafka consumer service for event processing
- **domain** - Shared domain models and repositories

## Development Commands

### Building and Testing

- you have to run 'docker compose -f test.yml' in api directory to run the tests

```bash
# Build all modules
./gradlew clean build

# Run tests for all modules
./gradlew test

# Run tests for specific module
./gradlew :api:test
./gradlew :consumer:test

# Build specific module
./gradlew :api:build
./gradlew :consumer:build
```

### Running Applications

```bash
# Run API server
./gradlew :api:bootRun

# Run consumer service
./gradlew :consumer:bootRun

# Run local development environment (API with dependencies)
./local-run.sh

# Run API with local Docker environment
cd api && ./local-app-run.sh
```

### Docker Operations

```bash
# Build and run API locally with Docker
cd api && ./local-app-run.sh

# Build and run consumer locally with Docker
cd consumer && ./local-run-consumer.sh
```

## Architecture

### Core Domain Models

- **Member** - User accounts and profiles
- **Interview** - Mock interview sessions
- **Question/RootQuestion** - Interview questions (root questions spawn follow-up questions)
- **Answer** - User responses to questions
- **Category** - Question categories

### Key Services

- **InterviewFacadeService** - Main orchestration for interview flow
- **InterviewProceedService** - Handles interview progression logic
- **RootQuestionService** - Manages root questions and categories
- **MemberService** - User management and ranking
- **S3Service** - File storage for audio/media
- **RedisService** - Caching and session management

### External Integrations

- **OpenAI GPT** - Question generation and analysis
- **AWS Bedrock** - Alternative LLM service
- **Supertone** - Text-to-speech conversion
- **Kakao OAuth** - User authentication
- **AWS S3** - File storage
- **Redis** - Caching and sessions
- **Kafka** - Event streaming between API and consumer

### Event-Driven Architecture

The system uses Kafka for asynchronous processing:

- **API module** produces events (interview likes, view counts, etc.)
- **Consumer module** processes events and updates statistics

### Database

- **MySQL** - Primary database with Flyway migrations in `/src/main/resources/db/migration/`
- **H2** - In-memory database for testing
- **Redis** - Session storage and caching

## Testing

Tests use JUnit 5 with Spring Boot Test framework. Key test utilities:

- **BaseControllerTest** - Base class for controller tests
- **BaseTest** - Common test configuration
- **Fixture builders** - Test data creation helpers in `global/fixture/`

## Configuration

- **application.yml** - Main configuration files in each module
- Environment variables for secrets (API keys, database credentials)
- Profiles: local development uses embedded configurations

## Key Design Patterns

- **Facade Pattern** - InterviewFacadeService orchestrates complex operations
- **Event Sourcing** - Kafka events for statistics and notifications
- **Repository Pattern** - JPA repositories for data access
- **DTO Pattern** - Separate request/response objects from domain models

## Service Architecture Guidelines

### Single Repository Principle
- **Each Service should manage only ONE Repository**
- This ensures clear separation of concerns and single responsibility
- Services should focus on business logic for a specific domain

### Multiple Repository Usage
- **When multiple Repositories are needed, create a FacadeService**
- FacadeService orchestrates multiple Services and ensures transaction consistency
- Example: `TokenFacadeService` manages both `TokenService` and `TokenPurchaseService`
- Use `@Transactional` annotation on FacadeService methods to ensure data consistency

### Service Structure Pattern
```
TokenService (manages TokenRepository only)
TokenPurchaseService (manages TokenPurchaseRepository only)  
TokenFacadeService (orchestrates both services with @Transactional)
```

### Benefits
- Clear boundaries between services
- Easier testing and maintenance
- Better transaction management
- Reduced coupling between domain models

## Method Organization Guidelines

### CRUD Method Ordering
- **Service and Controller methods should be organized in CRUD order**
- **C**reate methods first (save, create, register, etc.)
- **R**ead methods second (find, get, read, validate, etc.)
- **U**pdate methods third (update, modify, use, etc.)
- **D**elete methods last (delete, remove, etc.)

### Private Method Placement
- **Private methods should be placed immediately after the last public method that calls them**
- If multiple public methods call the same private method, place it after the last one in the file order
- This maintains logical grouping and readability

### Example Method Order
```java
// CREATE methods
public void createUser() { ... }
public void registerMember() { ... }

// READ methods  
public User findById() { ... }
public boolean validateUser() { ... }
private boolean isValidEmail() { ... } // called by validateUser

// UPDATE methods
public void updateUser() { ... }
public void useToken() { ... }

// DELETE methods
public void deleteUser() { ... }
```

## Method Naming Guidelines

### getXXX() Method Usage
- **Use getXXX() ONLY when retrieving a field from a specific entity or object**
- **DO NOT use getXXX() when computing, calculating, or aggregating values**
- Use appropriate verbs for computed values: `calculate`, `compute`, `determine`, `count`, etc.

### Examples
```java
// CORRECT - Getting field from entity
public String getName() { return this.name; }
public Long getId() { return this.id; }

// INCORRECT - Computing/calculating values  
public int getTotalCount() { ... } // ❌
public boolean getIsValid() { ... } // ❌

// CORRECT - Computing/calculating values
public int calculateTotalCount() { ... } // ✅
public int countTotalTokens() { ... } // ✅  
public boolean isValid() { ... } // ✅
public boolean hasEnoughTokens() { ... } // ✅
```

### Recommended Verbs for Computed Values
- **calculate** - for mathematical computations
- **compute** - for algorithmic calculations  
- **count** - for counting items
- **determine** - for decision-making logic
- **check** - for validation checks
- **is/has** - for boolean conditions

## File Creation Guidelines

- **End of Line**: ALWAYS add a newline character at the end of every file to avoid "No newline at end of file" warnings
- This applies to all file types: .sql, .java, .yml, .md, etc.

## Code Formatting Guidelines

- **Line Length**: Keep code lines under 120 characters. Do not break lines unless they exceed 120 characters
- This ensures code consistency and prevents unnecessary line breaks that reduce readability
