package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.ClassCouplingInfo;
import org.example.analyser.model.DependencyGraph;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.MethodCallInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CouplingAnalyzer {

    public List<ClassCouplingInfo> analyze(
            DependencyGraph graph,
            List<MethodCallInfo> methodCalls) {

        /*
         * Previously O(nodes * edges): for every class, scan
         * every edge. At real-monolith scale (thousands of
         * classes, tens of thousands of edges) that's the
         * actual bottleneck of a run. A single pass building
         * per-class tallies is O(edges + nodes) instead.
         */

        Map<String, Integer> outgoingByClass =
                new HashMap<>();

        Map<String, Integer> incomingByClass =
                new HashMap<>();

        for (DependencyInfo edge : graph.getEdges()) {

            outgoingByClass.merge(
                    edge.getSourceClass(), 1, Integer::sum
            );

            incomingByClass.merge(
                    edge.getTargetClass(), 1, Integer::sum
            );
        }

        Map<String, Integer> callWeightByClass =
                new HashMap<>();

        for (MethodCallInfo call : methodCalls) {

            callWeightByClass.merge(
                    call.getSourceClass(), 1, Integer::sum
            );

            callWeightByClass.merge(
                    call.getTargetClass(), 1, Integer::sum
            );
        }

        List<ClassCouplingInfo> results =
                new ArrayList<>();

        for (ClassInfo classInfo : graph.getNodes()) {

            int outgoing =
                    outgoingByClass.getOrDefault(
                            classInfo.getName(), 0
                    );

            int incoming =
                    incomingByClass.getOrDefault(
                            classInfo.getName(), 0
                    );

            int callWeight =
                    callWeightByClass.getOrDefault(
                            classInfo.getName(), 0
                    );

            results.add(
                    new ClassCouplingInfo(
                            classInfo.getName(),
                            classInfo.getPackageName(),
                            classInfo.getType(),
                            outgoing,
                            incoming,
                            callWeight
                    )
            );
        }

        return results;
    }
}
