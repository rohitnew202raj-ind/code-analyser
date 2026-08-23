package org.example.analyser.analyzer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetaAnnotationResolverTest {

    @Test
    void directAnnotationCarriesItself() {

        MetaAnnotationResolver resolver =
                new MetaAnnotationResolver(Map.of());

        assertThat(resolver.carries("RestController", "RestController"))
                .isTrue();
    }

    @Test
    void resolvesComposedAnnotationTransitively() {

        // @ApiController carries @RestController
        MetaAnnotationResolver resolver =
                new MetaAnnotationResolver(
                        Map.of(
                                "ApiController",
                                java.util.List.of("RestController", "RequestMapping")
                        )
                );

        assertThat(resolver.carries("ApiController", "RestController"))
                .isTrue();

        assertThat(resolver.carries("ApiController", "Service"))
                .isFalse();
    }

    @Test
    void guardsAgainstCycles() {

        // Pathological but shouldn't infinite-loop: A carries
        // B, B carries A.
        MetaAnnotationResolver resolver =
                new MetaAnnotationResolver(
                        Map.of(
                                "A", java.util.List.of("B"),
                                "B", java.util.List.of("A")
                        )
                );

        assertThat(resolver.carries("A", "SomethingElse")).isFalse();
    }
}
