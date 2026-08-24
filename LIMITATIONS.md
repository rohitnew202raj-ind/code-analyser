# Known Limitations

This document tracks what this analyzer does and does not
handle, and why. It started as a short list of deliberate,
temporary trade-offs; that list has since been worked through
(see "Resolved" below). What remains is either fundamentally
out of reach for a static AST-based analyzer, or a real
open-ended engineering effort deliberately left for later.

## Resolved

These were the original four deliberate trade-offs, now
addressed:

1. **Simple-name class matching.** Was: `target.getName().equals(targetType)`,
   ambiguous whenever two classes shared a simple name in
   different packages. Now: the JavaParser Symbol Solver is
   wired up (`JavaSourceParser.configureSymbolSolver`,
   `TypeResolver.resolveType`/`resolveDeclaringType`) against
   the target project's own source plus the JDK, and is tried
   first everywhere a call target or variable type is
   resolved. `ClassRegistry` also turns a genuinely ambiguous
   simple-name lookup into "unresolved" rather than "silently
   resolved to the wrong class." See the Symbol Solver
   limitation below for what this still can't reach.

2. **`@Component` → `BATCH_DEPENDENCY` heuristic.** Removed
   entirely. `BatchAnalyzer` now detects real batch/scheduling
   signals instead of guessing from a dependency's declared
   type: `@Scheduled`, `@Async`, `@EventListener`,
   `@KafkaListener`, `@JmsListener`, `@RabbitListener`
   annotated methods, and classes implementing
   `Tasklet`/`ItemReader`/`ItemWriter`/`ItemProcessor`. It
   produces its own inventory (`BatchProgramInfo`), printed
   alongside the API inventory. Spring Batch jobs assembled
   purely via `Job`/`Step` builder chains in a `@Configuration`
   class are still not detected - see below.

3. **Repository entity type via direct `extends` only.**
   `RepositoryInheritanceResolver` now propagates both the
   `REPOSITORY` classification and the resolved entity type
   through custom base-repository chains
   (`OrderRepository extends BaseRepository<OrderEntity, Long>`)
   via a fixed-point resolution pass after all classes are
   parsed. `CrudAnalyzer` also now prefers this resolved type
   over its old name-guessing fallback.

4. **Batch programs not analyzed.** `BatchAnalyzer` (see #2)
   now gives batch entry points a first-class inventory
   alongside REST APIs.

## Also fixed after real-world testing

Found by running the analyzer against a synthetic ~860-class
monolith built to look like a real messy legacy codebase
(interface-per-service, legacy V1/V2 service layers, custom
base repositories, event listeners, a God facade, etc.):

- **Service/repository dependency edges collapsed to `UNKNOWN`
  whenever the field was declared by interface type** - which
  is the standard Spring pattern (`OrderController` holds an
  `OrderService` field, not `OrderServiceImpl`). Classification
  only checked the *field's own* annotations, and interfaces
  don't carry `@Service`/`@Repository` - only their
  implementation does. On the test monolith, essentially every
  controller/facade → service edge in the whole dependency
  graph came back `UNKNOWN` because of this. `InterfaceRoleResolver`
  now propagates `SERVICE`/`REPOSITORY` classification from an
  implementing class down to the interface it implements, when
  every implementor agrees on the same role (a genuine conflict
  across multiple differently-classified implementors is left
  unresolved rather than guessed).
- **Plain `main()`-method batch entry points were invisible.**
  `BatchAnalyzer` only looked for Spring annotations
  (`@Scheduled` etc.) or Spring Batch interfaces - a
  cron-invoked class with a bare `public static void
  main(String[])` and no framework annotations at all (a very
  common shape for a legacy batch JAR) matched none of those
  signals. Now detected as a `MAIN_ENTRY_POINT` batch program,
  excluding the Spring Boot application's own `main()` (already
  captured separately as `APPLICATION`).
- **Every non-Spring-annotated class (and every dependency onto
  one) collapsed into a single, uninformative `UNKNOWN`.** DTOs,
  domain events, exceptions, constants holders, mapper/validator
  dependencies, plain interfaces - none of these carry a Spring
  stereotype, but they're still distinct, recognizable kinds of
  class. On the test monolith this was the single largest
  category in the class inventory and dependency graph, both by
  raw count. `SpringComponentAnalyzer` now falls back to a
  specific classification (`DTO`, `EVENT`, `EXCEPTION`,
  `CONSTANTS`, `SPECIFICATION`, `UTILITY`, `INTERFACE`, or a
  final `POJO` catch-all) based on naming convention and
  extends-clause when no stereotype annotation is present, and
  `DependencyAnalyzer` gained a `COMPONENT_DEPENDENCY` type for
  edges onto a plain `@Component` (mapper, validator, converter,
  interceptor). The remaining fallback for a dependency that
  matches none of those is named `OTHER_DEPENDENCY` rather than
  `UNKNOWN`, so a lingering catch-all is still self-explanatory
  in report output. Naming-convention classification is a
  heuristic, unlike the annotation-based checks elsewhere in
  this analyzer - a project with unconventional suffixes (e.g.
  a DTO not ending in `Dto`) will fall through to `POJO`/
  `OTHER_DEPENDENCY` rather than being misclassified.

## Also fixed along the way

- **`existsById` / other CRUD methods silently dropped.**
  `CrudAnalyzer.detectOperation` matched the literal string
  `"exists"`, missing Spring Data's actual `existsBy...`
  convention, and returned `null` (silently discarded) for
  anything unrecognized. Now matches by prefix and falls back
  to `CUSTOM_QUERY` instead of dropping the operation.
- **Acronym-mangling snake_case.** `HTTPOrderEntity` used to
  become `h_t_t_p_order_entity`. `SnakeCaseConverter` uses the
  standard two-pass regex approach and produces
  `http_order_entity`.
- **Duplicated, fragile field-string parsing.**
  `MethodCallAnalyzer` had its own weak field-only
  name/type extraction, separate from and inconsistent with
  `TypeResolver`. It now reuses `TypeResolver`, which also
  means local-variable-held services (not just fields) resolve
  correctly as call targets.
