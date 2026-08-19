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

## ADR-012: Authentication lives in its own module

**Decision.** A new module `sumbooklm-security` holds password hashing, the token lifecycle, the
cookie key derivation and the marker for operations that verify their session. It depends on
`domain` and `persistence`; `api` depends on it.

**Alternative considered.** Putting the same classes into `sumbooklm-api` next to the controllers,
which would have avoided a new module.

**Reason.** The same argument as ADR-010: a module boundary is compiler enforced, a package
convention is not. Keeping the services out of `api` also keeps them free of the servlet API, which
is what makes them testable without a web environment and what forces the transport layer to decide
explicitly which request facts, such as the caller address, reach the security layer.

**Cost.** One more POM, and one deliberate exception: the `SecurityFilterChain` lives in `api`, not
in `security`. It has to name concrete paths, and those are owned by `ApiPaths` in `api`. Which
routes are public is an API statement anyway.

## ADR-013: Both tokens are JWTs, verified by two different decoders

**Decision.** Access and refresh token are both HS256 signed JWTs from the same key. They carry a
`token_type` claim, and the application publishes two `JwtDecoder` beans that differ only in the
value of that claim they accept. The primary one accepts access tokens and is what the resource
server authenticates with; the second is used where a refresh token is exchanged.

**Alternative considered.** An opaque, random refresh token. Since every use of a refresh token is
checked against the database anyway, its signature carries no information the row does not already
hold, and an opaque token cannot be replayed against a signature-only verifier by mistake.

**Reason.** The brief asks for a JWT based approach with a strict separation of the two kinds. Two
decoders make that separation structural instead of a check somebody has to remember to write: a
refresh token presented as a bearer credential fails verification rather than being accepted as an
access token. That case is covered by a test.

**Cost.** Two decoder beans where the framework expects one, resolved by marking the access token
decoder `@Primary`. A refresh token is also considerably larger than an opaque token would be,
which matters because the client stores it in a cookie (see ADR-015).

## ADR-014: Refresh tokens are stored as digests, rotated on use, and revoke their session on reuse

**Decision.** `refresh_token` stores a SHA-256 digest of the issued token, never the token. Every
successful exchange revokes the presented token and issues a new pair. Presenting a token that was
already consumed revokes every token of the account.

**Reason.** Storing a digest keeps a database dump from being a set of usable credentials. Rotation
bounds the value of a stolen token to the time until the legitimate client refreshes next. Reuse of
a consumed token can only mean that two parties hold it, and the server cannot tell which one is
legitimate, so the safe answer is to end the session for both.

**Cost.** A client that loses the response of a refresh has lost its session, because the token it
still holds was already consumed on the server. That is the accepted trade-off of rotation. The
revocation path also had to be marked `noRollbackFor`, because it reports its outcome with an
unchecked exception, which would otherwise roll back the revocation it just performed.

## ADR-015: The client encrypts its token pair with a key it never stores

**Decision.** The server issues an opaque key handle in an `HttpOnly`, `SameSite=Strict` cookie. The
client encrypts its token pair with AES-GCM and stores the ciphertext in a second, script readable
cookie. `GET /api/v1/security/cookie-iv/` derives the key from the handle with HKDF over a server
secret and returns it together with a freshly generated initialization vector.

**Reason.** Anything a page can read, injected script can read. Moving the key out of script
readable storage means the encrypted cookie alone is worthless: copying it out of a browser profile,
a backup or a synchronised profile yields no tokens. Deriving instead of storing means no key
material is persisted anywhere, and rotating the secret invalidates every stored client cookie at
once.

**Honest limit.** The scheme does not stop script that runs inside the origin: the browser attaches
the handle cookie automatically, so injected code can simply repeat the request. It raises the cost
of an offline copy, not of code execution. That is stated in the JavaDoc of the package rather than
being left for a reviewer to discover.

**Cost.** Restoring a session needs one request before anything can be decrypted, so the client has
a third state next to authenticated and anonymous. The returned vector is for the next encryption
only; decryption uses the vector the client stored in front of its ciphertext, because reusing a
vector with the same key would break the authentication of the cipher.

## ADR-016: A user account is split between columns and CBOR payload

**Decision.** `user_account` carries identifier, username, password hash and the two timestamps as
columns. First name, last name and the two recorded network addresses live in the CBOR payload with
its schema version, encoded through an Aether Datafixers codec.

**Reason.** This is ADR-002 applied to the first real aggregate. The promoted columns are exactly
the ones something queries or compares: the username is looked up on every login, the hash is
compared, and the timestamps are what a listing would sort by. The profile is read only after the
row was already found, so keeping it in the payload costs nothing and makes adding a field a data
fix rather than a migration.

**Cost.** Reading an account decodes CBOR even when only the profile is needed, and a change to
`UserAccountPayload` is only safe together with a schema version and a fix.

## ADR-017: No `AuthenticationManager` and no `UserDetailsService`

**Decision.** The login endpoint compares the presented password against the stored hash with the
configured `PasswordEncoder` directly. Spring Security's `AuthenticationManager`,
`DaoAuthenticationProvider` and `UserDetailsService` are not used.

**Reason.** Those types exist to run an extensible chain of authentication mechanisms per request.
In this application credentials are verified at exactly one endpoint, and every subsequent request
is authenticated by the resource server from the access token. Wiring the chain would add three
beans and a second database lookup per login without changing any outcome.

**What is kept.** The parts that do carry weight: the delegating password encoder with its algorithm
prefix, `upgradeEncoding` to re-hash on login when the encoding changed, and the constant work on
the failing path so that a login does not reveal whether a username exists.

**Cost.** Adding a second authentication mechanism later means introducing the manager at that
point. Nothing in the current code prevents it.

## ADR-018: The API is versioned in the path

**Decision.** Endpoints live below `/api/v1`. `ApiPaths` declares the prefix and every endpoint
constant, and controllers map against those constants.

**Reason.** A breaking change can then be published next to the old contract instead of replacing
it, which matters because the frontend generates its client from the specification and a silent
change would surface as a type error at best. Declaring the paths as constants keeps the security
filter chain and the controllers from drifting apart, since both read the same values.

**Cost.** A version segment is a commitment: once `v2` exists, `v1` has to be maintained or
deliberately retired.

## ADR-019: The account screens leave the application theme

**Decision.** The login and registration screens do not use the semantic theme tokens of the
application. They are always dark, they use the grayscale of the JetBrains website directly through
a `jb-*` colour namespace, and they place their content on the left instead of centring it.

