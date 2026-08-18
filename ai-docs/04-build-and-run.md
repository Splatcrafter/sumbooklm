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
gate runs in `sumbooklm-persistence`, `sumbooklm-api` and `sumbooklm-app`; the remaining modules are
skipped for the reason given in finding 12.

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
