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

State as of 2026-08-18: build green across nine modules, 19 tests. The scaffold is complete and the
authentication module is implemented: registration, login, token rotation, logout, the weekly
cleanup of invalidated refresh tokens, the client cookie encryption parameters, and the login and
registration views bound to the generated OpenAPI client. The notebook side of the application does
not exist yet.
