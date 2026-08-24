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
    void skipsGenericWrapperSegmentInsteadOfCollapsingIntoOneDomain() {

        // A project that wraps everything in one non-domain
        // "core" package - com.acme.core.service.X,
        // com.acme.core.repository.X, com.acme.core.dto.X -
        // must not have all three collapse into a single "core"
        // domain: the wrapper segment should be skipped so the
        // real (technical-layer) segment underneath it is used
        // instead, same as if "core" weren't there at all.
        PackageDomainExtractor extractor =
                PackageDomainExtractor.fit(
                        classesIn(
                                "com.acme.core.service",
                                "com.acme.core.repository",
                                "com.acme.core.dto",
                                "com.acme"
                        )
                );

        assertThat(extractor.domainOf("com.acme.core.service"))
                .isEqualTo("service");
        assertThat(extractor.domainOf("com.acme.core.repository"))
                .isEqualTo("repository");
        assertThat(extractor.domainOf("com.acme.core.dto"))
                .isEqualTo("dto");
    }

    @Test
    void recursivelySkipsMultipleWrapperSegmentsInARow() {

        PackageDomainExtractor extractor =
                PackageDomainExtractor.fit(
                        classesIn(
                                "com.acme.core.internal.service",
                                "com.acme"
                        )
                );

        assertThat(extractor.domainOf("com.acme.core.internal.service"))
                .isEqualTo("service");
    }

    @Test
    void fallsBackToCoreWhenNothingRemainsAfterSkippingWrapperSegments() {

        // A class living directly in the wrapper package itself
        // (nothing left to descend into) still gets an honest
        // "core" answer rather than an index-out-of-bounds or a
        // misleading guess.
        PackageDomainExtractor extractor =
                PackageDomainExtractor.fit(
                        classesIn(
                                "com.acme.core",
                                "com.acme.core.service"
                        )
                );

        assertThat(extractor.domainOf("com.acme.core"))
                .isEqualTo("core");
    }

    @Test
    void doesNotSkipSegmentsThatArentGenericWrapperWords() {

        // "orders" is a real (business-looking) segment, not in
        // the generic wrapper list, so it must be used as-is -
        // confirms the wrapper skip is narrowly scoped rather
        // than skipping every single-word package segment.
        PackageDomainExtractor extractor =
                PackageDomainExtractor.fit(
                        classesIn(
                                "com.acme.orders.service",
                                "com.acme.reporting.batch"
                        )
                );

        assertThat(extractor.domainOf("com.acme.orders.service"))
                .isEqualTo("orders");
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
