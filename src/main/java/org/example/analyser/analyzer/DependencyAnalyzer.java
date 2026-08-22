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

            /*
             * LIMITATION (temporary heuristic):
             * We're treating any plain @Component that
             * depends on a repository as "batch."
             *
             * @Component does not necessarily mean "batch" -
             * it's just the closest signal we have right now,
             * and it happened to hold for our test project.
             *
             * We'll replace this with a proper BatchAnalyzer
             * that looks at @Scheduled, Spring Batch types
             * (Job/Step/Tasklet/ItemReader/ItemWriter), etc.
             * Batch programs matter as much as REST APIs for
             * the actual migration scenario (5 batch programs).
             */
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

    /*
     * LIMITATION (deliberately accepted for now):
     * We match classes by simple name only
     * (target.getName().equals(className)).
     *
     * This works for our current application, but a real
     * monolith can contain two classes with the same simple
     * name in different packages, e.g.:
     *
     *   com.company.sales.CustomerService
     *   com.company.reporting.CustomerService
     *
     * Simple-name matching becomes ambiguous in that case -
     * we'd resolve a dependency to the wrong class.
     *
     * We'll fix this later with the JavaParser Symbol Solver
     * (fully qualified type resolution). Do NOT add Symbol
     * Solver yet - out of scope for now.
     */
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