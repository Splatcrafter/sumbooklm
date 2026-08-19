# Build and Run

## Launch scripts

Three tasks, one script per task and platform, all in the repository root:

| Task | Windows | Linux and macOS |
| --- | --- | --- |
| build everything | `local-compile.bat` | `./local-compile.sh` |
| run the packaged artifact on 8080 | `local-start.bat` | `./local-start.sh` |
| run the Vite dev server on 5173 | `local-dev-server.bat` | `./local-dev-server.sh` |

All three forward their arguments: `local-compile` to Maven, so `-Dfrontend.skip=true` works, and
`local-start` to Spring Boot, so a later `--server.port` overrides the 8080 the script sets. Each one
checks its prerequisites first and fails with a sentence naming what is missing, rather than letting
Maven, Java or npm produce the error further down.

Two details worth knowing:

* `local-dev-server` prefers the Node toolchain the Maven build installed under
  `sumbooklm-frontend/target/node` over whatever is on the PATH, so the dev server runs on the same
  Node and npm versions as the packaged build. It falls back to the PATH when that directory does not
  exist yet.
* It passes `--strictPort`, because Vite otherwise moves to the next free port silently, and the
  dev proxy of the frontend as well as the scripts both assume 5173.

The dev server proxies `/api` and `/v3/api-docs` to the backend, so `local-start` has to be running
in parallel for anything beyond static views.

## Commands

```bash
mvn clean install                          # full build, produces sumbooklm-app/target/sumbooklm.jar
mvn clean install -Dfrontend.skip=true     # backend only, skips the Node toolchain and npm
java -jar sumbooklm-app/target/sumbooklm.jar
```

Frontend only, from `sumbooklm-frontend`:

```bash
npm install
npm run dev                                # Vite on :5173, proxies /api and /v3/api-docs to :8080
npm run build                              # tsc -b && vite build, output in target/dist
npm run api:generate                       # regenerates src/api/schema.d.ts, needs a running backend
npx shadcn@latest add <component>          # adds a Base UI component into src/components/ui
```

`SUMBOOKLM_BACKEND_URL` overrides the dev proxy target.

## Authentication endpoints

| Method and path | Authentication | Purpose |
| --- | --- | --- |
| `POST /api/v1/register` | none | creates an account and returns a token pair for it |
| `POST /api/v1/login` | none | verifies credentials and returns a token pair |
| `POST /api/v1/token/refresh` | refresh token in the body | rotates the pair |
| `POST /api/v1/logout` | access token as bearer | closes the session of that token |
| `GET /api/v1/security/cookie-iv/` | key handle cookie | hands out the cookie encryption parameters |

Everything else below `/api` requires a valid access token. Paths outside `/api` serve the single
page application and stay open.

```bash
SUMBOOKLM_JWT_SECRET=... SUMBOOKLM_COOKIE_SECRET=... java -jar sumbooklm-app/target/sumbooklm.jar
```

The development profile carries defaults for both secrets so the application starts without any
environment; the production profile does not, and a missing secret fails the startup.

## JavaDoc gate

`mvn verify` runs `maven-javadoc-plugin` with `show=private`, `doclint=all` and
`failOnWarnings=true` over main and test sources, so an undocumented element fails the build. The
gate runs in every module that has types, currently `sumbooklm-domain`, `sumbooklm-persistence`,
`sumbooklm-security`, `sumbooklm-workspace`, `sumbooklm-api` and `sumbooklm-app`; `sumbooklm-ingestion`
and `sumbooklm-ai` are skipped for the reason given in finding 12.

```bash
mvn verify -Dfrontend.skip=true            # runs the gate without the Node toolchain
mvn -Dmaven.javadoc.skip=true verify       # bypasses the gate
```

Confirmed on 2026-08-18 that the gate bites: deleting the comment on `ApiPaths.BASE` turned the
build red with `ApiPaths.java:17: warning: no comment`, and restoring it turned the build green.

## How the frontend reaches the backend artifact

1. `frontend-maven-plugin:install-node-and-npm` (generate-resources) downloads Node v24.19.0 and npm
   11.17.0 into `sumbooklm-frontend/target`.
2. `frontend-maven-plugin:npm` with `ci` (generate-resources) installs from `package-lock.json`.
3. `frontend-maven-plugin:npm` with `run build` (compile) type checks and bundles into
   `target/dist`.
4. `maven-resources-plugin:copy-resources` (prepare-package) copies `target/dist` into
   `target/classes/static`.
5. `sumbooklm-frontend-*.jar` therefore contains `static/index.html` and `static/assets/*`.
6. `sumbooklm-app` depends on that JAR; `spring-boot:repackage` nests it under `BOOT-INF/lib`.
7. At runtime Spring Boot scans `classpath:/static/` across all classpath entries, including nested
   JARs, and finds the SPA.

