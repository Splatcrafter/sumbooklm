# Research Findings

All versions below were verified against Maven Central metadata (`maven-metadata.xml`), the npm
registry, or by inspecting the published artifacts. Nothing here is from memory.

## Backend versions

| Artifact | Version | Note |
| --- | --- | --- |
| `org.springframework.boot:spring-boot-dependencies` | 4.1.0 | Latest GA (June 2026), Spring Framework 7.0.8 |
| `dev.langchain4j:langchain4j-bom` | 1.19.0 | Manages both the stable and the `-beta29` modules |
| `de.splatgames.aether.datafixers:aether-datafixers-bom` | 1.0.0-rc.1 | Latest published version |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 3.1.0 | The 3.x line targets Spring Boot 4 |
| `org.jsoup:jsoup` | 1.23.1 | |
| `com.github.eirslett:frontend-maven-plugin` | 2.0.2 | |
| JDK | 25 (Temurin 25.0.3) | Spring Boot 3.5.5+ and 4.x both support Java 25 |

## Frontend versions

| Package | Version |
| --- | --- |
| `vite` | 8.2.1 |
| `@vitejs/plugin-react` | 6.0.5 |
| `react` / `react-dom` | 19.2.8 |
| `react-router` | 8.3.0 |
| `typescript` | 5.9.3 (not 7.0.2, see below) |
| `tailwindcss` / `@tailwindcss/vite` | 4.3.3 |
| `@base-ui/react` | 1.7.0 |
| `shadcn` | 4.18.0 |
| `i18next` | 26.3.6 |
| `react-i18next` | 17.0.11 |
| `i18next-browser-languagedetector` | 8.2.1 |
| `lucide-react` | 1.32.0 |
| `react-markdown` | 10.1.0 |
| `remark-gfm` | 4.0.1 |
| `openapi-typescript` | 7.13.0 |
| `openapi-fetch` | 0.17.0 |
| Node (pinned in the Maven build) | v24.19.0 (Krypton LTS) |

## Findings that changed the plan

### 1. LangChain4j ships separate starters for Spring Boot 4

Artifacts named `langchain4j-*-spring-boot-starter` target Spring Boot 3. For Boot 4 the suffix is
`-spring-boot4-starter`. Both families share the BOM version, so a single `langchain4j-bom` import
resolves them. Used here: `langchain4j-spring-boot4-starter`,
`langchain4j-open-ai-spring-boot4-starter`, `langchain4j-ollama-spring-boot4-starter`.

### 2. There is no `langchain4j-in-memory` artifact

`InMemoryEmbeddingStore` was located by listing the jar contents of `dev.langchain4j:langchain4j`
1.19.0. It lives at `dev/langchain4j/store/embedding/inmemory/InMemoryEmbeddingStore.class` in the
aggregator module, not in `langchain4j-core` and not in a dedicated in-memory artifact. The AI
module therefore depends on `dev.langchain4j:langchain4j`.

### 3. Aether Datafixers has no CBOR `DynamicOps`, and does not need one

`aether-datafixers-codec` 1.0.0-rc.1 publishes `GsonOps`, `JacksonJsonOps`, `JacksonYamlOps`,
`JacksonXmlOps`, `JacksonTomlOps` and `SnakeYamlOps`. There is no CBOR variant.

Inspecting `JacksonJsonOps` shows why one is unnecessary:

```
public final class JacksonJsonOps implements DynamicOps<com.fasterxml.jackson.databind.JsonNode>
public JacksonJsonOps(com.fasterxml.jackson.databind.ObjectMapper)
```

The ops operate on `JsonNode`, which is Jackson's format independent tree, and the constructor takes
any `ObjectMapper`. `CBORMapper` is an `ObjectMapper`. The bridge is therefore
`new JacksonJsonOps(cborMapper)`: CBOR bytes decode into the same tree the fixers rewrite, and the
same mapper re-encodes them. CBOR is only the wire format; the intermediate representation is
identical to the JSON path.

### 4. The Aether Spring Boot starter is built against Boot 3.5.10 but runs on Boot 4.1.0

