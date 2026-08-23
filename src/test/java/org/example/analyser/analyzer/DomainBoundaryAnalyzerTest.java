package org.example.analyser.analyzer;

import org.example.analyser.model.DependencyType;
import org.example.analyser.model.DomainBoundaryInfo;
import org.example.analyser.model.DomainBoundaryVerdict;
import org.example.analyser.model.DomainCycle;
import org.example.analyser.model.DomainDependency;
import org.example.analyser.model.DomainInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainBoundaryAnalyzerTest {

    private final DomainBoundaryAnalyzer analyzer =
            new DomainBoundaryAnalyzer();

    @Test
    void flagsADomainWithNoCrossDomainDependenciesAsExtractionCandidate() {

        DomainInfo isolated = new DomainInfo("wishlist");
        isolated.getClasses().add(classInDomain());

        List<DomainBoundaryInfo> results =
                analyzer.analyze(
                        List.of(isolated), List.of(), List.of()
                );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getVerdict())
                .isEqualTo(DomainBoundaryVerdict.EXTRACTION_CANDIDATE);
    }

    @Test
    void flagsADomainConnectedToTooManyOthersAsTangled() {

        DomainInfo common = new DomainInfo("common");

        List<DomainInfo> domains = List.of(
                common,
                new DomainInfo("user"),
                new DomainInfo("order"),
                new DomainInfo("payment")
        );

        List<DomainDependency> dependencies = List.of(
                new DomainDependency(
                        "common", "user", DependencyType.SERVICE_DEPENDENCY
                ),
                new DomainDependency(
                        "common", "order", DependencyType.SERVICE_DEPENDENCY
                ),
                new DomainDependency(
                        "common", "payment", DependencyType.SERVICE_DEPENDENCY
                )
        );

        List<DomainBoundaryInfo> results =
                analyzer.analyze(domains, dependencies, List.of());

        DomainBoundaryInfo commonResult =
                byName(results, "common");

        assertThat(commonResult.getVerdict())
                .isEqualTo(DomainBoundaryVerdict.TANGLED);
    }

    @Test
    void doesNotFlagADomainAtExactlyTheThresholdAsTangled() {

        List<DomainInfo> domains = List.of(
                new DomainInfo("address"),
                new DomainInfo("category"),
                new DomainInfo("coupon")
        );

        List<DomainDependency> dependencies = List.of(
                new DomainDependency(
                        "address", "category", DependencyType.SERVICE_DEPENDENCY
                ),
                new DomainDependency(
                        "address", "coupon", DependencyType.SERVICE_DEPENDENCY
                )
        );

        List<DomainBoundaryInfo> results =
                analyzer.analyze(domains, dependencies, List.of());

        assertThat(byName(results, "address").getOutgoingDomainDependencies())
                .isEqualTo(2);
        assertThat(byName(results, "address").getVerdict())
                .isEqualTo(DomainBoundaryVerdict.EXTRACTION_CANDIDATE);
    }

    @Test
    void aDomainInACycleIsBlockedRegardlessOfCouplingCount() {

        List<DomainInfo> domains =
                List.of(new DomainInfo("address"), new DomainInfo("category"));

        List<DomainDependency> dependencies = List.of(
                new DomainDependency(
                        "address", "category", DependencyType.SERVICE_DEPENDENCY
                )
        );

        List<DomainCycle> cycles = List.of(
                new DomainCycle(
                        List.of("address", "category"),
                        "Circular dependency among 2 domains: address, category"
                )
        );

        List<DomainBoundaryInfo> results =
                analyzer.analyze(domains, dependencies, cycles);

        assertThat(byName(results, "address").getVerdict())
                .isEqualTo(DomainBoundaryVerdict.BLOCKED_BY_CYCLE);
        assertThat(byName(results, "category").getVerdict())
                .isEqualTo(DomainBoundaryVerdict.BLOCKED_BY_CYCLE);
    }

    private DomainBoundaryInfo byName(
            List<DomainBoundaryInfo> results, String name) {

        return results.stream()
                .filter(result -> result.getDomainName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private org.example.analyser.model.ClassInfo classInDomain() {
        org.example.analyser.model.ClassInfo classInfo =
                new org.example.analyser.model.ClassInfo();
        classInfo.setName("WishlistService");
        return classInfo;
    }
}
