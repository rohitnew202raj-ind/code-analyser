package org.example.analyser.analyzer;

import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.EntityMutationInfo;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.FlowPath;
import org.example.analyser.model.MethodCallInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Connects the dots the rest of the analyzer only records as
 * separate facts: "X calls Y" (MethodCallAnalyzer), "X touches
 * this table" (CrudAnalyzer/EntityMutationAnalyzer), "X is an
 * entry point" (ApiAnalyzer/BatchAnalyzer). None of those alone
 * answer "when this API is hit, what actually happens end to
 * end" - that requires walking the call graph outward from each
 * entry point until it bottoms out in a database operation.
 *
 * This does a breadth-first walk from each {@link EntryPointInfo}
 * over the {@link MethodCallInfo} edges, collecting every call
 * hop and every {@link CrudOperationInfo}/{@link EntityMutationInfo}
 * reachable along the way into one {@link FlowPath}. It is
 * intentionally a full reachable subgraph, not a single linear
 * chain: a real method almost always fans out into several
 * calls, and forcing that into one "path" would be misleading.
 *
 * LIMITATION (documented, not solved): the call graph this walks
 * is keyed by simple class name, the same representation
 * MethodCallAnalyzer/CrudAnalyzer already use, so it inherits
 * their resolution boundaries. Concretely: when a service field
 * is declared by interface type and the resolved call target is
 * recorded under the interface's name rather than its
 * implementation's (this happens when Symbol Solver resolves to
 * the declared type rather than a concrete implementation), the
 * walk dead-ends there - the implementation's own outgoing
 * calls are recorded under the implementation class's name, not
 * the interface's, so they're simply never reached. A flow that
 * looks short is sometimes a real short flow and sometimes this
 * boundary; both are represented identically (no more edges
 * found from that node) rather than guessed.
 *
 * A second, deliberate safety net: {@link #MAX_VISITED_NODES}
 * caps how large a single walk can grow. Real flows are a
 * handful of hops; hitting this cap means either a pathological
 * fan-out or - more likely - simple-name collisions merging
 * unrelated call chains together. Either way, {@link
 * FlowPath#isTruncated()} says so rather than silently returning
 * a partial result that looks complete.
 */
@Component
public class FlowEngine {

    private static final int MAX_VISITED_NODES = 2000;

    public List<FlowPath> analyze(
            List<EntryPointInfo> entryPoints,
            List<MethodCallInfo> methodCalls,
            List<CrudOperationInfo> crudOperations,
            List<EntityMutationInfo> entityMutations) {

        Map<String, List<MethodCallInfo>> callsBySource =
                groupByNode(
                        methodCalls,
                        MethodCallInfo::getSourceClass,
                        MethodCallInfo::getSourceMethod
                );

        Map<String, List<CrudOperationInfo>> crudBySource =
                groupByNode(
                        crudOperations,
                        CrudOperationInfo::getSourceClass,
                        CrudOperationInfo::getSourceMethod
                );

        Map<String, List<EntityMutationInfo>> mutationsBySource =
                groupByNode(
                        entityMutations,
                        EntityMutationInfo::getSourceClass,
                        EntityMutationInfo::getSourceMethod
                );

        List<FlowPath> paths = new ArrayList<>();

        for (EntryPointInfo entryPoint : entryPoints) {

            paths.add(
                    traceFlow(
                            entryPoint,
                            callsBySource,
                            crudBySource,
                            mutationsBySource
                    )
            );
        }

        return paths;
    }

    private FlowPath traceFlow(
            EntryPointInfo entryPoint,
            Map<String, List<MethodCallInfo>> callsBySource,
            Map<String, List<CrudOperationInfo>> crudBySource,
            Map<String, List<EntityMutationInfo>> mutationsBySource) {

        Set<String> visited = new LinkedHashSet<>();
        Deque<String> toVisit = new ArrayDeque<>();

        List<MethodCallInfo> steps = new ArrayList<>();
        List<CrudOperationInfo> databaseOperations = new ArrayList<>();
        List<EntityMutationInfo> reachedMutations = new ArrayList<>();
        boolean truncated = false;

        toVisit.add(
                nodeKey(
                        entryPoint.getClassName(),
                        entryPoint.getMethodName()
                )
        );

        while (!toVisit.isEmpty()) {

            String node = toVisit.poll();

            if (!visited.add(node)) {
                // Already walked this node - avoids re-expanding
                // it (and protects against a call cycle looping
                // forever).
                continue;
            }

            databaseOperations.addAll(
                    crudBySource.getOrDefault(node, List.of())
            );

            reachedMutations.addAll(
                    mutationsBySource.getOrDefault(node, List.of())
            );

            if (visited.size() >= MAX_VISITED_NODES) {
                truncated = true;
                break;
            }

            for (MethodCallInfo call :
                    callsBySource.getOrDefault(node, List.of())) {

                steps.add(call);

                String nextNode =
                        nodeKey(
                                call.getTargetClass(),
                                call.getTargetMethod()
                        );

                if (!visited.contains(nextNode)) {
                    toVisit.add(nextNode);
                }
            }
        }

        return new FlowPath(
                entryPoint,
                steps,
                databaseOperations,
                reachedMutations,
                truncated
        );
    }

    private String nodeKey(String className, String methodName) {
        return className + "." + methodName;
    }

    private <T> Map<String, List<T>> groupByNode(
            List<T> items,
            Function<T, String> classNameOf,
            Function<T, String> methodNameOf) {

        Map<String, List<T>> byNode = new HashMap<>();

        for (T item : items) {

            String key =
                    nodeKey(
                            classNameOf.apply(item),
                            methodNameOf.apply(item)
                    );

            byNode.computeIfAbsent(key, unused -> new ArrayList<>())
                    .add(item);
        }

        return byNode;
    }
}
