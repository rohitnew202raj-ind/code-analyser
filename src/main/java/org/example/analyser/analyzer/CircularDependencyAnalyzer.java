package org.example.analyser.analyzer;

import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
import org.example.analyser.model.DependencyGraph;
import org.example.analyser.model.DependencyInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds circular dependencies - groups of classes that
 * transitively depend on each other - using {@link
 * GraphCycleFinder} (Tarjan's strongly connected components)
 * over the application dependency graph.
 */
@Component
public class CircularDependencyAnalyzer {

    public List<ArchitectureFinding> analyze(DependencyGraph graph) {

        Map<String, List<String>> adjacency = new HashMap<>();

        for (DependencyInfo edge : graph.getEdges()) {

            adjacency
                    .computeIfAbsent(
                            edge.getSourceClass(),
                            key -> new ArrayList<>()
                    )
                    .add(edge.getTargetClass());
        }

        Set<String> nodes = new LinkedHashSet<>();
        graph.getNodes().forEach(node -> nodes.add(node.getName()));

        List<ArchitectureFinding> findings = new ArrayList<>();

        for (List<String> component :
                GraphCycleFinder.findCycles(nodes, adjacency)) {

            List<String> sorted = new ArrayList<>(component);
            Collections.sort(sorted);

            findings.add(
                    new ArchitectureFinding(
                            ArchitectureFindingType.CIRCULAR_DEPENDENCY,
                            sorted,
                            "Circular dependency among " + sorted.size()
                                    + " classes: "
                                    + String.join(", ", sorted)
                    )
            );
        }

        return findings;
    }
}
