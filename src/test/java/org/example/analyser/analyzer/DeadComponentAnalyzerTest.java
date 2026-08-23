package org.example.analyser.analyzer;

import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
import org.example.analyser.model.ClassCouplingInfo;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.EntryPointInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeadComponentAnalyzerTest {

    private final DeadComponentAnalyzer analyzer =
            new DeadComponentAnalyzer();

    @Test
    void flagsAServiceWithNoIncomingDependenciesAndNoInterface() {

        ClassInfo orphanService = new ClassInfo();
        orphanService.setName("OrphanService");
        orphanService.setType("SERVICE");

        ClassCouplingInfo coupling = new ClassCouplingInfo(
                "OrphanService", "com.acme", "SERVICE", 3, 0, 0
        );

        List<ArchitectureFinding> findings =
                analyzer.analyze(
                        List.of(orphanService),
                        List.of(coupling),
                        List.of()
                );

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getType())
                .isEqualTo(ArchitectureFindingType.DEAD_COMPONENT);
        assertThat(findings.get(0).getClasses())
                .containsExactly("OrphanService");
    }

    @Test
    void doesNotFlagAnImplementationOfAClassifiedInterfaceEvenWithZeroIncoming() {

        // The standard Spring "program to an interface" shape:
        // OrderService (the interface) carries the real incoming
        // edges; OrderServiceImpl itself legitimately has none.
        ClassInfo orderService = new ClassInfo();
        orderService.setName("OrderService");
        orderService.setType("SERVICE");

        ClassInfo orderServiceImpl = new ClassInfo();
        orderServiceImpl.setName("OrderServiceImpl");
        orderServiceImpl.setType("SERVICE");
        orderServiceImpl.getImplementedTypes().add("OrderService");

        ClassCouplingInfo interfaceCoupling = new ClassCouplingInfo(
                "OrderService", "com.acme", "SERVICE", 0, 3, 3
        );

        ClassCouplingInfo implCoupling = new ClassCouplingInfo(
                "OrderServiceImpl", "com.acme", "SERVICE", 4, 0, 4
        );

        List<ArchitectureFinding> findings =
                analyzer.analyze(
                        List.of(orderService, orderServiceImpl),
                        List.of(interfaceCoupling, implCoupling),
                        List.of()
                );

        assertThat(findings).isEmpty();
    }

    @Test
    void doesNotFlagAClassThatIsItselfAnEntryPoint() {

        ClassInfo eventListener = new ClassInfo();
        eventListener.setName("OrderAsyncEventListener");
        eventListener.setType("COMPONENT");

        ClassCouplingInfo coupling = new ClassCouplingInfo(
                "OrderAsyncEventListener", "com.acme", "COMPONENT",
                1, 0, 1
        );

        EntryPointInfo entryPoint = new EntryPointInfo(
                "OrderAsyncEventListener", "com.acme", "onCreatedAsync",
                "EVENTLISTENER", null, "order"
        );

        List<ArchitectureFinding> findings =
                analyzer.analyze(
                        List.of(eventListener),
                        List.of(coupling),
                        List.of(entryPoint)
                );

        assertThat(findings).isEmpty();
    }

    @Test
    void doesNotFlagAControllerWithZeroIncomingDependencies() {

        ClassInfo controller = new ClassInfo();
        controller.setName("OrderController");
        controller.setType("CONTROLLER");

        ClassCouplingInfo coupling = new ClassCouplingInfo(
                "OrderController", "com.acme", "CONTROLLER", 1, 0, 1
        );

        List<ArchitectureFinding> findings =
                analyzer.analyze(
                        List.of(controller), List.of(coupling), List.of()
                );

        assertThat(findings).isEmpty();
    }
}
