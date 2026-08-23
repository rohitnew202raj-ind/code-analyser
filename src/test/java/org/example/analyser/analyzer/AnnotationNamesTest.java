package org.example.analyser.analyzer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationNamesTest {

    @Test
    void keepsSimpleNameAsIs() {
        assertThat(AnnotationNames.simpleName("RestController"))
                .isEqualTo("RestController");
    }

    @Test
    void stripsFullyQualifiedPrefix() {
        assertThat(
                AnnotationNames.simpleName(
                        "org.springframework.web.bind.annotation.RestController"
                )
        ).isEqualTo("RestController");
    }

    @Test
    void handlesNull() {
        assertThat(AnnotationNames.simpleName((String) null)).isNull();
    }
}
