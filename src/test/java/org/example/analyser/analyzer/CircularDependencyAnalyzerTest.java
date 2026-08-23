package org.example.analyser.analyzer;

import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyGraph;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DependencyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CircularDependencyAnalyzerTest {

    private final CircularDependencyAnalyzer analyzer =
            new CircularDependencyAnalyzer();

    @Test
    void detectsATwoClassCycle() {

        ClassInfo a = classNamed("ServiceA");
        ClassInfo b = classNamed("ServiceB");

        DependencyGraph graph = new DependencyGraph(
                List.of(a, b),
                List.of(
                        edge("ServiceA", "ServiceB"),
                        edge("ServiceB", "ServiceA")
                )
        );

        List<ArchitectureFinding> findings = analyzer.analyze(graph);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getType())
                .isEqualTo(ArchitectureFindingType.CIRCULAR_DEPENDENCY);
        assertThat(findings.get(0).getClasses())
                .containsExactlyInAnyOrder("ServiceA", "ServiceB");
    }

    @Test
    void doesNotFlagALinearChainAsCircular() {

        ClassInfo a = classNamed("Controller");
        ClassInfo b = classNamed("Service");
        ClassInfo c = classNamed("Repository");

        DependencyGraph graph = new DependencyGraph(
                List.of(a, b, c),
                List.of(
                        edge("Controller", "Service"),
                        edge("Service", "Repository")
                )
        );

        List<ArchitectureFinding> findings = analyzer.analyze(graph);

        assertThat(findings).isEmpty();
    }

    @Test
    void detectsALargerCycleAsOneFinding() {

        DependencyGraph graph = new DependencyGraph(
                List.of(
                        classNamed("A"), classNamed("B"), classNamed("C")
                ),
                List.of(
                        edge("A", "B"),
                        edge("B", "C"),
                        edge("C", "A")
                )
        );

        List<ArchitectureFinding> findings = analyzer.analyze(graph);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getClasses())
                .containsExactlyInAnyOrder("A", "B", "C");
    }

    private ClassInfo classNamed(String name) {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setName(name);
        return classInfo;
    }

    private DependencyInfo edge(String source, String target) {
        return new DependencyInfo(
                source, target, "field", DependencyType.SERVICE_DEPENDENCY
        );
    }
}
