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

State as of 2026-08-18: build green across ten modules, 38 tests, and the JavaDoc gate active
everywhere. The scaffold is complete and three feature areas are implemented.

Authentication: registration, login, token rotation, logout, the weekly cleanup of invalidated
refresh tokens, the client cookie encryption parameters, and the login and registration views bound
to the generated OpenAPI client.

Workspace: the notebook, source document and chat session tables with their CBOR payloads, notebook
management over `/api/v1/notebooks` scoped to the account of the presented access token, and the
dashboard that lists, creates, renames, pins and removes them.

Ingestion: uploads and web addresses added below `/api/v1/notebooks/{id}/sources`, stored and
answered immediately, then parsed with Apache Tika or jsoup, cut into overlapping segments and
embedded in process into a shared vector store where every segment carries its notebook and its
source. The Sumbook view shows the sources with the stage each has reached, the conversation that
will be held about them, and the studio that will be generated from them. Chat sessions still exist
as storage only, and nothing answers a question yet.
