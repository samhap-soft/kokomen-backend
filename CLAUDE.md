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
