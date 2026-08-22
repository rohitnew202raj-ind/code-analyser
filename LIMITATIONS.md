# Known Limitations

These are deliberate, temporary trade-offs in the current analyzer. Each is
called out inline at its code location too. None of these should be "fixed"
opportunistically — they're sequenced for later, on purpose.

## 1. Class matching uses simple names, not fully-qualified names

`DependencyAnalyzer.findClass` (and the coupling/domain lookups built on top
of it) resolve a dependency's target class with:

```java
target.getName().equals(targetType)
```

That's a simple-name match. It works for the current test project, but a
real monolith can have two classes with the same simple name in different
packages:

```
com.company.sales.CustomerService
com.company.reporting.CustomerService
```

In that case the match is ambiguous and can silently resolve to the wrong
class.

**Fix (later):** JavaParser Symbol Solver, for fully-qualified type
resolution. **Do not add Symbol Solver yet** — out of scope until the rest
of the pipeline is stable.

## 2. `@Component` → `BATCH_DEPENDENCY` is a temporary heuristic

`DependencyAnalyzer.classifyDependency` currently treats any `@Component`
that depends on a repository as "batch." `@Component` does not actually mean
batch — it's just the closest signal available today, and it happens to
hold for the current test project because the one batch class there is
annotated `@Component`.

**Fix (later):** a proper `BatchAnalyzer` that looks at `@Scheduled`, Spring
Batch types (`Job`, `Step`, `Tasklet`, `ItemReader`/`ItemWriter`), and
job/step definitions rather than guessing from `@Component`.

## 3. Repository entity-type extraction only sees direct `extends`

`ClassAnalyzer.extractRepositoryEntityType` only detects the entity type
when a repository interface directly extends one of the hardcoded Spring
Data marker interfaces (`JpaRepository`, `CrudRepository`,
`PagingAndSortingRepository`, `Repository`).

If a project defines its own base repository and repositories extend that
instead:

```java
interface BaseRepository<T, ID> extends JpaRepository<T, ID> { }
interface OrderRepository extends BaseRepository<OrderEntity, Long> { }
```

`OrderRepository`'s extended type is `BaseRepository`, not `JpaRepository`,
so the check misses it and `repositoryEntityType` is never set for
`OrderRepository` — which in turn breaks CRUD/entity-mutation tracing for
that repository.

**Fix (later):** resolve the base interface transitively (or again, via
Symbol Solver) instead of matching only the immediate `extends` clause.

## 4. Batch programs are not analyzed yet

The analyzer currently only inventories REST APIs (`ApiAnalyzer`). The
actual migration scenario has 5 batch programs, and they matter just as much
as the REST APIs for understanding the application's behavior.

**Next up (after the above):** build the `BatchAnalyzer` referenced in (2)
and give batch programs the same first-class treatment (`ApiInfo`-style
inventory, entry points, scheduling, dependency tracing) that REST
controllers currently get.