**Reason.** Everything else in the application follows the shadcn tokens so it can be themed. The
account screens are the only screens with no application content behind them, which makes them the
one place where a deliberate composition costs nothing: there is no data whose colour coding has to
survive, and no layout that has to hold at arbitrary widths. Treating them as a themed page produced
the sign-in form every framework produces.

Left alignment is the part that carries the most. A centred card divides the screen symmetrically and
leaves the background as decoration behind it. Moving the card off centre gives the screen a
direction and turns the remaining space into something worth rendering.

**Cost.** Two divergences to maintain. The `jb-*` palette is a second colour system next to the
shadcn tokens, and the shadcn primitives need explicit overrides on these screens, which is why
those overrides are collected in one module instead of being repeated per form. If the application
ever gains a light theme for signed-in users, these screens will not follow it, which is intended
but has to stay a conscious choice rather than an oversight.

## ADR-020: The background is generated, in two forms (superseded by ADR-022)

**Status.** Superseded. The construction described here produced crumpled marble rather than waves,
and the SVG fallback did not resemble it. Kept because the reasoning about why the fallback is
generated rather than shipped still holds, and because the failure is instructive.

**Decision.** The account background is a WebGL 2 fragment shader: a second order domain warp over
fractional Brownian motion, squashed on one axis so it reads as sedimentary layers, with contour
lines on top and the same grayscale as the cards. Where it cannot run, an SVG filter chain produces a
still image in the same visual language.

**Alternatives considered.** A bitmap for the fallback, which would have added a binary asset that no
longer matches when the shader changes. An inline SVG element instead of a background image, which
re-runs its filter on every resize, on exactly the devices that get the fallback. A gradient, which
would have been visibly a different design rather than the same one held still.

**Reason.** The motif is not decoration picked for looks: layers that compact into something readable
is what the application does to sources. Generating it rather than shipping it means the still image
and the animation are the same construction expressed twice, and the palette is shared with the
cards, so the background cannot drift out of the design.

The fallback uses `feTurbulence` with `fractalNoise`, which is fBm in the SVG filter specification,
displaced by a second turbulence through `feDisplacementMap`, which is the domain warp. A transfer
table maps the result into the grey ramp, with a deliberate staircase in the table so the layering
shows. It is exposed as a data URI and used as a CSS background image, so the browser rasterises the
filter once and only scales it afterwards.

**Cost.** Two implementations of one image that have to be kept recognisably alike by hand, and a
shader whose visual result cannot be verified in this environment; see finding 24.

## ADR-021: The shader lowers its own quality before it gives up

**Decision.** The renderer measures the interval between the frames it renders. Sustained intervals
above its target walk a quality ladder down, first the octave count of the noise, then the resolution
the shader renders at. Only when the lowest level is still too slow does it hand over to the still
image. Small touch devices and browsers without WebGL 2 skip the shader from the start.

**Reason.** A capability check made up front is a guess about a device. A frame time is a measurement
of it. Guessing wrongly in one direction denies the animation to a machine that could run it; in the
other it leaves a phone rendering a full screen shader until the battery notices. Measuring gets both
right, and the ladder means the answer is a smaller version of the same image rather than a different
one.

A phone is exempted from the measurement on purpose. It could pass, and running a full screen
procedural shader behind a login form is still the wrong trade there.

**Cost.** The ladder is calibrated against one target frame rate, and the thresholds are judgement
rather than measurement across a device fleet. A visitor who asked for reduced motion gets a single
rendered frame instead of the animation, which keeps the image and drops the movement.

## ADR-022: The background is warped sine bands, and the fallback is the same field pre-rendered

**Decision.** The background is a stack of sine bands bent by low frequency fractal noise. The still
version is a PNG rendered from the same construction on the CPU by
`sumbooklm-frontend/scripts/accountBackground.mjs` and committed as
`src/assets/account-background.png`.

**What was wrong before.** ADR-020 warped fractional noise with the canonical amplitude of four over a
domain barely a few units wide. At that ratio the high octaves dominate and the image is a crumpled
marble texture at pixel scale, not waves. Combined with a colour ramp that spanned only the darkest
greys, the result had neither structure nor tonal range. Reversing the order fixes it: warping bands
gives waves, warping noise gives marble.

**Why a PNG and not the SVG filter chain.** The SVG version was an approximation of the shader by a
different mechanism, which is why it did not look like it. Rendering the actual field on the CPU
removes the resemblance problem entirely: the fallback is the same image, evaluated once at build
time. The cost is a 190 kB asset in the repository and an algorithm that exists twice, in GLSL and in
the generator script. That duplication is deliberate and is guarded by naming the constants
identically on both sides, so a divergence is visible by comparing two lists.

**Why a colour ramp at all.** The cards use the JetBrains website grayscale, which was the request.
The background was never part of that request, and forcing it into the same near-black greys is what
made the screen look dead. It now runs a six stop ramp from near black through deep indigo and
saturated violet into hot magenta and pink, at full saturation rather than muted. The palette is one
array in each of the two implementations, and the constants are compared between them rather than
trusted.

**Even lighting, and what carries readability instead.** An earlier version darkened the left half
with a horizontal sweep so the form had a quiet bed. That was removed: the darkened edge read as a
band rather than as lighting. Readability now rests on the cards themselves, which sit at 85 percent
opacity, and on a shadow behind the identity above them, which is the only text on the screen not
backed by a card. This was checked by rendering the field with a rectangle drawn where the card sits
rather than by reasoning about it.

**Sharpness.** The sine is pulled towards its extremes by an exponent below one before it samples the
ramp, which widens the plateaus and steepens the crossings. At 0.45 the bands gain defined edges while
the violet mid tones survive; pushing it further turns the image into a two colour poster and skips
the middle of the ramp entirely.

## ADR-023: The account cards reserve a height instead of fitting their content

**Decision.** The primary card of the account screens reserves a fixed minimum height from the small
breakpoint upwards, keeps its header at the top and pins its submit button to the bottom edge. All text
on both screens lives on a card; the frame around them carries none.

**Reason.** Sign-in and registration differ by one field row, so a card that fits its content changes
size when a visitor moves between them, and because the panel is vertically centred, both edges move at
once. Reserving the height of the longer form removes the movement, and pinning the button means the
spare room appears as spacing above it rather than as a hole in the middle. The header never moves,
which is the part a reader is looking at while the route changes.

Text was moved off the background for the same class of reason: it was legible over the dark parts of
the waves and poor over the bright ones, and which part it lands on depends on the viewport.

