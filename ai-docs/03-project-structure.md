# Project Structure

## Module graph

```
sumbooklm-parent                 (pom, aggregator + dependency and plugin management)
├── sumbooklm-domain             no dependencies
├── sumbooklm-persistence        -> domain
├── sumbooklm-security           -> domain, persistence
├── sumbooklm-ingestion          -> domain
├── sumbooklm-ai                 -> domain
├── sumbooklm-workspace          -> domain, persistence, ingestion, ai
├── sumbooklm-api                -> domain, persistence, security, workspace, ingestion, ai
├── sumbooklm-frontend           no Java, produces static/ resources
└── sumbooklm-app                -> api, frontend
```

The graph is acyclic and one directional. `domain` is a leaf and must stay framework free.
`persistence`, `ingestion` and `ai` are capability modules that must not reference each other:
`ingestion` knows how to read a source, `ai` knows how to embed one and how to ask a model, and
neither knows that notebooks exist. `security` and `workspace` are the two modules that combine capabilities and own tables; see
ADR-012 and ADR-024. `workspace` is therefore the only module that depends on `ingestion` and `ai`,
because the pipeline that runs one after the other is the lifecycle of a source and not a capability
of its own.

## Module responsibilities

**`sumbooklm-domain`** — Notebooks, sources, chunks, chat interactions as plain Java. No JPA, no
Jackson, no Spring, no LangChain4j. Holds the user account model and the workspace model, including
the transcript of a conversation and the two roles a message can have.

**`sumbooklm-persistence`** — Spring Data JPA, the CBOR payload codec, and the Aether Datafixers
integration. Holds `PayloadSchemaVersion`. Carries the H2 and PostgreSQL drivers at runtime scope.
This is the only module that depends on Jackson 2.

**`sumbooklm-security`** — Password hashing, the access and refresh token lifecycle, the weekly
cleanup of invalidated tokens, the derivation of the client cookie encryption parameters, and the
`@SensitiveOperation` marker with its aspect. Knows nothing about HTTP: it takes commands and returns
objects. Owns the `user_account` and `refresh_token` data through the persistence module.

**`sumbooklm-workspace`** — The lifecycle of a notebook and of everything below it: creating,
listing, renaming, pinning and removing a notebook, adding, listing and removing its sources, and the
pipeline that turns a stored source into segments. Like `security` it takes commands and returns
domain objects and knows nothing about HTTP. Every one of its methods carries the account it acts
for; see ADR-025. The pipeline runs after the storing transaction commits; see ADR-028.

**`sumbooklm-ingestion`** — jsoup for fetching and cleaning web sources, the LangChain4j Apache Tika
document parser for extracting text from PDF, Markdown, HTML and plain text uploads, and the splitter
that cuts the extracted text into segments. Produces text and segments, and knows nothing about where
either came from or goes.

**`sumbooklm-ai`** — Chat model access built per request from what the caller presented (ADR-034),
the instructions an answer is grounded by, in process embeddings via `all-MiniLM-L6-v2`, and vector
storage via `InMemoryEmbeddingStore`. `NotebookIndex` is the only way into the store in either
direction: writing takes the notebook and the source, and reading hands out a retriever filtered to
one notebook; see ADR-030 and ADR-035.

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
│   ├── AsyncConfiguration                      @EnableAsync, the ingestion and chat pools, ADR-028
│   └── H2ConsoleSecurityConfiguration          (dev profile only)
├── domain
│   ├── user                                    UserAccount, UserProfile, AccountActivity
│   └── workspace                               Notebook, SourceDocument, DocumentStatus, SourceKind,
│                                                ChatSession, ChatMessage, ChatRole
├── persistence
│   ├── schema.PayloadSchemaVersion
│   ├── payload                                 PayloadTypes, PayloadCodec, PayloadDataFixerBootstrap
│   ├── user                                    entity, repository, payload record and codec, mapper
│   ├── notebook                                entity, repository, payload record and codec, mapper
│   ├── document                                entity, repository, payload record and codec, count projection
│   ├── chat                                    entity, repository, session and message payloads, mapper
│   └── token                                   refresh token entity and repository
├── security
│   ├── config                                  SecurityProperties, bean and scheduling configuration
│   ├── authentication                          AuthenticationService, commands, result, failures
│   ├── token                                   issuer, refresh token service, cleanup job, claims
│   ├── cookie                                  cookie key derivation and its parameters
│   └── access                                  SensitiveOperation and its aspect
├── workspace
│   ├── notebook                                NotebookService, update command, failure
│   ├── source                                  SourceDocumentService, the pipeline, its event,
│   │                                            fingerprints and failures
│   └── chat                                    NotebookChatService, ChatSessionService, the recorder,
│                                                the turn context, the stream handler, failures
├── ingestion
│   ├── extraction                              file and web extractors, their result and failure
│   └── chunking                                TextChunker
├── ai
│   ├── embedding                               model and store beans, NotebookIndex, metadata keys
│   └── chat                                    ChatProvider, ModelSelection, ChatModelFactory,
│                                                GroundedPrompt, GroundedChatEngine, its handler
└── api
    ├── ApiPaths
    ├── config                                  OpenApiConfiguration, SecurityConfiguration
    ├── error                                   ApiExceptionHandler
    ├── support                                 ClientAddressResolver, KeyHandleCookieFactory,
    │                                            AuthenticatedUserResolver
    └── v1
        ├── auth                                controller and payloads of the token lifecycle
        ├── notebook                            controller and payloads of notebook management
        ├── source                              controller and payloads of source management
        ├── chat                                controller, BYOK headers, stream events, SSE writer
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
    │                    dropdown-menu, dialog, alert-dialog, tabs, textarea
    ├── notebooks
    │   ├── notebook.ts       client side type and its narrowing
    │   ├── notebooksApi.ts   the five calls of /api/v1/notebooks
    │   ├── notebookRoutes.ts the address one Sumbook lives under
    │   ├── TopicIcon.tsx     the topic square and its fallback icon
    │   ├── useNotebooks.ts   the list and the actions that change it
    │   └── useNotebook.ts    one Sumbook, loaded by its identifier
    ├── sources
    │   ├── source.ts         client side type, its narrowing and the pending predicate
    │   ├── sourcesApi.ts     the four calls below /api/v1/notebooks/{id}/sources
    │   └── useSources.ts     the list, its actions, and the polling while indexing runs
    ├── chat
    │   ├── chatMessage.ts    client side message and source types with their narrowing
    │   ├── chatApi.ts        the transcript call and the fetch that reads the event stream
    │   └── useChat.ts        the transcript, the answer being written, and asking
    ├── byok
    │   ├── modelSettings.ts           the settings, their rules and the headers they become
    │   ├── modelSettingsStore.ts      the encrypted cookie they live in
    │   ├── modelSettingsContext.ts    context and its value
    │   ├── ModelSettingsProvider.tsx  restore on sign-in, save, forget
    │   └── useModelSettings.ts        hook over the context
    ├── security
    │   └── encryptedCookies.ts  AES-GCM read, write and delete under the derived key
    ├── auth
    │   ├── authContext.ts    context, failure type, status
    │   ├── AuthProvider.tsx  session restore, login, register, refresh, logout
    │   ├── session.ts        client side types and narrowing of the generated ones
    │   ├── sessionStore.ts   the session as one of the encrypted cookies
    │   └── useAuth.ts        hook over the context
    ├── i18n
    │   ├── index.ts      i18next + language detector, de / en / ja
    │   └── locales/{de,en,ja}/common.json
    ├── lib/utils.ts      cn()
    └── routes            AppLayout (the signed-in shell), NotFoundPage
        ├── account       AccountLayout, AuthCard, BrandMark, authFormStyles,
        │                 LoginPage, RegisterPage
        ├── dashboard     DashboardPage, NotebookCard, NotebookCreateCard,
        │                 NotebookTitleDialog, NotebookDeleteDialog, NotebookMeta
        ├── settings      ModelSettingsDialog
        └── sumbook       SumbookPage, SourcesPanel, SourceListItem, AddSourceDialog,
                          ChatPanel, ChatComposer, ChatMessageView, StudioPanel, SumbookMeta
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

