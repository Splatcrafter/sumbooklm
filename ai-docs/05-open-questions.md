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

## 8. The JavaDoc gate is disabled in two modules

`sumbooklm-ingestion` and `sumbooklm-ai` set `maven.javadoc.skip=true` because Javadoc cannot
process a source set without a type (finding 12). The property has to be removed from each POM as
soon as the module gains its first class, otherwise the module keeps building without documentation
enforcement and nothing points that out. `sumbooklm-domain` was in the same state and is not any
more: it gained the user account types with the authentication module, and its property is gone.

## 9. Ending a session on every device

Logout closes the session the presented access token belongs to. There is no endpoint that closes
all sessions of an account, although the repository already has the query for it
(`revokeAllOfUser`, used by reuse detection). The open part is not the mechanism but the product
decision: whether a password change should end other sessions implicitly, and whether a user should
see a list of their open sessions.

## 10. The encrypted client cookie is close to a practical size limit

Measured against the running application, the encrypted cookie holding one token pair and the
account is around 1.7 kB, against a per-cookie limit of about 4 kB. Both tokens are JWTs, so the
size grows with every claim added. The headroom is real but not generous, and two changes would
consume it: adding roles or scopes to the access token, or storing more than the current account in
the client session. Opaque refresh tokens (see the alternative in ADR-013) would roughly halve it.

## 11. Rotating the two secrets

`sumbooklm.security.jwt.secret` and `sumbooklm.security.cookie.secret` have no rotation story.
Rotating the JWT secret invalidates every issued token at once, and rotating the cookie secret
invalidates every stored client cookie, which the client handles by discarding the cookie and asking
for a login. Both are acceptable as an emergency measure and neither is acceptable as routine
maintenance. Supporting overlap means accepting a set of keys with a key identifier in the header
rather than one key.

## 12. Argon2id instead of bcrypt

The delegating password encoder currently produces bcrypt hashes, which is its default and which
needs no additional dependency. Spring Security's `Argon2PasswordEncoder` requires Bouncy Castle on
the classpath. Because the algorithm travels as a prefix in the stored hash, moving to Argon2id is a
configuration change plus a dependency, and existing accounts migrate on their next login through
`upgradeEncoding`. The question is whether the dependency is wanted, not whether the migration
works.

## 13. Nothing limits the rate of authentication attempts

Neither the login nor the refresh endpoint is throttled. The password hash is deliberately expensive
and the failing path costs the same as the succeeding one, which raises the cost of guessing, but
that is not a substitute for a limit. Where the limit belongs depends on the deployment: a reverse
proxy in front of the application, or a bucket per username and per address inside it.

## 14. Unknown paths below the API prefix answer 401, not 404

The filter chain requires authentication for everything below `/api` that is not explicitly opened,
so an unauthenticated request to a path that does not exist is rejected before routing. That is
deliberate as long as the API is small, because it does not reveal which endpoints exist. It becomes
confusing once authenticated clients call unknown paths, which will read as an authorization problem
in their logs rather than a routing mistake.

## 15. A notebook card cannot be opened yet

The overview renders the notebooks and their actions, but nothing happens when a card itself is
clicked, because there is no route below `/` that shows one notebook. That is deliberate for this
step: inventing a detail route would have meant inventing what a notebook view contains. It has to be
answered before the dashboard is shown to anyone, since a grid of cards that do not open reads as
broken rather than as unfinished.

## 16. The document hash cannot be queried

`DocumentPayload.documentHash` is where the specification asked for it, in the payload. Duplicate
detection inside one notebook can therefore decode the sources of that notebook and compare, which is
cheap enough. Detection across all sources of an account cannot be expressed as a query at all and
would need the hash as an indexed column next to the payload. The choice belongs to the ingestion
step, which is the first code that will actually need it.

## 17. The frontend has no test runner

The account screens and the dashboard were both verified by rendering them through Vite's SSR loader
and asserting on the produced markup, with the scripts kept outside the repository. That catches
missing translation keys, wrong plural forms and structural regressions, but it is not part of
`npm run build` and nothing runs it in a pipeline. Adding Vitest plus Testing Library would turn those
scripts into real tests; it also adds a toolchain the project does not otherwise have.

## 18. `last_activity_at` is only written by the application

The timestamp the overview is ordered by is refreshed when a notebook is created and when it is
renamed. Opening a notebook does not refresh it, because there is no endpoint that opens one yet, and
adding a source does not either. Both have to write it once the corresponding endpoints exist, or the
section titled "recently opened" will be ordered by something else than what it claims.
