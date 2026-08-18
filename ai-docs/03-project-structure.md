# Project Structure

## Module graph

```
sumbooklm-parent                 (pom, aggregator + dependency and plugin management)
├── sumbooklm-domain             no dependencies
├── sumbooklm-persistence        -> domain
├── sumbooklm-security           -> domain, persistence
├── sumbooklm-workspace          -> domain, persistence
├── sumbooklm-ingestion          -> domain
├── sumbooklm-ai                 -> domain
├── sumbooklm-api                -> domain, persistence, security, workspace, ingestion, ai
├── sumbooklm-frontend           no Java, produces static/ resources
└── sumbooklm-app                -> api, frontend
```

The graph is acyclic and one directional. `domain` is a leaf and must stay framework free.
`persistence`, `ingestion` and `ai` are siblings and must not reference each other; anything that
needs two of them belongs in `api` or above. `security` and `workspace` are the two siblings that are
allowed to depend on `persistence`, because each owns tables of its own; see ADR-012 and ADR-024.

## Module responsibilities

**`sumbooklm-domain`** — Notebooks, sources, chunks, chat interactions as plain Java. No JPA, no
Jackson, no Spring, no LangChain4j. Holds the user account model and the workspace model; the source
and chunk side of the model joins it with the ingestion pipeline.

**`sumbooklm-persistence`** — Spring Data JPA, the CBOR payload codec, and the Aether Datafixers
integration. Holds `PayloadSchemaVersion`. Carries the H2 and PostgreSQL drivers at runtime scope.
This is the only module that depends on Jackson 2.

**`sumbooklm-security`** — Password hashing, the access and refresh token lifecycle, the weekly
cleanup of invalidated tokens, the derivation of the client cookie encryption parameters, and the
`@SensitiveOperation` marker with its aspect. Knows nothing about HTTP: it takes commands and returns
objects. Owns the `user_account` and `refresh_token` data through the persistence module.

**`sumbooklm-workspace`** — The lifecycle of a notebook and of everything below it: creating,
listing, renaming, pinning and removing, including the removal of the sources and chat sessions a
notebook holds. Like `security` it takes commands and returns domain objects and knows nothing about
HTTP. Every one of its methods carries the account it acts for; see ADR-025.

**`sumbooklm-ingestion`** — jsoup for fetching and cleaning web sources, the LangChain4j Apache Tika
document parser for extracting text from PDF, Markdown, HTML and plain text uploads. Produces domain
level results.

**`sumbooklm-ai`** — Chat model access (OpenAI compatible endpoint or Ollama), in process embeddings
via `all-MiniLM-L6-v2`, vector storage via `InMemoryEmbeddingStore`.

**`sumbooklm-api`** — Controllers, transport models, `ApiPaths`, `OpenApiConfiguration`, the
`SecurityFilterChain` and the exception handler that maps failures onto problem details. Owns the
API contract.

**`sumbooklm-frontend`** — The Vite / React application. No Java sources. `frontend-maven-plugin`
installs a private Node toolchain, runs `npm ci` and `npm run build`; `maven-resources-plugin` then
copies `target/dist` into `target/classes/static`, which makes the JAR a classpath resource bundle
that Spring Boot picks up automatically.

**`sumbooklm-app`** — `SumbookLmApplication`, `SinglePageApplicationConfiguration`, the profile
configuration, and the Spring Boot repackaging that produces `sumbooklm.jar`.

## Java package layout

Root package `de.pfoertner.assessment.sumbooklm`. The `@SpringBootApplication` class sits in that
root package, so component scanning reaches every module without explicit `basePackages`.

