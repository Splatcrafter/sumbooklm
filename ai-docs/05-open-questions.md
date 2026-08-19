# Open Questions

Decisions deliberately left open by the scaffold. Each is a real fork, not an oversight.

## 1. How production gets its DDL

ADR-005 removes Flyway, and `application-prod.yml` uses `ddl-auto: validate`, which means a
production database must already have the tables. The options:

* Generate the DDL once from the JPA model and apply it as part of provisioning. Fits the design:
  the relational schema is tiny and near static, so this is a rare event.
* Use `ddl-auto: update` in production too. Simplest, but it hands schema authority to Hibernate at
  runtime, which is normally judged an anti-pattern.
* Use Flyway strictly for structural DDL while payload evolution stays with Aether Datafixers. This
  is the escape hatch the brief keeps for emergencies.

The scaffold takes the conservative position (`validate`) so the choice stays explicit.

## 2. How the bring-your-own-key flow is shaped

Production is BYOK, which means the key arrives with the request rather than from configuration.
That rules out the eagerly constructed `ChatModel` bean the LangChain4j starters would otherwise
provide, and calls for a per-request model factory plus a decision about whether keys are ever
persisted, held for a session, or discarded after each call. In developer mode the same abstraction
has to offer the local inference server as a second option. None of this is wired yet; the starters
are on the classpath and no model properties are set.

## 3. Which fields get promoted out of the CBOR payload

ADR-002 says anything queried must become a real column. The concrete list depends on the first
real queries: notebook listing, source listing, and full text or vector search over chunks. Getting
this list wrong is the main way the blob design goes bad, so it deserves an explicit pass before the
first entity is written.

## 4. Vector store persistence

`InMemoryEmbeddingStore` loses everything on restart. Re-embedding on startup is now what happens
(question 19), which makes the loss survivable rather than absent: the vectors are still recomputed
every time the process starts, and the cost of that grows with the library.

The successor is pgvector via `langchain4j-pgvector`, which keeps the `EmbeddingStore` abstraction
intact. What the rebuild changed about that move is that it is no longer urgent, and that the code it
would replace already knows how to reconcile a store against the database.

## 5. TypeScript 7

Blocked by `openapi-typescript`'s `^5.x` peer range, not by anything in this project. Worth
re-checking periodically.

## 6. Aether Datafixers on Spring Boot 4

Verified working, not certified. The starter is compiled against Boot 3.5.10. Re-verify the context
start whenever either the starter or Spring Boot is upgraded, and watch for a Boot 4 targeted
release of the starter.

## 7. No linting or formatting toolchain yet

Neither ESLint/Prettier on the frontend nor Checkstyle/Spotless on the backend is configured. For an
assessment the conventions in the brief are strict enough that automated enforcement is probably
worth adding before the codebase grows.

## 8. Resolved: the JavaDoc gate is active everywhere

`sumbooklm-ingestion` and `sumbooklm-ai` set `maven.javadoc.skip=true` because Javadoc cannot
process a source set without a type (finding 12). Both gained their first classes with the ingestion
pipeline, and both properties are gone, as `sumbooklm-domain`'s was when it gained the user account
types. No module skips the gate any more, which is what this question was waiting for.

## 9. Ending a session on every device

Logout closes the session the presented access token belongs to. There is no endpoint that closes
all sessions of an account, although the repository already has the query for it
(`revokeAllOfUser`, used by reuse detection). The open part is not the mechanism but the product
decision: whether a password change should end other sessions implicitly, and whether a user should
see a list of their open sessions.

## 10. The encrypted client cookie is close to a practical size limit

Measured against the running application, the encrypted cookie holding one token pair and the
account is around 1.7 kB, against a per-cookie limit of about 4 kB. Both tokens are JWTs, so the
size grows with every claim added. The headroom is real but not generous, and two changes would
consume it: adding roles or scopes to the access token, or storing more than the current account in
the client session. Opaque refresh tokens (see the alternative in ADR-013) would roughly halve it.

## 11. Rotating the two secrets

`sumbooklm.security.jwt.secret` and `sumbooklm.security.cookie.secret` have no rotation story.
Rotating the JWT secret invalidates every issued token at once, and rotating the cookie secret
invalidates every stored client cookie, which the client handles by discarding the cookie and asking
for a login. Both are acceptable as an emergency measure and neither is acceptable as routine
maintenance. Supporting overlap means accepting a set of keys with a key identifier in the header
rather than one key.