**The trap in reserving a height.** The reserve has to exceed the content, not match it. A reserve one
pixel below the taller form leaves that form deciding its own height, and the two differ by that pixel.
Fractional line boxes make it worse, because a fractional content height rounds differently than the
reserve; the subtitle originally used a line height of 1.625 on a 14 pixel font, which is 22.75 pixels.
Every line box inside the card is now a whole number of pixels, and the reserve sits well above the
computed content height.

**Cost.** The reserve is a constant, and no CSS mechanism equalises the height of two independently
routed screens without one. A translation that wraps a line where the current ones do not will exceed
it, at which point that screen grows and the constant needs raising. The failure mode is deliberately
a growing card rather than clipped content, which is why the property is a minimum height and not a
fixed one.

## ADR-024: Notebook management is a module of its own

**Decision.** Creating, listing, changing and removing notebooks lives in `sumbooklm-workspace`, a new
module that depends on `domain` and `persistence` and on nothing else of the application. The API
module gained a dependency on it. The shared `Clock` bean moved out of the security module into
`sumbooklm-app`.

**Reason.** The two places it could otherwise have gone are both worse. Putting the service into the
API module would make the transport layer the owner of the rules, which is exactly what the auth flow
avoids by keeping `AuthenticationService` outside it. Putting it into `persistence` would give the
persistence module a service layer and make it the largest module in the build.

`security` already established the shape: a module that owns tables through `persistence`, takes
commands, returns domain objects and knows nothing about HTTP. `workspace` is the same shape for a
different aggregate, so the graph gains a sibling rather than a new kind of node.

**The clock.** Two modules now stamp timestamps, and they have to agree on what now is. A bean defined
in whichever module needed it first would leave the second one depending on a bean it does not
declare, so it moved to the composition root. Both modules ask for a `Clock` and neither supplies one.

**Cost.** One more module in the reactor, and the notebook aggregate is now spread over three of them:
the record in `domain`, the row and the payload in `persistence`, the rules in `workspace`. That is the
same spread the user account already has.

## ADR-025: Ownership is part of every query, never a check afterwards

**Decision.** Every repository method and every service method of the workspace module takes the
account it acts for, and the account is a parameter of the query. There is no method that loads a
notebook by identifier alone. A notebook of another account produces the same `NotebookNotFoundException`
as a notebook that does not exist, which the API answers with `404`.

**Reason.** The two ways to enforce ownership are to filter on it or to load first and compare
afterwards. The second one works until somebody adds a method that forgets the comparison, and the row
has already been read at that point, so a mistake leaks data rather than failing closed. Filtering
cannot be forgotten silently: a query without the owner does not compile against these repositories,
because no such method exists.

Answering `404` rather than `403` follows from the same reasoning. `403` on a foreign notebook tells
the caller that a notebook with that identifier exists, which is information they are not entitled to.

**Cost.** Every signature carries a user identifier, including the ones where it looks redundant next
to an identifier that is already unique. That redundancy is the point.

## ADR-026: What is a column of a notebook and what is payload

**Decision.** The `notebook` row carries the identifier, `user_id`, `created_at`, `last_activity_at`
and the payload envelope. The title, the pin state and the topic icon live in the CBOR payload. The
same split applies to `source_document` and `chat_session`: identifiers, owner, notebook and the
timestamps a list is ordered by are columns, everything else is payload.

**Reason.** A column is what a query has to reach without decoding, and the only queries the overview
performs are "the notebooks of this account, most recently active first" and "how many sources does
each of them hold". Nothing else is filtered or ordered on, so nothing else needs to be a column.
Everything a user edits is therefore in the half that a data fixer can change, which is what the
payload mechanism exists for.

**Consequence for the pin state.** Pinning is a user-visible flag stored in the payload, so the two
sections of the overview are produced by grouping in memory after reading every notebook of the
account, not by two queries. That is acceptable at the number of notebooks one person keeps, and it
avoids making the first user-visible flag the first reason to migrate a table.

**Consequence for the document hash.** The hash that identifies the content of a source is likewise in
the payload, so duplicate detection will compare decoded payloads inside one notebook rather than
asking the database. Detection across notebooks would need the hash promoted to an indexed column; see
the open questions.

**Cost.** Neither the title nor the pin state can appear in a `WHERE` clause. The first feature that
needs one, a search across notebooks being the obvious candidate, forces the decision to be revisited
for that field.

## ADR-027: The overview is two sections, and creating is a card

**Decision.** The dashboard shows pinned notebooks in a first section and everything else in a second
one. A notebook appears in exactly one of them. The pinned section is not rendered at all while
nothing is pinned. The first cell of the second section is always the card that creates a notebook.

**Reason.** A pinned notebook that also appears under the recent ones reads as two notebooks, and the
duplicate is worse than the completeness it buys. An empty section with a heading is worse than no
section, because it occupies the top of the screen with nothing.

The create action is a card rather than a button in the header because that is where the eye already
is, and because as a card it keeps its position as notebooks are added: it is the first cell of the
grid, not the cell after the last notebook.

**Cost.** A user who pins everything sees a second section holding only the create card. That is
correct but looks thin, and it is the price of not showing the same notebook twice.

## ADR-028: Indexing happens after the commit, on an executor of its own

**Decision.** Adding a source stores it and answers. The storing transaction publishes a
`SourceAddedEvent`, and an `@Async @TransactionalEventListener(AFTER_COMMIT)` runs the pipeline on a
bounded pool of two threads declared by the composition root.

**Reason.** Three properties are wanted at once, and each of the three parts buys one of them. The
event decouples the request from the work. The after-commit phase guarantees that the row exists
before anything reads it, which a direct call could not. The separate executor keeps a run that takes
a minute off the threads the web server answers requests with.

The pool is small on purpose: the work is computing embeddings, which already saturates the cores it
is given, so more threads would not index faster. The queue is bounded and full queues make the
caller run the task, which slows an upload down rather than dropping it.

**Alternatives.** Calling `@Async` from the storing method races the transaction. Doing the work in
the request holds a connection and a server thread for as long as the document is long. A queue in
the database would survive a restart, which the vector store does not, so it would add durability to
the one half of the pipeline that cannot use it.

**Cost.** A restart during a run leaves a source stuck in `INDEXING` for ever, because nothing picks
it up again. See open question 19.

## ADR-029: Segments are cut on paragraphs, at a thousand characters with a fifth of that as overlap

**Decision.** `DocumentByParagraphSplitter(1000, 200)`. Paragraph boundaries first, sentence
boundaries inside a paragraph that does not fit, and two hundred characters of the previous segment
repeated at the start of the next one.