```
de.pfoertner.assessment.sumbooklm
├── SumbookLmApplication
├── config
│   ├── SinglePageApplicationConfiguration
│   ├── TimeConfiguration                       the shared Clock, see ADR-024
│   └── H2ConsoleSecurityConfiguration          (dev profile only)
├── domain
│   ├── user                                    UserAccount, UserProfile, AccountActivity
│   └── workspace                               Notebook, DocumentStatus
├── persistence
│   ├── schema.PayloadSchemaVersion
│   ├── payload                                 PayloadTypes, PayloadCodec, PayloadDataFixerBootstrap
│   ├── user                                    entity, repository, payload record and codec, mapper
│   ├── notebook                                entity, repository, payload record and codec, mapper
│   ├── document                                entity, repository, payload record and codec, count projection
│   ├── chat                                    entity, repository, payload record and codec
│   └── token                                   refresh token entity and repository
├── security
│   ├── config                                  SecurityProperties, bean and scheduling configuration
│   ├── authentication                          AuthenticationService, commands, result, failures
│   ├── token                                   issuer, refresh token service, cleanup job, claims
│   ├── cookie                                  cookie key derivation and its parameters
│   └── access                                  SensitiveOperation and its aspect
├── workspace
│   └── notebook                                NotebookService, update command, failure
├── ingestion
├── ai
└── api
    ├── ApiPaths
    ├── config                                  OpenApiConfiguration, SecurityConfiguration
    ├── error                                   ApiExceptionHandler
    ├── support                                 ClientAddressResolver, KeyHandleCookieFactory,
    │                                            AuthenticatedUserResolver
    └── v1
        ├── auth                                controller and payloads of the token lifecycle
        ├── notebook                            controller and payloads of notebook management
        └── security                            cookie parameter endpoint and its payload
```

## Frontend layout

```
sumbooklm-frontend
├── pom.xml               Maven wiring for the npm build
├── package.json          exact versions, save-exact=true via .npmrc
├── vite.config.ts        aliases, build.outDir=target/dist, dev proxy to :8080
├── tsconfig.json         project references, plus the @/* alias the shadcn CLI reads
├── tsconfig.app.json     src, types: ["vite/client"]
├── tsconfig.node.json    vite.config.ts
├── components.json       shadcn, style "base-nova", icon library lucide
├── index.html
├── scripts
│   └── accountBackground.mjs  renders src/assets/account-background.png
└── src
    ├── main.tsx          bootstrap, imports ./index.css and @/i18n
    ├── App.tsx           createBrowserRouter + RouterProvider
    ├── index.css         Tailwind v4 CSS-first, shadcn tokens
    ├── api
    │   ├── schema.d.ts   generated from the live OpenAPI document
    │   ├── narrowing.ts  turns the all-optional generated types into required ones
    │   └── client.ts     openapi-fetch client bound to those types
    ├── assets
    │   └── account-background.png   still background, generated, not hand made
    ├── components
    │   ├── background   wave shader, its renderer, the deciding component
    │   └── ui           shadcn: button, input, label, field, separator, card,
    │                    dropdown-menu, dialog, alert-dialog
    ├── notebooks
    │   ├── notebook.ts       client side type and its narrowing
    │   ├── notebooksApi.ts   the four calls of /api/v1/notebooks
    │   └── useNotebooks.ts   the list and the actions that change it
    ├── auth
    │   ├── authContext.ts    context, failure type, status
    │   ├── AuthProvider.tsx  session restore, login, register, refresh, logout
    │   ├── session.ts        client side types and narrowing of the generated ones
    │   ├── sessionStore.ts   AES-GCM encryption of the session into a cookie
    │   └── useAuth.ts        hook over the context
    ├── i18n
    │   ├── index.ts      i18next + language detector, de / en / ja
    │   └── locales/{de,en,ja}/common.json
    ├── lib/utils.ts      cn()
    └── routes            AppLayout (the signed-in shell), NotFoundPage
        ├── account       AccountLayout, AuthCard, BrandMark, authFormStyles,
        │                 LoginPage, RegisterPage
        └── dashboard     DashboardPage, NotebookCard, NotebookCreateCard,
                          NotebookTitleDialog, NotebookDeleteDialog, NotebookMeta
```

The account routes are a second top level branch of the router rather than children of `AppLayout`.
They need the full viewport without the application header, and they carry their own dark palette, so
sharing a layout with the signed-in application would have meant undoing that layout inside them.