The two development servers are independent: Vite serves the SPA on 5173 with HMR and proxies API
calls to the Spring Boot process on 8080. The packaged artifact serves both from 8080.

## Verification performed on 2026-08-18

Full `mvn clean install` on JDK 25 / Maven 3.9.15: all eight modules green.

`SumbookLmApplicationTests#contextLoads` starts the full context under the `dev` profile. That
exercises the JPA data source, Hibernate 7.4.1, the Aether Datafixers auto configuration, the three
LangChain4j Boot 4 starters and springdoc, and it logs
`Adding welcome page: class path resource [static/index.html]`, which confirms the SPA reached the
classpath.

Against the running `java -jar sumbooklm-app/target/sumbooklm.jar`:

| Request | Status | Content type | Meaning |
| --- | --- | --- | --- |
| `GET /` | 200 | `text/html` | SPA entry document |
| `GET /notebooks/42/chat` | 200 | `text/html` | deep link falls back to the shell |
| `GET /some/deep/spa/route` | 200 | `text/html` | fallback is depth independent |
| `GET /assets/index-*.js` | 200 | `text/javascript` | real assets are still served as assets |
| `GET /api/unknown` | 404 | `application/json` | the API prefix guard works |
| `GET /v3/api-docs` | 200 | `application/json` | OpenAPI 3.1.0 document |
| `GET /swagger-ui/index.html` | 200 | `text/html` | UI not shadowed by the `/**` handler |

`npm run api:generate` against that instance produced a valid `src/api/schema.d.ts`, and the
subsequent `npm run build` type checked against it. The contract pipeline is closed end to end even
though no endpoints exist yet.

## Known build noise

* Mockito self-attach warning and the dynamic agent warning in tests. Both come from
  `spring-boot-starter-test` on JDK 25 and are harmless for now; adding Mockito as an explicit
  `-javaagent` silences them when tests become substantial.
* springdoc warns that `/v3/api-docs` and `/swagger-ui.html` are enabled by default. The `prod`
  profile disables the UI; the document itself stays enabled deliberately, because the frontend
  toolchain consumes it.

## Verification of the authentication module on 2026-08-18

Full `mvn clean install` on JDK 25 / Maven 3.9.15: all nine modules green, 19 tests, JavaDoc gate
active in every module that has types.

`AuthenticationFlowIntegrationTest` drives the assembled application over HTTP with `RestClient` on
a random port, twelve cases: registration returning a pair and the handle cookie, duplicate username
rejected as a conflict, a short password rejected before the service is reached, login returning the
profile that went through the CBOR payload, wrong password and unknown username rejected
identically, a protected endpoint rejected without a token, a refresh token rejected as a bearer
credential, the cookie parameter endpoint refused without a handle, a stable key and a fresh vector
per call, rotation consuming the presented token, reuse of a consumed token closing the whole
session, and logout blocking the next sensitive operation with its still unexpired access token.

`CookieCryptographyServiceTest` performs the encryption a browser would perform with the values the
service hands out, so the parameters are exercised rather than compared against constants.
`RefreshTokenCleanupJobTest` reads the cron expression out of the annotation and evaluates it, which
puts the next two runs on 2026-08-23 and 2026-08-30, both Sundays at midnight.

The client side of the cookie flow was then run against the packaged artifact with the same code the
browser executes, on Node's Web Crypto implementation:

| Step | Result |
| --- | --- |
| registration | `200`, cookie `Path=/ Max-Age=7776000 HttpOnly SameSite=Strict` |
| parameters | `sumbooklm_auth`, `AES-GCM`, 256 bit key, 128 bit tag |
| encrypted cookie | 1700 characters |
| key across two calls | identical |
| vector across two calls | different |
| decrypt with the vector from the ciphertext | round trip succeeds |
| parameters of a different handle | different key, decryption rejected |
| request without a handle cookie | `401` |
| refresh with the decrypted token | `200` |

Routing of the packaged artifact, checked against the running jar:

| Request | Status | Content type | Meaning |
| --- | --- | --- | --- |
| `GET /account/login` | 200 | `text/html` | the new routes are reachable as deep links |
| `GET /account/register` | 200 | `text/html` | same |
| `POST /api/v1/logout` without a token | 401 | | the filter chain protects the API prefix |
| `POST /api/v1/register` with an invalid body | 400 | `application/problem+json` | RFC 9457 shape |
| `GET /v3/api-docs` | 200 | `application/json` | contract regenerated for the frontend |

Note the one behaviour that changed against the scaffold: an unknown path below `/api` now answers
`401` instead of `404`, because authentication is required before routing happens. See open
question 14.

## Account screens

