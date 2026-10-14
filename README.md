# Product Service

Backend engineering assignment: a market-aware product information aggregator.

## Current scope

This is the project skeleton. Product endpoints, upstream mocks, aggregation,
timeouts and fallback behavior are not implemented yet.

## Requirements

- JDK 21
- Maven is provided by the included Maven Wrapper (3.9.12).
- Internet access is required for the first dependency download.

## Build and run

On Windows, select the installed JDK for the current PowerShell session:

```powershell
$env:JAVA_HOME = 'C:\Users\gring\.jdks\temurin-21.0.11'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
./mvnw.cmd clean verify
./mvnw.cmd spring-boot:run
```

On Linux/macOS, set `JAVA_HOME` to a JDK 21 installation and run:

```shell
sh mvnw clean verify
sh mvnw spring-boot:run
```

Health endpoint: `http://localhost:8080/actuator/health`.

Alternatively, after building:

```shell
java -jar target/productservice-0.0.1-SNAPSHOT.jar
```

## Planned structure

- `api`: REST controllers, request/response DTOs and error handling.
- `application`: aggregation orchestration, deadlines and fallback policies.
- `domain`: product, pricing, availability and customer models.
- `upstream`: client interfaces and simulated upstream implementations.

Packages will be introduced as their implementations are added.

## Planned behavior

`GET /api/v1/products/{productId}?market=pl-PL&customerId=customer-123`

Catalog is required. Pricing and availability failures produce a partial response.
Customer failure falls back to a standard, non-personalized response.
Supported markets, localization rules and timeout budgets will be documented
with the implementation.

## Design decisions

- Java 21 and Spring Boot 4.1.1 with Maven.
- Spring MVC for REST, Bean Validation for input validation, Actuator for health.
- No database is needed for the simulated upstream services.
- Aggregation logic will be separated from upstream implementations.

## Design question: related products (Option A)

Related products would be an optional upstream source with an independent timeout.
Its 200 ms typical latency and 90% reliability must not make the core product
unavailable. The source would run concurrently where dependencies allow, using
only the remaining request budget. Failure would return an explicit unavailable
state for recommendations. A client interface and adapter would isolate the new
source from the existing upstream implementations.

## Further work

Implement deterministic failure scenarios and tests, realistic mock delays,
partial responses, cancellation, metrics and operational documentation.
For production, evaluate circuit breakers, concurrency limits and caching policies
against actual upstream contracts and traffic.