## 12. Argon2id instead of bcrypt

The delegating password encoder currently produces bcrypt hashes, which is its default and which
needs no additional dependency. Spring Security's `Argon2PasswordEncoder` requires Bouncy Castle on
the classpath. Because the algorithm travels as a prefix in the stored hash, moving to Argon2id is a
configuration change plus a dependency, and existing accounts migrate on their next login through
`upgradeEncoding`. The question is whether the dependency is wanted, not whether the migration
works.

## 13. Nothing limits the rate of authentication attempts

Neither the login nor the refresh endpoint is throttled. The password hash is deliberately expensive
and the failing path costs the same as the succeeding one, which raises the cost of guessing, but
that is not a substitute for a limit. Where the limit belongs depends on the deployment: a reverse
proxy in front of the application, or a bucket per username and per address inside it.

## 14. Unknown paths below the API prefix answer 401, not 404

The filter chain requires authentication for everything below `/api` that is not explicitly opened,
so an unauthenticated request to a path that does not exist is rejected before routing. That is
deliberate as long as the API is small, because it does not reveal which endpoints exist. It becomes
confusing once authenticated clients call unknown paths, which will read as an authorization problem
in their logs rather than a routing mistake.

## 15. Resolved: a notebook card opens its Sumbook

The card now navigates to `/dashboard/sumbook/{id}`, which shows the sources, the conversation and
the studio. What remains open from the original question is smaller: the card is a `div` with
`role="link"` rather than an anchor, so it cannot be opened in a new tab. Wrapping the card in an
anchor would nest the menu button inside a link, which is invalid; the alternative is an invisible
anchor covering the card with the menu raised above it.

## 16. Resolved: the document hash is a column

The hash moved out of the payload into `document_hash`, with a unique constraint over the notebook and
the hash. Duplicate detection is a query now, and detection across a wider scope is a different query
rather than a different design.

What the move also settled is the rule for the payload, which had only been stated: a field that is
asked about belongs in a column, a field that is displayed belongs in the payload. The hash was the
one field that was never displayed, and it is the one that left.

## 17. The frontend has no test runner

The account screens and the dashboard were both verified by rendering them through Vite's SSR loader
and asserting on the produced markup, with the scripts kept outside the repository. That catches
missing translation keys, wrong plural forms and structural regressions, but it is not part of
`npm run build` and nothing runs it in a pipeline. Adding Vitest plus Testing Library would turn those
scripts into real tests; it also adds a toolchain the project does not otherwise have.

## 18. Opening a Sumbook does not count as activity

The timestamp the overview is ordered by is refreshed when a notebook is created, renamed, and now
when a source is added or removed. Opening one still does not refresh it, because the endpoint that
reads a notebook is a `GET` and a `GET` that writes is a `GET` that cannot be cached, retried or
prefetched. The section titled "recently opened" therefore orders by "recently changed".

The fix is an explicit endpoint that records the visit, called by the detail view when it mounts.
That is worth doing once there is something to open that is worth returning to; today the two orders
differ only for a Sumbook that is read and never touched.

## 19. Resolved: the index is rebuilt when the application starts

A source can now be indexed again, and every source is, once the application is ready. What was
missing was not persistence of the vectors but the ability to produce them a second time, which is
what both halves of the original question asked for.

Two things made it cheap enough to do unconditionally. The text a successful run extracted is stored
with the source, so rebuilding needs neither the parser nor the network and only pays for the
embeddings. And indexing replaces rather than appends, so running it again is safe whatever state the
store was in.

The consequence is that a restart retries exactly the sources that never succeeded, because those are
the only ones without stored text. A source that stayed in `INDEXING` because its run was interrupted
is picked up the same way.

What stays open is the cost. Every restart recomputes every embedding, which is seconds for a small
library and minutes for a large one, and a source is not answerable until the rebuild has reached it.
That is question 4's problem now rather than this one's: a store that survives the process removes the
rebuild instead of speeding it up, and the code that reconciles a store against the database is now
in place for it.

## 20. Resolved: the address guard decides at the moment of connecting

Both gaps came from the same thing: the rule was applied next to the request instead of inside it.
The check now lives in the resolver the HTTP client connects through, so the addresses that are judged
are the addresses that are used, and there is no second resolution to differ from the first.