The login and registration screens sit at `/account/login` and `/account/register` and are their own
router branch, not children of the application layout. They are always dark, they use the JetBrains
website grayscale from finding 22 through the `jb-*` Tailwind namespace defined in `src/index.css`,
and they place two stacked cards on the left over a generated background. The primary card reserves the
height of the longer of the two forms so that moving between them does not resize it, and no text sits
outside a card; see ADR-023. The background is not part
of that grayscale: it runs a saturated six stop ramp from near black through indigo and violet into
hot magenta, because forcing it into the same near-black greys left the screen without tonal range
(ADR-022).

The background has two forms and picks between them at runtime:

| Condition | Result |
| --- | --- |
| WebGL 2 available, pointer not coarse or viewport large | animated shader |
| Small touch device, or more than two cores unavailable | still image |
| No WebGL 2, shader rejected, or context lost | still image |
| Sustained slow frames | quality steps down, then still image |
| `prefers-reduced-motion: reduce` | shader, one frame, no loop |

Which of the two is on screen can be read off the element rather than guessed: the background
container carries `data-background="shader"` or `data-background="still"`. To force the still image on
a capable machine, disable WebGL in the browser or emulate a mobile device with a coarse pointer. The
animated path renders at a reduced backing store and targets thirty frames per second, so a low
reported frame rate is by design rather than a symptom.

The still image is generated, not drawn:

```bash
npm run background:generate     # rewrites src/assets/account-background.png
```

It has to be regenerated whenever the field or the palette in the shader changes, because it is the
same construction evaluated on the CPU. The constants are named identically in
`src/components/background/waveShader.ts` and in `scripts/accountBackground.mjs` so the two can be
compared.

## Verifying the shader

`glslang` from the Khronos prebuilt release validates the shaders as OpenGL ES 3.00 without a GPU:

```bash
glslang shader.frag     # exit code 0 and no diagnostics means it compiles
```

That covers compilation, not appearance. For appearance, the CPU renderer in
`scripts/accountBackground.mjs` produces the identical field as a PNG that can be looked at.

## Verification of the workspace module on 2026-08-18

Full `mvn clean install` on JDK 25 / Maven 3.9.15: all ten modules green, 28 tests, JavaDoc gate
active in every module that has types.

`NotebookApiIntegrationTest` drives the assembled application over HTTP on a random port, nine cases:
creation returning the notebook and its location, a whitespace title rejected before the service is
reached, every endpoint refusing a request without an access token, the overview ordered by activity
and carrying the pin state, an account never seeing a notebook of another account, a change carrying
only one field leaving the other alone, renaming refreshing the activity timestamp while pinning does
not, a foreign or unknown notebook answered as missing, removal emptying the overview and refusing a
second attempt, and removal refused once the session behind the access token has been closed.

The same flow was driven against the packaged jar with `curl`, which confirmed the parts a test
asserts on indirectly:

| Request | Result |
| --- | --- |
| `POST /api/v1/notebooks` with `"  Thermodynamics  "` | `201`, title stored trimmed, `topicIcon` empty, `sourceCount` 0 |
| `PATCH` with `{"pinned": true}` | `200`, title unchanged, `lastActivityAt` unchanged |
| `PATCH` with a new title | `200`, pin state unchanged, `lastActivityAt` refreshed |
| `GET /api/v1/notebooks` | `200`, most recently active first |
| `DELETE` then `DELETE` again | `204`, then `404` |
| `GET /api/v1/notebooks` without a token | `401` |
| `POST` with a whitespace title | `400` |

`npm run api:generate` against that instance regenerated `src/api/schema.d.ts` with the notebook
paths, and `npm run build` type checked the dashboard against it.

The dashboard itself was verified the way the account screens were, by rendering it through Vite's
SSR loader and asserting on the markup, in all three languages: no unresolved translation key, the
create card present, both section headings present, the topic icon rendered as the characters it is,
the fallback icon used exactly where the topic icon is empty, one separator per card, the activity
date formatted for the locale (`Oct 24, 2026`, `24. Okt. 2026`, `2026年10月24日`), the singular, plural
and zero forms of the source count, a shared minimum card height, and no character above `U+2000` in
the markup other than the topic icon and the separator. The script lives outside the repository; see
open question 17.

## Verification of the ingestion pipeline and the Sumbook view on 2026-08-18

Full `mvn clean install` on JDK 25 / Maven 3.9.15: all ten modules green, 38 tests, JavaDoc gate now
active in `sumbooklm-ingestion` and `sumbooklm-ai` as well, since both carry types for the first time.

`SourceApiIntegrationTest` drives the assembled application over HTTP on a random port, ten cases. Six
of them describe rules that hold before anything is stored, and four describe what the background run
does afterwards:

| Case | Asserted |
| --- | --- |
| Uploading a text file | `201` with `UPLOADED`, the file name kept, `kind` `FILE`, the location header, and then `READY` with a token count above zero |
| Uploading anything | the activity timestamp of the notebook is refreshed |
| Uploading no bytes | `400`, and nothing stored |
| Uploading the same text twice | `201`, then `409`, and `201` again in a second notebook |
| Adding `https://Example.org/article` then `.../article#section` | `201`, then `409`; `file:///etc/passwd` is `400` |
| Adding `http://127.0.0.1:9/secret` | `201`, then `ERROR`, because the guard refuses the address |
| A notebook of another account | listing, adding and removing all answer `404` |
| No access token | `401` |
| Removing a source | `204`, gone from the list, the count back to zero, and `404` on a second attempt |
| Removing the notebook | its sources go with it |

The indexing assertions poll the source until it has left `UPLOADED` and `INDEXING` rather than
pausing for a fixed time, so the suite neither guesses at machine speed nor sleeps longer than it has
to; the ten cases run in about eleven seconds including one real embedding run.

`npm run api:generate` against the packaged jar regenerated `src/api/schema.d.ts` with the four source
paths, and `npm run typecheck` and `npm run build` are green against it, with only the pre-existing
chunk size warning.

The generated client itself was then driven against that same running jar from Node, loading the very
modules the browser loads through Vite's SSR loader and prefixing the relative paths with the origin:
a real `File` uploaded through `uploadSourceFile` was stored, reached `READY` with sixteen tokens, the
same content was refused with `409`, `not-an-address` was refused with `400`, a link was accepted, the
upload was removed and the notebook's source count followed. That covers the one part the Java test
cannot reach, which is whether the multipart body the browser code builds is a request the server
accepts.

The Sumbook view was rendered through the SSR loader in all three languages: no unresolved translation
key, all three panel headings, the topic icon rendered as the characters it is, the fallback icon
where it is empty, one separator in the header, the activity date formatted for the locale, the source
count in header and composer including its singular form, the globe icon on web sources and the file
icon on uploads, a spinner on each of the two pending sources, a check on the indexed one and an alert
on the failed one, the token count of an indexed source in singular and plural, a remove action per
source, the send button disabled and grey while the field is empty, the summary placeholder, the copy
button offered as disabled, and no character above `U+2000` other than the topic icon and the
separator. The dashboard checks were re-run unchanged, plus one for the cards now announcing
themselves as links.

The composer needed a DOM rather than a string, so it was driven with `jsdom` installed outside the
manifest and removed afterwards: an empty field and a field holding only spaces keep the button
disabled, grey and showing the not-allowed cursor; text enables it and turns it blue with a white
arrow; shift with enter neither sends nor clears; enter sends the trimmed question; the button sends
as well. Those are the requirements that no amount of static markup can demonstrate.

All harnesses live outside the repository; see open question 17.

## Verification of the chat pipeline on 2026-08-19

Full `mvn clean install` on JDK 25 / Maven 3.9.15: all ten modules green, 45 tests, JavaDoc gate
unchanged.

`ChatApiIntegrationTest` drives the assembled application over HTTP on a random port, seven cases. No
language model is reachable from a build, so every question is asked against `http://127.0.0.1:9`,
which refuses the connection immediately. What that leaves observable is everything this application
is responsible for; only the generated words are out of reach, and they are the part it does not
write.

| Case | Asserted |
| --- | --- |
| Reading a fresh conversation | `200` with an empty title and no messages, not `404` |
| Asking without provider, without model, without a key, with an unknown provider | `400` each time, and nothing added to the transcript |
| Asking with an unreachable provider | `200`, the stream carries `sources` then `error`, and the question is in the transcript with the title derived from it |
| Two notebooks with one document each | the `sources` event of a question names only the document of the notebook it was asked in |
| A notebook without sources | an empty `sources` event and the same stream shape as any other question |
| A notebook of another account | reading and asking both answer `404` |
| No access token | `401` |

The fourth case is the one the retrieval filter exists for. It uploads a text about thermodynamics
into one notebook and a text about sourdough into another, waits for both to be indexed, and asks the
same question in both. It fails the moment the metadata filter stops being applied.

`npm run api:generate` against the packaged jar regenerated `src/api/schema.d.ts` with the two chat
paths, and `npm run typecheck` and `npm run build` are green against it, with only the pre-existing
chunk size warning.

The browser client was then driven against the running jar from Node, through Vite's SSR loader as
before. Eight checks: a real `File` uploaded and indexed, an empty transcript, the event stream parsed
into `sources` then `error`, the retrieved source reported with its name and number, a reason carried
by the failure, the question in the transcript afterwards, and a selection without a key rejected with
`400` before any stream starts.

