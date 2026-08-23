package org.example.analyser.analyzer;

import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
import org.example.analyser.model.ClassCouplingInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GodClassAnalyzerTest {

    private final GodClassAnalyzer analyzer = new GodClassAnalyzer();

    @Test
    void flagsAClassAtOrAboveTheOutgoingThreshold() {

        ClassCouplingInfo godFacade =
                new ClassCouplingInfo(
                        "GodFacade", "com.acme", "COMPONENT",
                        GodClassAnalyzer.OUTGOING_DEPENDENCY_THRESHOLD,
                        0, 0
                );

        List<ArchitectureFinding> findings =
                analyzer.analyze(List.of(godFacade));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getType())
                .isEqualTo(ArchitectureFindingType.GOD_CLASS);
        assertThat(findings.get(0).getClasses())
                .containsExactly("GodFacade");
    }

    @Test
    void doesNotFlagAClassBelowTheThreshold() {

        ClassCouplingInfo ordinaryServiceImpl =
                new ClassCouplingInfo(
                        "OrderServiceImpl", "com.acme", "SERVICE",
                        GodClassAnalyzer.OUTGOING_DEPENDENCY_THRESHOLD - 1,
                        1, 5
                );

        List<ArchitectureFinding> findings =
                analyzer.analyze(List.of(ordinaryServiceImpl));

        assertThat(findings).isEmpty();
    }
}
