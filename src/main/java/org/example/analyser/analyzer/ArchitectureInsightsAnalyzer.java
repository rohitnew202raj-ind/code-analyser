package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.DomainBoundaryInfo;
import org.example.analyser.model.DomainBoundaryVerdict;
import org.example.analyser.model.DomainInfo;
import org.example.analyser.model.EndpointFlowSummary;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.FlowPath;
import org.example.analyser.model.InsightsReport;
import org.example.analyser.model.MultiTableTransaction;
import org.example.analyser.model.TableUsageSummary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Answers cross-cutting architecture questions a reader actually
 * asks when planning a decomposition - "what does this API touch",
 * "which domain owns this table", "what's cheapest to extract
 * first" - by re-querying data the pipeline already computed
 * ({@link FlowPath}, {@link CrudOperationInfo}, {@link DomainInfo},
 * {@link DomainBoundaryInfo}). No new parsing; this is a reporting
 * layer over existing facts, same "document scope, don't guess"
 * approach as the other Intelligence analyzers.
 */
@Component
public class ArchitectureInsightsAnalyzer {

    private static final Set<String> WRITE_OPERATIONS =
            Set.of("CREATE_OR_UPDATE", "UPDATE", "DELETE", "FLUSH");

    public InsightsReport analyze(
            List<DomainInfo> domains,
            List<DomainBoundaryInfo> domainBoundaries,
            List<FlowPath> flows,
            List<CrudOperationInfo> crudOperations) {

        Map<String, String> domainByClassName =
                domainByClassName(domains);

        List<EndpointFlowSummary> endpointFlows =
                flows.stream()
                        .map(flow -> toEndpointFlowSummary(flow, domainByClassName))
                        .toList();

        Map<String, List<String>> tablesByDomain =
                tablesByDomain(crudOperations, domainByClassName);

        List<TableUsageSummary> sharedTableRanking =
                sharedTableRanking(crudOperations);

        List<DomainBoundaryInfo> extractionRanking =
                extractionRanking(domainBoundaries);

        List<MultiTableTransaction> multiTableTransactions =
                multiTableTransactions(crudOperations, domainByClassName);

        return new InsightsReport(
                domains,
                endpointFlows,
                tablesByDomain,
                sharedTableRanking,
                extractionRanking,
                multiTableTransactions
        );
    }

    private Map<String, String> domainByClassName(
            List<DomainInfo> domains) {

        Map<String, String> result = new LinkedHashMap<>();

        for (DomainInfo domain : domains) {
            for (ClassInfo classInfo : domain.getClasses()) {
                result.put(classInfo.getName(), domain.getName());
            }
        }

        return result;
    }

    /*
     * One row per entry point, reused for REST endpoints, batch
     * jobs, and every other trigger kind alike - the underlying
     * question ("what does this thing touch end-to-end") is the
     * same regardless of what triggers it.
     */
    private EndpointFlowSummary toEndpointFlowSummary(
            FlowPath flow,
            Map<String, String> domainByClassName) {

        EntryPointInfo entryPoint = flow.getEntryPoint();

        String triggerLabel =
                entryPoint.getPath() != null
                        ? entryPoint.getTriggerType() + " " + entryPoint.getPath()
                        : entryPoint.getTriggerType().toString();

        List<String> callChain =
                flow.getSteps().stream()
                        .map(step ->
                                step.getSourceClass() + "." + step.getSourceMethod()
                                        + " -> "
                                        + step.getTargetClass() + "." + step.getTargetMethod()
                        )
                        .toList();

        List<String> tablesRead = new ArrayList<>();
        List<String> tablesWritten = new ArrayList<>();
        List<String> tablesCustomQuery = new ArrayList<>();

        for (CrudOperationInfo operation : flow.getDatabaseOperations()) {

            String table = operation.getTableName();

            if (table == null) {
                continue;
            }

            if ("READ".equals(operation.getOperation())) {
                addIfAbsent(tablesRead, table);
            } else if (WRITE_OPERATIONS.contains(operation.getOperation())) {
                addIfAbsent(tablesWritten, table);
            } else {
                addIfAbsent(tablesCustomQuery, table);
            }
        }

        return new EndpointFlowSummary(
                triggerLabel,
                entryPoint.getTriggerType(),
                entryPoint.getClassName(),
                entryPoint.getMethodName(),
                entryPoint.getDomain(),
                callChain,
                tablesRead,
                tablesWritten,
                tablesCustomQuery,
                flow.isTruncated()
        );
    }

    private void addIfAbsent(List<String> values, String value) {

        if (!values.contains(value)) {
            values.add(value);
        }
    }

    /*
     * Which tables each domain's own classes directly query -
     * keyed by the *calling* class's domain (not the entity's),
     * so a domain calling into another domain's table shows up
     * here too, exposing that coupling rather than hiding it.
     */
    private Map<String, List<String>> tablesByDomain(
            List<CrudOperationInfo> crudOperations,
            Map<String, String> domainByClassName) {

        Map<String, LinkedHashSet<String>> byDomain =
                new LinkedHashMap<>();

        for (CrudOperationInfo operation : crudOperations) {

            String table = operation.getTableName();

            if (table == null) {
                continue;
            }

            String domain =
                    domainByClassName.getOrDefault(
                            operation.getSourceClass(), "core"
                    );

            byDomain.computeIfAbsent(domain, key -> new LinkedHashSet<>())
                    .add(table);
        }

        Map<String, List<String>> result = new LinkedHashMap<>();

        byDomain.forEach((domain, tables) ->
                result.put(domain, new ArrayList<>(tables))
        );

        return result;
    }