**Reason.** The size is chosen against the model rather than against the sources. all-MiniLM-L6-v2
degrades beyond roughly two hundred and fifty tokens, and ordinary prose runs about four characters
to the token, which is where the thousand comes from. Larger segments dilute the vector that stands
for them, so they win more comparisons and answer fewer of them.

Paragraphs are the unit an author already used to separate one thought from the next, so cutting on
them costs nothing and keeps segments that mean something on their own. The overlap exists for the
statement that straddles a boundary: without it that statement is in neither segment completely.

**Cost.** The overlap is stored and embedded twice, so both the index and the reported token count are
about a fifth larger than the text. That is visible to the user as a token count that exceeds what
they would get by counting the document.

## ADR-030: One vector store for everything, kept apart by metadata

**Decision.** A single `InMemoryEmbeddingStore` holds the segments of every notebook of every
account. Each segment carries `notebookId` and `sourceDocumentId` as metadata, and the only method
that writes to the store takes both as parameters.

**Reason.** The separation has to be impossible to forget, not merely documented. Tagging at the call
site would allow an untagged segment, and an untagged segment is one that every notebook of every
account can retrieve. Making both identifiers parameters of the single write path means a segment
without them cannot be produced.

A store per notebook was the alternative. It separates by construction, but it moves the problem to
managing the lifetime of one store per notebook, and it makes the eventual switch to a real vector
database harder, since those separate by metadata filter exactly as this does.

**Cost.** Correct separation now depends on every read passing a filter. Nothing reads yet, so the
first retrieval is where that has to be established.

## ADR-031: A file and an address are two endpoints, not one

**Decision.** `POST /sources/files` takes `multipart/form-data`, `POST /sources/links` takes JSON.
The collection itself stays at `/sources`.

**Reason.** One operation accepting both media types is described in the OpenAPI document as a body
that is sometimes one shape and sometimes another, and the generated client then offers a body type
that is honest about neither. Two operations keep the generated client able to say what it is
sending.

**Cost.** The two paths read as sub-resources of the collection although they are actions on it. The
alternative spellings, a query parameter selecting the kind or a single operation with two content
types, were worse in the specification rather than in the URL.

## ADR-032: An upload is stored, a page is not, and they are compared differently

**Decision.** The bytes of an uploaded file are stored in a `content` column next to the payload. A
web source stores only its address. Duplicate detection hashes the bytes of a file and the normalised
address of a page.

**Reason.** A file has no other home: the user's copy is not reachable from the server, so losing the
bytes means the source can never be parsed again. A page does have one, and a copy taken at upload
time would silently become a different document than the address resolves to.

That asymmetry decides the comparison as well. A file can be compared by content because the content
is in hand. A page cannot, because it has not been retrieved yet while the request is being answered,
and retrieving it first would make the caller wait for a foreign server before learning whether their
source was even accepted. Comparing addresses is the honest approximation, and normalising the case
of scheme and host and dropping the fragment covers the spellings that are the same address.

**Cost.** Two addresses serving the same page count as two sources. See open question 16 for the
other half of this trade, which is that the hash is in the payload rather than in a column.

## ADR-033: An opened Sumbook is three panels, and only the middle one grows

**Decision.** Sources on the left, the conversation in the middle, the studio on the right. The two
outer panels have fixed widths; the middle one takes what is left. Below the extra large breakpoint
the studio disappears, and below the large one the panels stack.

**Reason.** The two outer panels are lists, and a list has a width beyond which it only gets emptier.
The middle panel holds prose and a summary, which is the content whose readability actually depends
on width, so it is the one that should absorb the space.

The studio is the panel that disappears first because it is the one with nothing in it yet. It is
still rendered at full width rather than added later, so that the layout does not rearrange itself the
moment the first studio feature lands.

**Cost.** On a laptop at the large breakpoint the studio is not visible at all, which will be wrong
once it holds something the user needs. That is a breakpoint to revisit rather than a structure to
change.

## ADR-034: The chat model is presented per request and never stored

**Decision.** The provider, the model name, the key and the address travel in `X-AI-Provider`,
`X-AI-Model`, `X-AI-Api-Key` and `X-AI-Base-Url`. A client is built from them for one answer and
discarded. The application publishes no chat model bean and holds no key.

**Reason.** The deployment has no key of its own, and giving it one would make every question the
running cost of whoever hosts it. Presenting the key per request is also what makes revoking it real:
the user changes their settings, and the next question uses the new value with nothing cached in
between.

Headers rather than the body, because one of the values is a credential and the body of this request
is a question that is stored. A key in the body would end up in every log line that records one.

**Cost.** A client is constructed per answer, which for the OpenAI compatible path means an HTTP
client per answer. That is measurable next to a model that takes seconds to reply, and caching it
would mean holding keys in memory and deciding when a changed setting takes effect.

## ADR-035: Retrieval is filtered by the retriever, not after it

**Decision.** `NotebookIndex.retrieverFor(notebookId)` builds an `EmbeddingStoreContentRetriever`
whose metadata filter is the notebook. Nothing else reads the store.

**Reason.** The store is shared by every notebook of every account, so a read without a filter returns
other people's paragraphs. Filtering afterwards would mean the wrong segments were retrieved and then
dropped, which is one forgotten line away from being retrieved and used.

Making the notebook a parameter of the only method that hands out a retriever mirrors what indexing
already does, so both directions of the store are impossible to use without naming the notebook.

**Cost.** The floor on relevance is a constant in that method rather than a setting. It is the value
that decides whether a question the notebook says nothing about is answered from its least unrelated
paragraph, and it will need tuning against real documents.

## ADR-036: The answer is streamed as typed events, not as raw text

**Decision.** The endpoint answers with server sent events: one `sources` event, then `token` events,
then either `done` or `error`. Every payload is a JSON object.

**Reason.** A part of an answer can contain a newline, and a newline is what separates the fields of
an event. Sending bare text would mean encoding it anyway, and encoding it as an object costs the same
and leaves room for a field.

The `sources` event exists because an answer cites its sources by number while it is being written. A
client that only learned the sources at the end would have to hold the text back or render citations
it cannot name yet.

The `done` event repeats the whole answer rather than announcing the end. It is the text the provider
closed the stream with and the text that is stored, so a client that missed a part ends up with the
same answer as the transcript instead of a slightly different one.

**Cost.** The answer is transferred twice. For an answer of a few kilobytes that is cheaper than the
reconciliation the alternative needs.

## ADR-037: The question is stored before the model is asked

**Decision.** Opening a turn resolves the notebook, appends the question and returns the conversation
so far, in one transaction, on the request thread. Only then does the answer start being generated,
on an executor.