Build output goes to `target/dist` rather than `dist` so that `mvn clean` removes it and so the
frontend module obeys the same directory conventions as the Java modules.

## Configuration profiles

`application.yml` holds everything profile independent: application name and version, multipart
limits, springdoc paths, `spring.jpa.open-in-view: false`. `spring.profiles.default` is `dev`.

`application-dev.yml` — in memory H2, `ddl-auto: update`, H2 console at `/h2-console`, debug logging
for the application packages.

`application-prod.yml` — PostgreSQL from `SUMBOOKLM_DATASOURCE_*` environment variables,
`ddl-auto: validate`, Swagger UI disabled, `server.forward-headers-strategy: framework` so that the
recorded caller address is the one a reverse proxy reports rather than the proxy itself.

The `sumbooklm.security` namespace is split across the profiles on purpose. The issuer and the two
token lifetimes are profile independent and live in `application.yml`. The two secrets are not: the
development profile carries a value that is visibly a development value and can be overridden by an
environment variable, and the production profile has no default at all, so a missing
`SUMBOOKLM_JWT_SECRET` or `SUMBOOKLM_COOKIE_SECRET` fails the startup instead of silently using a
secret that is identical in every checkout. Secrets shorter than 32 characters are rejected as well,
because HMAC with SHA-256 would otherwise be keyed below its digest length.

No LangChain4j model properties are configured yet. Configuring
`langchain4j.open-ai.chat-model.api-key` would eagerly construct a model bean at startup, which
contradicts the bring-your-own-key model where the key arrives per request. The wiring for that
belongs with the implementation, not with the scaffold.

## Authentication flow

The sequence below is what the integration test and the manual verification both exercise.

1. `POST /api/v1/register` or `POST /api/v1/login`. The server stores or verifies the password hash,
   records the login timestamp and caller address, issues an access token and a refresh token,
   persists a row for the refresh token, and returns both tokens with the account. The response also
   sets `sumbooklm_key_handle`, an `HttpOnly` cookie holding an opaque handle.
2. The client calls `GET /api/v1/security/cookie-iv/`. The browser attaches the handle cookie
   automatically; the server derives the AES-GCM key from it and answers with the key, a fresh
   initialization vector and the name of the cookie the client should use.
3. The client encrypts `{ user, tokens }`, writes `initializationVector || ciphertext` Base64 encoded
   into `sumbooklm_auth`, and keeps the session in memory.
4. Protected requests carry `Authorization: Bearer <access token>`. The resource server verifies the
   signature, the issuer, the expiry and that `token_type` is `access`.
5. When the access token has expired, the client posts its refresh token to
   `POST /api/v1/token/refresh`. The server verifies the signature and the `refresh` token type,
   compares the digest against the stored row, revokes that row and issues a new pair.
6. On the next page load the client repeats step 2, reads the vector from the front of its
   ciphertext, decrypts, and continues at step 4 or 5.
7. `POST /api/v1/logout` is marked `@SensitiveOperation`, so it verifies the session named by the
   `sid` claim against the database before revoking it and clearing the handle cookie.

The relation that makes step 7 possible: an access token carries the identifier of the refresh token
it was issued with in its `sid` claim, so any operation can ask whether its session is still open
even though the access token itself is verified by signature alone.

## Database tables

Both tables are created by Hibernate from the entities; there are no migration scripts (ADR-005).

`user_account` — `id` (UUID, primary key), `username` (unique), `password_hash`, `registered_at`,
`last_login_at`, `payload` (CBOR bytes), `payload_version`, `record_version` (optimistic locking).
First name, last name and the two recorded addresses live in `payload`; see ADR-016.

`refresh_token` — `id` (UUID, primary key, equal to the `jti` of the token it describes), `user_id`
(foreign key to `user_account`), `token_hash` (unique, hexadecimal SHA-256 of the issued token),
`issued_at`, `expires_at` (indexed, the cleanup job filters on it), `revoked_at` (null while the
token is usable), `issued_to_ip`.
