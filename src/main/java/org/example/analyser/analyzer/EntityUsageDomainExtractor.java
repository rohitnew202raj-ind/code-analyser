package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives a candidate domain from *behavior* rather than naming or
 * package structure: classes that operate on the same entity/table
 * - a controller, its service, and the repository it calls, plus
 * the entity itself - get clustered into one domain named after
 * that entity, using the CRUD data {@link CrudAnalyzer} already
 * collects. No new parsing.
 *
 * This is the strongest of the three domain-extraction signals
 * when it applies, because it's grounded in what the code actually
 * *does* rather than in a naming or packaging convention a team
 * may or may not have followed consistently - but it only covers
 * classes reachable from a repository call. A class with no
 * persistence involvement at all (a pure orchestration class, a
 * config bean, a DTO never seen in a CRUD call) gets no candidate
 * here, same as {@link ClassNameDomainExtractor} leaves unmatched
 * names uncovered.
 *
 * A class touching more than one entity (an orchestration service
 * calling two different repositories, say) is assigned to whichever
 * entity it touches most often, ties broken by whichever entity it
 * touched first - deterministic, but still a simplification: a
 * genuinely cross-domain orchestrator doesn't have one "true" home.
 */
public final class EntityUsageDomainExtractor {

    private final Map<String, String> domainBySimpleClassName;
    private final double coverage;

    private EntityUsageDomainExtractor(
            Map<String, String> domainBySimpleClassName,
            double coverage) {

        this.domainBySimpleClassName = domainBySimpleClassName;
        this.coverage = coverage;
    }

    public static EntityUsageDomainExtractor fit(
            List<ClassInfo> classes,
            List<CrudOperationInfo> crudOperations) {

        Map<String, Map<String, Integer>> entityTouchCountsByClass =
                new LinkedHashMap<>();

        for (CrudOperationInfo operation : crudOperations) {

            String entityClass = operation.getEntityClass();

            if (entityClass == null || entityClass.isBlank()) {
                continue;
            }

            String entityDomain = entityDomainName(entityClass);

            recordTouch(
                    entityTouchCountsByClass,
                    operation.getSourceClass(),
                    entityDomain
            );

            recordTouch(
                    entityTouchCountsByClass,
                    operation.getRepositoryClass(),
                    entityDomain
            );

            recordTouch(
                    entityTouchCountsByClass,
                    entityClass,
                    entityDomain
            );
        }

        Map<String, String> domainBySimpleClassName =
                new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Integer>> entry
                : entityTouchCountsByClass.entrySet()) {

            String mostTouchedDomain =
                    entry.getValue().entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse(null);

            if (mostTouchedDomain != null) {
                domainBySimpleClassName.put(
                        entry.getKey(), mostTouchedDomain
                );
            }
        }

        long assignedClassCount =
                classes.stream()
                        .map(ClassInfo::getName)
                        .filter(domainBySimpleClassName::containsKey)
                        .count();

        double coverage =
                classes.isEmpty()
                        ? 0.0
                        : (double) assignedClassCount / classes.size();

        return new EntityUsageDomainExtractor(
                domainBySimpleClassName, coverage
        );
    }

    private static void recordTouch(
            Map<String, Map<String, Integer>> entityTouchCountsByClass,
            String simpleClassName,
            String domain) {

        if (simpleClassName == null || simpleClassName.isBlank()) {
            return;
        }

        entityTouchCountsByClass
                .computeIfAbsent(
                        simpleClassName,
                        key -> new LinkedHashMap<>()
                )
                .merge(domain, 1, Integer::sum);
    }

    /*
     * The entity class's own name, with a known layer suffix
     * (typically "Entity") stripped via ClassNameDomainExtractor
     * so a repository entity like "PatientEntity" produces the
     * domain "Patient" rather than "PatientEntity" - consistent
     * with what class-name clustering would produce for the same
     * entity's controller/service. Falls back to the entity's own
     * name unchanged when no suffix matches (e.g. "Patient" with
     * no "Entity" suffix at all).
     */
    private static String entityDomainName(String entityClassSimpleName) {

        String stripped =
                ClassNameDomainExtractor.domainOf(entityClassSimpleName);

        return (stripped == null || stripped.isBlank())
                ? entityClassSimpleName
                : stripped;
    }

    public String domainOf(String simpleClassName) {
        return domainBySimpleClassName.get(simpleClassName);
    }

    /**
     * Fraction of all scanned classes this strategy could assign a
     * domain to - the confidence signal: high when most of the
     * codebase is reachable from a CRUD operation, near zero for a
     * codebase with little or no persistence involvement.
     */
    public double coverage() {
        return coverage;
    }
}
