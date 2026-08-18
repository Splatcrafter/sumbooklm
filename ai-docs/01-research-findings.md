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

This hits `sumbooklm-domain`, `sumbooklm-ingestion` and `sumbooklm-ai`, which are placeholders at
scaffold stage. They set `maven.javadoc.skip=true` in their own POM with a comment that ties the
property to the first type added to the module. `sumbooklm-frontend` needs no such property: the
plugin skips a module with no Java sources at all on its own, as does the packaging POM of the
parent.