The starter POM declares `<spring-boot.version>3.5.10</spring-boot.version>` and its description says
"Spring Boot 3.x auto-configuration". Two things make it work on Boot 4 anyway, both verified:

* Boot 4.1 still ships `spring-boot-autoconfigure` with
  `org.springframework.boot.autoconfigure.condition.ConditionalOn*` and still reads
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, which is the
  only Spring contract the starter uses.
* `DynamicOpsAutoConfiguration$JacksonOpsConfiguration#jacksonOps` injects the Jackson 2
  `ObjectMapper` with `@Autowired(required = false)` and falls back to `JacksonJsonOps.INSTANCE`.
  Boot 4 publishes a Jackson 3 `ObjectMapper` bean, so the parameter simply resolves to `null` and
  the configuration still produces a usable bean.

Verified by an actual context start, not by reading alone. Note the risk: the starter is not
certified against Boot 4, so an upgrade of either side needs a re-check.

### 5. Spring Boot 4.1 manages Jackson 2 *and* Jackson 3

`spring-boot-dependencies` 4.1.0 imports both `com.fasterxml.jackson:jackson-bom` 2.21.4 and
`tools.jackson:jackson-bom` 3.1.4, and ships a `spring-boot-jackson2` module. Running the web layer
on Jackson 3 while the persistence payload codec uses Jackson 2 is a supported configuration, and
both versions stay centrally managed. No version property has to be maintained by hand.

### 6. TypeScript 7 exists but is not usable here yet

`typescript@7.0.2` is the current `latest` tag. `openapi-typescript@7.13.0` still declares
`peerDependencies: { typescript: "^5.x" }`, and npm refuses to install the combination. Since the
generated client is a hard requirement, TypeScript is pinned to 5.9.3. Re-check when
`openapi-typescript` widens its peer range.

### 7. Base UI moved package names

The package referenced by older material, `@base-ui-components/react`, is stuck at `1.0.0-rc.0`.
The maintained package is `@base-ui/react`, currently 1.7.0. shadcn's Base UI components target the
latter.

### 8. shadcn encodes base and preset in a single `style` field

The `components.json` schema enumerates `style` values of the form `<base>-<preset>` where base is
one of `radix`, `base`, `aria` and preset is one of `vega`, `nova`, `maia`, `lyra`, `mira`, `luma`,
`sera`, `rhea`. `shadcn init -h` reports the default as `--preset=base-nova`, but the `--preset`
flag itself only accepts the bare preset name; `--preset base-nova` is rejected. The working
invocation is `shadcn init --base base --preset nova`, which writes `"style": "base-nova"`.

The CLI also adds `shadcn` itself as a runtime dependency, because the generated `src/index.css`
does `@import "shadcn/tailwind.css"`. That is intended in v4 and not a mistake.

### 9. `org.springframework.lang.Nullable` is deprecated in Spring Framework 7

Framework 7 moved to JSpecify. `org.jspecify:jspecify` 1.0.0 arrives transitively through
`spring-core`, and `org.jspecify.annotations.Nullable` is the replacement. It is a `TYPE_USE`
annotation, so it belongs on the return type (`protected @Nullable Resource getResource(...)`).

### 10. Node floor is set by React Router, not by Vite

Vite 8.2.1 declares `node: ^20.19.0 || >=22.12.0`, but `react-router` 8.3.0 declares
`node: >=22.22.0`. The Maven build pins v24.19.0, the current Krypton LTS, which satisfies both.

### 11. The shadcn CLI reads `paths` from the root `tsconfig.json`

With TypeScript project references, `paths` normally lives in `tsconfig.app.json` and the root
`tsconfig.json` only carries `references`. The shadcn CLI reads the root file, finds no alias, and
writes components into a literal directory named `@` next to the project root instead of into
`src/`. Mirroring `baseUrl` and `paths` into the root `tsconfig.json` fixes it; the duplication is
inert for `tsc -b` because the root project compiles no files.

Verified afterwards: `npx shadcn@latest add button` created `src/components/ui/button.tsx`. The
component was removed again, because the scaffold carries no UI primitives yet.

