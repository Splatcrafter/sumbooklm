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
| [06-deployment.md](06-deployment.md) | Container image, Compose stack and the settings a deployment reads |

State as of 2026-08-20: build green across ten modules, 617 tests, and the JavaDoc gate active
everywhere. The scaffold is complete and five feature areas are implemented. Of those tests, 69 drive
the application end to end from the application module and 548 are unit tests below it, written for the
states a running deployment reaches rarely: races, refusals, permits that have to be given back, and the
constants that are contracts with stored rows or with a generated client.

Authentication: registration, login, token rotation, logout, the weekly cleanup of invalidated
refresh tokens, the client cookie encryption parameters, and the login and registration views bound
to the generated OpenAPI client.

Workspace: the notebook, source document and chat session tables with their CBOR payloads, notebook
management over `/api/v1/notebooks` scoped to the account of the presented access token, and the
dashboard that lists, creates, renames, pins and removes them.

Ingestion: uploads and web addresses added below `/api/v1/notebooks/{id}/sources`, stored and
answered immediately, then parsed with Apache Tika, or fetched through an address rule that decides at
the moment of connecting and parsed with jsoup, cut into overlapping segments and
embedded in process into a shared vector store where every segment carries its notebook and its
source. The text a source was read as is stored with it, so indexing it again needs neither the parser
nor the network, and every source is indexed again once the application starts, because the vector
store does not survive the process while the sources do. The Sumbook view shows the sources with the
stage each has reached, which of seven reasons stopped the ones that could not be read, when each page
was last read and a way to read it again, and the conversation held about them.

Chat: `POST /api/v1/notebooks/{id}/chat` answers as a stream of server sent events, from passages
retrieved under a metadata filter on that notebook alone, under instructions that permit no other
material and require a Markdown citation per statement. The model is not configured on the server:
the provider, the model name and the key travel in headers, are turned into a client for one answer
and are forgotten with the response, while the browser keeps them encrypted next to its session. The
transcript is stored in the CBOR payload of the chat session, the question before the model is asked
and the answer once the stream has finished. A notebook holds as many conversations as its user
starts, and an answer being written can be stopped, which keeps what had arrived. An account may have three answers being generated at
once, and a deployment that declares itself served over HTTPS refuses any API request that arrived
without it.

Summary: `GET /api/v1/notebooks/{id}/summary` returns the text a model wrote about the sources of a
Sumbook, and `POST` has one written, from the stored text of every source that was read rather than from
what a question retrieves. The material is shared out over a budget so that no source disappears, the
text is stored in the notebook payload together with a fingerprint of the sources it describes, and a
summary whose sources have changed says so instead of being rewritten behind the reader. The first
summary of a Sumbook is written by itself once a model is configured; every one after that is asked for,
because the key is the reader's. The language it is written in is the language the interface is being
read in, which is switched from the frame of every screen, signed in or not.