Redirects needed no separate treatment after that. Each hop is a connection and therefore passes the
same resolver, which is why the client may follow them again. The recorded plan was to drive redirects
by hand; moving the check one layer down made that unnecessary rather than merely easier.

What replaced the two gaps is one that no resolver can close, recorded as question 31.

## 21. Resolved: a failed source reports which of seven things went wrong

`DocumentFailure` is stored next to the stage and returned with the source, and the interface turns it
into a sentence in the language of its user. The set is closed, as the question asked for, and the
constants are drawn along the lines that change what the user does next: a refused address and an
unreachable one are two of them, while an unsupported format and a damaged document are one.

The extractor that raised the failure chooses the constant rather than something later deriving it,
because only the extractor knows what actually happened. The message it also carries stays in the log,
where the host names and file paths in it are not a problem.

What is not covered is a failure that never reaches an extractor, which is recorded as `UNEXPECTED`
and tells the user nothing beyond the fact that it happened. That is the correct answer for a defect
in this application, and it is meant to stay rare rather than to become informative.

## 22. Resolved: duplicate detection is one query

Adding a source asks the database whether the notebook already holds that hash, which is an index
lookup rather than a scan that decodes every payload.

The change went one step further than the question asked for. A check followed by an insert is two
statements, so two uploads of the same file can both pass the check before either has written; the
unique constraint over the notebook and the hash is what makes the rule hold in that case, and the
service turns the violation into the same conflict the check produces.

Detection is still scoped to one notebook rather than to an account. That is what the product means:
the same document in two notebooks is two sources.

## 23. Resolved: the index forgets after the commit

Deleting a source or a notebook now publishes an event, and the segments are removed once that
transaction has committed, through the same mechanism indexing already used.

That does not remove the mismatch, it chooses which side of it to be on. Before, a rollback could
leave a source that exists and cannot be retrieved from, which reports success while answering
nothing. Now what can be left behind is a segment whose source is gone, and one of those is already
ignored when an answer is assembled, because a passage whose source the notebook does not list is
dropped.

The leftover is not collected by anything, which is recorded as question 33.

## 24. Resolved: the API refuses to be reached without TLS

A deployment now declares whether it is served over HTTPS, and one that is refuses every API request
that arrived without it. The refusal is a status rather than a redirect, because a redirect is written
after the secret has already crossed the network.

The rule covers everything below the API prefix rather than the chat endpoint alone. A key is a
credential and so is an access token, and the token opens a session that lasts ninety days.

The other half of the question is closed differently: the application does see the key, and the only
way it would not is for the browser to call the provider directly, which removes the retrieval that
made the question worth asking. What it does instead is make sure the key cannot leak on its way
through. It is never stored, never logged, and the record that carries it prints itself with the key
replaced by a marker, so a selection that ever reached a log line or the message of an exception takes
nothing with it.

## 25. Resolved: an account may have three answers being generated at once

A permit is taken before the question is stored and returned when the answer ends, and an account
beyond its permits is refused with `429` without anything being written. One account can therefore no
longer fill the pool.

Writing the test for it turned up two defects that had nothing to do with the limit and everything to
do with what the limit now permits. Two questions in one notebook at the same time failed, because
both refreshed its activity timestamp through a versioned entity. And two answers arriving at once
lost one of them, because a transcript is appended to by decoding, adding and encoding. Both are
fixed, and both were invisible until something asked several things of one notebook at once.

What remains is recorded as questions 34 and 35: the count is per instance, and a bound on answers in
flight is not a bound on questions over time.

## 26. Resolved: an answer can be stopped, and what arrived is kept

The composer offers a stop while an answer is being written. The stop ends the answer for the reader,
keeps what had arrived, stores it as the answer and gives the account its permit back.

The two behaviours the question asked about are now separate on purpose. Leaving a Sumbook still lets
the answer finish and be stored, because it was already paid for and will be there on the next visit.
Pressing stop is the deliberate act, and only that one ends it.

The half that could not be done is the half the question cared most about. The cancellation handle
cancels by closing the body of the response, and the HTTP stack drains a chunked body before it closes
it, so calling it waits for the provider rather than stopping it; from the thread handling the stop it
hangs that request outright. What is left is question 36.

## 27. Resolved: a notebook holds as many conversations as its user starts

Conversations are listed, started and removed below `/notebooks/{id}/chats`, and a question names the
one it continues. The schema needed nothing: the row already had an identifier, its own timestamps and
a title in its payload, and what changed is that the service stopped using exactly one of them.

