# Thundering Herd Resolver - Java Spring Boot

## Overview

Production-ready Java Spring Boot implementation of Thundering Herd mitigation strategies.

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+
- Docker

### Run

```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Run application
mvn spring-boot:run

# Open browser
http://localhost:8080
```

## Project Structure

```
src/main/java/com/faang/thunderingherd/
├── core/              # SingleFlight + CacheManager
├── service/           # Business logic
├── controller/        # REST endpoints
├── model/             # JPA entities
└── config/            # Configuration

src/test/java/         # Unit and integration tests
```

## Results

| Scenario | Requests | DB Queries | Improvement |
|----------|----------|------------|-------------|
| Baseline | 100 | 100 | - |
| Single-Flight | 100 | 1 | 100x |
| Full Solution | 100 | 0 | ∞ |

## Technologies

- Java 17
- Spring Boot 3.2.0
- Redis 7
- H2 Database
- Maven
- Docker

## License

MIT