A second harness put a fake Ollama server in front of the same client: a plain HTTP server answering
`POST /api/chat` with newline delimited JSON, which is exactly what the LangChain4j Ollama client
parses. That closes the last gap, because it is the only way to observe a successful answer without a
real model. Fourteen checks, and the interesting ones are what the fake server received: the first
message is a system message that carries the citation format `[n](#source-n)`, the heading `[1]
thermodynamics.txt`, and the text of the retrieved passage itself. The question is the last message.
Then the answer arrives in three parts through `onToken`, assembles into the text the fake model sent,
is announced once through `onDone`, ends up in the transcript as an `ASSISTANT` message, and a second
question is sent with the earlier exchange in front of it.

The message rendering needed a DOM, so `jsdom` was installed outside the manifest and removed
afterwards: Markdown emphasis and lists are rendered, `[1](#source-1)` becomes a chip carrying the
number and the name of its source rather than a link, the cited sources are listed under the answer, a
question is shown exactly as typed and is not rendered as markup, questions sit right and answers
left, a failed answer names its reason, the composer stays disabled and grey while the field holds
only whitespace and turns blue with text, shift with enter does not send, enter sends and clears, and
a composer disabled while an answer is being written sends nothing at all.

Every translated key used in the interface was checked against all three locale files: ninety-nine
keys, none missing in any language, and the three keys built at runtime are the account failure
reasons and the provider names.

All harnesses live outside the repository; see open question 17.

## Verification of the index rebuild on 2026-08-19

Full `mvn clean install`: all ten modules green, 49 tests.

`IndexRestoreIntegrationTest` runs against a database of its own, because the rebuild deliberately
spans every account there is and would otherwise rebuild the sources the other suites use to provoke
failures. A restart cannot be performed from inside the application it would restart, so what a
restart does is reproduced instead: the store is emptied through the bean while the database keeps
every source.

| Case | Asserted |
| --- | --- |
| Emptying the store | the source still reports `READY` while its segments are gone, which is the failure this job exists for |
| Running the rebuild | the same number of segments is back, the stage, the token count and the name are unchanged |
| Indexing an indexed source again | `202` with `UPLOADED`, then `READY`, and the segment count is the same rather than doubled |
| Indexing a failed source again | it is read again and fails again while the address stays unreachable, and it stores no segments |
| A source of another account, of another notebook, or one that does not exist | `404` each time |

The assertions count the segments the store holds for one source rather than asking a question about
it. That is the state the rebuild is responsible for, and whether an answer can be produced from it is
what the chat suite already covers.

A real restart was then verified outside the test suite, because that is the thing the whole change is
about and no test in a running application can perform it. The packaged jar was started against a file
based H2 database, an account was registered, a text file uploaded and indexed, and a question asked:
the stream named `thermodynamics.txt` among its sources. The process was stopped, started again
against the same database file, and the same question asked: the source was named again, the log
carried `Rebuilding the retrieval index for 1 sources` followed by `Rebuilt the retrieval index for 1
of 1 sources`, and the whole rebuild took eighty-four milliseconds because the text was already
stored.

The claim that a rebuild needs no network could not be verified for a web source, because the guard
against internal addresses refuses a page served from this machine and no other host is reachable from
the container. What is verified is the branch itself: an uploaded file is rebuilt from stored text,
and a source that has none is read again, which is what the failed source case shows. The branch is
taken on the presence of the text rather than on the kind of the source, so the two cases share it.

The retry control was rendered in all three languages: a failed source offers it, an indexed and a
running one do not, every source can still be removed, and the label is translated rather than a key.

Every translated key used in the interface was checked against all three locale files again: one
hundred keys, none missing in any language.

All harnesses live outside the repository; see open question 17.

## Verification of the address guard and the failure reasons on 2026-08-19

Full `mvn clean install`: all ten modules green, 65 tests. `sumbooklm-ingestion` has tests for the
first time, so the JavaDoc gate now applies to its test sources as well.

`PublicAddressResolverTest` states the rule as a list of addresses rather than as the behaviour of a
request: loopback in both families, the wildcard address, `localhost`, the three private ranges, the
link local range including `169.254.169.254`, and multicast are all refused; a public literal is
returned unchanged; and a name that does not resolve is reported as unknown rather than as refused,
because the two become different reasons on the source. The addresses are literals, so the cases need
no name server and describe the rule rather than the network the build runs on.

`WebPageTextExtractorTest` runs a server inside the test. A server a test can start is reachable only
on loopback, which is exactly what the real rule refuses, so the rule is stubbed with one that names
two hosts: the server, and a host standing for everything the real rule refuses.

| Case | Asserted |
| --- | --- |
| A page | retrieved, title read, navigation and scripts left out of the text |
| A redirect to the refused host | the retrieval ends as `BLOCKED` |
| The refused host directly | `BLOCKED`, with no request attempted |
| `file:///etc/passwd` | `BLOCKED` |
| A response served as `application/pdf` | `UNREADABLE` |
| A page with an empty body | `EMPTY` |
| A `404` | `UNREACHABLE` |
| A body of nine megabytes | `TOO_LARGE`, refused rather than truncated |