The interface question is answered by not adding a column. The conversations are reached through a
menu above the transcript, because a fourth column would take width from the one panel whose content
needs it, and a reader works in one conversation at a time.

What is left over is that listing them decodes every transcript in order to count its messages, which
is question 37.

## 28. Resolved: the request is bounded by characters as well

The engine keeps the whole prompt under a character budget and shortens the conversation from the
oldest message forwards until it fits. The instructions, the passages and the question are sent whole,
because they are what the answer has to be based on.

Characters rather than tokens, as the question expected: counting tokens needs the tokenizer of the
model the request goes to, and this application does not learn which model that is until the request
is made. The budget is low enough that the approximation being wrong by a factor of two still leaves
room.

The count bound stayed. Ten messages is how far back a conversation is still relevant, and the budget
is what fits; the two answer different questions. What the budget does not handle is question 38.

## 29. Resolved: a source is read again when a user asks

`POST /sources/{id}/refresh` reads a source again from where it came from, and the interface offers it
on every web page and on anything that failed. A source now carries the moment it was last read, which
is what tells a reader how old the material behind an answer is.

Both halves of the decision the question left open were made deliberately. Nothing reads on a
schedule: that would fetch pages nobody is looking at and change what a Sumbook says without anyone
asking. And the answers that cited an earlier version are left alone, because a transcript is a record
of what was said, and rewriting it would make the conversation claim something it never did.

A reading that fails leaves the previous text in place, so a page that has gone away keeps answering
with what it last said rather than becoming an empty source. What is left over is question 39.

## 30. The rebuild has no bound and no back pressure

The job walks every source in the database, one after another, and nothing stops it. With a large
library that is a long run on the indexing pool, during which every upload queues behind it, and there
is no way to see how far it has come other than the two lines it logs.

Chunking it by notebook and rebuilding the ones being opened first would fix the part the user
notices, which is that their own Sumbook is not answerable yet. That needs a priority the current
executor does not have.

## 31. Resolved: a deployment may name the hosts sources come from

`sumbooklm.ingestion.web.allowed-hosts` is empty by default, which is what was there before: any host,
provided the address it leads to is a public one. A deployment that cannot accept the gap the question
described fills the list, and every host it did not name is then refused before it is even resolved.

The list narrows and never widens. A named host is still judged by its address, so naming an internal
wiki does not make it reachable, and each of the two rules can only take away what the other allowed.
That is what keeps them readable together, and it costs nothing that was reachable before.

Empty by default is the point of the answer rather than a detail of it. Filling the list turns adding a
source from something a user decides into something an operator has permitted, which is right where the
two are different people and wrong for a notebook somebody runs for themselves. The application does not
choose between those; the deployment does.

The rule sits in the resolver every connection passes, where question 20 put the rule about addresses, so
a redirect to a host nobody named is refused by the code that refused the first hop. What an entry cannot
express is a site rather than a host, which is question 40.

## 32. Resolved: the unexpected cause stays uninformative and becomes countable

No constant was added, because the question was right about what would make it better. What the user
reads is unchanged, and it is unchanged on purpose: the application genuinely does not know what
happened, which is exactly what distinguishes this cause from the seven an extractor names, and the only
alternative on offer is a stack trace in a sentence.

What changed is the report. A run that reaches it logs at error level, with the trace, the notebook, and
how many times this instance has recorded one since it started. One line answers whether it happened, and
the count answers whether it is happening: the twentieth occurrence since a start is a defect report
rather than an incident. Error level rather than warning, because everything an extractor names is an
outcome that was handled, and this is the only one that means the application is wrong.

What was deliberately not done is adding a metrics facility for one number. That is question 41.

## 33. Resolved: an hourly pass keeps only the sources that exist

`OrphanSegmentCollector` asks the index to remove every segment whose source the database no longer
holds. A schedule is right here for the reason it was wrong for reading pages: the run reaches nothing
outside the process and can only remove what no source claims, so the worst it ever does is nothing.

Not at startup, which is where the question expected it. The store is empty after a start, so a rebuild
cannot find an orphan at all; they accumulate while the application runs, and that is when they are
looked for. The rebuild was left as it is for the reason the question gave.