**Reason.** The two failures that matter happen at different times. A notebook that does not belong to
the caller and a model selection that cannot be used are both known before anything is generated, and
both belong in the status of the response rather than inside a stream that has already started with
status 200.

Everything after that can fail without losing the question. A user whose provider was unreachable sees
what they asked and can ask it again once they have fixed their settings.

**Cost.** A conversation can hold a question with no answer after it. That is a state the interface has
to render, which it does by marking the answer that never arrived rather than by hiding the question.

## ADR-038: The answer is written to the transcript by a third component

**Decision.** The finished answer is handed to an `@Async` recorder, which calls the transactional
service. The client is told that the answer is complete without waiting for the write.

**Reason.** The last part of an answer arrives on a thread belonging to the provider's HTTP client, and
that thread is what closes the response. Running a transaction there would make the user wait for a
database write after they have already read the answer.

**Cost.** A client that reloads the transcript immediately after the last word can be ahead of the
write and see the answer missing. The window is a few milliseconds, and the interface already holds
the answer it just received.

## ADR-039: Grounding is instructions plus numbered passages, and citations are Markdown links

**Decision.** The system message carries the rules and the retrieved passages, numbered and named. A
citation is written as `[n](#source-n)`.

**Reason.** Retrieval on its own is a hint. Without the rule that only the passages may be used and
that a missing answer has to be admitted, the model answers from its training and the retrieval merely
biases it.

The citation is a Markdown link because the answer is Markdown: a client that renders Markdown already
parses it, and no second convention has to be extracted from the text. The target is an anchor rather
than a URL, so the client decides what following a citation means. Numbers are per source rather than
per passage, because a citation should name a document the reader can open.

**Cost.** The model can cite a number that was never offered. That is rendered as a plain number
rather than as a name, which leaves the mistake visible instead of inventing a document for it.

## ADR-040: The settings are encrypted into a second cookie under the same key

**Decision.** The model settings live in a cookie whose name is the one the backend chose plus a
suffix, encrypted with the same derived key as the session.

**Reason.** The key is already derived per browser and already unreadable to script that cannot repeat
the request, and the settings contain a credential that deserves the same treatment as the token pair.
Reusing the scheme also means the API key becomes unreadable at sign-out, because the key handle is
expired then, which is the behaviour a credential handed in by a user should have.

**Cost.** The settings cannot be read before the session has been restored, so the interface starts
with none and fills them in. Signing out discards them for good rather than keeping them for the next
sign-in.

## ADR-041: The text a source was read as is stored with the source

**Decision.** A finished indexing run writes the extracted text into `extracted_text` next to the row.
A run that finds it there does not read the source again, whatever kind of source it is. For an
upload the original bytes are kept as well.

**Reason.** Indexing happens more than once. It happens when a source is added, when a user asks for
it again, and for every source whenever the process starts, so reading the source became the expensive
part of an operation that is otherwise arithmetic.

Reading again is also not equivalent to reading once. A parser is deterministic, but a web page is
not: a rebuild that re-fetched would produce segments from whatever the address serves now, silently
replacing what the stored answers cited. The stored text is exactly what the index was built from,
which makes rebuilding a repetition rather than a new reading.

The bytes of an upload stay because they are the source of truth for a file, which has no other home
(ADR-032). The text is derived from them and may be derived differently by a later parser.

**Cost.** An uploaded file is stored twice, once as bytes and once as text. And a web page is now read
exactly once ever, so a page that changes is never noticed; see open question 29.

## ADR-042: The whole index is rebuilt at startup, including what succeeded

**Decision.** Once the application is ready, every source in the database is put through the pipeline
again, on the indexing executor, one after another. No status is exempt.

**Reason.** The vector store lives in the heap and the sources live in the database, so after a
restart every source reports the stage it reached while the segments behind it are gone. That is the
worst shape a failure can have: nothing reports an error, questions are answered from an empty index,
and the answers are refusals that look like the notebook simply says nothing about it.

Exempting the sources that succeeded would be exempting exactly the ones that lost something. After a
restart a finished source and an interrupted one are in the same position, and what distinguishes them
is only what the rebuild costs: one is rebuilt from its stored text, the other is read for the first
time. That is also why failures are not exempt either, which makes a restart a retry for them at the
price of one attempt each.

The alternative, marking sources as unindexed at shutdown, does not survive the shutdown that matters,
which is the one that was not orderly.

**Cost.** Every embedding is recomputed on every start, and a source is not answerable until the run
reaches it. Both are the price of a store that does not survive the process; see open questions 4
and 30.

## ADR-043: Indexing replaces the segments of a source

**Decision.** `NotebookIndex.index` removes everything stored for that source before it writes.

**Reason.** A source is indexed more than once now, and appending would leave every paragraph in the
index as many times as the source was read. That is not merely wasteful: the retriever returns the
nearest segments, so duplicates crowd out other sources and the answer narrows to whatever was indexed
most often.

Putting the removal inside the write rather than in front of its callers means there is no call site
that can forget it, which is the same reason the notebook identifier is a parameter there (ADR-030).

**Cost.** A rebuild that fails after the removal leaves the source with no segments at all, where
before it had stale ones. Stale segments are worse: they answer questions from a version of a document
the application no longer believes in.

## ADR-044: The address guard sits in the resolver, not in front of the request

**Decision.** Web sources are fetched with Apache HttpClient 5, whose connection manager is given a
`DnsResolver` that refuses every name leading into a network of this server. jsoup keeps the parsing.
Redirects are followed by the client, up to five hops.

**Reason.** Checking an address and then fetching it are two resolutions of the same name, and a name
that answers differently the second time is the whole of a rebinding attack. Inside the resolver there
is only one resolution, and it is the one the connection uses.

The same seam removes the reason to drive redirects by hand. Every hop is a connection, so every hop
passes the resolver, and a redirect cannot reach an address the first request was not allowed to
reach. The recorded plan was to handle redirects in the application; putting the check one layer lower
made that unnecessary rather than merely easier.

jsoup could not host the rule because it resolves the host inside the call that fetches, which leaves
no seam at all. It stays for what it is good at, which is turning bytes into a document.

**Cost.** A second HTTP stack in the application, and with it a changed default for every Spring
`RestClient`, which was verified rather than assumed (finding 45). The rule still cannot see a public
address that only this machine can reach, which no resolver can; see open question 31.

## ADR-045: A failed source reports a cause, not a message

