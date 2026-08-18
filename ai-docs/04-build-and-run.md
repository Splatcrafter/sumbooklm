# Build and Run

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