### 12. Javadoc fails on a source set that contains only a `package-info.java`

`javadoc` exits with `error: No public or protected classes found to document.` when a module
carries a package declaration but no type. `-private` does not change this, because the problem is
the absence of types rather than their visibility.

This hit `sumbooklm-domain`, `sumbooklm-ingestion` and `sumbooklm-ai`, which were placeholders at
scaffold stage. They set `maven.javadoc.skip=true` in their own POM with a comment that ties the
property to the first type added to the module. `sumbooklm-frontend` needs no such property: the
plugin skips a module with no Java sources at all on its own, as does the packaging POM of the
parent.

Update with the authentication module: `sumbooklm-domain` gained its first types and the property
was removed from its POM, so the gate now runs there. `sumbooklm-ingestion` and `sumbooklm-ai` are
still placeholders and still skip; see open question 8.

### 13. Spring Boot 4 renamed the security starters

Boot 4 groups starters by module, the same rename that turned `spring-boot-starter-web` into
`spring-boot-starter-webmvc`. Both spellings are present in `spring-boot-dependencies` 4.1.0, but
the new family is the one to use:

| Boot 3 name | Boot 4 name |
| --- | --- |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |

`spring-boot-starter-security` kept its name. `spring-boot-starter-aop` does not exist in 4.1.0 at
all, so the aspect that enforces the sensitive operation marker needs `-aspectj`.

### 14. `TestRestTemplate` is gone in Spring Boot 4

`spring-boot-test` 4.1.0 contains no `org.springframework.boot.test.web.client` package. The
replacement in Framework 7 is `org.springframework.test.web.servlet.client.RestTestClient`, next to
the existing `WebTestClient` and `MockMvcTester`.

The integration test uses plain `RestClient` from `spring-web` instead, bound to the port from
`@LocalServerPort`. It gives explicit control over headers and cookies, which the test needs in
order to assert on `Set-Cookie` and to present a key handle by hand. Its default behaviour of
throwing on a failing status is switched off with
`defaultStatusHandler(status -> true, (request, response) -> { })`, because the failing statuses are
what most of the test asserts on.

### 15. The JDK 25 key derivation API is final, not preview

JEP 510 finalised `javax.crypto.KDF` in JDK 25. Verified by running the derivation on
Temurin 25.0.3 without `--enable-preview`:

```java
KDF.getInstance("HKDF-SHA256").deriveKey("AES", HKDFParameterSpec.ofExtract()
        .addIKM(masterSecret).addSalt(handle).thenExpand(context, 32));
```

That removes the reason to pull in a third party library or to hand roll HKDF over `Mac` for the
cookie key derivation of ADR-015.

### 16. The Aether starter finds the current version by reflection

`DataFixerAutoConfiguration` resolves the version a bootstrap writes in three steps: the property
`aether.datafixers.domains.<domain>.current-version`, then a field literally named
`CURRENT_VERSION` on the bootstrap class read via `Class#getField`, then
`aether.datafixers.default-current-version`. If none of them yields a value, the context fails to
start.

Consequences for `PayloadDataFixerBootstrap`: the field has to stay `public static final` and of
type `DataVersion`, and renaming it breaks the startup rather than the compilation. The starter also
only builds a fixer at all when a `DataFixerBootstrap` bean exists
(`@ConditionalOnBean(DataFixerBootstrap.class)`), and `MigrationService` only when a fixer exists.

Two further details that shaped `PayloadCodec`:

* `DataFixerImpl#update` returns the input unchanged when source and target version are equal, so a
  payload at the current version costs a comparison and no tree walking.
* `AetherDataFixer#encode` and `#decode` resolve the codec through the schema of a given version,
  which is why the schema registration is what makes the round trip work, not the migration.

### 17. Spring Security 7 removed the no-argument DSL methods

`HttpSecurity` in 7.1.0 only exposes the `Customizer` overloads; `http.csrf().disable()` no longer
compiles. Disabling now reads `csrf(csrf -> csrf.disable())`.

Two more shapes that were verified against the 7.1.0 jars rather than assumed:

* `NimbusJwtEncoder.withSecretKey(SecretKey)` and `NimbusJwtDecoder.withSecretKey(SecretKey)` both
  return builders, so no `JWKSource` has to be assembled by hand for the HMAC case.
* `DaoAuthenticationProvider` now takes its `UserDetailsService` through the constructor. Not used
  here, see ADR-017.

### 18. `openapi-typescript` marks every generated property optional

springdoc emits no `required` array for records unless every component is annotated, so the
generated `schema.d.ts` types every field as `string | undefined`. Rather than annotating every
component of every response, the frontend narrows once, in `src/auth/session.ts`, and raises a
`MalformedResponseError` when a field the client relies on is missing. The rest of the frontend then
works with non-optional types.

### 19. TypeScript 5.7 made `Uint8Array` generic over its buffer

`new Uint8Array(length)` infers `Uint8Array<ArrayBufferLike>`, which no longer satisfies
`BufferSource` in the Web Crypto signatures because `ArrayBufferLike` includes `SharedArrayBuffer`.
Allocating explicitly as `new Uint8Array(new ArrayBuffer(length))` gives `Uint8Array<ArrayBuffer>`
and compiles. This hits every byte array handed to `crypto.subtle`.

### 20. shadcn CLI additions are self-contained

`npx shadcn@4.18.0 add button input label field` wrote five files into `src/components/ui` and
touched nothing else: no `package.json` change, no `index.css` change, no reformatting of
`components.json`. The `field` component pulls in `separator` as a dependency, which is why five
files appear for four requested components.

### 21. `core.autocrlf=true` would break the shell scripts

The development container has `core.autocrlf=true` in its Git configuration, which converts every
file Git considers text to CRLF on checkout. For a `.sh` file that is fatal rather than cosmetic: the
shebang becomes `#!/usr/bin/env bash\r` and the kernel reports the interpreter as not found.

A `.gitattributes` pins the endings per type instead of leaving them to a per-user setting:

```
* text=auto

*.sh text eol=lf
*.bat text eol=crlf
*.cmd text eol=crlf
```

Repository content is already normalised to LF by the existing setting, so the file changes nothing
about what is stored; it only fixes what lands in a working tree, on any machine and independently of
how the person cloning has configured Git.

### 22. The JetBrains website grayscale, read from its own stylesheet

The greys of the JetBrains IDEs and the greys of the JetBrains website are two different systems, and
the website one is the one used here. It could not be found in written documentation, so it was read
out of `https://www.jetbrains.com/_assets/common.*.css` and `default-page.*.css`, where it is defined
under an `--rs-color-*` namespace:

| Token | Value |
| --- | --- |
| `--rs-color-black` | `#19191c` |
| `--rs-color-grey-95` | `#252528` |
| `--rs-color-grey-90` | `#303033` |
| `--rs-color-grey-80` | `#474749` |
| `--rs-color-grey-70` | `#5e5e60` |
| `--rs-color-grey-60` | `#757577` |
| `--rs-color-grey-50` | `#8c8c8e` |
| `--rs-color-grey-40` | `#a3a3a4` |
| `--rs-color-grey-30` | `#bababb` |
| `--rs-color-grey-20` | `#d1d1d2` |
| `--rs-color-grey-10` | `#e8e8e8` |
| `--rs-color-grey-5` | `#f4f4f4` |
| `--rs-color-white` | `#fff` |

Three properties of the ramp are worth keeping in mind. It is exactly linear: every step is 23 higher
per channel than the previous one, from 25 at the black end. The greys are neutral, and only the black
carries a slight blue cast, `rgb(25, 25, 28)`. And the site does most of its layering not with the
greys at all but with alpha steps over black and white, `--rs-color-black-t5` through `-t95` and the
same for white, which is why translucent surfaces rather than solid greys were used for the cards.

Two further values were taken from the same source: `--rs-card-border-radius: 16px`, which is the
rounding the cards use, and `--rs-color-danger: #f45c4a`, the single non-grey the site keeps for
failures and therefore the only colour in the form.

