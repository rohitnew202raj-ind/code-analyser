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