The ordering is the whole of it. The pass deletes by what it does not recognise, so a source stored
between it reading the list and it deleting would be deleted although it exists. Writing therefore takes
a shared lock and the pass an exclusive one, taken before it reads the list: a segment that is in the
store was written by a run that finished before the lock was granted, and that run had a committed row
behind it, so the list contains it. That is why the index asks for the sources rather than being handed
them; a caller that read the list itself would be reading it too early and nothing could tell. What it
costs is question 42.

## 34. Resolved: the bound that has to be shared is a second bound

The permits stay where they were, and for the reason the question gave: what they protect is the thread
pool of the instance that took the request, so a count in that instance is the correct shape and two
instances protecting their own pools is not a mistake.

What was missing is the other kind of bound, and it was added rather than moved. The questions of an
account are counted over the last hour in a table every instance writes to, so the count is shared
because the database is. Nothing new had to be operated for it.

The two now say what they are for. One is a concurrency about this process, the other is a rate about
the installation, and neither pretends to be the other. What the rate still does not know is what a
question costs, which is question 43.

## 35. Resolved: an account may ask sixty times an hour

`sumbooklm.chat.questions-per-hour` bounds how often one account may ask, counted over a window that
moves rather than one that resets, and the default is high enough that a person reading their sources
will not meet it. A refusal is a `429` carrying `Retry-After`, so a client learns that this one lasts
rather than passing in a moment.

The question said such a bound belongs in front of the whole API rather than in the chat service. It is
in the chat service, and the reason is that only one endpoint has anything behind it worth bounding: a
question embeds text, retrieves, writes a transcript and holds a connection open, while the rest of the
API reads rows. A bound in front of everything would have to be told all of that anyway, and it would
still be the wrong place to know how long an account has to wait.

What a bound in front of the whole API is still needed for is question 13, which is about attempts to
log in rather than about questions, and remains open.

## 36. Resolved: a stop abandons the request

Stopping now closes the body of the response, which abandons the exchange: the socket is closed and the
provider is writing to nobody. The handle that does it belongs to the response rather than to the client,
so it is handed to the cancellation with the first part of the answer, and a stop that arrives before
that is applied by the thread that hands it over.

The reason it could not be done before was not the handle but the client underneath it. The one the
framework detects here wraps Apache HttpClient, which is on the class path because reading web sources
needs it, and whose close first reads the remainder of the message. Cancelling therefore held the stop
request open for as long as the answer took. Both models are now built with the JDK client instead,
whose close abandons rather than drains, and the flag stays as what the reading thread notices.

How far that reaches is the provider's business. Closing the connection is the whole of what a client
can do; whether the generation stops and what is charged for what was generated differs between
providers and is question 45.

## 37. Listing conversations decodes every transcript

A list of conversations shows how many messages each holds, and the count is in the payload, so
answering it decodes every transcript of the notebook. That is the same shape as the content hash
before it became a column, and the same answer would apply.

It is cheap for the number of conversations a person keeps, and it stops being cheap at the point
where somebody keeps hundreds. What makes it worth leaving is that the count is the only thing being
decoded for, and dropping it from the list would remove the problem without a schema change.

## 38. One long message costs the conversation its context

The conversation is sent as a run of its most recent messages, so a single message that does not fit
the budget takes everything before it as well. A model that answers at great length therefore costs
the next question the context it would have referred to.

Truncating that message instead would keep the run, at the price of sending half a sentence and
letting the model treat it as the whole of what was said. Summarising it is the real answer and is a
second request to a model the user pays for, which is a feature rather than a fix.

## 39. Nothing notices that a page has changed

A page is read when it is added and when a user asks for it again. Nothing compares it against what it
said before, so a Sumbook can answer from a version that no longer exists and only the date on the
source hints at it.

Noticing needs either a schedule, which was deliberately not taken, or a conditional request per page
using the validators a server offers. The second is cheap for the server being asked and would turn
the date into something the application could keep current on its own, at the cost of storing what the
server said last time.

## 40. A permitted host is a name, not a site

An entry in the allow list is compared against the whole host, so a site served from several names has to
be named several times, and permitting a name does not permit anything below it. That is deliberate: a
permission that spread to every subdomain would spread to whatever anyone is able to have published
there.

The cost is carried by whoever maintains the list. A page that redirects to a name nobody thought of
fails, and it fails as an address that may not be retrieved, which is the same sentence a user gets for
an address that points into the network of the server. Telling the two apart is possible and was not
done, because the difference is about this deployment rather than about their source.

