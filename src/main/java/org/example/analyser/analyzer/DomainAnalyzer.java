package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.DomainExtractionResult;
import org.example.analyser.model.DomainInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Groups classes into "domains" by running three independent
 * extraction strategies and picking whichever one actually found a
 * real signal, instead of trusting a single heuristic everywhere:
 *
 * <ul>
 * <li>{@link PackageDomainExtractor} - structural, from package
 * names. Wins on conventionally domain-first-packaged projects.</li>
 * <li>{@link ClassNameDomainExtractor} - naming, from class-name
 * suffixes. Wins when packages are purely technical/layered but
 * classes are still named per-domain (`PatientController`,
 * `PatientService` in flat `controller`/`service` packages).</li>
 * <li>{@link EntityUsageDomainExtractor} - behavioral, from which
 * entity/table each class actually touches (reusing
 * {@link CrudAnalyzer}'s data). Wins when neither naming nor
 * packaging carries a domain signal but the persistence graph
 * does.</li>
 * </ul>
 *
 * Each strategy scores itself with a confidence in [0, 1] (see the
 * private {@code confidenceOf*} methods for exactly what each
 * measures), and the single highest-scoring strategy's assignment
 * is used for the *entire* project - deliberately not mixed
 * per-class, so the result stays one coherent, explainable view
 * rather than a patchwork of whichever heuristic happened to answer
 * for each individual class. A class the winning strategy has no
 * answer for (e.g. entity-usage wins overall but this particular
 * class never touches persistence) falls back to the "core" bucket,
 * same fallback {@link PackageDomainExtractor} has always used.
 *
 * Ties are broken package-based > entity-usage > class-name, in
 * that order - preferring the signal that requires the fewest
 * assumptions (raw package structure) over one grounded in naming
 * convention (the weakest assumption of the three) when confidence
 * alone doesn't distinguish them.
 *
 * LIMITATION (documented, not solved): all three are heuristics
 * over the same underlying ambiguity - "domain" is a business
 * concept a static analyzer cannot truly know. A project that
 * defeats all three at once (technical packages, non-descriptive
 * class names, no persistence layer at all - a pure orchestration
 * or messaging service, say) still bottoms out at "core" for
 * everything, honestly reporting "no signal" rather than guessing.
 */
@Component
public class DomainAnalyzer {

    public DomainExtractionResult analyze(
            List<ClassInfo> classes,
            List<CrudOperationInfo> crudOperations) {

        PackageDomainExtractor packageExtractor =
                PackageDomainExtractor.fit(classes);

        EntityUsageDomainExtractor entityExtractor =
                EntityUsageDomainExtractor.fit(classes, crudOperations);

        Map<String, String> packageAssignment = new LinkedHashMap<>();
        Map<String, String> classNameAssignment = new LinkedHashMap<>();
        Map<String, String> entityAssignment = new LinkedHashMap<>();

        for (ClassInfo classInfo : classes) {

            String key = classInfo.getFullyQualifiedName();

            packageAssignment.put(
                    key,
                    packageExtractor.domainOf(classInfo.getPackageName())
            );

            String classNameDomain =
                    ClassNameDomainExtractor.domainOf(classInfo.getName());

            if (classNameDomain != null) {
                classNameAssignment.put(key, classNameDomain);
            }

            String entityDomain =
                    entityExtractor.domainOf(classInfo.getName());

            if (entityDomain != null) {
                entityAssignment.put(key, entityDomain);
            }
        }

        double packageConfidence =
                confidenceOfPackageStrategy(packageAssignment);

        double classNameConfidence =
                confidenceOfClassNameStrategy(
                        classNameAssignment, classes.size()
                );

        double entityConfidence = entityExtractor.coverage();

        List<ScoredStrategy> strategies = List.of(
                new ScoredStrategy(
                        "package-based", packageConfidence, 2, packageAssignment
                ),
                new ScoredStrategy(
                        "entity-usage", entityConfidence, 1, entityAssignment
                ),
                new ScoredStrategy(
                        "class-name", classNameConfidence, 0, classNameAssignment
                )
        );

        ScoredStrategy winner =
                strategies.stream()
                        .max(Comparator
                                .comparingDouble(ScoredStrategy::confidence)
                                .thenComparingInt(ScoredStrategy::tieBreakRank))
                        .orElseThrow();

        Map<String, DomainInfo> domains = new LinkedHashMap<>();

        for (ClassInfo classInfo : classes) {

            String domain =
                    winner.assignment().get(classInfo.getFullyQualifiedName());

            if (domain == null || domain.isBlank()) {
                domain = "core";
            }

            domains.computeIfAbsent(domain, DomainInfo::new)
                    .getClasses()
                    .add(classInfo);
        }

        Map<String, Double> strategyConfidence = new LinkedHashMap<>();
        strategyConfidence.put("package-based", round(packageConfidence));
        strategyConfidence.put("class-name", round(classNameConfidence));
        strategyConfidence.put("entity-usage", round(entityConfidence));

        return new DomainExtractionResult(
                new ArrayList<>(domains.values()),
                winner.name(),
                strategyConfidence
        );
    }

    /*
     * How much of package-based extraction's output is real
     * business domains rather than technical-layer names in
     * disguise ("controller", "service", ... or its own "core"
     * fallback) - the exact signal that a purely layered project
     * (no domain segment anywhere in its packages) has defeated
     * this strategy.
     */
    private double confidenceOfPackageStrategy(
            Map<String, String> assignment) {

        Set<String> distinctDomains =
                new LinkedHashSet<>(assignment.values());

        if (distinctDomains.isEmpty()) {
            return 0.0;
        }

        long realDomains =
                distinctDomains.stream()
                        .filter(domain -> !isGenericPackageDomain(domain))
                        .count();

        return (double) realDomains / distinctDomains.size();
    }

    private boolean isGenericPackageDomain(String domain) {

        return "core".equalsIgnoreCase(domain)
                || ClassNameDomainExtractor.isTechnicalLayerWord(domain);
    }

    /*
     * Coverage (fraction of classes whose name matched a known
     * layer suffix) multiplied by clustering strength (fraction of
     * those matches that actually landed in a shared, non-singleton
     * domain). Either factor alone can be misleadingly high on its
     * own - e.g. every class could match a suffix yet each produce
     * a unique one-off domain, which is not a real cluster - so
     * both have to hold for this strategy to be confident.
     */
    private double confidenceOfClassNameStrategy(
            Map<String, String> assignment,
            int totalClasses) {

        if (totalClasses == 0 || assignment.isEmpty()) {
            return 0.0;
        }

        double coverage =
                (double) assignment.size() / totalClasses;

        Map<String, Long> clusterSizes =
                assignment.values().stream()
                        .collect(Collectors.groupingBy(
                                domain -> domain,
                                Collectors.counting()
                        ));

        long classesInRealClusters =
                clusterSizes.values().stream()
                        .filter(size -> size > 1)
                        .mapToLong(Long::longValue)
                        .sum();

        double clusteringRatio =
                (double) classesInRealClusters / assignment.size();

        return coverage * clusteringRatio;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record ScoredStrategy(
            String name,
            double confidence,
            int tieBreakRank,
            Map<String, String> assignment) {
    }
}