The redirect case is the one the whole change is for. The extractor lets the client follow redirects
rather than driving them, and what makes that safe is that every hop is a connection and therefore
passes the resolver. The stub is what makes it observable: the first hop is allowed, the second is
not, and the retrieval has to end as refused.

The source suite gained three cases at the level of the API: an address in the loopback range is
reported as `BLOCKED` rather than merely failed, a name that does not resolve as `UNREACHABLE`, a file
of nothing but whitespace as `EMPTY`, and an indexed source as `NONE`.

The dependency added for this changes what Spring Boot builds for every `RestClient`, which is the
transport the chat providers are reached through, so the streaming harness was re-run against the fake
Ollama server: the answer still arrives part by part and ends up in the transcript. That was checked
rather than assumed.

The source list item was rendered in all three languages for each of the six causes: every one reads
as a sentence rather than as a key, an indexed source shows its token count instead of a reason, and
the retry control is offered on a failed source and on no other.

All harnesses live outside the repository; see open question 17.

## Verification of the hash column and the deferred index removal on 2026-08-19

Full `mvn clean install`: all ten modules green, 68 tests.

Duplicate detection reaches the database now, so the suite states both halves of it. The existing case
covers the check: the same content twice in one notebook is `409`, and the same content in a second
notebook is `201`. A new case covers the constraint, directly against the repository, because two
requests never arrive at the same instant in a test and a check without a constraint behind it is
exactly what a race defeats. A second row carrying the hash of an existing source is refused by the
table in the same notebook and accepted in another one.

Removing segments after the commit is invisible in a response, so it is asserted by counting what the
store holds: deleting a source takes its segments out, and deleting a notebook takes out the segments
of the sources it held. Both would have passed before the change as well, which is the point: they are
what keeps the move from quietly dropping the removal altogether.

The schema changed, so the restart harness was run again from an empty file backed database: the table
is created with the column and the constraint, a file is uploaded and indexed, the process is stopped
and started against the same file, and the source is retrievable again in ninety-one milliseconds.

The frontend is untouched by this change. The hash was never part of a response, so the generated
client and the interface are the same as before.

## Verification of the transport rule and the bound on answers on 2026-08-19

Full `mvn clean install`: all ten modules green, 77 tests. `sumbooklm-workspace` has tests for the
first time.

`SecureTransportIntegrationTest` runs a context that declares itself served over HTTPS and then makes
every request over plain HTTP. Registration, the notebook collection, a transcript and the cookie
parameter endpoint are all refused with `426`; the refusal is a problem document rather than an empty
body; and the application shell is still served, so a visitor of a misconfigured deployment reaches
something that can tell them what is wrong.

`ConcurrentAnswerLimitTest` states the bound as arithmetic: three permits and no more, a returned
permit can be taken again, an account that returned everything starts over, returning a permit that
was never taken changes nothing, and two accounts are counted apart.

The bound itself needs answers that do not arrive, so the chat suite runs a provider that accepts a
request and holds it. Three questions are asked and waited for until all three have reached the
provider, a fourth is refused with `429`, and the transcript is asserted to hold three questions and
not the fourth. The provider is then released, and the transcript is asserted to reach six messages,
which is what states that no accepted answer was lost.

That last assertion exists because writing this test found two defects, neither of them caused by the
limit and both of them only reachable once several requests hit one notebook at once. Two questions in
one notebook failed with an optimistic locking error, because both refreshed its activity timestamp
through the entity. And two answers arriving together lost one of them, because a transcript is
appended to by decoding a payload, adding to it and encoding it again, and the second writer was told
it had lost. The timestamp is now written by a statement that does not touch the counter and does take
a row lock, and the answer is appended under a pessimistic lock on the session.

The streaming harness and the generated client harness were both re-run afterwards, since the locking
sits directly under them, and both still pass. The message rendering harness gained the refused turn:
it says that too many answers are being written rather than reporting a failure, in all three
languages.

## Verification of several conversations and of stopping on 2026-08-19

Full `mvn clean install`: all ten modules green, 81 tests.

The chat suite grew four cases. A fresh notebook holds no conversations and reading it creates none. A
notebook holds several, each keeps its own transcript, removing one leaves the others, and removing it
twice is a `404`. A conversation belongs to one notebook, so asking through another notebook of the
same account is a `404` as well. And the bound on answers in flight now uses one conversation per
question, which is what a client would do.

Stopping needs an answer that is arriving, because the provider only offers something to cancel once
it has produced something. The case runs a provider that keeps writing until it is let go: the
question is asked on another thread, the test waits until it has reached the provider, asks for the
stop, and then asserts that the stream ended as `done` rather than `error`, that it carries what had
arrived, that it does not carry what was written afterwards, and that the transcript holds the partial
answer. Stopping a conversation with nothing running answers the same way, because an answer that has
just finished and one that never started are the same thing to ask about.

