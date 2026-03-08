# ADR-002: Hexagonal Architecture (Ports & Adapters) for All Microservices

## Context

Each microservice in FinCore is a non-trivial domain with business rules (payment state machine, fraud rule engine, FX
conversion invariants). The question is how to structure the code so that:

1. Business logic can be unit-tested without starting Spring, without a database, without Kafka
2. Infrastructure details (JPA, Kafka, HTTP clients) can be swapped without touching domain code
3. New team members understand where to find business logic vs. infrastructure glue

The flat package structure (controllers + services + repositories in one layer) common in Spring tutorials fails all
three: `@Service` classes import `@Repository` interfaces, domain objects are JPA entities, and unit tests require
`@MockBean` for everything.

---

## Decision

All services follow **Hexagonal Architecture** as defined by Alistair Cockburn (2005), also known as Ports & Adapters.

**Structure:**

```
domain/
  model/      - aggregates, value objects, domain events (pure Java, zero dependencies)
  port/in/    - use case interfaces (what the application can do)
  port/out/   - repository and client interfaces (what the application needs)
  service/    - pure domain services (stateless logic, no @Component)

application/  - use case implementations (@Service, @Transactional, orchestration only)

adapter/
  in/web/     - REST controllers (Spring MVC - calls use case interfaces)
  in/messaging/ - Kafka consumers (Spring Kafka - calls use case interfaces)
  out/persistence/ - JPA adapters (implement repository port interfaces)
  out/client/  - WebClient adapters (implement service client port interfaces)
  out/messaging/ - Kafka producers (implement event publisher port interfaces)

infrastructure/
  config/     - @Configuration classes, bean wiring
```

**The domain layer has a compile-time enforced rule: zero Spring imports, zero JPA imports, zero Kafka imports.** If a
PR introduces `import org.springframework.*` into `domain/`, it fails code review.

---

## Consequences

**Positive:**

- Domain unit tests are plain JUnit - no `@SpringBootTest`, no Testcontainers, sub-second execution
- Infrastructure can be replaced: swapping PostgreSQL for MongoDB means writing a new `out/persistence` adapter, not
  touching domain or application layers
- Use case interfaces document the contract: `InitiatePaymentUseCase.initiatePayment(command)` is readable without
  knowing anything about REST or Kafka
- Onboarding is faster: "business logic lives in `domain/` and `application/`, everything else is infrastructure"

**Negative:**

- More files than a flat structure - a simple CRUD endpoint touches Controller + UseCase interface + UseCase
  implementation + Repository port + JPA adapter + Entity + Mapper
- Mapper boilerplate between domain objects and JPA entities (partially mitigated by MapStruct)
- Over-engineering risk for genuinely simple services - accepted here because all six services have non-trivial domain
  logic

---

## Boundary Enforcement

Enforced by ArchUnit in each service's test suite:

```java

@ArchTest
static final ArchRule domainHasNoDependencyOnInfrastructure =
		noClasses().that().resideInAPackage("..domain..")
				.should().dependOnClassesThat()
				.resideInAnyPackage("..adapter..", "..infrastructure..");

@ArchTest
static final ArchRule applicationDependsOnlyOnDomain =
		classes().that().resideInAPackage("..application..")
				.should().onlyDependOnClassesThat()
				.resideInAnyPackage("..domain..", "java..", "org.springframework.stereotype..",
						"org.springframework.transaction..", "lombok..", "..application..");
```

These tests run in the unit test phase (`mvn test`) - no containers needed, fail in under 100ms.