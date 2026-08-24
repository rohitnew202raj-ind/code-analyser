package org.example.analyser.analyzer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassNameDomainExtractorTest {

    @Test
    void stripsControllerServiceRepositorySuffixesToTheSameDomain() {

        assertThat(ClassNameDomainExtractor.domainOf("PatientController"))
                .isEqualTo("Patient");

        assertThat(ClassNameDomainExtractor.domainOf("PatientService"))
                .isEqualTo("Patient");

        assertThat(ClassNameDomainExtractor.domainOf("PatientRepository"))
                .isEqualTo("Patient");
    }

    @Test
    void preferLongerCompoundSuffixOverShorterOne() {

        // "ServiceImpl" must be matched before the shorter "Impl"
        // grabs it and leaves a misleading "PatientService".
        assertThat(ClassNameDomainExtractor.domainOf("PatientServiceImpl"))
                .isEqualTo("Patient");

        assertThat(ClassNameDomainExtractor.domainOf("PatientRepositoryImpl"))
                .isEqualTo("Patient");
    }

    @Test
    void returnsNullWhenNoKnownSuffixMatches() {

        assertThat(ClassNameDomainExtractor.domainOf("Zenith")).isNull();
        assertThat(ClassNameDomainExtractor.domainOf("OrderFlowRunner")).isNull();
    }

    @Test
    void returnsNullWhenSuffixConsumesTheWholeName() {

        assertThat(ClassNameDomainExtractor.domainOf("Controller")).isNull();
        assertThat(ClassNameDomainExtractor.domainOf("Service")).isNull();
    }

    @Test
    void returnsNullForNullOrBlankInput() {

        assertThat(ClassNameDomainExtractor.domainOf(null)).isNull();
        assertThat(ClassNameDomainExtractor.domainOf("")).isNull();
    }

    @Test
    void recognizesTechnicalLayerWordsCaseInsensitively() {

        assertThat(ClassNameDomainExtractor.isTechnicalLayerWord("controller"))
                .isTrue();

        assertThat(ClassNameDomainExtractor.isTechnicalLayerWord("Repository"))
                .isTrue();

        assertThat(ClassNameDomainExtractor.isTechnicalLayerWord("ENTITY"))
                .isTrue();

        assertThat(ClassNameDomainExtractor.isTechnicalLayerWord("orders"))
                .isFalse();

        assertThat(ClassNameDomainExtractor.isTechnicalLayerWord(null))
                .isFalse();
    }
}
