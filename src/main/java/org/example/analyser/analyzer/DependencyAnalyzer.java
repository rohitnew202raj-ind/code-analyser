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

        List<DependencyInfo> applicationDependencies =
                new ArrayList<>();

        for (ClassInfo sourceClass : classes) {

            for (DependencyInfo dependency :
                    sourceClass.getDependencies()) {

                // Entity relationships are already classified
                // by ClassAnalyzer.
                if (dependency.getType()
                        == DependencyType.ENTITY_RELATIONSHIP) {

                    if (isApplicationClass(
                            classes,
                            dependency.getTargetClass())) {

                        applicationDependencies.add(
                                dependency
                        );
                    }

                    continue;
                }

                String targetType =
                        dependency.getTargetClass();

                ClassInfo targetClass =
                        findClass(
                                classes,
                                targetType
                        );

                if (targetClass == null) {
                    continue;
                }

                DependencyType dependencyType =
                        classifyDependency(
                                sourceClass,
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
            ClassInfo sourceClass,
            ClassInfo targetClass) {

        // Repository dependency
        if ("REPOSITORY".equals(
                targetClass.getType())) {

            if ("COMPONENT".equals(
                    sourceClass.getType())) {

                return DependencyType.BATCH_DEPENDENCY;
            }

            return DependencyType.REPOSITORY_DEPENDENCY;
        }

        // Service dependency
        if ("SERVICE".equals(
                targetClass.getType())) {

            return DependencyType.SERVICE_DEPENDENCY;
        }

        return DependencyType.UNKNOWN;
    }

    // ==========================================
    // FIND APPLICATION CLASS
    // ==========================================

    private ClassInfo findClass(
            List<ClassInfo> classes,
            String className) {

        return classes.stream()
                .filter(clazz ->
                        clazz.getName()
                                .equals(className))
                .findFirst()
                .orElse(null);
    }

    private boolean isApplicationClass(
            List<ClassInfo> classes,
            String className) {

        return findClass(
                classes,
                className
        ) != null;
    }
}