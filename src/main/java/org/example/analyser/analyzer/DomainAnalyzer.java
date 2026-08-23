package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DomainInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DomainAnalyzer {

    public List<DomainInfo> analyze(
            List<ClassInfo> classes) {

        PackageDomainExtractor domainExtractor =
                PackageDomainExtractor.fit(classes);

        Map<String, DomainInfo> domains =
                new LinkedHashMap<>();

        for (ClassInfo classInfo : classes) {

            String domain =
                    domainExtractor.domainOf(
                            classInfo.getPackageName()
                    );

            domains.computeIfAbsent(
                    domain,
                    DomainInfo::new
            );

            domains.get(domain)
                    .getClasses()
                    .add(classInfo);
        }

        return new ArrayList<>(
                domains.values()
        );
    }
}