    /*
     * Every table touched by 2+ distinct classes, ranked
     * descending - an open ranking rather than
     * SharedEntityHotspotAnalyzer's threshold-filtered finding.
     */
    private List<TableUsageSummary> sharedTableRanking(
            List<CrudOperationInfo> crudOperations) {

        Map<String, LinkedHashSet<String>> classesByTable =
                new LinkedHashMap<>();

        Map<String, String> entityByTable = new LinkedHashMap<>();

        for (CrudOperationInfo operation : crudOperations) {

            String table = operation.getTableName();

            if (table == null) {
                continue;
            }

            classesByTable
                    .computeIfAbsent(table, key -> new LinkedHashSet<>())
                    .add(operation.getSourceClass());

            entityByTable.putIfAbsent(table, operation.getEntityClass());
        }

        return classesByTable.entrySet().stream()
                .map(entry -> new TableUsageSummary(
                        entry.getKey(),
                        entityByTable.get(entry.getKey()),
                        new ArrayList<>(entry.getValue())
                ))
                .sorted(Comparator
                        .comparingInt(TableUsageSummary::getTouchingClassCount)
                        .reversed())
                .toList();
    }

    /*
     * Cheapest/safest-to-extract-first ordering: extraction
     * candidates first (sorted by their own cross-domain edge
     * count, fewest first), then tangled domains, then
     * cycle-blocked domains last - a cycle has to be broken
     * before extraction is even possible, regardless of how
     * "cheap" its raw coupling count looks.
     */
    private List<DomainBoundaryInfo> extractionRanking(
            List<DomainBoundaryInfo> domainBoundaries) {

        Comparator<DomainBoundaryInfo> byVerdictThenCost =
                Comparator
                        .comparingInt(
                                (DomainBoundaryInfo info) ->
                                        verdictRank(info.getVerdict())
                        )
                        .thenComparingInt(
                                DomainBoundaryInfo::getCrossDomainEdgeCount
                        );

        return domainBoundaries.stream()
                .sorted(byVerdictThenCost)
                .toList();
    }

    private int verdictRank(DomainBoundaryVerdict verdict) {

        return switch (verdict) {
            case EXTRACTION_CANDIDATE -> 0;
            case TANGLED -> 1;
            case BLOCKED_BY_CYCLE -> 2;
        };
    }

    /*
     * A method whose own body's CRUD calls touch 2+ distinct
     * tables - see MultiTableTransaction's javadoc for exactly
     * what this does and doesn't claim to detect.
     */
    private List<MultiTableTransaction> multiTableTransactions(
            List<CrudOperationInfo> crudOperations,
            Map<String, String> domainByClassName) {

        Map<MethodKey, LinkedHashSet<String>> tablesByMethod =
                new LinkedHashMap<>();

        Map<MethodKey, LinkedHashSet<String>> entitiesByMethod =
                new LinkedHashMap<>();

        Map<MethodKey, LinkedHashSet<String>> domainsByMethod =
                new LinkedHashMap<>();

        for (CrudOperationInfo operation : crudOperations) {

            String table = operation.getTableName();

            if (table == null) {
                continue;
            }

            MethodKey key = new MethodKey(
                    operation.getSourceClass(), operation.getSourceMethod()
            );

            tablesByMethod
                    .computeIfAbsent(key, k -> new LinkedHashSet<>())
                    .add(table);

            String entityClass = operation.getEntityClass();

            if (entityClass != null) {

                entitiesByMethod
                        .computeIfAbsent(key, k -> new LinkedHashSet<>())
                        .add(entityClass);

                String entityDomain =
                        domainByClassName.getOrDefault(entityClass, "core");

                domainsByMethod
                        .computeIfAbsent(key, k -> new LinkedHashSet<>())
                        .add(entityDomain);
            }
        }

        List<MultiTableTransaction> result = new ArrayList<>();

        for (Map.Entry<MethodKey, LinkedHashSet<String>> entry
                : tablesByMethod.entrySet()) {

            if (entry.getValue().size() < 2) {
                continue;
            }

            MethodKey key = entry.getKey();

            Set<String> domainsTouched =
                    domainsByMethod.getOrDefault(key, new LinkedHashSet<>());

            result.add(new MultiTableTransaction(
                    key.className(),
                    key.methodName(),
                    domainByClassName.getOrDefault(key.className(), "core"),
                    new ArrayList<>(entry.getValue()),
                    new ArrayList<>(
                            entitiesByMethod.getOrDefault(
                                    key, new LinkedHashSet<>()
                            )
                    ),
                    domainsTouched.size() > 1
            ));
        }

        return result;
    }

    private record MethodKey(String className, String methodName) {
    }
}
