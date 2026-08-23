package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryInheritanceResolverTest {

    private final RepositoryInheritanceResolver resolver =
            new RepositoryInheritanceResolver();

    @Test
    void propagatesRepositoryTypeAndEntityThroughCustomBaseRepository() {

        // interface BaseRepository<T, ID> extends JpaRepository<T, ID> {}
        ClassInfo baseRepository = new ClassInfo();
        baseRepository.setName("BaseRepository");
        baseRepository.setType("REPOSITORY");

        // interface OrderRepository extends BaseRepository<OrderEntity, Long> {}
        ClassInfo orderRepository = new ClassInfo();
        orderRepository.setName("OrderRepository");
        orderRepository.setType("UNKNOWN");
        orderRepository.getExtendedTypes().add("BaseRepository");
        orderRepository.getExtendsTypeArguments()
                .put("BaseRepository", "OrderEntity");

        List<ClassInfo> classes =
                List.of(baseRepository, orderRepository);

        resolver.resolve(classes);

        assertThat(orderRepository.getType()).isEqualTo("REPOSITORY");
        assertThat(orderRepository.getRepositoryEntityType())
                .isEqualTo("OrderEntity");
    }

    @Test
    void doesNotPromoteUnrelatedClasses() {

        ClassInfo service = new ClassInfo();
        service.setName("OrderService");
        service.setType("SERVICE");
        service.getExtendedTypes().add("BaseRepository");
        // No repository in scope at all.

        resolver.resolve(List.of(service));

        assertThat(service.getType()).isEqualTo("SERVICE");
    }

    @Test
    void resolvesThroughTwoLevelsOfIndirection() {

        ClassInfo baseRepository = new ClassInfo();
        baseRepository.setName("BaseRepository");
        baseRepository.setType("REPOSITORY");

        // interface AuditableRepository<T, ID> extends BaseRepository<T, ID> {}
        ClassInfo auditableRepository = new ClassInfo();
        auditableRepository.setName("AuditableRepository");
        auditableRepository.setType("UNKNOWN");
        auditableRepository.getExtendedTypes().add("BaseRepository");
        // Generic passthrough - type argument is still a type
        // variable at this level ("T"), not a concrete type.
        auditableRepository.getExtendsTypeArguments()
                .put("BaseRepository", "T");

        ClassInfo orderRepository = new ClassInfo();
        orderRepository.setName("OrderRepository");
        orderRepository.setType("UNKNOWN");
        orderRepository.getExtendedTypes().add("AuditableRepository");
        orderRepository.getExtendsTypeArguments()
                .put("AuditableRepository", "OrderEntity");

        List<ClassInfo> classes =
                List.of(baseRepository, auditableRepository, orderRepository);

        resolver.resolve(classes);

        assertThat(auditableRepository.getType()).isEqualTo("REPOSITORY");
        assertThat(orderRepository.getType()).isEqualTo("REPOSITORY");
        assertThat(orderRepository.getRepositoryEntityType())
                .isEqualTo("OrderEntity");
    }
}
