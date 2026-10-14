# Product Service

Backend engineering assignment: a market-aware product information aggregator.

## Current scope

The REST contract, domain models, input validation, Problem Details error handling
and four upstream client interfaces are implemented and covered by contract tests.
Upstream mocks, aggregation, timeouts and fallback execution are not implemented yet.
A valid product request currently returns `501 Not Implemented`; this temporary
application service will be replaced by the aggregator.

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

## Structure

- `api`: REST controllers, request/response DTOs and error handling.
- `application`: aggregation orchestration, deadlines and fallback policies.
- `domain`: product, pricing, availability and customer models.
- `upstream`: client interfaces and simulated upstream implementations.

The application layer returns domain data; the API layer maps it to the public response.

## API contract

`GET /api/v1/products/{productId}?market=pl-PL&customerId=customer-123`

### Inputs

- `productId`: 1-100 ASCII letters, digits, dots, underscores or hyphens; starts with a letter or digit.
- `market`: required, exactly `nl-NL`, `de-DE` or `pl-PL` (case-sensitive).
- `customerId`: optional; when supplied, follows the same rules as `productId`. Empty values are invalid.

Market defines the language and currency: Dutch/EUR, German/EUR, Polish/PLN.
IDs are opaque strings. These validation rules are a project choice, not specified by the assignment.

### Response

Example of the intended partial response (not yet served by the pending implementation):

```json
{
  "market": "pl-PL",
  "product": {
    "id": "P-123",
    "name": "Filtr",
    "description": "Filtr oleju",
    "specifications": {"type": "oil"},
    "images": ["https://example.com/filter.jpg"]
  },
  "pricing": {"status": "UNAVAILABLE", "data": null},
  "availability": {"status": "UNKNOWN", "data": null},
  "personalization": "STANDARD"
}
```

Available pricing has `status: AVAILABLE` and `data` containing `basePrice`,
`discountAmount`, `finalPrice` and an ISO currency code. Amounts are decimals;
`discountAmount` is an amount, not a percentage. Pricing owns calculation and rounding.
Available stock has `status: AVAILABLE` and `data` containing nonnegative `stockLevel`,
`warehouseLocation` and nullable `expectedDeliveryDate` (`YYYY-MM-DD`).
A stock level of zero means out of stock; `UNKNOWN` means no stock data is available.
Personalization is `STANDARD` or `PERSONALIZED`; customer IDs and preferences are not exposed.

Catalog is required. Pricing and availability failures will produce a partial `200` response.
Customer failure will use `CustomerContext.standard()` for pricing and return `STANDARD`.
These fallback policies will be implemented in the aggregation step.

### Errors

All errors use `application/problem+json` (RFC 9457), with `type`, `title`, `status`,
`detail` and `instance`. Custom problem types use `urn:productservice:problem:<code>`.

| HTTP status | Meaning |
| --- | --- |
| 400 | Missing/invalid input or unsupported market |
| 404 | Catalog confirms the product does not exist |
| 503 | Required upstream data is unavailable |
| 504 | Required upstream data timed out |
| 500 | Unexpected server error; internal details are not returned |
| 501 | Temporary: aggregation has not been implemented |

```json
{
  "type": "urn:productservice:problem:unsupported-market",
  "title": "Bad Request",
  "status": 400,
  "detail": "Unsupported market. Supported values: nl-NL, de-DE, pl-PL.",
  "instance": "/api/v1/products/P-123"
}
```

### Upstream contracts

Clients are blocking interfaces, independent of Spring and REST DTOs. They return
non-null domain data or throw typed exceptions. Concurrency and fallback belong to
the future aggregator; a timeout exception also represents an aggregator deadline.
Only Catalog may report `ProductNotFoundException`. Failures from optional sources
must be caught by the aggregator before they reach the REST exception handler.
Pricing receives the resolved customer context rather than looking up the customer again.
The customer client is called only when a customer ID is provided.

### Tests

Run `./mvnw.cmd verify` (Windows) or `sh mvnw verify` (Linux/macOS).
Contract tests verify all markets, request forwarding, input validation, partial JSON,
zero stock, currency serialization, error status mappings and hidden internal details.
They replace `ProductQuery` with a deterministic stub and do not claim to test upstream fallback execution.

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