Writing that case is what found the mechanism. The first attempt called the cancellation handle of the
chat client from the thread handling the stop, and the stop request hung for three minutes until the
read timed out: the handle cancels by closing the body of the response, and Apache drains a chunked
body before it closes it. The handle is not used any more, and what a stop does and does not do is
recorded rather than assumed.

Both harnesses were re-run against the packaged jar. The generated client one now starts a conversation
before it asks. The streaming one gained a second conversation with its own transcript, a removal, and
a stop driven through the real client against a provider that ticks: the answer ends as finished,
carries the ticks, does not carry the ending, and the transcript holds it.

The interface harness gained the stop control and the switcher: an answer being written offers a stop
instead of a send, pressing it reports the stop, a Sumbook without conversations shows no switcher, and
one with two names the open conversation. All translated keys are present in all three languages.

## Verification of the request budget and of reading a source again on 2026-08-19

Full `mvn clean install`: all ten modules green, 86 tests. `sumbooklm-ai` has tests for the first time.

`PromptBudgetTest` states what fits at the edges rather than in the middle: a short conversation is
kept whole and in order, a long one keeps its most recent messages and loses the oldest, a single
message beyond the budget takes the conversation with it, instructions that fill the budget on their
own leave nothing, and an empty conversation needs no case of its own. Driving those through a model
would have meant choosing texts by their length and asserting on what a provider received.

Reading a source again is asserted where its effects are visible. Reading an indexed file again keeps
the number of segments the same rather than doubling it, and moves the moment it was read. A rebuild
after the store has lost its segments leaves that moment where it was, because a rebuild is not a
reading. A source that failed is read again and fails again while its address stays unreachable. A
source of another account, of another notebook, or one that does not exist answers `404`.

An uploaded source reports no moment before it has been read and one afterwards, which is the pair
that makes the field mean what it says.

What could not be verified here is a page that changed between two readings. The rule that refuses
internal addresses makes a server started inside the build unreachable, and no other host is reachable
from the container, so the branch is covered by the file path and by the failing address rather than
by a page whose content moved.

The generated client harness drives the reading again through the real browser code: a file that is
indexed is read again, ends indexed, and carries a later moment than before. The interface harness
covers what a reader sees: an indexed page says when it was read and offers to read it again, an
indexed file says how much it became and does not, a failed source of either kind offers it, and a
source that is running has the control disabled.

## Verification of the permitted hosts, the unexpected report and the collection pass on 2026-08-19

Full `mvn clean install`: all ten modules green, 93 tests. Nothing about the API changed in this pass,
so the generated client was not regenerated and the browser harnesses were not re-run: no endpoint, no
field and no text moved, and a refused host already reached the reader as the one cause it shares with
an address that points inwards.

`PublicAddressResolverTest` states the permitted hosts as three cases against the same rule it already
stated as a list of addresses. A deployment that names one host refuses a public address nothing is
wrong with. A named host reaches the resolution step, which is visible as the failure being an unknown
host rather than a refusal, and the names for that are under `.invalid` so that no name server is asked.
And a named host that resolves to loopback is still refused, which is the case that states that the list
narrows and never widens.

`NotebookIndexTest` runs against the real in-memory store with a model that answers every segment with
the same vector, because the cases are about which segments the store holds and not about how near they
are to a question. A pass keeps the sources it was told about and removes the one it was not, a pass that
finds no source at all empties the store, which is the case a filter over an empty list cannot express,
and a source stored while a pass is running survives it.

That last case forces the interleaving instead of hoping for it: the writer is started from inside the
pass, and the case first asserts that it has not got through. Without the lock it would fail sometimes
and pass often, which is the wrong way round for a test about an ordering.

`IndexRestoreIntegrationTest` gained the end of it. A row removed through the repository leaves its
segments behind, exactly as a removal whose cleanup did not follow would, and it takes a pass to remove
them. The case first runs a pass while the source still exists and asserts that nothing was removed,
because a collector that removes everything would pass the second half on its own.

Two things are stated by construction rather than by a running application. A non-empty list of permitted
hosts is exercised through the constructor the container also uses, not through a context started with
the property set, since the property that binds it is exercised at every start and the rule it feeds is
the same object either way. And the hourly schedule is not waited for: the pass is invoked directly, and
what the annotation adds is a clock.

### A defect found while switching the scheduler on

`RefreshTokenCleanupJob` has carried `@Scheduled(cron = "0 0 0 * * SUN")` since the security module was
written, and its own documentation says that it runs weekly. Nothing had ever switched scheduling on, so
it never ran: the annotation was inert, the table it keeps proportional grew without a bound, and no test
noticed because every test that touches it calls the method itself.