**Decision.** `DocumentFailure` is a closed set of seven constants stored in the payload next to the
stage. The extractor that raised the failure chooses the constant, and the interface turns it into a
sentence in the language of its user.

**Reason.** What a parser or an HTTP client says when it fails names hosts, file paths and internals
of a library. None of that may be handed to a user, and translating it is not possible either, so a
message field would have produced a reason that is either unsafe or unshown.

The set is small because a cause is only worth reporting if it changes what the user does next. Seven
constants are what remained after collapsing everything that leads to the same action, which is why an
unsupported format and a damaged document are one constant and a refused address and an unreachable
one are two.

The cause travels with the exception rather than being derived from it afterwards. Only the extractor
knows whether an address was refused, unreachable or merely empty, and reconstructing that from a
message or from the type of an underlying exception would be guesswork about a fact that was known.

**Cost.** A cause added later is a value an older client does not know, which is why the client
narrows anything unfamiliar to `UNEXPECTED` rather than rendering a key. And the payload grew a field
inside schema `V1_0_0` again, which is the last change that can be made that way once the application
has been deployed anywhere.

## ADR-046: The content hash is a column with a unique constraint over it

**Decision.** `document_hash` moves out of the CBOR payload into a column, with a unique constraint
over `(notebook_id, document_hash)`. Adding a source asks the database whether the notebook already
holds that content, and the constraint is what answers if two requests ask at the same moment.

**Reason.** The rule that decides where a field lives is whether it is queried or displayed
(ADR-016), and the hash is the one field of the payload that is never shown and always asked about.
Leaving it in the payload meant decoding every source of a notebook to compare one string, which is
fine for a dozen sources and wrong for a thousand.

The constraint is the part that makes the rule true rather than likely. A check followed by an insert
is two statements, and two uploads of the same file can both pass the check before either has
written. With the constraint the second insert fails, and the service turns that into the same
conflict the check would have produced.

**Cost.** The payload lost a field, which is the third change to schema `V1_0_0` and the last one
that can be made that way once the application has been deployed anywhere. And detection is still
scoped to one notebook: the constraint spans a notebook, not an account, which is what the product
means by adding the same document to two notebooks.

## ADR-047: The index forgets after the commit, not during it

**Decision.** Deleting a source or a notebook publishes an event, and the segments are removed by a
listener that runs after the transaction has committed.

**Reason.** The vector store is not part of the transaction and cannot be rolled back with it.
Removing during the transaction means a rollback leaves a source that exists and can no longer be
retrieved from, which is the failure that reports success: nothing is marked, nothing is logged, and
the source keeps saying it is ready.

Removing afterwards inverts what can go wrong. The leftover is then a segment whose source is gone,
and one of those is already ignored when an answer is assembled, because a passage whose source the
notebook does not list is dropped. The failure that survives is the one that is already handled.

The listener does not run on its own thread, unlike indexing. Removal is a filtered pass over the
store rather than a neural network, and keeping it on the committing thread means a source that is
deleted and added again cannot have the two overtake each other.

**Cost.** A removal that fails now leaves segments nobody will collect, since the rebuild at startup
indexes the sources that exist rather than reconciling the store against them. That is the trade
above, taken deliberately.

## ADR-048: A deployment that is served over HTTPS refuses everything else

**Decision.** `sumbooklm.security.require-secure-transport` installs a filter that answers `426` to
every request below the API prefix that did not arrive over a secure connection. It is off by default
and on in the production profile. Requests outside the prefix are unaffected.

**Reason.** Every request below that prefix carries a credential: an access token, a password, or the
API key the user handed in for one question. A deployment reachable from elsewhere without TLS
publishes all three.

It refuses rather than redirects, which is the part worth arguing. A redirect is the usual answer and
it is wrong here: by the time it is written the secret has already crossed the network, and telling
the client to send it again does nothing about the copy that was made. Refusing at least does not ask
for a second transmission.

The application shell stays reachable so that a visitor of a misconfigured deployment gets something
that can tell them what is wrong rather than a blank refusal.

**Cost.** The property is a claim about the deployment that the application cannot check. Setting it
where TLS is not actually terminated makes the API unreachable, which is loud rather than silent, and
that is the correct direction for this mistake.

## ADR-049: One account may have three answers being generated at once

**Decision.** A permit is taken before the question is stored and returned when the answer ends. An
account beyond its permits is refused with `429`, and nothing about the question is written.

**Reason.** An answer holds a thread of the answering pool for as long as the provider takes, which
can be minutes. Without a bound, one account asking forty questions and forty accounts asking one look
identical to the pool, and the first of them fills it. The tokens are the user's problem either way;
the threads are everyone's.

Three, because a reader with two Sumbooks open should not wait, and because it is far enough below the
pool that one account cannot take it. Refused rather than queued, because a queued request holds the
connection open for the length of an answer that has not started, which is indistinguishable from a
server that stopped responding.

Taking the permit before the question is stored is what keeps a refused question out of the transcript.
The permit is returned by whichever ending the answer reaches and by only one of them, so a provider
reporting both cannot hand the account a permit it does not hold.

**Cost.** The count is per instance, so two instances permit twice the limit. That is the right shape
for a bound on threads, and the wrong one for a bound on spending; see open question 34.

## ADR-050: The notebook row is what serialises the work inside it

**Decision.** Everything that changes what a notebook holds refreshes its activity timestamp with a
bulk update, and does so before reading what it is about to change. The transcript is additionally
read under a pessimistic lock when an answer is appended.

**Reason.** The timestamp had been written through the entity, which put concurrent touches against
the optimistic locking counter and failed one of them, although the two do not disagree about
anything. A statement that writes the column without reading the counter removes the conflict, and it
takes a write lock that turns concurrent work inside one notebook into a queue.

That ordering is what makes the rest safe. A transcript is appended to by decoding, adding and
encoding, and two of those at once lose a message. Opening a turn is covered by the notebook lock;
storing an answer is not, because it happens later and on another thread, so it takes the lock on the
session itself.

**Cost.** Work inside one notebook is serialised even where it need not be, and the lock is held for
the length of a short transaction. Both are cheaper than the alternative, which is a user losing a
message they paid a provider to generate.

## ADR-051: A notebook holds conversations, and a question names the one it continues

**Decision.** `GET`, `POST` and `DELETE` below `/notebooks/{id}/chats` list, start and remove
conversations; a question is posted to `/chats/{sessionId}/questions`. A client that holds no
conversation creates one, which is one request more on the very first question.

**Reason.** One conversation per notebook meant a transcript that only ever grew and a subject that
could never be changed. Everything the schema needed was already there: a row with its own identifier
and timestamps and a title in its payload. What was missing was that the service used exactly one of
them, and that the interface had no way to say which.