- **Domain grouping's hardcoded "4th package segment" rule.**
  Replaced by `PackageDomainExtractor`, which derives the
  split point from the longest common package prefix across
  every scanned class, so it adapts to whatever convention a
  given project actually uses instead of assuming one specific
  shape.
- **Annotation matching missed fully-qualified usage.**
  `@org.springframework...RestController` used to fail every
  literal-string check. `AnnotationNames.simpleName` normalizes
  before matching, and `MetaAnnotationResolver` additionally
  resolves composed/meta-annotations declared inside the
  scanned project (a custom `@ApiController` that itself
  carries `@RestController`).
- **No `@Controller` (MVC) or other entry-point roles.**
  `ClassInfo` now carries a `roles` list (not just one
  mutually-exclusive `type`), covering `@Controller`,
  `@Aspect`, `@ControllerAdvice`/`@RestControllerAdvice`,
  `@FeignClient`, `@GrpcService` alongside the existing set.
- **Records/enums invisible.** Class analysis now walks
  `TypeDeclaration<?>`, covering `RecordDeclaration` and
  `EnumDeclaration`, not just `ClassOrInterfaceDeclaration`.
- **Lombok-generated methods invisible.** `ClassAnalyzer`
  synthesizes the conventional getter/setter method names for
  `@Data`/`@Getter`/`@Setter`/`@Value` (class- or field-level),
  since JavaParser doesn't run annotation processors.
- **Only field-declared dependencies were visible.**
  `RuntimeDependencyAnalyzer` now also detects
  `ApplicationContext.getBean(X.class)` and direct
  `new SomeApplicationClass()` instantiation.
- **Interface-declared `@RequestMapping`/`@GetMapping` etc.**
  were invisible when a controller implemented an annotated
  interface without repeating the annotations. `ApiAnalyzer`
  now merges those in.
- **No GraphQL detection.** `@QueryMapping`, `@MutationMapping`,
  `@SubscriptionMapping`, `@SchemaMapping` are now recognized
  (annotation-based, so tractable the same way REST mappings
  are).
- **`CommandLineRunner`/`ApplicationRunner` startup entry points
  were invisible.** `BatchAnalyzer` only recognized
  `@Scheduled`/event-listener annotations, Spring Batch
  interfaces, and bare `main()` methods - a `@Component
  implements CommandLineRunner` class (a common shape for a
  one-off data-seeding or migration step that runs once at
  application startup) matched none of those signals. Now
  detected as a `STARTUP_RUNNER` batch program.
- **`target/`, `build/`, `.git/`, IDE folders scanned as source.**
  `ProjectScanner` now excludes them.
- **One bad file aborted the whole run.** Parsing is now
  per-file error-tolerant: a file that fails to parse is
  skipped and reported in a "files skipped" section instead of
  crashing the analysis.
- **Hardcoded target path, hardcoded Java 21.**
  `AnalyzerRunner` now takes the target path as a CLI argument;
  `BuildConfigReader` detects each module's actual Java version
  and source roots from its `pom.xml` (recursively, for
  multi-module Maven) or `build.gradle`.
- **O(nodes × edges) coupling, O(n) domain/dependency lookups.**
  `CouplingAnalyzer` is now a single pass over edges;
  `DomainDependencyAnalyzer` and `DependencyAnalyzer` use
  hash-map lookups instead of repeated linear scans.
- **Unweighted coupling.** `ClassCouplingInfo` now also carries
  a `callWeight` derived from the actual method-call graph, in
  addition to the structural edge counts.
- **Console-only output.** `ReportExporter` writes the full
  analysis to `report.json` and to Graphviz `.dot` files
  (dependency graph and domain graph).
- **Sequential, single-threaded parsing.** The first pass
  (parsing + per-class AST analysis, which does not touch the
  Symbol Solver) now runs in parallel across files. The second
  pass (method-call/entity-mutation/runtime-dependency
  analysis) stays sequential deliberately - see below.
- **Secrets in field text.** Field declarations whose variable
  name looks secret-like (`password`, `token`, `apiKey`, etc.)
  have any string literal in their stored text redacted before
  it reaches the report.
- **@Table not inherited from `@MappedSuperclass`.**
  `CrudAnalyzer` now walks the entity's `extends` chain for an
  inherited `@Table` before falling back to a snake_case guess.

## Still not solved (deliberately out of scope)

These aren't oversights - each would require capability well
beyond AST pattern-matching, and attempting a shallow version
would produce misleading rather than merely incomplete output.

- **Kotlin/Groovy source files.** JavaParser cannot parse
  non-Java JVM languages; a gradually-migrating codebase with
  Kotlin classes participating in the same Spring context has
  a real blind spot here. Supporting it would mean integrating
  an entirely separate parser toolchain, not extending this
  one.
- **WebFlux functional routing** (`RouterFunction`,
  `.route(GET("/x"), handler)`). Not annotation-based;
  recognizing it means following arbitrary fluent method-chain
  composition - a data-flow analysis problem, not a per-node
  AST check.
- **Full gRPC endpoint inventory.** Only the `@GrpcService`
  class itself is tagged (see `SpringComponentAnalyzer`).
  Actual RPC method inventory would require reading generated
  `.proto` stubs, which usually aren't part of the scanned Java
  sources.
- **Spring Batch jobs assembled via `Job`/`Step` builders**
  in a `@Configuration` class (as opposed to a dedicated
  `Tasklet`/`ItemReader`/`ItemWriter` class). Same class of
  problem as WebFlux functional routing.
- **Which concrete bean actually gets injected** when a field
  is declared as an interface with multiple `@Qualifier`- or
  `@Profile`-selected implementations. This is genuinely
  runtime information (active profile, conditional bean
  registration) that cannot be derived from source alone.
  FQN-based matching (via the Symbol Solver) improves precision
  where there's no ambiguity, but doesn't solve this class of
  problem.