Enabling the scheduler for the collection pass fixes that as a side effect. Both jobs now run, and the
lesson is the one the case above is also about: an annotation that describes when something happens says
nothing about whether anything is listening.

## Verification of the two bounds and of a stop that reaches the provider on 2026-08-19

Full `mvn clean install`: all ten modules green, 98 tests.

`ChatModelFactoryTest` is the case the change to the client exists for. A provider writes parts of an
answer until writing fails, the answer is stopped once the first part has arrived, and the assertion is
that the provider notices: a write that throws is the only way a test can tell an abandoned request from
one that is still being read and discarded. The same class states the other half, that an address which
refuses the connection ends the run as failed rather than leaving a reader waiting, which is what a
client that reports nothing would have cost.

`ChatApiIntegrationTest` states it once more through the whole application, and adds the part that made
the earlier design impossible: the stop request itself has to return, and it is asserted to return in
well under the time an answer takes. The measurement that motivated all of this was a stop that took
185 seconds because closing the body read the rest of the message first.

`QuestionRateIntegrationTest` runs with the bound configured down to two questions, because the cases
are about the edge and not about where it is. An account asks twice and is admitted twice, is refused
the third time with `Retry-After`, and the refused question is asserted to be absent from the transcript.
A second account asks in the same window and is admitted, which is what states that the count is per
account.

That suite carries one lesson worth recording: its client is built on the HTTP client of the platform
rather than the detected one, because the detected one obeys `Retry-After`. Against this refusal it slept
for the rest of the hour and reported what came afterwards, which looked exactly like a hanging test. The
behaviour is correct for a client and is the reason the header is worth sending; it is only a test that
must not have it.

The plural sentences of the refusal were checked through the real i18n setup in all three languages, for
one minute and for many, since a plural key that resolves to itself is the usual way such a message is
shipped broken. The interface harness has a case for the new sentence as well; it could not be run here,
because the development dependency it drives a DOM with is no longer installed in this container.

## Verification of the grounding failure on 2026-08-19

This one started from a real answer, not from a test. Asked to summarise an uploaded CV, the assistant
produced a complete profile of a person who does not exist: an invented employer, invented dates, an
invented telephone number and an invented address, with a citation behind every line. Everything except
the name, the city and the languages was made up.

Three measurements found the cause, run against the actual file through the application's own extractor,
splitter and embedding model.

The extraction is not the problem. The document yields 5272 characters and six segments, and the real
address, telephone number and employers are all in them.

The retrieval was the problem. With the floor at 0.65, the question "Fasse den Lebenslauf zusammen"
scored 0.617 at best, so **none** of the six segments reached the model. The model was then told that
there were no sources and asked anyway, which is the case it invents in.

The floor cannot do what it looked like it did. Measured over the same six segments, an unrelated
question about baking bread scores 0.641 and the request to summarise the document itself scores 0.617:
the unrelated question scores higher than the relevant one. The distributions overlap completely, so no
threshold separates them, and the value only decided how much real material was thrown away.

| question | best score | at 0.50 | at 0.55 | at 0.60 | at 0.65 |
| --- | --- | --- | --- | --- | --- |
| Fasse den Lebenslauf zusammen. | 0.617 | 6 | 4 | 2 | 0 |
| Wo wohnt Erik? | 0.719 | 6 | 4 | 1 | 1 |
| Welche Sprachen spricht er? | 0.700 | 6 | 4 | 1 | 1 |
| Wie backe ich Brot? | 0.619 | 6 | 2 | 1 | 0 |
| Wie repariere ich ein Fahrrad? | 0.619 | 6 | 2 | 1 | 0 |

The deeper reason is the embedding model. `AllMiniLmL6V2` is trained on English, and against German text
its scores compress into a band between about 0.57 and 0.72 whatever the question, which is why nothing
separates. A multilingual model would spread them again, and it is the change that would improve
retrieval most; it needs an ONNX artifact this repository does not have.

The token count went the same way. The same model reports **126 tokens for every input it is given**:
414 characters, 964 characters, the whole 5272-character document and 9600 characters of generated German
all come back as 126. The "756 tokens" the interface showed for the CV was six segments times a constant.
The number is no longer shown; a source now reports when it was read.

What the build now states: `ChatApiIntegrationTest` asserts that a question a notebook has no passages
for never reaches a provider that would have answered, and that the stream still ends as finished with
nothing generated. The isolation case no longer asserts that an unrelated notebook returns no sources,
because the floor cannot promise that; it asserts what is true and matters, that a notebook never returns
the sources of another.

Three cases had to be given a source to keep testing what they test. They asked in empty notebooks and
relied on the provider being contacted anyway, which is exactly the behaviour that was removed.