The question names its conversation rather than the notebook resolving a current one. A server side
notion of current would have to be stored, would have to be updated by reading, and would answer
differently for two tabs of the same user; naming it makes the request say what it means.

**Reason for the shape.** The conversations are reached through a menu above the transcript rather
than a column beside it. A fourth column would take width from the one panel whose content actually
needs it, and a reader works in one conversation at a time.

**Cost.** Listing decodes the payload of every conversation of a notebook in order to count its
messages, which is the same shape of problem the content hash had before it became a column. It is
cheap for the number of conversations a person keeps and would need a column if that stopped being
true.

## ADR-052: Stopping an answer stops what this application does with it

**Decision.** A stop is recorded as a flag. The thread reading the stream notices it between two
parts, ends the answer with what has arrived, stores that, releases the permit and stops forwarding.
The provider is not told.

**Reason.** It cannot be told. The handle the chat client offers cancels by closing the body of the
response, and the HTTP stack drains a chunked body before closing it, so calling it waits for the
provider instead of stopping it. Calling it from the thread that asked for the stop makes that request
hang; calling it from the reading thread is the same as reading on and ignoring the rest.

What is left is worth doing on its own. The reader stops waiting, the answer they already read is
kept rather than discarded, and the account gets its permit back immediately instead of when a model
it no longer cares about has finished.

Keeping the partial answer rather than discarding it follows from who asked. The user pressed stop
after reading part of an answer, so that part is what the notebook said; throwing it away would delete
something they had already been shown.

**Cost.** The tokens for whatever the provider generates after the stop are still spent, and a thread
still reads them to the end. Both are recorded as open questions rather than hidden.

## ADR-053: The request is bounded by characters, and only the conversation is shortened

**Decision.** The engine keeps the whole prompt under a character budget. The instructions, the
retrieved passages and the question are sent whole; the conversation is shortened from the oldest
message forwards until the rest fits.

**Reason.** A rule that counts messages says ten short exchanges and ten long ones are the same
request. They are not, and the long ones are what exceeds a context while the count still reports
room.

Characters rather than tokens, because counting tokens needs the tokenizer of the model the request
goes to. This application does not know which model that is until the request is made and never
learns its context length, which is the price of the key belonging to the user. The budget is set low
enough that the approximation being wrong by a factor of two still leaves room.

Only the conversation is shortened because it is the only part that is context rather than content.
Dropping a passage would change what the answer may be based on, and dropping part of the question
would change the question.

The count bound stays alongside it. The two answer different things: ten messages is how far back the
conversation is still relevant, and the budget is what fits.

**Cost.** A single message longer than the budget takes the whole conversation with it, because what
is sent is a run of the most recent messages rather than a selection. A model that writes a very long
answer therefore costs the next question its context.

## ADR-054: A source is read again when a user asks, and old answers are left alone

**Decision.** `POST /sources/{id}/refresh` reads a source again from where it came from. Nothing reads
on a schedule. The transcripts that cited an earlier version are not touched, and a source carries the
moment it was last read.

**Reason.** Storing the extracted text made a page a snapshot, which is what makes rebuilding free of
the network and what makes a page that changes keep answering with what it said that day.

Reading on an interval would fetch pages nobody is looking at, spend the network of the operator on
behalf of the user, and change what a Sumbook says without anyone asking. A refresh is a deliberate
act, like adding the source was.

Old answers stay because a transcript is a record of what was said. Rewriting them to match a new
version would make the conversation claim something it never claimed. What was missing instead is the
date, and that is now on the source: a reader can see how old the material behind an answer is.

The stored text is ignored for that run rather than deleted, so a reading that fails leaves the source
answering with what it said before. And a rebuild at startup is not a reading: it indexes from the
stored text and moves no date, because a restart is not a reason to believe a page has changed.

**Cost.** Nothing notices that a page has changed. A user has to suspect it and ask, which is the
trade an interval would reverse.

## ADR-055: A deployment may name the hosts sources come from, and the list only narrows

**Decision.** `sumbooklm.ingestion.web.allowed-hosts` is empty by default, which permits any host whose
address is a public one. A deployment that fills it refuses every host it did not name, and a named host
is still judged by its address. The rule sits in the same resolver as the address rule.

**Reason.** An address can be judged. Loopback, the private ranges and the link local range are inside
by definition, and refusing them costs nothing that anybody wanted. What cannot be judged is a public
address that a firewall makes reachable only from this server: nothing about it says so, and a resolver
that tried to guess would refuse ordinary hosts.

The only thing that closes that is somebody stating which hosts are acceptable, and that somebody is
whoever runs the deployment rather than whoever uses it. Empty by default is therefore the honest
setting: for a notebook a person runs for themselves, a list would turn adding a source into asking
themselves for permission.

The list narrows and never widens. A named host that resolves inwards is still refused, so the list is
not a way to reach the network of the server, and each rule can only take away what the other allowed.
That is what makes the two readable as one sentence, and it costs nothing that was reachable before.

Putting it in the resolver is what ADR-044 already bought: every hop of every redirect passes it, so a
page that redirects to a host nobody named is refused by the code that refused the first hop.

**Cost.** An entry is a host and not a site. A deployment whose users read a site served from several
names has to name each of them, and a page that redirects to a name nobody thought of fails as an
address that may not be retrieved. The user is told that much and no more, because the difference
between the two reasons is about this server rather than about their source.

## ADR-056: An unexpected failure is reported as a rate, not as a message

**Decision.** `DocumentFailure.UNEXPECTED` stays as uninformative to the user as it was. What changed is
the report: a run that reaches it logs at error level with the trace, the notebook, and the number of
times this instance has recorded one since it started.

**Reason.** The cause is honest and useless at the same time, and both halves are deliberate. It is
useless because the alternative is showing a user what a stack trace says, and it is honest because the
application genuinely does not know what happened, which is what distinguishes it from the seven causes
an extractor names.

So the value worth improving is not the wording but the number of occurrences, and a number needs
somebody to be able to see it. One line per occurrence answers whether it happened; the count answers
whether it is happening. An operator reading that the twentieth unexpected failure since the start has
just been recorded is reading a defect report, not an incident.

Error level rather than warning for the same reason: everything an extractor names is an outcome the
application handled, and this is the only one that means the application is wrong.

**Cost.** The count lives in one instance and starts again at every restart, and it is a log line rather
than a measurement. Watching it as a rate across instances needs a metrics facility, which this
deployment does not have and which would put an endpoint in front of the same authentication that serves
notebooks.