- **A project-wide custom Hibernate `PhysicalNamingStrategy`
  bean.** Table-name resolution understands explicit `@Table`
  (including inherited via `@MappedSuperclass`) and falls back
  to a snake_case guess; it cannot know about arbitrary custom
  naming-strategy beans without interpreting Spring
  configuration in general.
- **Gradle build files beyond simple `sourceCompatibility`
  literals.** `BuildConfigReader`'s Gradle support is
  regex-based, not a real Groovy/Kotlin-DSL parser. A version
  catalog reference, a variable, or a convention plugin
  supplying the Java version will not be picked up; it falls
  back to a default rather than failing.
- **Repository entity-type inference through a custom base
  repository** assumes the Spring Data convention that the
  *first* generic type argument is always the entity type, at
  every level of the chain (`RepositoryInheritanceResolver`).
  A base repository that reorders or adds type parameters
  before the entity type would confuse this.

## Symbol Solver: what it does and doesn't reach

The Symbol Solver (`javaparser-symbol-solver-core`) is
configured with the target project's own source (via
`JavaParserTypeSolver`, one per detected module) plus the JDK
(via `ReflectionTypeSolver`). This is what lets chained calls,
overload-aware resolution, and lambda-parameter types resolve
correctly in most cases - none of which the old string/AST
heuristics could reach at all.

It is **not** configured with the target project's actual
dependency jars (Spring, JPA, etc. themselves). So a call
resolving into an external framework type - `orders.save(x)`
where `save` is declared on `JpaRepository`, for instance -
fails to resolve via the Symbol Solver and falls back to the
older heuristics (field/parameter/local-variable type lookup),
which is exactly the case those heuristics already handled
correctly. Every call site in this codebase is written to fall
back gracefully rather than assume resolution succeeds.

One consequence worth calling out explicitly: a
Lombok-generated method combined with a lambda-parameter
receiver (`items.forEach(i -> i.setStatus(x))` where `setStatus`
only exists via `@Data`, never written in source) is still
missed - Symbol Solver can't resolve a call to a method that
was never actually parsed, and the AST fallback doesn't cover
lambda parameters either. A hand-written setter behind a
lambda parameter, or a Lombok-generated setter called via a
field/local/parameter receiver, both work; only the
combination of "Lombok-only" and "lambda parameter" doesn't.

## Flow Engine: what it connects and where it stops

`FlowEngine` walks the method-call graph outward from each
`EntryPointInfo` (an API, a scheduled/event trigger, a batch
step, a startup runner) to assemble a `FlowPath`: every call
hop, database operation, and entity mutation reachable from
that entry point, not just the disconnected per-node facts the
rest of the analyzer records. It's a breadth-first walk over
the same simple-class-name-keyed data `MethodCallAnalyzer` and
`CrudAnalyzer` already produce, so it inherits their resolution
boundaries rather than introducing new ones.

The one worth calling out explicitly: when a field is declared
by interface type (`private OrderService orderService`, the
standard Spring pattern) and a call into it resolves to the
interface rather than the implementing class, the walk dead-ends
at that node. The implementation's own outgoing calls are
recorded under the implementation class's name
(`OrderServiceImpl.create -> ...`), not the interface's
(`OrderService.create`), so `FlowEngine` never finds them -
there's no data connecting the two under this representation.
A flow that looks short at the interface boundary is
indistinguishable from a real short flow; both are represented
identically (no further edges found) rather than guessed at.
Closing this gap would mean giving the call graph itself
interface-to-implementation awareness (the same kind of
resolution `InterfaceRoleResolver` already does for
classification, just for call edges instead of dependency
edges) - a real, valuable follow-up, deliberately out of scope
here.

A separate, deliberate safety net: the walk caps out at 2000
visited nodes per entry point (`FlowEngine.MAX_VISITED_NODES`).
Real flows are a handful of hops; hitting this cap in practice
would mean either a pathological fan-out or simple-name
collisions merging unrelated call chains together. Either way,
`FlowPath.isTruncated()` says so explicitly rather than
silently returning a partial result that looks complete.

## Architecture Intelligence: scope of the four checks

`CircularDependencyAnalyzer`, `GodClassAnalyzer`,
`RepositoryBypassAnalyzer`, and `DeadComponentAnalyzer` are all
structural queries over data the rest of the analyzer already
computes correctly (the dependency graph, coupling numbers,
roles, entry points) - none of them re-resolve anything on their
own, so they inherit whatever precision that underlying data
already has.

- **Circular dependencies** are reported as whole strongly
  connected components (Tarjan's algorithm), not as individual
  elementary cycles - a tightly coupled group of N classes can
  contain an exponential number of distinct cycle paths through
  it, and enumerating them would bury the one thing that
  actually matters under noise.
- **God class** uses a fixed outgoing-coupling threshold (10),
  not a configurable one - see `GodClassAnalyzer`'s javadoc for
  where that number comes from and its honest limits.
- **Layer violations** are checked as exactly one concrete case:
  a controller depending directly on a repository
  (`RepositoryBypassAnalyzer`). Broader, project-specific
  layering policy ("domain X must never depend on domain Y")
  isn't attempted - this analyzer has no way to infer such a
  policy on its own, and guessing at one would be actively
  misleading rather than merely incomplete.
- **Dead/orphan components** are restricted to
  `SERVICE`/`REPOSITORY`/`COMPONENT`-classified classes with zero
  incoming dependency edges that aren't themselves an entry
  point. Getting this right required one non-obvious exclusion:
  a class that implements a `SERVICE`/`REPOSITORY`-classified
  interface is never flagged, even with zero incoming edges of
  its own - that's the normal shape of the standard "program to
  an interface" Spring pattern (the interface carries the real
  incoming edges, not the implementation), and flagging it
  anyway would mean flagging essentially every correctly wired
  `*Impl` class in a typical codebase. Verified end-to-end
  against a small hand-written sample project covering all four
  checks, including this exact interface-vs-implementation case.

