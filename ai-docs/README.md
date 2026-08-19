# AI Docs

Internal knowledge base for this repository. These notes exist so that later sessions can pick up
the reasoning behind the scaffold without re-deriving it.

**These files are an internal tool only. Production code must never reference this directory, and
none of the reasoning recorded here may leak into source comments or JavaDoc.**

| File | Content |
| --- | --- |
| [01-research-findings.md](01-research-findings.md) | Verified library versions and the surprises found while verifying them |
| [02-architecture-decisions.md](02-architecture-decisions.md) | Decision records for the choices that shape the codebase |
| [03-project-structure.md](03-project-structure.md) | Module graph, package layout, dependency rules |
| [04-build-and-run.md](04-build-and-run.md) | Build lifecycle, dev workflow, verification evidence |
| [05-open-questions.md](05-open-questions.md) | Deliberately deferred decisions and their trade-offs |

State as of 2026-08-19: build green across ten modules, 45 tests, and the JavaDoc gate active
everywhere. The scaffold is complete and four feature areas are implemented.

Authentication: registration, login, token rotation, logout, the weekly cleanup of invalidated
refresh tokens, the client cookie encryption parameters, and the login and registration views bound
to the generated OpenAPI client.

Workspace: the notebook, source document and chat session tables with their CBOR payloads, notebook
management over `/api/v1/notebooks` scoped to the account of the presented access token, and the
dashboard that lists, creates, renames, pins and removes them.

Ingestion: uploads and web addresses added below `/api/v1/notebooks/{id}/sources`, stored and
answered immediately, then parsed with Apache Tika or jsoup, cut into overlapping segments and
embedded in process into a shared vector store where every segment carries its notebook and its
source. The Sumbook view shows the sources with the stage each has reached, the conversation held
about them, and the studio that will be generated from them.

Chat: `POST /api/v1/notebooks/{id}/chat` answers as a stream of server sent events, from passages
retrieved under a metadata filter on that notebook alone, under instructions that permit no other
material and require a Markdown citation per statement. The model is not configured on the server:
the provider, the model name and the key travel in headers, are turned into a client for one answer
and are forgotten with the response, while the browser keeps them encrypted next to its session. The
transcript is stored in the CBOR payload of the chat session, the question before the model is asked
and the answer once the stream has finished.
