# Open Questions

Decisions deliberately left open by the scaffold. Each is a real fork, not an oversight.

## 1. How production gets its DDL

ADR-005 removes Flyway, and `application-prod.yml` uses `ddl-auto: validate`, which means a
production database must already have the tables. The options:

* Generate the DDL once from the JPA model and apply it as part of provisioning. Fits the design:
  the relational schema is tiny and near static, so this is a rare event.
* Use `ddl-auto: update` in production too. Simplest, but it hands schema authority to Hibernate at
  runtime, which is normally judged an anti-pattern.
* Use Flyway strictly for structural DDL while payload evolution stays with Aether Datafixers. This
  is the escape hatch the brief keeps for emergencies.

The scaffold takes the conservative position (`validate`) so the choice stays explicit.

## 2. How the bring-your-own-key flow is shaped

Production is BYOK, which means the key arrives with the request rather than from configuration.
That rules out the eagerly constructed `ChatModel` bean the LangChain4j starters would otherwise
provide, and calls for a per-request model factory plus a decision about whether keys are ever
persisted, held for a session, or discarded after each call. In developer mode the same abstraction
has to offer the local inference server as a second option. None of this is wired yet; the starters
are on the classpath and no model properties are set.

## 3. Which fields get promoted out of the CBOR payload

ADR-002 says anything queried must become a real column. The concrete list depends on the first
real queries: notebook listing, source listing, and full text or vector search over chunks. Getting
this list wrong is the main way the blob design goes bad, so it deserves an explicit pass before the
first entity is written.

## 4. Vector store persistence

`InMemoryEmbeddingStore` loses everything on restart. If re-embedding on startup is unacceptable
even for the MVP, the successor is pgvector via `langchain4j-pgvector`, which keeps the
`EmbeddingStore` abstraction intact.

## 5. TypeScript 7

Blocked by `openapi-typescript`'s `^5.x` peer range, not by anything in this project. Worth
re-checking periodically.

## 6. Aether Datafixers on Spring Boot 4

Verified working, not certified. The starter is compiled against Boot 3.5.10. Re-verify the context
start whenever either the starter or Spring Boot is upgraded, and watch for a Boot 4 targeted
release of the starter.

## 7. No linting or formatting toolchain yet

Neither ESLint/Prettier on the frontend nor Checkstyle/Spotless on the backend is configured. For an
assessment the conventions in the brief are strict enough that automated enforcement is probably
worth adding before the codebase grows.