The website sets `JetBrains Sans` as its typeface. It is not used here, because it is not freely
licensed; the project keeps Geist, which is close in character.

### 23. Domain warping, and the one change that makes it read as layers

The background is built on Inigo Quilez's domain warping, verified against
`https://iquilezles.org/articles/warp/` rather than from memory. The second order form is:

```
q = vec2(fbm(p), fbm(p + vec2(5.2, 1.3)))
r = vec2(fbm(p + 4q + vec2(1.7, 9.2)), fbm(p + 4q + vec2(8.3, 2.8)))
pattern = fbm(p + 4r)
```

Applied to an isotropic domain this produces the familiar marbled clouds. The one change that turns
it into something else is squashing the sample domain on the vertical axis before evaluating, here by
a factor of about three: the features stretch horizontally and the marbling reads as sedimentary
layers. Time enters through the two warp vectors rather than by translating `p`, so the layers slide
against each other instead of the whole field drifting past.

Two implementation notes. The octave loop folds the domain with `mat2(1.6, 1.2, -1.2, 1.6)`, a
rotation combined with a scale of two, because summing octaves on the same axes leaves a visible grid.
And the contour lines use `fwidth` to derive their width from the local gradient, which keeps them one
pixel wide everywhere instead of dissolving where the field is flat; `fwidth` is core in GLSL ES 3.00
and needs an extension in 1.00, which is one of the reasons the renderer requires WebGL 2 rather than
falling back to WebGL 1.

### 24. What could not be verified in this environment

The container has no browser and no GPU, so the parts of the account screens that only exist at
runtime were verified as far as they can be and no further. This is recorded so a later session does
not mistake the build passing for the visuals being checked.

Verified:

* Both shaders parse, checked with `@shaderfrog/glsl-parser` after stripping the version pragma. The
  parser reports the built-in globals as unknown identifiers, which is expected of a parser without a
  symbol table for GLSL ES 3.00.
* The fallback SVG is well formed and every `url(#id)` reference resolves, checked with an XML parser.
  The data URI is about three kilobytes.
* Both account pages render without error through Vite's server side module loader with
  `renderToStaticMarkup`, which exercises the component tree, the router and the still image branch,
  since `window` is absent there and the capability check therefore chooses the fallback.
* The palette reaches the built stylesheet and the rounding utilities resolve to the theme variables.

Not verified: whether the shader compiles on a real driver, and what either background actually looks
like. The renderer is built so that a shader that fails to compile, a context that is refused and a
context that is lost all end in the still image rather than in a blank screen, which bounds the damage
of the first case but does not remove the need to look at it.

### 25. The background failure was design, not plumbing

The first version of the account background looked like grey concrete. The instinct was to suspect the
pipeline: a shader that failed to compile would fall back to the still image, and the still image was
also poor, so both paths would have looked wrong. That instinct was wrong, and the way it was settled
is worth keeping.

`glslang` was installed to `~/.local/bin` from the Khronos prebuilt release and both shaders validated
clean as OpenGL ES 3.00, exit code zero, no diagnostics. So the shader compiled and ran; what the
browser showed was what the shader computed.

The construction itself was the fault, in two ways:

* **Warp amplitude against domain size.** Domain warping multiplies the displacement by four. That is
  calibrated for a domain of several units. The shader mapped the whole viewport into roughly four
  units wide and then folded the domain by two per octave across five octaves, so the highest octave
  ran at pixel frequency and the displacement scrambled it. The result is crumpled marble. Warping a
  sine stack instead of warping the noise puts the structure back: the bands are the structure and the
  noise only bends them.
* **A hash that degrades at scale.** `fract(sin(dot(p, k)) * 43758.5453)` loses its usefulness once
  the folded coordinates reach the thousands, because the argument of the sine exhausts the mantissa
  and the pattern turns into grain. It was replaced with a bit mixing hash over the integer lattice,
  which GLSL ES 3.00 supports through `uint` arithmetic with defined wraparound. There is no scale at
  which it degrades.

### 26. Rendering the shader on the CPU is what made the design reviewable

