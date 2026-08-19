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

`InMemoryEmbeddingStore` loses everything on restart. Re-embedding on startup is now what happens
(question 19), which makes the loss survivable rather than absent: the vectors are still recomputed
every time the process starts, and the cost of that grows with the library.

The successor is pgvector via `langchain4j-pgvector`, which keeps the `EmbeddingStore` abstraction
intact. What the rebuild changed about that move is that it is no longer urgent, and that the code it
would replace already knows how to reconcile a store against the database.

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

## 8. Resolved: the JavaDoc gate is active everywhere

`sumbooklm-ingestion` and `sumbooklm-ai` set `maven.javadoc.skip=true` because Javadoc cannot
process a source set without a type (finding 12). Both gained their first classes with the ingestion
pipeline, and both properties are gone, as `sumbooklm-domain`'s was when it gained the user account
types. No module skips the gate any more, which is what this question was waiting for.

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

## 15. Resolved: a notebook card opens its Sumbook

The card now navigates to `/dashboard/sumbook/{id}`, which shows the sources, the conversation and
the studio. What remains open from the original question is smaller: the card is a `div` with
`role="link"` rather than an anchor, so it cannot be opened in a new tab. Wrapping the card in an
anchor would nest the menu button inside a link, which is invalid; the alternative is an invisible
anchor covering the card with the menu raised above it.

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

## 18. Opening a Sumbook does not count as activity

The timestamp the overview is ordered by is refreshed when a notebook is created, renamed, and now
when a source is added or removed. Opening one still does not refresh it, because the endpoint that
reads a notebook is a `GET` and a `GET` that writes is a `GET` that cannot be cached, retried or
prefetched. The section titled "recently opened" therefore orders by "recently changed".

The fix is an explicit endpoint that records the visit, called by the detail view when it mounts.
That is worth doing once there is something to open that is worth returning to; today the two orders
differ only for a Sumbook that is read and never touched.

## 19. Resolved: the index is rebuilt when the application starts

A source can now be indexed again, and every source is, once the application is ready. What was
missing was not persistence of the vectors but the ability to produce them a second time, which is
what both halves of the original question asked for.

Two things made it cheap enough to do unconditionally. The text a successful run extracted is stored
with the source, so rebuilding needs neither the parser nor the network and only pays for the
embeddings. And indexing replaces rather than appends, so running it again is safe whatever state the
store was in.

The consequence is that a restart retries exactly the sources that never succeeded, because those are
the only ones without stored text. A source that stayed in `INDEXING` because its run was interrupted
is picked up the same way.

What stays open is the cost. Every restart recomputes every embedding, which is seconds for a small
library and minutes for a large one, and a source is not answerable until the rebuild has reached it.
That is question 4's problem now rather than this one's: a store that survives the process removes the
rebuild instead of speeding it up, and the code that reconciles a store against the database is now
in place for it.

## 20. The guard against internal addresses is not airtight

A submitted address is resolved and refused when it points into a private range, and the address the
request finally landed on is checked again before the page is read. Two gaps remain. The name is
resolved once for the check and again for the request, so a name that changes between the two is not
covered. And a redirect into a private range is followed before it is judged, so the request is made
even though its content is discarded.

Closing both means resolving the name once and connecting to the resolved address, with redirects
handled by the application rather than by the library. That is the right shape once sources may be
added by somebody other than the person running the server.

## 21. A failed source says that it failed, not why

`DocumentStatus.ERROR` carries no reason. A user whose upload failed sees that it failed and can only
guess whether the file was scanned, the format unsupported or the page unreachable, and the log is not
theirs to read.

Storing the reason in the payload is easy; deciding what may be shown is the actual question. The
message of a text extraction failure names hosts and file names, and the message of an unexpected
failure names internals, so it needs a small set of causes rather than a free text field.

## 22. Duplicate detection decodes every payload of the notebook

Adding a source reads every source of the notebook and decodes its payload to compare one hash. That
is cheap for a notebook with a dozen sources and wrong for one with a thousand.

It is the direct consequence of the hash living in the payload, which open question 16 already
records. The change is the same one: promote `documentHash` to an indexed column and let the database
answer the question.

## 23. Removing a notebook takes its vectors out outside any transaction

Deleting a notebook or a source removes the matching segments from the vector store inside the
transaction that deletes the rows, but the store has no transaction of its own. A rollback after that
point would leave rows whose vectors are gone.

Nothing follows the removal, so the window is theoretical today. It stops being theoretical as soon as
the store is a real database, at which point the removal belongs in an after-commit listener like the
one indexing already uses.

## 24. The key is handed to the server on every question

Bring your own key means the browser sends the key to this application, which forwards it to the
provider. The application stores it nowhere and logs it nowhere, but it does see it, and a request
made over plain HTTP carries it in the clear.

The honest alternative is for the browser to call the provider directly, which removes the server from
the path entirely, and with it the retrieval that made the question worth asking. The middle ground is
to require HTTPS for the chat endpoint outside development, which is a deployment decision this
scaffold does not make yet.

## 25. Nothing bounds how many questions one account may ask

A question occupies a thread of the chat pool for as long as the provider takes. Eight of them can be
in flight, another thirty-two wait, and after that the caller answers its own question on a request
thread. Nothing distinguishes one account asking forty questions from forty accounts asking one.

The cost is the user's rather than the operator's, which is what makes this less urgent than it looks,
but the threads are not. A limit per account is the shape to add, and the pool is where it would be
enforced.

## 26. A cancelled answer is generated to the end

Leaving a Sumbook aborts the request, and the server keeps generating. That is deliberate: the answer
is already paid for and is worth storing for the next visit. It also means a user cannot stop an
answer they no longer want, and that every abandoned question still costs them tokens.

Stopping it needs the cancellation handle LangChain4j exposes on the streaming callbacks, plus a
decision about what a half generated answer is worth storing as. Both are cheap; deciding which of the
two behaviours a user expects is not.

## 27. The transcript is one conversation per notebook

A notebook holds one conversation, created by its first question. The table already carries an
identifier and its own timestamps, and the payload already carries a title, so several conversations
about one set of sources are a change to the service rather than to the schema.

What is missing is the interface for it. A list of conversations is a fourth thing competing for the
width of a Sumbook, and until there is a reason to have two, one is the honest model.

## 28. History is trimmed by count, not by size

A question is asked with the last ten messages. Ten short exchanges and ten long ones are not the same
request, and the long ones can exceed the context of a small local model while the count still says
they fit.

Counting tokens instead needs a tokenizer per provider, which is the thing this application deliberately
does not have. Counting characters is the approximation to make, once there is a reason to believe the
count is what breaks first.

## 29. A web page is never fetched again

Storing the extracted text made rebuilding free of the network, and it also means a page is read
exactly once, when it is added. A page that changes afterwards keeps answering with what it said that
day, and nothing tells the user how old that is.

Refreshing it is the same call as indexing it again, minus the stored text, so the mechanism is
already there. What is missing is the decision: whether a refresh is something the user asks for, or
something that happens on an interval, and what should happen to an answer that cited the version that
is being replaced.

## 30. The rebuild has no bound and no back pressure

The job walks every source in the database, one after another, and nothing stops it. With a large
library that is a long run on the indexing pool, during which every upload queues behind it, and there
is no way to see how far it has come other than the two lines it logs.

Chunking it by notebook and rebuilding the ones being opened first would fix the part the user
notices, which is that their own Sumbook is not answerable yet. That needs a priority the current
executor does not have.