## 41. The rate of unexpected failures is a log line

The count of unexpected failures lives in the heap of one instance and starts again at every restart, so
it says how often this process has been there and nothing about the deployment. Reading it as a rate
means reading the log, which is what most operators do and which no dependency was added for.

Making it a measurement means a metrics facility. That is a small dependency and not a small decision:
the endpoint would sit behind the same authentication that serves notebooks, where every account that can
register would be able to read it, and an operator-only credential is a second authentication scheme for
one number. Worth revisiting when anything else in the application needs to be measured.

## 42. The collection pass holds off indexing while it reads the database

The pass takes the lock that writing shares, then reads every source identifier there is, then deletes.
Indexing therefore waits for one query per pass, and the list of identifiers is as large as the library.
Neither is bounded by anything but the size of the installation, which is the same shape as question 30
and has the same answer available: work through it a notebook at a time.

It is left as one pass because it runs hourly and holds the lock for a single statement, and because
splitting it by notebook would mean the pass no longer sees a segment whose notebook is gone as well.

## 43. The rate counts questions, not what they cost

A question that produces four thousand tokens and one that produces twenty count the same, so the bound
is a bound on how often somebody asks rather than on what asking costs. Where the keys belong to the
users that is the honest unit, because the cost is not this application's to count.

It stops being the right unit the moment an operator pays for anything behind the endpoint. What is then
wanted is a bound on spending, and the number that would feed it arrives after the answer, in what the
provider reports. Counting it means storing what each answer used and turning the bound from a decision
made before a question into one made from what the previous ones cost.

## 44. Nothing bounds the cheap endpoints

The bound is on questions, which is where the expensive work is. Listing notebooks, reading transcripts
and refreshing tokens are bounded by nothing at all, and a client that asks for them in a loop is
answered as fast as the database can.

That is deliberate for now: each of those is a query, and the first thing to run out would be the
connection pool rather than a provider or a bill. A bound in front of the whole API is the answer, and
it is the same answer question 13 has been waiting for, so the two belong together.

## 45. A stopped answer may still cost what it already generated

Closing the connection is how far a client can reach. Whether the provider notices at once, whether it
stops generating, and what it charges for what it generated before it noticed are its own behaviour, and
they differ between providers.

Reporting that honestly to a reader would mean knowing what each provider does, which is a table this
application would have to keep current. What it says instead is what it knows: the answer stopped, and
what arrived is kept.

## 46. A summary is written in one language and read in three

A summary is written in the language the interface was in when it was requested and stored as that text.
Switching the interface afterwards changes every sentence on the screen except the one the model wrote,
which is then a German paragraph under Japanese headings.

Rewriting it on a switch would spend a request on an action that is otherwise free, and doing it silently
would spend it without being asked. Storing one summary per language would multiply the cost by the
number of languages a reader tries. What is there instead is the button that already exists for a summary
whose sources changed, which writes the text again in the language now being read.

## 47. Every readable source is loaded into the heap to be summarised

Writing a summary reads the stored text of every source of the notebook, and the shortening happens
afterwards. A notebook holding two hundred large documents therefore builds a list of two hundred texts in
order to send twenty thousand characters of them.

Shortening in the query is possible: the length is already read for the fingerprint, and a substring per
row would bound what is loaded. It is not done yet because the share each source gets depends on how much
the others need, so a query would have to be told a budget it cannot compute. The honest fix is to read
the lengths first and the texts second, which is two queries for a case nobody has hit.

## 48. A summary holds a thread of the web server

An answer is streamed and generated on the executor of the workspace module, so the request that started
it returns immediately. A summary is one response, so the request that asked for it waits for the
provider, up to the two minutes the client allows.

That is the simpler protocol and the reason for it stands, but it means a handful of readers summarising
large notebooks at once occupy threads that are otherwise never held for long. The bound on requests in
flight per account caps it at three per account, which is a cap on the abuse rather than on the load.

## 49. The fingerprint of the sources sees a length, not a text

A summary is recognised as out of date when a source is added, removed or read again with a text of
another length. A page whose new version has exactly the length of the old one is not noticed, and neither
is one whose text changed in a way that keeps the count.

Hashing the text would notice everything and would mean reading every text to answer a question that is
asked whenever a notebook is opened. A hash stored with the source at the moment it is indexed would fix
that, and it is the same column that question 39 would need in order to notice that a page changed at all.
