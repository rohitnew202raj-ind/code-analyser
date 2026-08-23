package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PackageDomainExtractorTest {

    @Test
    void extractsDomainAfterCommonPrefix() {

        PackageDomainExtractor extractor =
                PackageDomainExtractor.fit(
                        classesIn(
                                "com.acme.orders.web",
                                "com.acme.orders.service",
                                "com.acme.reporting.batch"
                        )
                );

        assertThat(extractor.domainOf("com.acme.orders.web"))
                .isEqualTo("orders");

        assertThat(extractor.domainOf("com.acme.reporting.batch"))
                .isEqualTo("reporting");
    }

    @Test
    void fallsBackToCoreWhenPackageIsExactlyThePrefix() {

        PackageDomainExtractor extractor =
                PackageDomainExtractor.fit(
                        classesIn(
                                "com.acme.orders.web",
                                "com.acme.orders.service"
                        )
                );

        assertThat(extractor.domainOf("com.acme.orders"))
                .isEqualTo("core");
    }

    @Test
    void handlesSinglePackageProject() {

        PackageDomainExtractor extractor =
                PackageDomainExtractor.fit(
                        classesIn("com.acme.app")
                );

        assertThat(extractor.domainOf("com.acme.app"))
                .isEqualTo("core");
    }

    @Test
    void handlesNullOrBlankPackage() {

        PackageDomainExtractor extractor =
                PackageDomainExtractor.fit(
                        classesIn("com.acme.orders.web")
                );

        assertThat(extractor.domainOf(null)).isEqualTo("core");
        assertThat(extractor.domainOf("")).isEqualTo("core");
    }

    private List<ClassInfo> classesIn(String... packageNames) {

        return List.of(packageNames).stream()
                .map(packageName -> {
                    ClassInfo classInfo = new ClassInfo();
                    classInfo.setPackageName(packageName);
                    classInfo.setName("Dummy");
                    return classInfo;
                })
                .toList();
    }
}
