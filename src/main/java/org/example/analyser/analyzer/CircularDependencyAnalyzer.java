package org.example.analyser.analyzer;

import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
import org.example.analyser.model.DependencyGraph;
import org.example.analyser.model.DependencyInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds circular dependencies - groups of classes that
 * transitively depend on each other - using Tarjan's strongly
 * connected components algorithm over the application dependency
 * graph. A strongly connected component of size &gt; 1 is, by
 * definition, a cycle: every class in it can reach every other
 * class in it by following dependency edges.
 *
 * This reports whole strongly connected components rather than
 * enumerating every individual cycle path through them - a
 * component of N tightly coupled classes can contain an
 * exponential number of distinct elementary cycles, and listing
 * them all would bury the one thing that actually matters
 * ("these classes are mutually entangled") under noise.
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

        TarjanState state = new TarjanState();

        for (String node : nodeNames(graph, adjacency)) {

            if (!state.index.containsKey(node)) {
                strongConnect(node, adjacency, state);
            }
        }

        List<ArchitectureFinding> findings = new ArrayList<>();

        for (List<String> component : state.components) {

            if (component.size() < 2) {
                continue;
            }

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

    private Set<String> nodeNames(
            DependencyGraph graph,
            Map<String, List<String>> adjacency) {

        Set<String> names = new LinkedHashSet<>();

        graph.getNodes()
                .forEach(node -> names.add(node.getName()));

        names.addAll(adjacency.keySet());

        return names;
    }

    /*
     * Standard iterative-friendly Tarjan's SCC, written
     * recursively for clarity: dependency chains at any
     * realistic project scale are nowhere near deep enough to
     * risk a stack overflow.
     */
    private void strongConnect(
            String node,
            Map<String, List<String>> adjacency,
            TarjanState state) {

        state.index.put(node, state.counter);
        state.lowlink.put(node, state.counter);
        state.counter++;
        state.stack.push(node);
        state.onStack.add(node);

        for (String neighbor :
                adjacency.getOrDefault(node, List.of())) {

            if (!state.index.containsKey(neighbor)) {

                strongConnect(neighbor, adjacency, state);

                state.lowlink.put(
                        node,
                        Math.min(
                                state.lowlink.get(node),
                                state.lowlink.get(neighbor)
                        )
                );

            } else if (state.onStack.contains(neighbor)) {

                state.lowlink.put(
                        node,
                        Math.min(
                                state.lowlink.get(node),
                                state.index.get(neighbor)
                        )
                );
            }
        }

        if (state.lowlink.get(node).equals(state.index.get(node))) {

            List<String> component = new ArrayList<>();
            String member;

            do {
                member = state.stack.pop();
                state.onStack.remove(member);
                component.add(member);
            } while (!member.equals(node));

            state.components.add(component);
        }
    }

    private static final class TarjanState {

        final Map<String, Integer> index = new HashMap<>();
        final Map<String, Integer> lowlink = new HashMap<>();
        final Deque<String> stack = new ArrayDeque<>();
        final Set<String> onStack = new HashSet<>();
        final List<List<String>> components = new ArrayList<>();
        int counter = 0;
    }
}