**Not attempted in this pass** (each is a reasonable follow-up,
not an oversight): "shared entity hotspots" (entities/tables
touched by an unusually large number of services - answerable
from `CrudOperationInfo`/`EntityMutationInfo` already collected,
just not wired into a finding yet), and a configurable/pluggable
threshold system for `GodClassAnalyzer` instead of a fixed
constant.

## Domain Intelligence: what "extraction candidate" means

`DomainBoundaryAnalyzer` and `DomainCircularDependencyAnalyzer`
suggest microservice-extraction candidates from the domain
dependency graph `DomainDependencyAnalyzer` already computes -
again, a structural query over existing data, not new parsing.
Domain-level cycle detection reuses the exact same algorithm as
class-level circular-dependency detection (`GraphCycleFinder`,
extracted from `CircularDependencyAnalyzer` for this reason), and
a domain caught in a cycle is treated as an automatic
disqualifier (`BLOCKED_BY_CYCLE`), overriding whatever the raw
coupling count would otherwise suggest - two domains that depend
on each other cannot become separate deployable services without
either merging back together or restructuring the dependency
first.

The one thing worth being explicit about: this is a **purely
structural** signal, and structure alone cannot distinguish a
genuinely cohesive small business domain from a domain that's
small only because it's a leftover grouping of cross-cutting
infrastructure classes (a `common` or `config` package, say) -
both look identical from here: low class count, low cross-domain
coupling. `EXTRACTION_CANDIDATE` means "structurally isolated,"
not "this is definitely a good business boundary" - that
judgment still needs a human who knows what the domain is
actually *for*. Verified end-to-end against a hand-written sample
covering all three verdicts (`EXTRACTION_CANDIDATE`, `TANGLED`,
`BLOCKED_BY_CYCLE`) at once.

## Export upgrade: Mermaid and HTML report

`ReportExporter` now writes two additional formats alongside the
existing `report.json` and Graphviz `.dot` files: Mermaid source
(`dependency-graph.mmd`, `domain-graph.mmd`) and a single
self-contained `report.html`.

