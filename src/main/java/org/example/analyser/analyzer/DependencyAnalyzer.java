package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DependencyType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DependencyAnalyzer {

    public List<DependencyInfo> analyze(
            List<ClassInfo> classes) {

        ClassRegistry registry =
                new ClassRegistry(classes);

        List<DependencyInfo> applicationDependencies =
                new ArrayList<>();

        for (ClassInfo sourceClass : classes) {

            for (DependencyInfo dependency :
                    sourceClass.getDependencies()) {

                // Entity relationships are already classified
                // by ClassAnalyzer.
                if (dependency.getType()
                        == DependencyType.ENTITY_RELATIONSHIP) {

                    if (registry.findBySimpleName(
                            dependency.getTargetClass()) != null) {

                        applicationDependencies.add(
                                dependency
                        );
                    }

                    continue;
                }

                String targetType =
                        dependency.getTargetClass();

                ClassInfo targetClass =
                        registry.findBySimpleName(targetType);

                if (targetClass == null) {
                    continue;
                }

                DependencyType dependencyType =
                        classifyDependency(
                                targetClass
                        );

                dependency.setType(
                        dependencyType
                );

                applicationDependencies.add(
                        dependency
                );
            }
        }

        return applicationDependencies;
    }

    // ==========================================
    // CLASSIFICATION
    // ==========================================

    private DependencyType classifyDependency(
            ClassInfo targetClass) {

        // Repository dependency
        //
        // Previously, a plain @Component depending on a
        // repository was guessed to be "batch" here. That
        // heuristic is gone now that BatchAnalyzer detects
        // actual batch/scheduling signals (@Scheduled,
        // Spring Batch interfaces, etc.) instead of guessing
        // from a component's declared type.
        if ("REPOSITORY".equals(
                targetClass.getType())) {

            return DependencyType.REPOSITORY_DEPENDENCY;
        }

        // Service dependency
        if ("SERVICE".equals(
                targetClass.getType())) {

            return DependencyType.SERVICE_DEPENDENCY;
        }

        // Plain @Component dependency (mapper, validator,
        // converter, interceptor, ...) - a real, resolved
        // dependency, just not a service or repository.
        if ("COMPONENT".equals(
                targetClass.getType())) {

            return DependencyType.COMPONENT_DEPENDENCY;
        }

        return DependencyType.OTHER_DEPENDENCY;
    }
}
