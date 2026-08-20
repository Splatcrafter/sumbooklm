<div align="center">

# 📚 SumbookLM

**A self-hosted, source-grounded research notebook.**
Upload documents, add web pages, and ask questions that are answered *only* from your own material — with a citation behind every statement.

[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.19.0-1C3C3C)](https://docs.langchain4j.dev/)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)](https://vite.dev/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-4-06B6D4?logo=tailwindcss&logoColor=white)](https://tailwindcss.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)

[![Publish container image](https://github.com/Splatcrafter/sumbooklm/actions/workflows/docker-publish.yml/badge.svg)](https://github.com/Splatcrafter/sumbooklm/actions/workflows/docker-publish.yml)
[![Tests](https://img.shields.io/badge/tests-617%20passing-brightgreen)](#-quality-gates)
[![JavaDoc gate](https://img.shields.io/badge/JavaDoc-enforced-blue)](#-quality-gates)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Docker](https://img.shields.io/badge/Docker-ghcr.io-2496ED?logo=docker&logoColor=white)](https://github.com/Splatcrafter/sumbooklm/pkgs/container/sumbooklm)

</div>

---

## ✨ What it is

SumbookLM is a notebook application built around retrieval-augmented generation. You create a
**Sumbook**, fill it with sources — PDFs, Markdown, HTML, plain text, or a web address — and then ask
questions about them. The model never answers from its own knowledge: it receives the passages that
were actually retrieved from *your* sources, under instructions that permit no other material and
require a Markdown citation per statement.

The whole thing ships as **one executable JAR**. The Spring Boot backend and the React single page
application are served from the same port, so a deployment is one container and one database.

### 🔑 Bring your own key

No model credentials live on the server. The provider, the model name and the API key travel in
request headers, are turned into a client for exactly one answer, and are forgotten with the
response. The browser keeps them AES-GCM encrypted in a cookie next to the session, under a key
derived from an `HttpOnly` handle the server hands out and the client never stores.

Supported providers: **OpenAI**, **Groq**, and **Ollama** (or any OpenAI-compatible base URL).

---

## 🚀 Features

| Feature                      | Explanation                                                                                                                                                                   |
|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 🔐 **Accounts and sessions** | Registration, login, JWT access tokens (5 min) and rotating refresh tokens (90 days), reuse detection that closes the whole session, and a weekly cleanup job                 |
| 📓 **Sumbooks**              | Create, rename, pin, re-icon and remove notebooks; the dashboard orders them by last activity                                                                                 |
| 📥 **Ingestion**             | File uploads parsed with Apache Tika, web pages fetched through an address guard and cleaned with jsoup, chunked into 1 000-character segments with 200 characters of overlap |
| 🧠 **In-process embeddings** | `all-MiniLM-L6-v2` (384 dimensions) runs inside the JVM — no embedding API, no key, no egress                                                                                 |
| 💬 **Grounded chat**         | Streamed over Server-Sent Events, retrieval filtered to one notebook by metadata, numbered passages, Markdown citations, and a stop button that keeps what already arrived    |
| 🗂️ **Many conversations**    | A Sumbook holds as many transcripts as you start; a question names the one it continues                                                                                       |
| 📝 **Summaries**             | Written from *every* readable source rather than from what a question retrieves, budgeted so no source disappears, and marked stale when the sources change                   |
| 🌍 **i18n**                  | German, English and Japanese, switchable from the frame of every screen — signed in or not; summaries are written in the language you are reading                             |
| 🛡️ **SSRF guard**            | Every hop of a web fetch resolves through a rule that refuses addresses inside the server's own networks, plus an optional host allowlist                                     |
| 🚦 **Back pressure**         | Three answers in flight per account, 60 questions per hour per account (counted in the database, so every instance sees the same number)                                      |
| 📄 **OpenAPI**               | The backend owns the contract; the frontend's TypeScript types are generated from the live document                                                                           |

---

## 🏗️ Architecture

### Module graph

```
sumbooklm-parent                 aggregator, dependency and plugin management
├── sumbooklm-domain             plain Java, no framework
├── sumbooklm-persistence        -> domain
├── sumbooklm-security           -> domain, persistence
├── sumbooklm-ingestion          -> domain
├── sumbooklm-ai                 -> domain
├── sumbooklm-workspace          -> domain, persistence, ingestion, ai
├── sumbooklm-api                -> domain, persistence, security, workspace, ingestion, ai
├── sumbooklm-frontend           no Java, produces static/ resources
└── sumbooklm-app                -> api, frontend
```

The graph is acyclic and one-directional. `domain` is a framework-free leaf. `persistence`,
`ingestion` and `ai` are capability modules that must not reference each other: `ingestion` knows how
to read a source, `ai` knows how to embed one and how to ask a model, and neither knows that
notebooks exist. `security` and `workspace` are the two modules that combine capabilities and own
tables.

### Notable design decisions

- 🧊 **Business data lives in a CBOR payload**, not in relational columns. Every table carries
  `payload` plus the `payload_version` it was written at; fields that are queried get a column,
  fields that are only displayed do not. Schema evolution runs through **Aether Datafixers**.
- 🔁 **Indexing happens after the commit**, on an executor of its own. The extracted text is stored
  with the source, so re-indexing needs neither the parser nor the network.
- ♻️ **The vector store is in-process and rebuilt at every start**, because it does not survive the
  process while the sources do. That is also why startup is slow and why no volume is needed for it.
- 🔒 **Ownership is part of every query**, never a check afterwards — every workspace method carries
  the account it acts for.
- 🚪 **A deployment that declares itself served over HTTPS refuses any API request that arrived
  without it**, before the token is even read.

> 📖 The full reasoning — 65 architecture decision records, research findings, and 49 tracked open
> questions — lives in [`ai-docs/`](ai-docs/README.md).

### Tech stack

| Layer                    | Choice                                                                                      |
|--------------------------|---------------------------------------------------------------------------------------------|
| Language / runtime       | Java 25, Maven 3.9+                                                                         |
| Backend                  | Spring Boot 4.1.0, Spring Security (resource server), Spring Data JPA, Hibernate 7          |
| AI                       | LangChain4j 1.19.0, `all-MiniLM-L6-v2` ONNX embeddings, `InMemoryEmbeddingStore`            |
| Ingestion                | Apache Tika, Apache HttpClient, jsoup 1.23.1                                                |
| Persistence              | PostgreSQL 18 (prod), H2 in-memory (dev), CBOR payloads, Aether Datafixers                  |
| API docs                 | springdoc-openapi 3.1.0                                                                     |
| Frontend                 | React 19, TypeScript 5.9, Vite 8, Tailwind CSS 4, shadcn / Base UI, react-router 8, i18next |
| Client / server contract | `openapi-typescript` + `openapi-fetch`, generated from `/v3/api-docs`                       |
| Container                | Two-stage Dockerfile (Temurin 25), layered extraction, Compose stack                        |

---

## ⚡ Quick start

### Prerequisites

| Tool             | Version                                                       | Needed for                            |
|------------------|---------------------------------------------------------------|---------------------------------------|
| JDK              | **25** or newer                                               | everything                            |
| Maven            | **3.9+**                                                      | the build (there is no Maven wrapper) |
| Node / npm       | installed automatically by Maven (Node v24.19.0, npm 11.17.0) | the frontend                          |
| Docker + Compose | any recent version with BuildKit                              | the container deployment              |

### Build and run locally

```bash
git clone https://github.com/Splatcrafter/sumbooklm.git
cd sumbooklm

./local-compile.sh        # full build -> sumbooklm-app/target/sumbooklm.jar
./local-start.sh          # serves API + SPA on http://localhost:8080
```

On Windows use `local-compile.bat` and `local-start.bat`. Both scripts check their prerequisites
first and fail with a sentence naming what is missing, and both forward their arguments — to Maven
and to Spring Boot respectively:

```bash
./local-compile.sh -Dfrontend.skip=true       # backend only, skips the Node toolchain
./local-start.sh --server.port=9090           # overrides the port the script sets
```

The default profile is `dev`: in-memory H2, `ddl-auto: update`, the H2 console at `/h2-console`,
Swagger UI at `/swagger-ui.html`, and development defaults for both secrets so the application starts
without any environment at all.

### Plain Maven

```bash
mvn clean install                                    # full build
mvn clean install -Dfrontend.skip=true               # backend only
java -jar sumbooklm-app/target/sumbooklm.jar
```

### Frontend development

```bash
./local-dev-server.sh     # Vite on http://localhost:5173, HMR, proxies /api to :8080
```

Run `./local-start.sh` in parallel — the dev server proxies `/api` and `/v3/api-docs` to the backend
on 8080, and `SUMBOOKLM_BACKEND_URL` overrides that target. The script prefers the Node toolchain
that the Maven build installed under `sumbooklm-frontend/target/node`, so the dev server runs on the
same versions as the packaged build.

From `sumbooklm-frontend`:

```bash
npm install
npm run dev                    # Vite on :5173
npm run build                  # tsc -b && vite build -> target/dist
npm run typecheck              # tsc -b
npm run api:generate           # regenerates src/api/schema.d.ts from a running backend
npx shadcn@latest add <name>   # adds a Base UI component into src/components/ui
```

---

## 🐳 Deployment

One image holding the application, one Compose stack adding PostgreSQL.

```bash
cp .env.example .env
# fill in SUMBOOKLM_JWT_SECRET, SUMBOOKLM_COOKIE_SECRET and POSTGRES_PASSWORD
openssl rand -base64 48        # generate each secret separately

docker compose up -d
```

The stack is also deployable straight into **Portainer** — the Compose file keeps its
`version: "3.9"` key precisely because Portainer refuses a file without one, and stays inside the 3.x
schema for the same reason.

> 🔄 On the **first** deploy the application may restart once or twice while PostgreSQL initialises
> its volume. The 3.x schema has no way to say "start after the database is healthy", so the app
> exits when it cannot open its data source and `restart: unless-stopped` brings it back. It fails
> before the expensive part of the startup, so each attempt costs seconds.

### Pulling instead of building

`docker-compose.yml` declares both `image` and `build`, so a stack deployed from a registry pulls and
a stack deployed from this repository builds under the same name:

```bash
SUMBOOKLM_IMAGE=ghcr.io/splatcrafter/sumbooklm:latest docker compose up -d
```

Publishing is **started by hand**, never by a push: run the *Publish container image* workflow from
the Actions tab on the branch or tag you want out. Tags are derived from that ref, so
`:latest` moves only when somebody moves it.

### 💾 What needs a volume

Only the database. Source content, payloads and chat transcripts are all columns, and the vector
store is in-process and rebuilt at every start by design — which is why `start_period` of the health
check is 180 seconds and why a deployment with a lot of content takes a while to serve its first page.

> ⚠️ Give the container **at least 2 GB** of memory. Embedding runs in-process, and every source is
> indexed again at startup.

---

## ⚙️ Configuration

All settings are read from environment variables. See [`.env.example`](.env.example) for the full,
commented list.

### Required in production

| Variable                  | Meaning                                                                                                              |
|---------------------------|----------------------------------------------------------------------------------------------------------------------|
| `SUMBOOKLM_JWT_SECRET`    | HMAC secret for both token types. **No default in `prod`** — a missing or sub-32-character value fails the startup   |
| `SUMBOOKLM_COOKIE_SECRET` | Secret the client cookie encryption key is derived from. Same rules                                                  |
| `POSTGRES_PASSWORD`       | Written into the cluster when the volume is first created. Changing it later does **not** change the role's password |

### Optional

| Variable                                               | Default                                               | Meaning                                                                                                                 |
|--------------------------------------------------------|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `SUMBOOKLM_IMAGE`                                      | `ghcr.io/splatcrafter/sumbooklm:latest`               | Image the stack runs                                                                                                    |
| `SUMBOOKLM_PORT`                                       | `8080`                                                | Host port                                                                                                               |
| `POSTGRES_DB` / `POSTGRES_USER`                        | `sumbooklm`                                           | Database name and role                                                                                                  |
| `SUMBOOKLM_REQUIRE_HTTPS`                              | `true`                                                | Refuse anything below `/api` that did not arrive over HTTPS. Depends on the reverse proxy setting `X-Forwarded-Proto`   |
| `SUMBOOKLM_DDL_AUTO`                                   | `update`                                              | The application ships no migration tool, so Hibernate writes the schema. Move to `validate` once it is settled          |
| `SUMBOOKLM_ALLOWED_SOURCE_HOSTS`                       | *(empty)*                                             | Comma-separated hosts a web source may be fetched from. Empty permits every **public** host; the list only ever narrows |
| `SUMBOOKLM_JAVA_OPTS`                                  | `-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError` | JVM options. The percentage is of the container limit                                                                   |
| `SUMBOOKLM_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | *(set by Compose)*                                    | PostgreSQL connection, `prod` profile only                                                                              |

> 🔓 Turning `SUMBOOKLM_REQUIRE_HTTPS` off means access tokens travel in the clear. Do it only to
> reach a deployment directly over plain HTTP.

---

## 🔌 API

Everything lives below `/api/v1`. Interactive documentation is at `/swagger-ui.html` (dev profile
only) and the OpenAPI 3.1 document at `/v3/api-docs`.

### Authentication

| Method | Path                          | Auth                  | Purpose                                   |
|--------|-------------------------------|-----------------------|-------------------------------------------|
| `POST` | `/api/v1/register`            | —                     | Create an account, return a token pair    |
| `POST` | `/api/v1/login`               | —                     | Verify credentials, return a token pair   |
| `POST` | `/api/v1/token/refresh`       | refresh token in body | Rotate the pair                           |
| `POST` | `/api/v1/logout`              | bearer                | Close the session of that token           |
| `GET`  | `/api/v1/security/cookie-iv/` | key handle cookie     | Hand out the cookie encryption parameters |

### Notebooks, sources, chat

| Method                 | Path                                                 | Purpose                                                     |
|------------------------|------------------------------------------------------|-------------------------------------------------------------|
| `GET` `POST`           | `/api/v1/notebooks`                                  | List / create                                               |
| `GET` `PATCH` `DELETE` | `/api/v1/notebooks/{id}`                             | Read / rename, pin, re-icon / remove                        |
| `GET`                  | `/api/v1/notebooks/{id}/sources`                     | List sources with stage, failure cause and last-read moment |
| `POST`                 | `/api/v1/notebooks/{id}/sources/files`               | Add an upload (`multipart/form-data`, ≤ 32 MB)              |
| `POST`                 | `/api/v1/notebooks/{id}/sources/links`               | Add a web address                                           |
| `DELETE`               | `/api/v1/notebooks/{id}/sources/{sourceId}`          | Remove a source and its segments                            |
| `POST`                 | `/api/v1/notebooks/{id}/sources/{sourceId}/refresh`  | Read the source again rather than reuse the stored text     |
| `GET` `POST`           | `/api/v1/notebooks/{id}/summary`                     | Read / have one written                                     |
| `GET` `POST`           | `/api/v1/notebooks/{id}/chats`                       | List / start a conversation                                 |
| `GET` `DELETE`         | `/api/v1/notebooks/{id}/chats/{sessionId}`           | Read / remove a transcript                                  |
| `POST`                 | `/api/v1/notebooks/{id}/chats/{sessionId}/questions` | Ask — responds `text/event-stream`                          |
| `POST`                 | `/api/v1/notebooks/{id}/chats/{sessionId}/stop`      | Stop the answer being written, keeping what arrived         |

Everything else below `/api` requires a valid access token. Paths outside `/api` serve the single
page application and stay open. Failures are returned as RFC 9457 `application/problem+json`.

### 🗝️ BYOK headers

The question endpoint reads the model from headers rather than from configuration:

| Header          | Example                                              |
|-----------------|------------------------------------------------------|
| `X-AI-Provider` | `OPENAI`, `GROQ`, `OLLAMA`                           |
| `X-AI-Model`    | `gpt-4o-mini`                                        |
| `X-AI-Api-Key`  | your key (not required for Ollama)                   |
| `X-AI-Base-Url` | optional override of the provider's default base URL |

### 📡 Stream events

The answer arrives as typed Server-Sent Events: `sources` (the numbered passages that were
retrieved, with their documents), `token` (each generated part), `done` (the finished answer), and
`error` if anything failed after the stream opened.

---

## ✅ Quality gates

```bash
mvn verify -Dfrontend.skip=true      # tests + JavaDoc gate, without the Node toolchain
mvn -Dmaven.javadoc.skip=true verify # bypasses the JavaDoc gate
```

- **617 tests** across 86 test classes. 69 of them drive the assembled application end to end over
  HTTP on a random port; the remaining 548 are unit tests written for the states a running deployment
  reaches rarely — races, refusals, permits that have to be given back, and the constants that are
  contracts with stored rows or with a generated client.
- **The JavaDoc gate is enforced by the build.** `maven-javadoc-plugin` runs with `show=private`,
  `doclint=all` and `failOnWarnings=true` over main *and* test sources, so a single undocumented
  element turns the build red.
- **Module boundaries** are part of the POM graph, not a convention — a cycle simply does not compile.

---

## 🗺️ Repository layout

```
sumbooklm/
├── sumbooklm-domain/         plain Java model: accounts, notebooks, sources, transcripts
├── sumbooklm-persistence/    JPA entities, CBOR payload codecs, Aether Datafixers
├── sumbooklm-security/       password hashing, token lifecycle, cookie key derivation
├── sumbooklm-ingestion/      Tika, jsoup, the address resolver, the chunker
├── sumbooklm-ai/             embeddings, NotebookIndex, grounded prompts, summary engine
├── sumbooklm-workspace/      notebook / source / chat lifecycle and the indexing pipeline
├── sumbooklm-api/            controllers, ApiPaths, SecurityFilterChain, problem details
├── sumbooklm-frontend/       Vite + React SPA, packaged into static/ resources
├── sumbooklm-app/            SumbookLmApplication, profiles, Spring Boot repackaging
├── ai-docs/                  internal knowledge base (ADRs, findings, open questions)
├── .devcontainer/            hardened reproducible dev environment
├── Dockerfile                two-stage build, layered extraction
├── docker-compose.yml        application + PostgreSQL
└── .env.example              every setting a deployment reads
```

`ai-docs/` is an internal tool: production code never references it, and none of its reasoning is
allowed to leak into source comments or JavaDoc.

---

## 🧭 Known limitations

Tracked in full in [`ai-docs/05-open-questions.md`](ai-docs/05-open-questions.md). The ones worth
knowing before you deploy:

- 🗃️ **No migration tool.** The schema is written by Hibernate. Replacing that with Flyway or
  Liquibase is the proper fix and is not done here.
- ♻️ **The startup rebuild has no bound and no back pressure**, so a large corpus makes startup slow.
- 🔁 **Nothing limits the rate of authentication attempts** yet.
- 🛑 **Stopping an answer stops what this application does with it.** The provider may still bill for
  what it already generated.
- 🧪 **The frontend has no test runner** and there is no linting or formatting toolchain yet.
- 👀 **Nothing notices that a web page has changed** — re-reading a source is something a user asks for.

---

## 📜 License

Released under the [MIT License](LICENSE). Copyright © 2026 Erik Pförtner.
