package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DependencyType;
import org.example.analyser.model.DomainDependency;
import org.example.analyser.model.DomainInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DomainDependencyAnalyzer {

    public List<DomainDependency> analyze(
            List<DomainInfo> domains,
            List<DependencyInfo> dependencies) {

        List<DomainDependency> result =
                new ArrayList<>();

        // Class name -> domain name, built once instead of
        // scanning every domain's class list per dependency.
        Map<String, String> domainByClassName =
                new HashMap<>();

        for (DomainInfo domain : domains) {

            for (ClassInfo classInfo : domain.getClasses()) {

                domainByClassName.put(
                        classInfo.getName(),
                        domain.getName()
                );
            }
        }

        Map<String, DomainDependency> byKey =
                new HashMap<>();

        for (DependencyInfo dependency : dependencies) {

            // Entity relationships are not treated as
            // service dependencies yet.
            if (dependency.getType()
                    == DependencyType.ENTITY_RELATIONSHIP) {

                continue;
            }

            String sourceDomain =
                    domainByClassName.get(
                            dependency.getSourceClass()
                    );

            String targetDomain =
                    domainByClassName.get(
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

            String key =
                    sourceDomain
                            + "->"
                            + targetDomain
                            + ":"
                            + dependency.getType();

            DomainDependency existing =
                    byKey.get(key);

            if (existing != null) {

                existing.incrementCount();

            } else {

                DomainDependency created =
                        new DomainDependency(
                                sourceDomain,
                                targetDomain,
                                dependency.getType()
                        );

                byKey.put(key, created);
                result.add(created);
            }
        }

        return result;
    }
}
