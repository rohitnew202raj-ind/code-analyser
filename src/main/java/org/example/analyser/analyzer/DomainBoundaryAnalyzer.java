package org.example.analyser.analyzer;

import org.example.analyser.model.DomainBoundaryInfo;
import org.example.analyser.model.DomainBoundaryVerdict;
import org.example.analyser.model.DomainCycle;
import org.example.analyser.model.DomainDependency;
import org.example.analyser.model.DomainInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Suggests microservice-extraction candidates from the domain
 * dependency graph {@code DomainDependencyAnalyzer} already
 * computes: a domain that's largely self-contained (few distinct
 * domains it touches or is touched by, and not part of a
 * domain-level cycle) is a plausible service boundary; a domain
 * tangled with many others, or caught in a cycle, is not - not
 * without restructuring first.
 *
 * THRESHOLD (documented, not configurable in v1): {@value
 * #DISTINCT_DOMAIN_THRESHOLD} distinct connected domains
 * (incoming + outgoing, counted once each regardless of how many
 * dependency edges or types connect the pair). A fixed starting
 * point, not a precise cutoff - the same honest caveat as {@link
 * GodClassAnalyzer}'s threshold, applied one level up.
 *
 * SCOPE (deliberate): this is a purely structural signal. It
 * cannot tell a genuinely cohesive small business domain apart
 * from a domain that's small only because it's a leftover
 * grouping of cross-cutting infrastructure classes (a "common"
 * or "config" package, say) - both look identical from here: low
 * class count, low cross-domain coupling. That distinction
 * requires knowing what the domain is <em>for</em>, which isn't
 * derivable from package structure alone. Read
 * {@code EXTRACTION_CANDIDATE} as "structurally isolated," not as
 * "this is definitely a good business boundary" - the latter
 * still needs a human who knows the domain.
 */
@Component
public class DomainBoundaryAnalyzer {

    static final int DISTINCT_DOMAIN_THRESHOLD = 2;

    public List<DomainBoundaryInfo> analyze(
            List<DomainInfo> domains,
            List<DomainDependency> domainDependencies,
            List<DomainCycle> domainCycles) {

        Map<String, Set<String>> outgoingByDomain = new HashMap<>();
        Map<String, Set<String>> incomingByDomain = new HashMap<>();
        Map<String, Integer> crossDomainEdgeCountByDomain = new HashMap<>();

        for (DomainDependency dependency : domainDependencies) {

            outgoingByDomain
                    .computeIfAbsent(
                            dependency.getSourceDomain(),
                            key -> new HashSet<>()
                    )
                    .add(dependency.getTargetDomain());

            incomingByDomain
                    .computeIfAbsent(
                            dependency.getTargetDomain(),
                            key -> new HashSet<>()
                    )
                    .add(dependency.getSourceDomain());

            crossDomainEdgeCountByDomain.merge(
                    dependency.getSourceDomain(),
                    dependency.getCount(),
                    Integer::sum
            );

            crossDomainEdgeCountByDomain.merge(
                    dependency.getTargetDomain(),
                    dependency.getCount(),
                    Integer::sum
            );
        }

        Set<String> domainsInCycles = new HashSet<>();
        domainCycles.forEach(
                cycle -> domainsInCycles.addAll(cycle.getDomains())
        );

        List<DomainBoundaryInfo> results = new ArrayList<>();

        for (DomainInfo domain : domains) {

            String name = domain.getName();

            Set<String> outgoing =
                    outgoingByDomain.getOrDefault(name, Set.of());

            Set<String> incoming =
                    incomingByDomain.getOrDefault(name, Set.of());

            Set<String> connectedDomains = new HashSet<>(outgoing);
            connectedDomains.addAll(incoming);

            int crossDomainEdgeCount =
                    crossDomainEdgeCountByDomain.getOrDefault(name, 0);

            DomainBoundaryVerdict verdict;
            String reason;

            if (domainsInCycles.contains(name)) {

                verdict = DomainBoundaryVerdict.BLOCKED_BY_CYCLE;
                reason = name + " is part of a circular domain "
                        + "dependency - extraction requires "
                        + "breaking the cycle first";

            } else if (connectedDomains.size()
                    > DISTINCT_DOMAIN_THRESHOLD) {

                verdict = DomainBoundaryVerdict.TANGLED;
                reason = name + " is connected to "
                        + connectedDomains.size()
                        + " other domains (threshold: "
                        + DISTINCT_DOMAIN_THRESHOLD
                        + ") - not a clean boundary as-is";

            } else if (connectedDomains.isEmpty()) {

                verdict = DomainBoundaryVerdict.EXTRACTION_CANDIDATE;
                reason = name + " has no cross-domain dependencies "
                        + "- fully self-contained";

            } else {

                verdict = DomainBoundaryVerdict.EXTRACTION_CANDIDATE;
                reason = name + " is connected to only "
                        + connectedDomains.size()
                        + " other domain(s) - a plausible service "
                        + "boundary";
            }

            results.add(
                    new DomainBoundaryInfo(
                            name,
                            domain.getClassCount(),
                            outgoing.size(),
                            incoming.size(),
                            crossDomainEdgeCount,
                            verdict,
                            reason
                    )
            );
        }

        return results;
    }
}