## Answering a question

1. `POST /api/v1/notebooks/{id}/chat` carries the question in its body and the model in its headers.
   The selection is validated first, then the notebook is resolved for the account of the access
   token, the question is appended to the conversation of that notebook, and the transaction commits.
   Both failures that can still be a status code have happened by now (ADR-037).
2. The request returns an `SseEmitter`. Everything after this point runs on the chat executor.
3. The retriever of that notebook embeds the question and reads the segments whose `notebookId`
   metadata matches, above the relevance floor (ADR-035). Their sources are named from the source
   table, numbered per document, and sent as the `sources` event.
4. A client is built for the presented provider, and the model is asked with the rules, the numbered
   passages, the last messages of the conversation and the question. Each part it generates is
   written to the stream as a `token` event.
5. The finished answer is handed to the asynchronous recorder and sent as the `done` event, after
   which the stream is closed. A failure at any point after step 2 becomes an `error` event instead,
   and the question stays in the transcript without an answer.

## Database tables

All tables are created by Hibernate from the entities; there are no migration scripts (ADR-005).
Every one of them carries a `payload` of CBOR bytes and the `payload_version` those bytes were written
at, so a field that is not part of a query lives there rather than in a column (ADR-016).

`user_account` — `id` (UUID, primary key), `username` (unique), `password_hash`, `registered_at`,
`last_login_at`, `payload` (CBOR bytes), `payload_version`, `record_version` (optimistic locking).
First name, last name and the two recorded addresses live in `payload`; see ADR-016.

`refresh_token` — `id` (UUID, primary key, equal to the `jti` of the token it describes), `user_id`
(foreign key to `user_account`), `token_hash` (unique, hexadecimal SHA-256 of the issued token),
`issued_at`, `expires_at` (indexed, the cleanup job filters on it), `revoked_at` (null while the
token is usable), `issued_to_ip`.

`notebook` — `id` (UUID, primary key), `user_id` (indexed, every query is scoped to it),
`created_at`, `last_activity_at` (the overview orders by it), `payload`, `payload_version`,
`record_version`. Title, pin state and topic icon live in `payload`.

`source_document` — `id` (UUID, primary key), `user_id` and `notebook_id` (both indexed), `created_at`,
`content` (the uploaded bytes, null for a web source, see ADR-032), `payload`, `payload_version`,
`record_version`. Name, kind, origin, stage, token count and content hash live in `payload`.

`chat_session` — `id` (UUID, primary key), `user_id` and `notebook_id` (both indexed), `created_at`,
`last_message_at`, `payload`, `payload_version`, `record_version`. The title and the whole transcript
live in `payload`: a message is never read without its conversation and never changes once appended,
so a row per message would add a join without ever being addressed on its own.

The rows below a notebook carry its identifier rather than a foreign key with a cascade. Removing a
notebook therefore removes its sources and its conversations explicitly, in the same transaction,
where that cascade is visible.