Mermaid was chosen over GraphML for the same reason DOT was kept
rather than replaced: it needs no separate tool to view - GitHub,
GitLab, and most modern Markdown renderers draw a
` ```mermaid ` fence directly, and mermaid.live covers everything
else. GraphML was considered and deliberately cut: it exists to
feed dedicated graph-editing tools (Gephi, yEd) that this
project's users are unlikely to already have open, whereas DOT
and Mermaid both cover "render it now, wherever I already am."
The Mermaid files are generated *from the same graph data* as the
`.dot` files, not as a replacement for them - both are written on
every run.

Mermaid's own renderer does not scale indefinitely: at a few
hundred nodes and edges a `flowchart` diagram becomes visually
unreadable (and, in some renderers, slow to lay out) regardless
of how correct the underlying data is. This tool does not
simplify, cluster, or paginate the diagram to compensate - it
emits one node per class and one edge per dependency, in full, the
same way the `.dot` export always has. For a large target project,
the domain-level Mermaid diagram (fewer nodes - one per domain,
not per class) stays useful much longer than the full
class-level dependency diagram does; `report.json` remains the
only format with no size ceiling.

`report.html` is a **summary/dashboard**, not a full data dump: it
covers the same "so what should I look at first" material a
reviewer would want on one scrollable page - stat cards,
architecture findings, domain boundary verdicts, the domain
overview, and the entry point inventory - all rendered from
inline CSS with no external stylesheet, script, or CDN dependency,
so the file opens correctly straight from disk with no server and
no network access. It deliberately excludes the full class
inventory, method-call graph, CRUD operations, and entity
mutations: at real project scale that data is large, table-shaped,
and better explored in `report.json` (or the DOT/Mermaid graphs)
than reproduced as a second, slower copy in HTML. There is no
separate PDF exporter - a browser's "Print > Save as PDF" on
`report.html` covers that need without adding a PDF-rendering
dependency to the project. All findings/domain-boundary/entry-point
text is HTML-escaped before being written, since it is built from
class and package names taken from the target project's own
source. Verified end-to-end against a hand-written sample project
exercising a class-level cycle, a domain-level cycle, a god class,
two dead components, and a domain boundary in each of the three
verdict states, with all six output files inspected directly.

`domain-extraction-map.html` is a third HTML output, written
alongside `report.html`: an interactive, clickable version of
`domain-graph.mmd`'s data, aimed specifically at the "what should
we extract into a service first" migration decision rather than
at general review. It reuses the exact same `DomainDependency` and
`DomainBoundaryInfo` lists the Mermaid/table versions already
render from - `ReportExporter` serializes them straight to JSON
via the same `ObjectMapper` used for `report.json`, so the page
can never drift from what the other exports say. The node
layout is computed once, synchronously, by a small hand-rolled
force-directed simulation embedded in the page itself (repulsion
between all node pairs, spring attraction along edges, a
centering force, ~450 iterations) - not a dependency on D3 or any
other charting library, so the file has no build step and needs
no network access to open, matching the same "self-contained,
opens from disk" requirement `report.html` already documents.
Node size encodes total degree (incoming + outgoing domain
edges), not class count, so a small-but-central domain like a
shared `common` module visually dominates the map exactly because
it's the bottleneck, not because it has the most code - which is
the property `DomainBoundaryAnalyzer`'s own "TANGLED" verdict is
already trying to flag. Clicking a domain shows
`DomainBoundaryAnalyzer`'s own `reason` text for that verdict,
not a second, independently-written explanation - so the page can
only ever repeat what the analysis actually found, never invent
a plausible-sounding but different justification. Verified
end-to-end against both the synthetic-monolith fixture (4 domains,
5 edges, all three verdict states present) and this project's own
source (2 domains, 0 edges - the empty-edges path), with the
embedded JSON parsed back out and checked in both cases.

## Persistence Deep Analysis: N+1 queries and shared entity hotspots

`NPlusOneQueryAnalyzer` and `SharedEntityHotspotAnalyzer` are both
structural queries over the CRUD data `CrudAnalyzer` already
computes - no new parsing pass, same approach as the
architecture/domain intelligence checks.

**N+1 query risk** flags a repository READ call made from inside
a loop - the classic SELECT N+1 shape (one query per loop
iteration instead of one batched query). Detecting "inside a
loop" required one small new capability: `MethodCallAnalyzer` now
walks each call expression's AST ancestors up to its enclosing
method and checks for an actual `for`/`for-each`/`while`/`do-while`
statement among them, recorded as `MethodCallInfo.insideLoop` and
carried through to the matching `CrudOperationInfo` one-to-one (no
guessing at correspondence after the fact - `CrudAnalyzer` sets it
from the exact call site it's already iterating).

**Scope, explicitly**: only READ operations are flagged as N+1
risk. A write call (save/update/delete) inside a loop is a real
but different performance concern - repeated writes don't cause
extra *queries* the way lazy reads do, and whether Spring Data
batches them depends on JPA batch-size configuration this tool
can't see - so it's left alone rather than folded into a finding
name it doesn't match. Loop detection itself only recognizes
actual loop *statements* - a `Stream`/`Collection` iteration
written as `orders.forEach(o -> repo.save(o))` is not detected,
since that's a method call (the receiver's actual type would need
to be resolved to tell it apart from any other lambda-accepting
call). A hand-written `for`/`while`/`for-each` loop around a
repository call - the far more common real-world source of N+1
bugs - is still caught correctly, including one written *inside*
a lambda that is itself inside a loop.

**Shared entity hotspot** flags an entity/table read or written by
`SharedEntityHotspotAnalyzer.SHARED_ENTITY_THRESHOLD` (currently
3) or more distinct classes - the persistence-layer equivalent of
`GodClassAnalyzer`'s coupling signal, and, like every other
threshold in this codebase, a fixed starting point rather than a
scientifically precise cutoff. This was explicitly called out as
"not attempted" in the Architecture Intelligence phase; it closes
that gap with data already being collected.

Verified end-to-end against a hand-written sample project: a
`for-each` loop calling `OrderRepository.findById` per iteration
(correctly flagged, while a `save()` call outside any loop in the
same class was correctly left alone), and the `Order` entity
accessed by three distinct services (correctly flagged at exactly
the threshold). Both findings appeared correctly in the console
output, `report.json`, and the HTML report's new "Persistence
Findings" table.

## Behavior Model: read/write classification and sequence diagrams

Every earlier phase modeled either static structure (dependencies,
domains) or reachability (the Flow Engine's "what can this entry
point reach"). Neither answers the more concrete question a
reviewer actually asks about a specific endpoint: "if I call this,
what does it actually *do*, and in what order?" Phase 7 answers
both halves of that from data already collected - no new parsing.

**`EntryPointBehaviorAnalyzer`** classifies each entry point as
`READ_ONLY` or `MUTATING` by inspecting the `FlowPath` the Flow
Engine already traced for it: any write-shaped database operation
(`CREATE_OR_UPDATE`/`UPDATE`/`DELETE`) or entity field mutation
anywhere in the reachable flow makes it `MUTATING`; otherwise
`READ_ONLY`. This is meant to answer "is this safe to retry,
cache, or call speculatively" - the kind of question that matters
before treating an endpoint as idempotent.

**Scope, explicitly**: `CUSTOM_QUERY` operations (a repository
method `CrudAnalyzer` couldn't classify by name, e.g. a
hand-written `@Query`-annotated method with an arbitrary name) are
conservatively treated as writes. There's no way to know from the
method name alone whether such a query reads or writes, and for a
classification meant to answer "is this safe to treat as
read-only," guessing `MUTATING` is the safe direction to be wrong
in - some genuinely read-only custom queries will be
over-classified as `MUTATING`, never the reverse. This inherits
every limitation the Flow Engine itself already documents (the
interface-vs-implementation call-graph boundary, the
`MAX_VISITED_NODES` truncation safety net).

**Sequence diagrams**: `ReportExporter` now also writes one
Mermaid `sequenceDiagram` per entry point under
`sequence-diagrams/*.mmd` - the dynamic counterpart to the static
DOT/Mermaid dependency and domain graphs from Phase 5, showing
the actual call *order* the Flow Engine traced (something an
unordered edge list can't express). Same design choice as every
other diagram in this tool: Mermaid source text, not a rendered
image, pasted into a GitHub markdown fence or mermaid.live. One
file per entry point rather than one combined file, since
Mermaid's `sequenceDiagram` syntax describes exactly one diagram -
a directory of small files is more useful than one large file
nothing can render as a whole.

Verified end-to-end against a hand-written sample project with two
entry points sharing a domain: a read-only `GET` (correctly
classified `READ_ONLY`, zero write operations) and a mutating
`POST` calling `repository.save(...)` (correctly classified
`MUTATING`, one write operation). Both sequence diagrams were
inspected directly and correctly showed the controller → service →
repository call order with the right method names; the HTML
report's Entry Points table showed the matching behavior badges,
and the new "Mutating entry points" stat card counted exactly one.

## Synthetic-monolith fixture and CI

Every phase up to this one was verified by hand: build the jar,
write a one-off throwaway Spring project in a scratch directory,
run it, eyeball the console output, delete everything. That
caught real bugs along the way - most notably the Phase 3
`OrphanService` fixture bug described above, where a throwaway
sample project accidentally gave a "should be dead" class an
incoming edge and silently defeated the exact check it was meant
to exercise. None of that manual verification runs again on the
next change, though: a regression that quietly stopped
`DeadComponentAnalyzer` (or any other analyzer) from firing would
go unnoticed until someone happened to look.

`src/test/resources/fixtures/synthetic-monolith` is a small,
committed, versioned Spring project (four packages/domains:
`order`, `payment`, `inventory`, `common`) deliberately built to
trip every finding type, domain-boundary verdict, and behavior
classification this tool produces: a class-level circular
dependency, a domain-level circular dependency, a god class, a
repository-bypass, four dead components (including a
deliberately-unreferenced `OrphanService`, the exact shape of the
Phase 3 bug), an N+1 query, a shared-entity hotspot, all three
domain-boundary verdicts (`EXTRACTION_CANDIDATE`/`TANGLED`/
`BLOCKED_BY_CYCLE`), and both `READ_ONLY`/`MUTATING` entry point
classifications across REST and `@Scheduled` trigger types.

`SyntheticMonolithIntegrationTest` runs the real `AnalyzerRunner`
bean against this fixture and asserts on `report.json` - not
exact counts or exact wording (that would make the test as
fragile as pinning every analyzer's message text a second place),
but *presence* of each category above. The goal is "did this
whole category of detection stop firing," which is exactly the
class of regression the Phase 3 bug represents and exactly what
manual scratchpad verification never protected against, since it
never reran.

This is wired into `mvn test`, and therefore into the new
`.github/workflows/ci.yml` GitHub Actions workflow, which now runs
the full test suite and a jar build on every push and pull request
against `main`. Nothing before this phase enforced that a broken
`mvn test` (or a broken build) couldn't merge; this is the first
automated gate in the repository's history.

**Scope, explicitly**: this one fixture cannot cover everything -
it has no Gradle module, no WebFlux functional routing, no Spring
Batch job builders, and doesn't exercise every threshold's exact
boundary value. It is a regression net for "did an existing,
working check stop working," not a substitute for the phase-by-
phase hand verification (building the jar, running it against a
purpose-built sample, reading the actual output) that still
happens for whatever a *new* phase adds.

## Entity mutations are in-memory changes, not confirmed database writes

`EntityMutationAnalyzer` previously recorded every setter call
(`order.setStatus(x)`) with `operation = "UPDATE"` - the same word
`CrudAnalyzer` uses for an actual repository write. That
conflated two different things: a setter call only proves an
object changed *in memory*. Whether it ever reaches the database
depends on facts this analyzer has no way to confirm - whether
the object is a JPA-managed entity at all (`new Order()` never
gets persisted just because a setter was called on it, versus an
entity loaded via a repository/`EntityManager`), whether the call
happens inside a transaction, and whether a subsequent
`repository.save(...)`/`EntityManager.merge(...)` or JPA
dirty-checking at commit time actually writes it.

The recorded operation is now `"FIELD_MUTATION"`, a distinct
vocabulary from `CrudOperationInfo`'s `CREATE_OR_UPDATE`/`READ`/
`UPDATE`/`DELETE`/`CUSTOM_QUERY`/`FLUSH`, so the two can no longer
be confused downstream. This does not change any analyzer's
*behavior* - `EntryPointBehaviorAnalyzer` already only checked
whether the entity-mutations list was non-empty, never the
operation string on it, so this was a mislabeling bug, not a
detection bug. It matters because `report.json` is meant to be
read and trusted directly (that's the whole point of Phase 5's
export upgrade); a field that says `"UPDATE"` when no database
write was ever confirmed is misleading on its own, independent of
whether any analyzer's logic happened to look at it.

**Not attempted here** (a real, larger follow-up, not folded into
this fix): actually confirming persistence - tracking whether the
mutated object was constructed fresh (`new Order()`, never
persisted) versus loaded from a repository (a JPA-managed entity,
where dirty checking will actually write the change), and whether
a `@Transactional` boundary is in scope. That requires real
data-flow analysis (tracing where an object came from across
statements), not a single-call-site check, and deserves its own
phase rather than a guess bolted onto this one.

## Entry point trigger type is now a real enum

`EntryPointInfo.triggerType` was a raw `String`, populated by
`ApiAnalyzer` and `BatchAnalyzer` independently with whatever
literal each happened to write. In practice the two analyzers
already emitted genuinely distinct values per trigger kind - REST
verbs (`GET`/`POST`/...), GraphQL operation kinds, and separate
labels per batch/event annotation - so trigger kinds were *not*
actually being collapsed into one generic "batch" bucket the way
an early read of this code might suggest. What was real: two of
those labels (`EVENTLISTENER`, `KAFKALISTENER`, and their
siblings for JMS/Rabbit) were built by uppercasing an annotation's
simple name with no separator, an accident of implementation
rather than a deliberate naming choice, and nothing enforced that
every analyzer's trigger label actually matched anything - a typo
in a new analyzer's string literal would silently produce a
trigger type nothing else recognized.

`TriggerType` makes every execution model explicit as an enum:
REST verbs, GraphQL operation kinds, and - now cleanly named -
`SCHEDULED`, `ASYNC`, `EVENT_LISTENER`, `KAFKA_CONSUMER`,
`JMS_CONSUMER`, `RABBIT_CONSUMER`, `SPRING_BATCH_STEP_COMPONENT`,
`STARTUP_RUNNER`, and `MAIN_ENTRY_POINT`. `report.json`'s shape is
unchanged - Jackson serializes an enum to its constant name by
default, the same string that was there before (just spelled
correctly and consistently now). `BatchAnalyzer` had zero test
coverage for its six annotation-driven triggers before this
change (only the interface-based and `main()`-based paths were
tested); added a single test asserting all six map to their
correct, distinct `TriggerType`.

## Classification confidence: annotation vs. structural vs. guessed

`ClassInfo.type` has always been determined by a mix of evidence
of very different reliability - a class directly annotated
`@Service` is unambiguous, while a class classified `DTO` purely
because its name ends in `Dto` is a guess that a class like
`OrderDtoValidator` would already defeat. Both were reported
identically before this change, with no way for a `report.json`
consumer to tell them apart.

`ClassInfo.typeSource` (a new `ClassificationSource` enum) now
records which kind of evidence actually produced `type`:

- `ANNOTATION` - carries the deciding Spring stereotype directly
  or through a composed/meta annotation.
- `STRUCTURAL` - an AST-confirmed fact that isn't an annotation:
  extending a known Spring Data repository interface with no
  `@Repository` of its own, extending a known exception
  supertype, or literally being an `interface` declaration.
  Deliberately distinguished from `ANNOTATION` (different kind of
  evidence) but still high confidence.
- `NAMING_HEURISTIC` - matched a name suffix (`*Dto`, `*Event`,
  `*Utils`, ...) because nothing stronger was found. Lower
  confidence, and the one place false positives/negatives are
  expected.
- `NONE` - the `POJO` catch-all: no annotation, structural fact,
  or naming pattern matched anything. Tagged separately from
  `NAMING_HEURISTIC` since no heuristic actually fired to produce
  it - it's the honest "nothing matched" default, not a guess
  that happened to be wrong.

One subtlety worth calling out explicitly: `REPOSITORY` and
`EXCEPTION` can each be reached by *either* an annotation/
naming-only path *or* a structural one (extending
`JpaRepository`/`CrudRepository`/etc. with no `@Repository`
annotation; extending a known exception supertype vs. only
matching the `*Exception` name suffix). `SpringComponentAnalyzer`
now distinguishes these per-class rather than reporting every
repository or exception as equally confirmed.

## Spring bean resolution: only @Primary, never a guess

`BeanResolutionAnalyzer` answers a question nothing earlier in
this tool addressed: when an interface has multiple Spring-managed
implementations, which one actually gets wired when code
`@Autowire`s it? Silent multi-implementation ambiguity was
previously invisible - the dependency graph and call graph both
already record edges against the *interface*, never a specific
implementation (a pre-existing limitation `FlowEngine` documents),
so there was no signal anywhere that an interface even had more
than one candidate.

**Deliberately, only `@Primary` is used to resolve ambiguity** -
it's the one Spring bean-selection mechanism that's a static fact
about the implementation class itself, true regardless of how or
where the interface gets injected. `@Qualifier` and Spring
profiles are explicitly **not** used to eliminate candidates:

- `@Qualifier` disambiguates at each individual injection site (a
  specific field/parameter), not at the interface level - the
  same interface can resolve differently at two different call
  sites in the same codebase. Modeling that needs per-injection-
  site analysis, a larger feature than this pass attempts.
- Which Spring profile is active is a deployment-time decision
  this static analysis has no way to know. Eliminating a
  `@Profile`-restricted candidate would be guessing how the
  application is actually run, not reading a fact from the
  source - so every profile-restricted candidate stays a
  candidate, with its profile surfaced in the description as
  context rather than used to narrow anything down.

**Also not modeled**: `@Bean`-annotated factory methods inside
`@Configuration` classes (only class-level `implements` plus
class-level stereotype annotations are read), and `@Conditional`
variants beyond `@Profile`. Only implementations already
classified `SERVICE`/`REPOSITORY`/`COMPONENT` (confirmed
Spring-managed candidates) count - a class that merely implements
an interface without carrying a stereotype of its own (a test
double, a manually-instantiated helper) is excluded, since it
isn't a bean Spring would ever actually choose between.

## WebFlux functional routing and Spring Batch builder detection

Two entry-point gaps `ApiAnalyzer`/`BatchAnalyzer` were structurally
unable to close, both closed by adding a dedicated analyzer rather
than stretching an existing one to cover a shape it wasn't built
for.

**`WebFluxRouterAnalyzer`** finds WebFlux functional endpoints -
`RouterFunction` beans built via `RouterFunctions.route()`'s
fluent builder (`.GET(path, handler)`, `.POST(path, handler)`,
...). `ApiAnalyzer` only recognizes annotation-based endpoints
(`@GetMapping` et al.); functional routing declares no annotations
on the handler methods at all, so it was previously entirely
invisible. **Scope, explicitly**: only the modern builder-style
chain is recognized, not the older `route(predicate, handler)
.andRoute(...)` chain or `.nest(...)` composition. Only a method
*reference* handler (`handler::getOrder`) produces an entry point;
an inline lambda handler carries no method name to report one
against, so those routes are silently skipped, not guessed at.
One JavaParser quirk worth recording: the left side of `X::method`
always parses as a `TypeExpr`, never a `NameExpr`, even when `X` is
a lowercase local variable/parameter rather than an actual type -
`handlerReference.getScope()` has to be read as a `TypeExpr` and
its type's text used as the variable name, not matched against
`NameExpr` as every other method-reference-adjacent lookup in this
codebase does.

**`SpringBatchBuilderAnalyzer`** finds Spring Batch jobs/steps
assembled via `new JobBuilder(...)`/`new StepBuilder(...)` inside
a `@Bean` method - exactly the gap `BatchAnalyzer`'s own javadoc
already called out as unsolved (it only recognizes a class
directly implementing `Tasklet`/`ItemReader`/etc., not a builder
chain assembled inside a `@Configuration` method body).
**Scope, explicitly**: this only makes the job/step *visible* as
an entry point (class/method/domain) - it does not trace which
`ItemReader`/`ItemProcessor`/`ItemWriter` beans a step actually
wires. That would mean following each builder call's arguments
back to the parameter/field that produced them - a data-flow
problem, not a per-node AST check, and a real, larger follow-up
left for a later phase.

Verified end-to-end against the synthetic-monolith fixture: an
`OrderRouterConfig` with two functional routes correctly produced
`OrderHandler.getOrder`/`OrderHandler.createOrder` entry points
(and correctly excluded `OrderHandler` from `DEAD_COMPONENT`, since
it's now a recognized entry point owner), and an `OrderBatchConfig`
correctly produced `SPRING_BATCH_JOB_BUILDER`/
`SPRING_BATCH_STEP_BUILDER` entries.

## Parallelization scope

Only the first pass (parsing + per-class structural analysis)
runs in parallel. The second pass (method-call, entity-mutation,
and runtime-dependency analysis) is sequential on purpose: it's
where Symbol Solver resolution actually happens, and its
internal caches are not documented as safe for concurrent
access. Given the first pass is the more expensive one at real
project scale (full AST construction for every file), this is
where parallelization pays off without risking subtle
concurrency bugs in a component this codebase doesn't control.

## FIELDS and METHODS are now structured data, not strings

`ClassInfo.fields` and `ClassInfo.methods` used to be
`List<String>` - each entry a pre-formatted line like
`"private String apiKey"`, built once at parse time and then
re-parsed (or just pattern-matched) by anything downstream that
needed the field's actual type or name. `TypeResolver.resolveFieldType`
was the worst offender: ~50 lines of manual tokenizing to pull a
type back out of that string on every call.

Both are now `List<FieldInfo>` / `List<MethodInfo>`, dedicated
model classes carrying `name`, `type`/`returnType`, modifiers
(`isStatic`, `isFinal` on fields), and now - for the first time
in this codebase - the field's or method's own annotations
(`annotations` as written, `annotationSimpleNames` for matching),
plus a method's parameters as `List<ParameterInfo>`. No analyzer
consumes method-level annotations yet; this just makes future
checks (e.g. "this endpoint method has no `@Transactional`")
possible without another model change.

**Deliberate scope narrowing, not an oversight:** `FieldInfo`
never captures a field's initializer expression - only its
declared type and name. The old string-based FIELDS output used
to include initializer literals, and there used to be a
`redactIfSecret` regex step in `ClassAnalyzer` to blank out
anything that looked like an API key or password before it hit
console/JSON output. That whole redaction step is gone now, not
because it was buggy, but because it's no longer needed: nothing
in the new representation can capture a secret literal in the
first place. `ClassAnalyzerTest.structuredFieldsNeverCaptureInitializerValues`
asserts this property directly (a field initialized to a
`"sk-live-..."`-shaped string comes back with no trace of the
value), rather than testing that redaction fires correctly.

**Silent-bug risk this migration created, and how it was closed:**
`List<T>.contains(Object)` accepts any `Object`, so
`sourceClass.getMethods().contains(call.getNameAsString())` in
`MethodCallAnalyzer` kept compiling cleanly after the model
change but would have always returned `false` at runtime (a
`MethodInfo` never `.equals()` a `String`) - silently breaking
same-class method-call resolution with zero test or compiler
signal. Found by re-grepping every `.getFields()`/`.getMethods()`
call site across the whole codebase after the model change,
rather than trusting a clean compile. Fixed by adding
`ClassInfo.hasMethodNamed(String)` and using it in place of the
broken `.contains(...)` call.

## Type resolution: what got centralized, and what didn't

`TypeResolver.isApplicationClass(String, List<ClassInfo>)` is
now the single implementation of "is this type name one of the
classes we scanned, as opposed to a JDK/library type" - it used
to be copy-pasted verbatim in both `MethodCallAnalyzer` and
`RuntimeDependencyAnalyzer`. Centralizing an exact duplicate is a
clear win: one implementation to test, one place to fix if the
definition of "application class" ever needs to change.

**Deliberately left alone:** a few other places do something
*similar* - `CrudAnalyzer.findEntityForRepository`'s
naming-convention guess-fallback, and `CrudAnalyzer.findClass`
plus `WebFluxRouterAnalyzer.packageOf`'s small "find by name"
helpers. None of these are the same logic as
`isApplicationClass`, just superficially alike (all iterate
`List<ClassInfo>` looking for a name match). Forcing them into a
shared abstraction would either weaken `isApplicationClass`'s own
contract to accommodate `CrudAnalyzer`'s guessing behavior, or add
an abstraction layer to save a handful of lines in helpers that
are already trivial to read in place. Not worth the test blast
radius (`CrudAnalyzer` in particular) for the value gained - so
they stay as they are.

## Dependency-jar resolution for Symbol Solver (Maven only)

The Symbol Solver (see "Symbol Solver: what it does and doesn't
reach" above) used to only ever see the target project's own
source roots plus the JDK's own classes (`ReflectionTypeSolver`).
Every external framework type - `JpaRepository`, `ResponseEntity`,
anything from Spring, JPA, or any other dependency - always fell
through to source-text/AST heuristics, never a real resolved
type, because the solver had no jars to look in.

`MavenDependencyResolver` closes part of that gap by shelling out
to `mvn --batch-mode --quiet dependency:build-classpath` against
the target project's own `pom.xml` - the same goal a developer
would run by hand - and feeding the resulting jar paths into a
`JarTypeSolver` added to the existing `CombinedTypeSolver`. It
deliberately does not reimplement Maven's own dependency
resolution (transitive versions, exclusions, scope rules,
dependency management inheritance); getting that right is Maven's
job, not this analyzer's.

**Scope, stated plainly:**

- **Maven-only.** A Gradle target project resolves zero
  dependency jars through this path (see "Gradle" limitations
  elsewhere in this file); doing the equivalent for Gradle is a
  separate, larger effort.
- **Local-cache-only, no network fetch.** This only ever reads
  jars already present in the local Maven repository (typically
  `~/.m2/repository`). If the target project has never been built
  in this environment, its dependencies aren't cached yet, and
  resolution returns nothing - not a fetch-and-wait, just an
  empty list.
- **Root `pom.xml` only.** A multi-module reactor's per-submodule
  dependencies aren't separately aggregated - only the root POM is
  resolved. For a single-module project (or a root module with
  real source of its own) this is complete; for an aggregator-only
  root POM it may resolve nothing useful.
- **Always fails soft.** No `mvn` on `PATH`, a broken build, a
  timeout (90s) - any of these return an empty list, and the
  analyzer falls back to exactly the source+JDK-only resolution
  that existed before this feature, never a hard failure of the
  whole run.

**Verified, not assumed:** run against this project's own
`pom.xml` (a real Maven project with real Spring/JavaParser
dependencies already cached), the analyzer reports
`Dependency jars: 73 (resolved via mvn dependency:build-classpath)`.
Run against the synthetic-monolith fixture (deliberately has no
`pom.xml`, see below), it reports
`Dependency jars: 0 (Maven-only; none resolved or not a Maven project)`
- confirming the fallback path is exactly as graceful as
documented, not just in theory.
