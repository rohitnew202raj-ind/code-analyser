package org.example.analyser.analyzer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds strongly connected components of size &gt; 1 in a
 * directed graph - shared by {@code CircularDependencyAnalyzer}
 * (class-level) and {@code DomainCircularDependencyAnalyzer}
 * (domain-level), since "find the cycles" is the exact same
 * algorithm at both granularities and a correctness-sensitive
 * graph traversal is exactly the kind of thing that shouldn't
 * exist as two independently-maintained copies.
 *
 * Standard Tarjan's SCC, written recursively for clarity: graphs
 * at any realistic project scale (classes or domains) are
 * nowhere near deep enough to risk a stack overflow.
 *
 * A strongly connected component of size &gt; 1 is, by
 * definition, a cycle: every node in it can reach every other
 * node in it by following edges. This reports whole components
 * rather than enumerating every individual elementary cycle
 * path through them - a tightly coupled group of N nodes can
 * contain an exponential number of distinct cycles, and listing
 * them all would bury the one thing that actually matters
 * ("these are mutually entangled") under noise.
 */
final class GraphCycleFinder {

    private GraphCycleFinder() {
    }

    static List<List<String>> findCycles(
            Set<String> nodes,
            Map<String, List<String>> adjacency) {

        Set<String> allNodes = new LinkedHashSet<>(nodes);
        allNodes.addAll(adjacency.keySet());

        TarjanState state = new TarjanState();

        for (String node : allNodes) {

            if (!state.index.containsKey(node)) {
                strongConnect(node, adjacency, state);
            }
        }

        List<List<String>> cycles = new ArrayList<>();

        for (List<String> component : state.components) {

            if (component.size() >= 2) {
                cycles.add(component);
            }
        }

        return cycles;
    }

    private static void strongConnect(
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
