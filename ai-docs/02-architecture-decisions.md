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
made the screen look dead. It now runs a six stop ramp from near black through indigo and violet to a
muted rose, which keeps the neutral grey cards reading as neutral while giving the screen tonal range.
The palette is one array in each of the two implementations.