The container has no browser and no GPU, so the visual result could not be looked at, and the first
attempt was therefore shipped unseen. The way out was to implement the field in Node, write the result
as a PNG with a small encoder over `zlib`, and read the image back. From there the design could be
judged and iterated: the marble was visible immediately, the switch to warped bands was visible
immediately, and the palettes could be compared side by side.

Two details made it useful rather than merely possible. Drawing a rectangle where the card sits, at
roughly the card's opacity, showed whether the form would stay readable instead of leaving it to be
guessed. And once the design was settled, the same renderer became the generator for the production
still image, so the throwaway harness turned into the fallback.

The remaining gap is that agreement between the GLSL and the CPU implementation rests on reading them
side by side. The named constants and the colour ramp were compared programmatically and match; the
control flow was checked by hand. The one trap found that way was the fold matrix: `mat2` takes its
arguments in column order, so writing the rotation as it reads on paper produces the mirror image of
the intended one.

### 27. PNG filtering matters more than resolution for this image

The generated still background is almost entirely smooth gradients. Written with filter type 0, no
filtering, it came to 428 kB at 800 by 450. Switching the encoder to Paeth filtering, where each byte
is stored as its difference to a predicted neighbour, halved it to 218 kB with identical pixels. That
is a larger saving than dropping the resolution would have given, and it costs no quality.

Verified by decoding the result with an independent implementation of the Paeth reconstruction rather
than by trusting the encoder: the file parses, reports 800 by 450 at eight bits with colour type 2,
and reconstructs to plausible pixel values.

The asset is only fetched by the devices that use it. The image element exists in the DOM solely when
the capability check chose the still background, so a desktop that runs the shader never downloads it.

### 28. Spring Boot 4 does not enable `-parameters` unless the build says so

This project does not inherit from `spring-boot-starter-parent`, and `maven-compiler-plugin` does not
keep formal parameter names on its own. Every Spring binding that carries no explicit name relies on
those names, so `@PathVariable final UUID notebookId` compiled cleanly, deployed cleanly and then
failed on the first request with `Name for argument of type [java.util.UUID] not specified`.

Two things were changed rather than one. `<parameters>true</parameters>` is now set in the parent, so
the class of failure disappears for every module. The path variables are additionally named in their
annotations, because a binding that depends on a compiler flag is a binding that breaks when someone
builds the module in another way.

### 29. A timestamp taken at nanosecond precision does not survive the round trip

`Instant.now()` on this JDK produces nanoseconds; the timestamp column keeps microseconds. The
creation endpoint therefore returned `...513260260Z` while every later read of the same notebook
returned `...513260Z`, so the response of a write disagreed with the response of the next read.

The service now truncates to microseconds before it stores anything, which is the precision the column
actually keeps. The alternative, re-reading the row after the write, would have hidden the mismatch
behind an extra query instead of removing it.

### 30. Aether Datafixers codecs for enums

`Codecs` publishes no enum codec. The stage of a source document is encoded through
`Codecs.STRING.comapFlatMap(name -> DataResult, DocumentStatus::name)`, which writes the constant by
name and turns an unknown name into a `DataResult` failure rather than an exception, so a payload
written by a future version fails the migration with a message instead of crashing the decoder.

Encoding by name rather than by ordinal is what keeps a constant inserted into the middle of the enum
from silently changing the meaning of stored rows.

### 31. Background work after a commit is `@Async` plus `@TransactionalEventListener`

Indexing must not start before the row describing the source is visible to other transactions. A
plain `@Async` call from the storing method races its own transaction and intermittently finds
nothing, which is the kind of failure that passes locally and fails under load.

Both annotations on one listener method solve it: the listener is invoked after the commit, and the
async interceptor moves it onto the ingestion executor. The listener then calls back into the service
for the short state transitions, because a `@Transactional` method invoked on `this` bypasses the
proxy and would silently run without a transaction.

One consequence worth remembering: `@TransactionalEventListener` does not fire at all when the
publisher runs outside a transaction. That is correct here, since the only publisher is a
`@Transactional` method, but it makes a plain unit test of the publisher look as if nothing happened.

### 32. The embedding model reports its own token count

