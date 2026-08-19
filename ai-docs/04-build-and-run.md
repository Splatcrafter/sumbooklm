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
