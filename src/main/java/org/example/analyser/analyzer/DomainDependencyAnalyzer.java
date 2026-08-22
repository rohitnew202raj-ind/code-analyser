package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DependencyType;
import org.example.analyser.model.DomainDependency;
import org.example.analyser.model.DomainInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DomainDependencyAnalyzer {

    public List<DomainDependency> analyze(
            List<DomainInfo> domains,
            List<DependencyInfo> dependencies) {

        List<DomainDependency> result =
                new ArrayList<>();

        Map<String, DomainInfo> domainMap =
                new LinkedHashMap<>();

        for (DomainInfo domain : domains) {
            domainMap.put(
                    domain.getName(),
                    domain
            );
        }

        for (DependencyInfo dependency : dependencies) {

            // Entity relationships are not treated as
            // service dependencies yet.
            if (dependency.getType()
                    == DependencyType.ENTITY_RELATIONSHIP) {

                continue;
            }

            String sourceDomain =
                    findDomain(
                            domainMap,
                            dependency.getSourceClass()
                    );

            String targetDomain =
                    findDomain(
                            domainMap,
                            dependency.getTargetClass()
                    );

            if (sourceDomain == null
                    || targetDomain == null) {
                continue;
            }

            // Same-domain dependency is not a
            // cross-domain dependency.
            if (sourceDomain.equals(targetDomain)) {
                continue;
            }

            DomainDependency existing =
                    result.stream()
                            .filter(d ->
                                    d.getSourceDomain()
                                            .equals(sourceDomain)
                                            && d.getTargetDomain()
                                            .equals(targetDomain)
                                            && d.getType()
                                            == dependency.getType()
                            )
                            .findFirst()
                            .orElse(null);

            if (existing != null) {

                existing.incrementCount();

            } else {

                result.add(
                        new DomainDependency(
                                sourceDomain,
                                targetDomain,
                                dependency.getType()
                        )
                );
            }
        }

        return result;
    }

    private String findDomain(
            Map<String, DomainInfo> domainMap,
            String className) {

        for (DomainInfo domain :
                domainMap.values()) {

            boolean found =
                    domain.getClasses()
                            .stream()
                            .anyMatch(classInfo ->
                                    classInfo.getName()
                                            .equals(className)
                            );

            if (found) {
                return domain.getName();
            }
        }

        return null;
    }
}