`AbstractInProcessEmbeddingModel.embedAll` returns `Response<List<Embedding>>` whose `TokenUsage`
carries the input token count, summed over the segments and with the two special tokens per segment
already subtracted. No separate tokenizer, and no estimate next to the model, is needed for the
`tokenCount` the payload stores.

`langchain4j-embeddings` also publishes `HuggingFaceTokenCountEstimator`, which was not needed. The
tokenizer it would want is present: `langchain4j-embeddings-all-minilm-l6-v2` ships both
`all-minilm-l6-v2.onnx` (90 MB) and `all-minilm-l6-v2-tokenizer.json` (712 kB) as classpath
resources, loaded by a static field on first use of the model class.

### 33. `ResponseEntityExceptionHandler` already maps an oversized upload

Adding an `@ExceptionHandler(MaxUploadSizeExceededException.class)` next to the inherited one does
not override it, it collides with it, and the context fails to build with
`Ambiguous @ExceptionHandler method mapped for`. The inherited mapping answers 413 already, so the
correct action was to delete the addition rather than to reorder it.

The failure is worth knowing because it appears at startup rather than at the request that would have
triggered either handler, so it is loud and immediate but points at `handlerExceptionResolver` rather
than at the class that caused it.

### 34. `MultipartBodyBuilder` pulls in Reactive Streams

Building a multipart request for `RestClient` with `MultipartBodyBuilder` fails with
`NoClassDefFoundError: org/reactivestreams/Publisher` in a module that carries no reactive stack. The
builder supports publisher parts, and merely loading the class resolves the type.

A plain `LinkedMultiValueMap<String, Object>` holding a `Resource` per part goes through
`FormHttpMessageConverter` instead and needs nothing extra. The resource has to override
`getFilename()`, because that is what becomes the submitted file name.

### 35. openapi-typescript describes a binary part as a string

An OpenAPI `format: binary` property is generated as `file: string`, so the generated body type of a
multipart operation cannot hold a `File`. openapi-fetch is still usable: the `bodySerializer` hook
receives the body and returns whatever should be sent, so the `File` travels through a value the
generated type calls a string and is put into a `FormData` by the serializer.

The alternative, generating `Blob` instead, needs a transform passed through the Node API of
openapi-typescript rather than the CLI, which would replace a one line cast with a build step.

### 36. jsoup `text()` destroys the structure the splitter cuts on

`Document.text()` returns the whole page as one line. The paragraph splitter cuts on two consecutive
newlines, so a page extracted that way has no boundary to cut on and is chopped at sentence level by
the fallback splitter instead.

Selecting the block elements that carry prose and joining them with a blank line preserves the
structure. Two details matter: the noise elements have to be removed first, or navigation ends up as
paragraphs, and a list item nested in another item is matched twice by the selector, which is why a
paragraph identical to the one before it is dropped.

### 37. The streaming interface is called `StreamingChatModel`

LangChain4j renamed `StreamingChatLanguageModel` to `StreamingChatModel` in 1.0.0, together with the
handler, which is now `StreamingChatResponseHandler` in `dev.langchain4j.model.chat.response`. Code
and articles written against 0.3x therefore do not compile against 1.19.0, and the old names are gone
rather than deprecated.

The handler has one method per kind of part (`onPartialResponse`, `onPartialThinking`,
`onPartialToolCall`) and exactly two endings, `onCompleteResponse` and `onError`. The complete
response carries the assembled `AiMessage`, so a caller does not have to concatenate the parts itself
in order to know what was said.

### 38. The retrieval pieces live in `langchain4j-core`, not in a RAG module

`ContentRetriever`, `EmbeddingStoreContentRetriever`, `Query` and `Content` are all in
`dev.langchain4j.rag` inside `langchain4j-core`. There is no separate `langchain4j-rag` artifact to
add, which is easy to miss because the documentation talks about a RAG module.

The builder of `EmbeddingStoreContentRetriever` takes either a fixed `filter(Filter)` or a
`dynamicFilter(Function<Query, Filter>)`. A retriever built per notebook only needs the fixed one, and
`InMemoryEmbeddingStore.search` applies it against the metadata of the segment.

