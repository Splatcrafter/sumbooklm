# Architecture Decisions

Short decision records. Each one states the choice, the reason, and what it costs.

## ADR-001: Spring Boot 4.1.0 as the baseline

**Decision.** Build on Spring Boot 4.1.0 / Spring Framework 7.0.8 on JDK 25.

**Alternative considered.** Spring Boot 3.5.16, which is exactly the baseline the Aether Datafixers
starter is compiled against, and which would keep a single Jackson 2 stack in the whole application.

**Reason.** Boot 4.1 manages Jackson 2 and Jackson 3 side by side, so the Jackson 2 requirement of
`aether-datafixers-codec` is satisfied by a centrally managed dependency rather than a hand pinned
one. springdoc 3.1.0 and the LangChain4j `-spring-boot4-starter` family both target Boot 4, so no
part of the stack is left behind. The user picked this option explicitly after the trade-off was
presented.

**Cost.** Two Jackson stacks are on the classpath: Jackson 3 (`tools.jackson`) serves the web layer,
Jackson 2 (`com.fasterxml.jackson`) serves the CBOR payload codec. The Aether starter is not
certified for Boot 4; its compatibility was verified empirically (see research findings, item 4) and
has to be re-verified on every upgrade of either side.

## ADR-002: Business data lives in a CBOR blob, not in relational columns

**Decision.** Relational tables carry identity, ownership, timestamps and lookup keys. The business
payload of an aggregate is a single CBOR encoded `bytea` / `BLOB` column plus an integer schema
version column.

**Reason.** The evolving part of the model is the notebook payload, not the table layout. Keeping
evolution inside the payload turns schema migration into a data transformation problem, which is
what Aether Datafixers solves, instead of a DDL problem, which is what Flyway solves at the price of
a migration script per change.

**Cost.** The payload is opaque to SQL. Anything that has to be queried, sorted, filtered or joined
on must be promoted to a real column. That promotion is a deliberate act, and the set of promoted
columns is the actual relational contract.

## ADR-003: Schema versions are integers, `MAJOR * 100 + MINOR * 10 + PATCH`

**Decision.** Version `1.0.0` is stored as `100`. Constants live in
`de.pfoertner.assessment.sumbooklm.persistence.schema.PayloadSchemaVersion`.

**Reason.** The data fixer pipeline selects fixes by comparing an origin version to a target version,
which requires a total order over versions. A single monotonically increasing integer provides that
order and fits in one narrow column.

**Cost.** The encoding saturates at nine minor and nine patch releases per step. That is acceptable
for a payload format; if it ever becomes tight, the encoding widens without touching stored data as
long as the new scheme stays monotonic over the existing values.

## ADR-004: CBOR is bridged into Aether Datafixers through `JacksonJsonOps`

**Decision.** Migration uses `new JacksonJsonOps(cborMapper)` rather than a bespoke CBOR
`DynamicOps` implementation.

**Reason.** `JacksonJsonOps` is typed over `JsonNode`, Jackson's format independent tree, and accepts
any `ObjectMapper`. `CBORMapper` is an `ObjectMapper`. Decoding CBOR bytes therefore yields exactly
the tree the fixers already know how to rewrite, and the same mapper re-encodes the result. Writing a
CBOR specific ops implementation would duplicate `JacksonJsonOps` for no gain.

**Cost.** The persistence module is bound to Jackson 2, because that is the Jackson generation
`aether-datafixers-codec` compiles against. See ADR-001 for how that is contained.

## ADR-005: Flyway is not used

**Decision.** No Flyway dependency, no migration scripts.

**Reason.** Follows directly from ADR-002. With business data in the payload, the relational schema
is small and near static, and a migration tool would mostly manage its own bookkeeping tables.

**Cost.** The DDL still has to reach a production database somehow. See open questions.

## ADR-006: One executable artifact, frontend built by Maven

**Decision.** `sumbooklm-frontend` is a Maven module that runs the Vite build through
`frontend-maven-plugin` and packages the output under `static/` in its own JAR. `sumbooklm-app`
depends on that JAR and repackages everything into one executable Spring Boot artifact.

