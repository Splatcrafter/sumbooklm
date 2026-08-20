# Deployment

The container deployment: one image holding the application, one Compose stack adding a database,
and a workflow that publishes the image so a server pulls instead of builds.

| File | Role |
| --- | --- |
| `Dockerfile` | Two stage build, Maven and Node in the first stage, a JRE in the second |
| `.dockerignore` | Keeps the build context to what the build reads |
| `docker-compose.yml` | The application and PostgreSQL, deployable as a Portainer stack |
| `.env.example` | Every setting the stack reads, with the reasoning for the ones that have a choice |
| `.github/workflows/docker-publish.yml` | Builds and pushes to `ghcr.io/<owner>/sumbooklm`, started by hand |

## Why the image is built the way it is

**Noble on both stages, not Alpine.** The Maven build downloads a Node toolchain, and the embedding
model ships an ONNX runtime with native libraries. Both are linked against glibc, so a musl base
would fail — the frontend stage at build time, the model at the first embedding.

**The artifact is taken apart before it is copied.** The packaged jar is about 320 MB, almost all of
it the embedding model and its runtime. `java -Djarmode=tools -jar ... extract --layers --launcher`
splits it along the boundaries the Spring Boot build recorded, and the four directories are copied in
four `COPY` instructions ordered least to most likely to change. Measured on the current tree:

| Layer | Size |
| --- | --- |
| `dependencies` | 308 MB |
| `spring-boot-loader` | 676 KB |
| `application` | 972 KB |
| `snapshot-dependencies` | empty |

A change to the source therefore rewrites about a megabyte rather than 320. The internal module jars
land in `application` rather than in `snapshot-dependencies`, which is why that layer is empty despite
the version being a snapshot.

The entry point is `java org.springframework.boot.loader.launch.JarLauncher` with the extracted tree
as the working directory, which is the launcher form of the extraction. Verified: the application
starts from that layout in ten seconds and answers `GET /` with 200.

**Maven and npm caches are BuildKit cache mounts.** `/root/.m2` and `/root/.npm`, so that a rebuild
on the same host does not resolve the dependency tree and download a Node toolchain again. This makes
BuildKit a requirement of the build, which is the default builder everywhere the stack would run.

**curl is installed rather than assumed.** The health check runs through it, and installing it
explicitly means a later base image cannot drop it without the build saying so.

## Two decisions the deployment had to make for the application

**The health check asks for `/`, not for an endpoint below `/api`.** No actuator is on the class
path, so there is no `/actuator/health` to ask. Under the production profile `SecureTransportFilter`
refuses everything below `/api/` that did not arrive over HTTPS, and the connection from inside the
container is always plain — so any API path would answer 426 forever. `/` falls under `anyRequest()
.permitAll()` and serves the SPA index, which is a truthful signal that the context came up.

`start_period` is 180 seconds because every source is embedded again at startup, so a deployment with
content in it takes far longer to reach a served page than an empty one.

**`SPRING_JPA_HIBERNATE_DDL_AUTO` is set to `update`, overriding the `validate` of the production
profile.** There is no Flyway and no Liquibase on the class path and no SQL in the tree, so `validate`
against a fresh database fails at startup and the container crash loops — a first deploy could never
come up. The override is an environment variable rather than an edit to `application-prod.yml`: the
file keeps stating the stricter intent, and `.env.example` documents moving back to `validate` once
the schema is settled. Replacing this with real migrations is the proper fix and is not done here.

## The Compose file carries a version key

`version: "3.9"` stands at the top although the Compose Spec declared the key obsolete and the
`docker compose` binary ignores it with a warning. Portainer is what reads this file, and it does not
read one without it. The Compose Spec key `name` was dropped in the same move rather than kept beside
it: it is not part of the 3.x schema, and Portainer names the stack itself.

The long form of `depends_on` and the `logging` block are kept as they are. Compose v2 loads every
file through the Spec loader whatever the version says, so the key changes what Portainer accepts and
nothing about how the stack runs.

## Publishing is started by hand

The workflow has `workflow_dispatch` as its only trigger. Publishing an image is what puts a version
in front of whoever runs the stack, which is a moment somebody picks rather than one that follows
from a commit landing on `master`. The tags are derived from the ref the run was started on, so
publishing a release means starting the workflow on that tag.

The consequence to know: `ghcr.io/<owner>/sumbooklm:latest` moves only when somebody moves it, so a
stack pinned to `latest` and a `master` that has moved on are not the same thing.

## Settings that carry risk

* `SUMBOOKLM_JWT_SECRET` and `SUMBOOKLM_COOKIE_SECRET` have no default in the production profile and
  the application refuses to start without them. Compose declares both with `:?`, so the stack fails
  to deploy with a named error rather than starting into a startup failure.
* `POSTGRES_PASSWORD` is written into the cluster when the volume is first created. Changing it later
  changes the value the application sends and not the password of the role, and the stack stops
  connecting.
* `SUMBOOKLM_REQUIRE_HTTPS` defaults to on and depends on the reverse proxy setting
  `X-Forwarded-Proto`; the production profile sets `server.forward-headers-strategy: framework` so
  that the header is what `request.isSecure()` reads. Reaching the deployment directly over plain
  HTTP means turning it off, and access tokens then travel in the clear.

## Not covered

Nothing is written to the container file system: source content, payloads and chat sessions are all
columns, so the database volume is the whole of the state. The vector store is in process and is
rebuilt at every start by design, which is why it needs no volume and why startup is slow.
