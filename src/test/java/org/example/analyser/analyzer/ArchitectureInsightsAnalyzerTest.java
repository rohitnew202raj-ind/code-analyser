package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.DomainBoundaryInfo;
import org.example.analyser.model.DomainBoundaryVerdict;
import org.example.analyser.model.DomainInfo;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.FlowPath;
import org.example.analyser.model.InsightsReport;
import org.example.analyser.model.MethodCallInfo;
import org.example.analyser.model.MultiTableTransaction;
import org.example.analyser.model.TableUsageSummary;
import org.example.analyser.model.TriggerType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureInsightsAnalyzerTest {

    private final ArchitectureInsightsAnalyzer analyzer =
            new ArchitectureInsightsAnalyzer();

    @Test
    void tracesAnEndpointsFullCallChainAndTables() {

        DomainInfo orderDomain = domain("order", "OrderController", "OrderService", "OrderRepository");

        EntryPointInfo entryPoint = new EntryPointInfo(
                "OrderController", "com.acme.order", "view",
                TriggerType.GET, "/orders/{id}", "order"
        );

        FlowPath flow = new FlowPath(
                entryPoint,
                List.of(
                        new MethodCallInfo(
                                "OrderController", "view", "OrderService", "getOrder"
                        ),
                        new MethodCallInfo(
                                "OrderService", "getOrder", "OrderRepository", "findById"
                        )
                ),
                List.of(crudOp(
                        "OrderService", "OrderRepository", "READ", "Order", "orders"
                )),
                List.of(),
                false
        );

        InsightsReport insights = analyzer.analyze(
                List.of(orderDomain), List.of(), List.of(flow), List.of()
        );

        assertThat(insights.getEndpointFlows()).hasSize(1);

        var summary = insights.getEndpointFlows().get(0);

        assertThat(summary.getTriggerLabel()).isEqualTo("GET /orders/{id}");
        assertThat(summary.getCallChain()).containsExactly(
                "OrderController.view -> OrderService.getOrder",
                "OrderService.getOrder -> OrderRepository.findById"
        );
        assertThat(summary.getTablesRead()).containsExactly("orders");
        assertThat(summary.getTablesWritten()).isEmpty();
    }

    @Test
    void classifiesWriteAndReadOperationsSeparately() {

        EntryPointInfo entryPoint = new EntryPointInfo(
                "OrderController", "com.acme.order", "create",
                TriggerType.POST, "/orders", "order"
        );

        FlowPath flow = new FlowPath(
                entryPoint,
                List.of(),
                List.of(
                        crudOp("OrderService", "OrderRepository", "READ", "Order", "orders"),
                        crudOp("OrderService", "OrderRepository", "CREATE_OR_UPDATE", "Order", "orders"),
                        crudOp("OrderService", "OrderRepository", "CUSTOM_QUERY", "Order", "orders_view")
                ),
                List.of(),
                false
        );

        InsightsReport insights = analyzer.analyze(
                List.of(), List.of(), List.of(flow), List.of()
        );

        var summary = insights.getEndpointFlows().get(0);

        assertThat(summary.getTablesRead()).containsExactly("orders");
        assertThat(summary.getTablesWritten()).containsExactly("orders");
        assertThat(summary.getTablesCustomQuery()).containsExactly("orders_view");
    }

    @Test
    void mapsTablesToTheDomainOfTheCallingClass() {

        DomainInfo orderDomain = domain("order", "OrderService");
        DomainInfo paymentDomain = domain("payment", "PaymentService");

        List<CrudOperationInfo> crudOperations = List.of(
                crudOp("OrderService", "OrderRepository", "READ", "Order", "orders"),
                crudOp("PaymentService", "PaymentRepository", "READ", "Payment", "payments")
        );

        InsightsReport insights = analyzer.analyze(
                List.of(orderDomain, paymentDomain), List.of(), List.of(), crudOperations
        );

        Map<String, List<String>> tablesByDomain = insights.getTablesByDomain();

        assertThat(tablesByDomain.get("order")).containsExactly("orders");
        assertThat(tablesByDomain.get("payment")).containsExactly("payments");
    }

    @Test
    void ranksSharedTablesByDistinctTouchingClassesDescending() {

        List<CrudOperationInfo> crudOperations = List.of(
                crudOp("OrderService", "OrderRepository", "READ", "Order", "orders"),
                crudOp("OrderController", "OrderRepository", "READ", "Order", "orders"),
                crudOp("OrderReportJob", "OrderRepository", "READ", "Order", "orders"),
                crudOp("PaymentService", "PaymentRepository", "READ", "Payment", "payments")
        );

        InsightsReport insights = analyzer.analyze(
                List.of(), List.of(), List.of(), crudOperations
        );

        List<TableUsageSummary> ranking = insights.getSharedTableRanking();

        assertThat(ranking.get(0).getTableName()).isEqualTo("orders");
        assertThat(ranking.get(0).getTouchingClassCount()).isEqualTo(3);
        assertThat(ranking.get(1).getTableName()).isEqualTo("payments");
        assertThat(ranking.get(1).getTouchingClassCount()).isEqualTo(1);
    }

    @Test
    void extractionRankingPutsCandidatesFirstAndCycleBlockedLast() {

        DomainBoundaryInfo tangled = new DomainBoundaryInfo(
                "billing", 5, 4, 2, 6,
                DomainBoundaryVerdict.TANGLED, "too tangled"
        );

        DomainBoundaryInfo blocked = new DomainBoundaryInfo(
                "order", 5, 1, 1, 2,
                DomainBoundaryVerdict.BLOCKED_BY_CYCLE, "cycle"
        );

        DomainBoundaryInfo cheapCandidate = new DomainBoundaryInfo(
                "inventory", 3, 0, 1, 1,
                DomainBoundaryVerdict.EXTRACTION_CANDIDATE, "isolated"
        );

        DomainBoundaryInfo pricierCandidate = new DomainBoundaryInfo(
                "reporting", 3, 0, 3, 3,
                DomainBoundaryVerdict.EXTRACTION_CANDIDATE, "mostly isolated"
        );

        InsightsReport insights = analyzer.analyze(
                List.of(), List.of(tangled, blocked, cheapCandidate, pricierCandidate),
                List.of(), List.of()
        );

        assertThat(insights.getExtractionRanking())
                .extracting(DomainBoundaryInfo::getDomainName)
                .containsExactly("inventory", "reporting", "billing", "order");
    }

    @Test
    void flagsAMethodTouchingMultipleTablesAsAMultiTableTransaction() {

        DomainInfo billDomain = domain("Bill", "HospitalGodService", "BillEntity");
        DomainInfo patientDomain = domain("Patient", "PatientEntity");

        List<CrudOperationInfo> crudOperations = List.of(
                crudOp("HospitalGodService", "BillRepository", "UPDATE", "BillEntity", "BILL_TBL"),
                crudOp("HospitalGodService", "PatientRepository", "UPDATE", "PatientEntity", "PAT_MSTR")
        );

        InsightsReport insights = analyzer.analyze(
                List.of(billDomain, patientDomain), List.of(), List.of(), crudOperations
        );

        List<MultiTableTransaction> transactions = insights.getMultiTableTransactions();

        assertThat(transactions).hasSize(1);

        MultiTableTransaction transaction = transactions.get(0);

        assertThat(transaction.getClassName()).isEqualTo("HospitalGodService");
        assertThat(transaction.getTables()).containsExactlyInAnyOrder("BILL_TBL", "PAT_MSTR");
        assertThat(transaction.isSpansMultipleDomains()).isTrue();
    }

    @Test
    void doesNotFlagAMethodTouchingOnlyOneTable() {

        List<CrudOperationInfo> crudOperations = List.of(
                crudOp("OrderService", "OrderRepository", "READ", "Order", "orders"),
                crudOp("OrderService", "OrderRepository", "UPDATE", "Order", "orders")
        );

        InsightsReport insights = analyzer.analyze(
                List.of(), List.of(), List.of(), crudOperations
        );

        assertThat(insights.getMultiTableTransactions()).isEmpty();
    }

    private DomainInfo domain(String name, String... classNames) {

        DomainInfo domain = new DomainInfo(name);

        for (String className : classNames) {
            ClassInfo classInfo = new ClassInfo();
            classInfo.setName(className);
            domain.getClasses().add(classInfo);
        }

        return domain;
    }

    private CrudOperationInfo crudOp(
            String sourceClass,
            String repositoryClass,
            String operation,
            String entityClass,
            String tableName) {

        return new CrudOperationInfo(
                sourceClass, "someMethod",
                repositoryClass, "someRepoMethod",
                operation, entityClass, tableName
        );
    }
}
