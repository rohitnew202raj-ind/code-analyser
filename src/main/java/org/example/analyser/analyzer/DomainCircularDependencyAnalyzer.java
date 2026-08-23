package org.example.analyser.analyzer;

import org.example.analyser.model.DomainCycle;
import org.example.analyser.model.DomainDependency;
import org.example.analyser.model.DomainInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds circular dependencies between domains - the same signal
 * {@link CircularDependencyAnalyzer} surfaces at the class
 * level (see {@link GraphCycleFinder}), just one level up.
 *
 * A domain cycle is a much stronger disqualifier for
 * microservice extraction than raw coupling numbers: two
 * domains that depend on each other cannot be split into
 * separate deployable services without either merging them back
 * together or restructuring the dependency first - a
 * synchronous cross-service call cycle is a distributed-systems
 * hazard (coupled deploys, potential deadlock under synchronous
 * calls), not just an untidy dependency graph. {@link
 * DomainBoundaryAnalyzer} treats membership in a cycle as an
 * automatic disqualifier, overriding whatever the raw coupling
 * count would otherwise suggest.
 */
@Component
public class DomainCircularDependencyAnalyzer {

    public List<DomainCycle> analyze(
            List<DomainInfo> domains,
            List<DomainDependency> domainDependencies) {

        Map<String, List<String>> adjacency = new HashMap<>();

        for (DomainDependency dependency : domainDependencies) {

            adjacency
                    .computeIfAbsent(
                            dependency.getSourceDomain(),
                            key -> new ArrayList<>()
                    )
                    .add(dependency.getTargetDomain());
        }

        Set<String> nodes = new LinkedHashSet<>();
        domains.forEach(domain -> nodes.add(domain.getName()));

        List<DomainCycle> cycles = new ArrayList<>();

        for (List<String> component :
                GraphCycleFinder.findCycles(nodes, adjacency)) {

            List<String> sorted = new ArrayList<>(component);
            Collections.sort(sorted);

            cycles.add(
                    new DomainCycle(
                            sorted,
                            "Circular dependency among " + sorted.size()
                                    + " domains: "
                                    + String.join(", ", sorted)
                    )
            );
        }

        return cycles;
    }
}