## ADR-057: The pass that collects orphaned segments locks out writing before it reads

**Decision.** `OrphanSegmentCollector` runs hourly and asks the index to keep only the sources the
database still holds. The index takes a shared lock while writing segments and an exclusive one for the
pass, and it takes that lock before it reads the list of sources, which is why the list is asked for
rather than passed in.

**Reason.** Removing the segments of a deleted source happens after the commit (ADR-047), so a removal
that fails leaves them with nothing left to remove them. They are invisible to an answer, since a passage
whose source the notebook no longer lists is dropped, but they occupy memory and cost a comparison on
every search. Nothing else would ever collect them.

A schedule is right here for the reason it was wrong for reading pages (ADR-054): this run reaches
nothing outside the process, and it can only remove what no source claims, so the worst a run does is
nothing at all.

The ordering is the whole of the design. The pass deletes by what it does not recognise, so a source
stored between it reading the list and it deleting would be deleted although it exists. With the lock
taken first, any segment in the store was written by a run that finished before the lock was granted, and
that run had a committed row behind it, so the list the pass then reads contains it. A caller that read
the list itself and handed over the result would be reading it too early, and nothing downstream could
tell.

Not at startup, which is where the question expected it. The store is empty after a start, so a rebuild
cannot find an orphan; they accumulate while the application runs.

**Cost.** Indexing waits for one query per pass, and the pass reads every source identifier there is into
memory. Both are bounded by the size of the library rather than by a page, which is the same shape as the
rebuild and is recorded with it.

## ADR-058: Two bounds on asking, one per instance and one in the database

**Decision.** The bound on answers in flight stays where it was, in the heap of the instance that took
the request. A second bound counts the questions of an account over the last hour in a table of its own,
which every instance writes to. It is configured as `sumbooklm.chat.questions-per-hour`, it is checked
before anything is stored, and the refusal carries `Retry-After`.

**Reason.** The two protect different things and therefore belong in different places. What an answer
occupies while it is generated is a thread of one pool, and a pool is protected by a count in the
process that owns it. What an account asks for over a day is not about any one process: it is what the
installation serves, and two instances that each permit their own share of it permit twice as much of
something there is only one of.

A concurrency says nothing about a rate. An account that asks one question, waits for it and asks the
next is inside the first bound for as long as it likes, which is exactly the shape of a client that was
left running. The second bound is the one that ends that, and it has to be a rate for the same reason.

The database is the shared count because it is already shared. A store of its own would be a second
thing to operate and to fail, and what it would buy is a counter faster than the transcript write that
happens in the same request.

A question is recorded before it is stored, so a turn that fails after that point has still been
counted. That is the safe direction: a bound that only counted what was stored successfully could be
avoided by asking in a way that fails late.

`Retry-After` is on this refusal and not on the other one. Being busy with one's own answers passes
sooner than any number this application could name; having asked too often passes at a moment it knows
exactly, and a client that cannot tell the two apart would retry immediately against a bound that will
refuse it for the next hour.

**Cost.** Two questions of one account can read the same count and both be admitted, so the limit can be
exceeded by as many questions as that account may have in flight. Serialising it would mean locking the
account for every question, which is a cost everybody pays to make a bound exact that is approximate by
nature. And it counts questions rather than what they cost, which is the bound an operator who pays for
the tokens actually wants.

## ADR-059: The chat client is chosen so that a stop can abandon the request

**Decision.** Both providers are addressed through the JDK HTTP client rather than through the one the
framework finds on the class path. The handle that belongs to the response is handed to the cancellation
with the first part of the answer, and stopping closes the body through it.

**Reason.** This reverses the cost recorded in ADR-052, and it reverses it by removing its cause rather
than by accepting more of it. Stopping closes the body of the response. What that costs depends on the
client underneath: the one the framework detects here wraps Apache HttpClient, whose close first reads
the remainder of the message, so cancelling from the thread that asked for the stop held that request
open for as long as the answer took. That is why the earlier design recorded the stop as a flag and let
the reader discard what kept arriving.

Choosing the client makes the same handle safe. The JDK client abandons the exchange when its body is
closed, so the close returns at once, the socket is closed, and the provider is writing to nobody. The
detected client is only detected because reading web sources put Apache HttpClient on this class path,
which is a good reason for that module and no reason at all for this one.

The flag stays as well. It is what the reading thread notices, so that nothing more reaches the reader
and the run ends with what arrived, and it is what makes a stop that arrives before the first part still
apply once the response exists.

**Cost.** Closing the connection is how far this application can reach. Whether the provider then stops
generating, and what it charges for what it generated, is the provider's business and differs between
them. What is certain is only that nothing is being read and nothing more is being paid for on this
side.

## ADR-060: A question with no passages is answered without a model

**Decision.** Retrieval decides whether a model is asked at all. A question whose notebook returned no
passages ends with an empty answer, which the interface renders as a written sentence in the language of
its reader. The relevance floor is lowered to 0.5, and the token count the embedding model reports is no
longer shown.

**Reason.** This reverses the part of ADR-039 that put an unanswerable question to the model anyway, with
instructions saying that there were no sources and that it must then say so. Measured against a real
notebook, that assumption is simply false. Asked to summarise a document whose passages had all been
discarded by the floor, the model produced a complete summary of a document that does not exist: an
invented employer, invented dates, an invented telephone number and an invented address, every line
marked with a citation to a source it had never been given. The one thing an ungrounded model will not
do is admit that it has nothing.

The floor is the other half of the same failure. It was set at 0.65 on the assumption that a threshold
separates a question the notebook answers from one it does not. Measured against the segments of a German
document, this embedding model scores an unrelated question about baking bread at 0.64 and the request to
summarise the document itself at 0.62. The distributions overlap completely, so no threshold separates
anything; a high one only discards the passages of the questions a reader asks first. It is lowered to
where it stops throwing real material away, and what keeps an answer honest is that the passages reach
the model and that a question without any is not asked.

The token count went the same way. The embedding model reports one hundred and twenty six tokens for
every input it is given, whatever its length, so the number in the interface was a constant multiplied by
the number of segments. A source now says when it was read, which is true of both kinds and is what a
reader can do something with.

**Cost.** The sentence for an unanswerable question is written by this application rather than generated,
so it is the same sentence every time and it does not address what was asked. That is the trade: a
sentence that says less and is never invented. And the floor no longer expresses relevance, so a notebook
will hand over its nearest passages even for a question it says nothing about, which now depends entirely
on the model following the instruction to say that they do not answer it.
