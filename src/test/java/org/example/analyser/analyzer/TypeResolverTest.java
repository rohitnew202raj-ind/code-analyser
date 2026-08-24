package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TypeResolverTest {

    private final TypeResolver typeResolver = new TypeResolver();

    @Test
    void recognizesAClassPresentInTheScannedProject() {

        ClassInfo orderService = new ClassInfo();
        orderService.setName("OrderService");

        assertThat(
                typeResolver.isApplicationClass(
                        "OrderService", List.of(orderService)
                )
        ).isTrue();
    }

    @Test
    void doesNotRecognizeAnExternalLibraryOrJdkType() {

        ClassInfo orderService = new ClassInfo();
        orderService.setName("OrderService");

        assertThat(
                typeResolver.isApplicationClass(
                        "ArrayList", List.of(orderService)
                )
        ).isFalse();
    }

    @Test
    void normalizeTypeNameStripsGenericsAndPackagePrefix() {

        assertThat(typeResolver.normalizeTypeName("Optional<OrderEntity>"))
                .isEqualTo("Optional");

        assertThat(typeResolver.normalizeTypeName("com.acme.order.OrderEntity"))
                .isEqualTo("OrderEntity");

        assertThat(typeResolver.normalizeTypeName(null)).isNull();
    }
}
