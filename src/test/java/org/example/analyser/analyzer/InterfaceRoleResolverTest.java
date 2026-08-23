package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceRoleResolverTest {

    private final InterfaceRoleResolver resolver =
            new InterfaceRoleResolver();

    @Test
    void propagatesServiceRoleFromSingleImplementorToInterface() {

        ClassInfo orderService = new ClassInfo();
        orderService.setName("OrderService");
        orderService.setType("UNKNOWN");

        ClassInfo orderServiceImpl = new ClassInfo();
        orderServiceImpl.setName("OrderServiceImpl");
        orderServiceImpl.setType("SERVICE");
        orderServiceImpl.getImplementedTypes().add("OrderService");

        resolver.resolve(List.of(orderService, orderServiceImpl));

        assertThat(orderService.getType()).isEqualTo("SERVICE");
        assertThat(orderService.getRoles()).contains("SERVICE");
    }

    @Test
    void leavesInterfaceUnresolvedWhenImplementorsConflict() {

        ClassInfo paymentGateway = new ClassInfo();
        paymentGateway.setName("PaymentGateway");
        paymentGateway.setType("UNKNOWN");

        ClassInfo serviceImpl = new ClassInfo();
        serviceImpl.setName("ServiceImpl");
        serviceImpl.setType("SERVICE");
        serviceImpl.getImplementedTypes().add("PaymentGateway");

        ClassInfo repositoryImpl = new ClassInfo();
        repositoryImpl.setName("RepositoryImpl");
        repositoryImpl.setType("REPOSITORY");
        repositoryImpl.getImplementedTypes().add("PaymentGateway");

        resolver.resolve(
                List.of(paymentGateway, serviceImpl, repositoryImpl)
        );

        assertThat(paymentGateway.getType()).isEqualTo("UNKNOWN");
    }

    @Test
    void doesNotOverrideAlreadyClassifiedInterface() {

        // Pathological but shouldn't happen: an interface that
        // somehow already resolved to REPOSITORY shouldn't be
        // downgraded by a SERVICE implementor.
        ClassInfo someInterface = new ClassInfo();
        someInterface.setName("SomeInterface");
        someInterface.setType("REPOSITORY");

        ClassInfo impl = new ClassInfo();
        impl.setName("Impl");
        impl.setType("SERVICE");
        impl.getImplementedTypes().add("SomeInterface");

        resolver.resolve(List.of(someInterface, impl));

        assertThat(someInterface.getType()).isEqualTo("REPOSITORY");
    }

    @Test
    void ignoresInterfacesNotPresentInScannedProject() {

        ClassInfo impl = new ClassInfo();
        impl.setName("Impl");
        impl.setType("SERVICE");
        impl.getImplementedTypes().add("SomeExternalLibraryInterface");

        // Should not throw.
        resolver.resolve(List.of(impl));
    }
}
