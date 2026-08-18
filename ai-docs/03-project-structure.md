# Project Structure

## Module graph

```
sumbooklm-parent                 (pom, aggregator + dependency and plugin management)
├── sumbooklm-domain             no dependencies
├── sumbooklm-persistence        -> domain
├── sumbooklm-ingestion          -> domain
├── sumbooklm-ai                 -> domain
├── sumbooklm-api                -> domain, persistence, ingestion, ai
├── sumbooklm-frontend           no Java, produces static/ resources
└── sumbooklm-app                -> api, frontend
```

The graph is acyclic and one directional. `domain` is a leaf and must stay framework free.
`persistence`, `ingestion` and `ai` are siblings and must not reference each other; anything that
needs two of them belongs in `api` or above.

## Module responsibilities

**`sumbooklm-domain`** — Notebooks, sources, chunks, chat interactions as plain Java. No JPA, no
Jackson, no Spring, no LangChain4j. Currently only the package declaration exists.

**`sumbooklm-persistence`** — Spring Data JPA, the CBOR payload codec, and the Aether Datafixers
integration. Holds `PayloadSchemaVersion`. Carries the H2 and PostgreSQL drivers at runtime scope.
This is the only module that depends on Jackson 2.

**`sumbooklm-ingestion`** — jsoup for fetching and cleaning web sources, the LangChain4j Apache Tika
document parser for extracting text from PDF, Markdown, HTML and plain text uploads. Produces domain
level results.

**`sumbooklm-ai`** — Chat model access (OpenAI compatible endpoint or Ollama), in process embeddings
via `all-MiniLM-L6-v2`, vector storage via `InMemoryEmbeddingStore`.

**`sumbooklm-api`** — Controllers, transport models, `ApiPaths`, `OpenApiConfiguration`. Owns the
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
├── config.SinglePageApplicationConfiguration
├── domain
├── persistence
│   └── schema.PayloadSchemaVersion
├── ingestion
├── ai
└── api
    ├── ApiPaths
    └── config.OpenApiConfiguration
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
└── src
    ├── main.tsx          bootstrap, imports ./index.css and @/i18n
    ├── App.tsx           createBrowserRouter + RouterProvider
    ├── index.css         Tailwind v4 CSS-first, shadcn tokens
    ├── api
    │   ├── schema.d.ts   generated from the live OpenAPI document
    │   └── client.ts     openapi-fetch client bound to those types
    ├── i18n
    │   ├── index.ts      i18next + language detector, de / en / ja
    │   └── locales/{de,en,ja}/common.json
    ├── lib/utils.ts      cn()
    ├── components/ui     shadcn components land here
    └── routes            AppLayout, HomePage, NotFoundPage
```

Build output goes to `target/dist` rather than `dist` so that `mvn clean` removes it and so the
frontend module obeys the same directory conventions as the Java modules.

## Configuration profiles

`application.yml` holds everything profile independent: application name and version, multipart
limits, springdoc paths, `spring.jpa.open-in-view: false`. `spring.profiles.default` is `dev`.

`application-dev.yml` — in memory H2, `ddl-auto: update`, H2 console at `/h2-console`, debug logging
for the application packages.

`application-prod.yml` — PostgreSQL from `SUMBOOKLM_DATASOURCE_*` environment variables,
`ddl-auto: validate`, Swagger UI disabled.

No LangChain4j model properties are configured yet. Configuring
`langchain4j.open-ai.chat-model.api-key` would eagerly construct a model bean at startup, which
contradicts the bring-your-own-key model where the key arrives per request. The wiring for that
belongs with the implementation, not with the scaffold.
