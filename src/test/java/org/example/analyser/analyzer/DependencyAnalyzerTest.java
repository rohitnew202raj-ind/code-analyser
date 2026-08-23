package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DependencyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyAnalyzerTest {

    private final DependencyAnalyzer analyzer = new DependencyAnalyzer();

    @Test
    void classifiesDependencyOnServiceAsServiceDependency() {

        ClassInfo controller = classWithDependency(
                "OrderController", "OrderService", "service");

        ClassInfo service = new ClassInfo();
        service.setName("OrderService");
        service.setType("SERVICE");

        List<DependencyInfo> result =
                analyzer.analyze(List.of(controller, service));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType())
                .isEqualTo(DependencyType.SERVICE_DEPENDENCY);
    }

    @Test
    void classifiesDependencyOnRepositoryAsRepositoryDependency() {

        ClassInfo service = classWithDependency(
                "OrderServiceImpl", "OrderRepository", "repository");

        ClassInfo repository = new ClassInfo();
        repository.setName("OrderRepository");
        repository.setType("REPOSITORY");

        List<DependencyInfo> result =
                analyzer.analyze(List.of(service, repository));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType())
                .isEqualTo(DependencyType.REPOSITORY_DEPENDENCY);
    }

    @Test
    void classifiesDependencyOnPlainComponentAsComponentDependency() {

        ClassInfo service = classWithDependency(
                "OrderServiceImpl", "OrderMapper", "mapper");

        ClassInfo mapper = new ClassInfo();
        mapper.setName("OrderMapper");
        mapper.setType("COMPONENT");

        List<DependencyInfo> result =
                analyzer.analyze(List.of(service, mapper));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType())
                .isEqualTo(DependencyType.COMPONENT_DEPENDENCY);
    }

    @Test
    void classifiesDependencyOnNeitherServiceRepositoryNorComponentAsOther() {

        ClassInfo mapper = classWithDependency(
                "OrderMapper", "OrderCreatedEvent", "event");

        ClassInfo event = new ClassInfo();
        event.setName("OrderCreatedEvent");
        event.setType("EVENT");

        List<DependencyInfo> result =
                analyzer.analyze(List.of(mapper, event));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType())
                .isEqualTo(DependencyType.OTHER_DEPENDENCY);
    }

    @Test
    void dropsDependencyOnUnresolvedTargetInsteadOfLabelingIt() {

        ClassInfo controller = classWithDependency(
                "OrderController", "SomeExternalLibraryType", "external");

        List<DependencyInfo> result =
                analyzer.analyze(List.of(controller));

        assertThat(result).isEmpty();
    }

    private ClassInfo classWithDependency(
            String sourceName,
            String targetType,
            String fieldName) {

        ClassInfo source = new ClassInfo();
        source.setName(sourceName);

        source.getDependencies().add(
                new DependencyInfo(
                        sourceName,
                        targetType,
                        fieldName,
                        DependencyType.OTHER_DEPENDENCY
                )
        );

        return source;
    }
}
