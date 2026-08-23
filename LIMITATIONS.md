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