**Reason.** Spring Boot resolves `classpath:/static/` across every classpath entry, including nested
JARs, so the frontend does not have to be copied into the application module's own resources. The
frontend stays a first class module with its own lifecycle, and `mvn install` produces exactly one
deployable file.

**Cost.** A full Maven build downloads a private Node toolchain and runs `npm ci`. The
`-Dfrontend.skip=true` flag exists for backend-only iterations.

## ADR-007: SPA fallback via a resource resolver, not a catch-all controller

**Decision.** `SinglePageApplicationConfiguration` registers a resource handler for `/**` backed by a
`PathResourceResolver` subclass that returns `index.html` when a path resolves to no packaged asset.

**Reason.** Client side routes have arbitrary depth, so pattern based forwarding needs a growing list
of patterns. A resolver handles any depth with one rule. Controllers are matched before resource
handlers, so real endpoints keep winning, and more specific resource patterns such as the ones the
OpenAPI UI registers are unaffected because `SimpleUrlHandlerMapping` prefers the more specific
pattern.

Registration order matters and works in our favour: `ResourceHandlerRegistry` keeps the last
registration for a given pattern, and application `WebMvcConfigurer` beans run after Spring Boot's
auto configuration, so this `/**` handler replaces Boot's default one instead of being shadowed by it.

**Cost.** The resolver must explicitly refuse to rewrite paths below the API prefix, otherwise an
unmatched API route would answer an HTTP client with an HTML document instead of a 404. That guard is
implemented and verified.

## ADR-008: The backend owns the API contract

**Decision.** springdoc generates the OpenAPI document from the controllers. The frontend generates
its TypeScript types from that document with `openapi-typescript` and calls the API through
`openapi-fetch`.

**Reason.** One source of truth, and a type error in the frontend build whenever the backend contract
moves underneath it.

**Cost.** Type generation requires a running backend (`npm run api:generate`). Generation is not
wired into the Maven build, because that would make the frontend build depend on a live server. The
generated `src/api/schema.d.ts` is committed instead.

## ADR-009: Embeddings run in process, chat models do not

**Decision.** Embeddings use `all-MiniLM-L6-v2` bundled into the artifact; vectors are held in
LangChain4j's `InMemoryEmbeddingStore`. Chat completion is delegated to an OpenAI compatible endpoint
with a caller supplied key, or to a local inference server such as Ollama.

**Reason.** Embedding is the high volume, low value call: running it locally removes both API cost
and the need for a key at ingest time. Chat completion is the low volume, high value call, where the
user's choice of model matters.

**Cost.** The in memory store does not survive a restart and does not scale beyond one node. It is a
development and MVP choice; a pgvector backed store is the natural successor and LangChain4j exposes
both behind `EmbeddingStore`.

## ADR-010: Module boundaries enforce the dependency direction

**Decision.** Seven Maven modules with an explicit dependency graph rather than one backend module
with package conventions.

**Reason.** A package convention is enforced by review; a module boundary is enforced by the
compiler. `sumbooklm-domain` cannot accidentally import a JPA entity manager because JPA is not on
its classpath.

**Cost.** More POM files, and a cross-cutting change touches several modules.

## ADR-011: JavaDoc completeness is enforced by the build

**Decision.** `maven-javadoc-plugin` 3.12.0 runs `javadoc-no-fork` and `test-javadoc-no-fork` in the
`verify` phase with `show=private`, `doclint=all` and `failOnWarnings=true`. Missing or malformed
documentation on any element, including private fields and constructors, fails the build.

**Reason.** Documentation completeness was previously enforced by review only, and review missed
several members: a public `addResourceHandlers` without any comment, every constant of the SPA
configuration, and three implicit default constructors. A doclint run finds all of them in one pass
and cannot be talked out of it.

**Cost.** Implicit default constructors have to be written out explicitly, because an implicit one
cannot carry a comment and doclint reports it as `use of default constructor, which does not provide
a comment`. Modules that contain no type at all cannot run the gate at all; see finding 12.