### 39. `minScore` is a mapped cosine similarity, not a cosine

`RelevanceScore.fromCosineSimilarity` is `(cosine + 1) / 2`, so the zero to one scale the retriever
compares against is not the cosine it came from. A threshold that looks strict is permissive: `0.5`
accepts everything with a non-negative cosine, and a value around `0.65` is what actually rejects
unrelated text.

### 40. Ollama streams newline delimited JSON, not server sent events

`OllamaServerSentEventParser` reads the response body line by line and hands each line on as the data
of an event. The endpoint is `POST {baseUrl}/api/chat`, each line is an object with a `message` and a
`done` flag, and the final line carries `done: true` with the token counts. That is enough to stand a
fake provider up in a test harness: a plain HTTP server writing those lines is indistinguishable from
Ollama as far as the client is concerned.

### 41. `SseEmitter` accepts events before the container has initialised it

An `SseEmitter` returned from a controller method is only wired to the response after the method has
returned, and background work can start before that. `ResponseBodyEmitter` keeps early sends and
replays them once it is initialised, so a handler that starts writing immediately does not have to
synchronise with the request thread.

What it does not tolerate is being completed twice, or being written to after the reader has gone,
which is why the writer in this application closes exactly once and treats a failed write as the end
of the stream.

### 42. Aether Datafixers has a list codec

`Codecs.list(elementCodec)` and `Codec#listOf()` produce a codec for `List<A>`, which is what lets a
record codec hold a repeated nested record. `Codecs.LONG` plus `xmap` is enough for a timestamp; there
is no codec for `java.time` types, and encoding an instant as microseconds since the epoch keeps it a
single integer in the tree.

### 43. `@Lob String` is a large object on PostgreSQL

Hibernate maps a `@Lob String` to `oid` on PostgreSQL, which is a pointer into the large object table
rather than a value in the column, and reading one outside a transaction fails. The byte variant is
harmless because it becomes `bytea`, which is why the existing `content` column is fine and a text
column next to it would not have been.

`@Column(length = Length.LONG32)` avoids the question: the provider picks the widest text type it has,
which is `text` on PostgreSQL and `clob` on H2, and neither is a large object handle. `org.hibernate.
Length` is the only Hibernate specific import in the entity, and it contributes an integer rather than
a behaviour.

### 44. jsoup resolves the host inside the call that fetches

`Jsoup.connect(url).execute()` opens the connection itself, and nothing in its API sits between
resolving the name and connecting to what it resolved to. A check performed before the call is
therefore always a second resolution, whatever it checks.

Apache HttpClient 5 has the seam: `PoolingHttpClientConnectionManagerBuilder.setDnsResolver` replaces
the resolution the connection operator performs, so the addresses that are judged are the addresses
that are connected to. TLS is unaffected, because the client keeps the host name for the certificate
and for the server name indication and only takes the address from the resolver.

The resolver is also called for every hop of a redirect, since each hop is a new route. Letting the
client follow redirects is therefore safe once the resolver is in place, which is the opposite of the
usual advice and follows from where the check sits.

### 45. Apache HttpClient 5 on the classpath changes what Spring builds by default

Spring Boot prefers `HttpComponentsClientHttpRequestFactory` for `RestClient` as soon as
`httpclient5` is present, so adding the dependency for one feature changes the transport of every
other caller. In this application that is the LangChain4j Spring `RestClient` the chat providers are
reached through.

It was verified rather than assumed: the streaming harness that drives a fake Ollama server through
the real client was re-run after the dependency was added, and the answer still arrives part by part.
Apache HttpClient hands out the response body as a stream like the JDK client does.

### 46. `httpclient5` and `httpcore5` are versioned separately

Spring Boot 4.1.0 manages `httpclient5` 5.6.1, which depends on `httpcore5` 5.4.2. A local repository
that already held `httpclient5` 5.6.1 and `httpcore5` 5.4 as transitive artifacts of something else
therefore still fails an offline build, because the pair does not match. The two are released on
their own cadence and a resolved client version does not imply a resolved core version.
