package org.example.analyser.analyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DependencyType;
import org.springframework.stereotype.Component;

@Component
public class ClassAnalyzer {

    public ClassInfo analyze(
            CompilationUnit compilationUnit,
            ClassOrInterfaceDeclaration clazz) {

        ClassInfo classInfo = new ClassInfo();

        // ======================================
        // CLASS NAME
        // ======================================

        classInfo.setName(
                clazz.getNameAsString()
        );

        // ======================================
        // PACKAGE
        // ======================================

        compilationUnit.getPackageDeclaration()
                .ifPresent(pkg ->
                        classInfo.setPackageName(
                                pkg.getNameAsString()
                        )
                );

        // ======================================
        // CLASS ANNOTATIONS
        // ======================================

        clazz.getAnnotations()
                .forEach(annotation ->
                        classInfo.getAnnotations()
                                .add(annotation.toString())
                );

        // ======================================
        // FIELDS
        // ======================================

        for (FieldDeclaration field : clazz.getFields()) {

            classInfo.getFields()
                    .add(field.toString());

            String targetType =
                    field.getElementType()
                            .asString();

            for (VariableDeclarator variable :
                    field.getVariables()) {

                String fieldName =
                        variable.getNameAsString();

                // ----------------------------------
                // ENTITY RELATIONSHIP
                // ----------------------------------

                boolean entityRelationship =
                        field.getAnnotations()
                                .stream()
                                .anyMatch(
                                        this::isEntityRelationship
                                );

                if (entityRelationship) {

                    DependencyInfo dependency =
                            new DependencyInfo(
                                    clazz.getNameAsString(),
                                    targetType,
                                    fieldName,
                                    DependencyType.ENTITY_RELATIONSHIP
                            );

                    classInfo.getDependencies()
                            .add(dependency);

                    continue;
                }

                // ----------------------------------
                // NORMAL FIELD DEPENDENCY
                // ----------------------------------

                DependencyInfo dependency =
                        new DependencyInfo(
                                clazz.getNameAsString(),
                                targetType,
                                fieldName,
                                DependencyType.UNKNOWN
                        );

                classInfo.getDependencies()
                        .add(dependency);
            }
        }

        // ======================================
        // METHODS
        // ======================================

        for (MethodDeclaration method :
                clazz.getMethods()) {

            classInfo.getMethods()
                    .add(method.getNameAsString());
        }

        // ======================================
        // EXTENDS
        // ======================================

        clazz.getExtendedTypes()
                .forEach(type -> {

                    classInfo.getInterfaces()
                            .add(type.getNameAsString());

                    /*
                     * Detect Spring Data repositories.
                     *
                     * Example:
                     *
                     * JpaRepository<OrderEntity, Long>
                     *
                     * We extract:
                     *
                     * OrderEntity
                     */
                    extractRepositoryEntityType(
                            type,
                            classInfo
                    );
                });

        // ======================================
        // IMPLEMENTS
        // ======================================

        clazz.getImplementedTypes()
                .forEach(type ->
                        classInfo.getInterfaces()
                                .add(type.getNameAsString())
                );

        return classInfo;
    }

    // ==========================================
    // REPOSITORY ENTITY TYPE
    // ==========================================

    private void extractRepositoryEntityType(
            ClassOrInterfaceType type,
            ClassInfo classInfo) {

        String interfaceName =
                type.getNameAsString();

        boolean springDataRepository =
                interfaceName.equals("JpaRepository")
                        || interfaceName.equals("CrudRepository")
                        || interfaceName.equals(
                        "PagingAndSortingRepository"
                )
                        || interfaceName.equals(
                        "Repository"
                );

        if (!springDataRepository) {
            return;
        }

        /*
         * Expected:
         *
         * JpaRepository<OrderEntity, Long>
         *
         * type arguments:
         *
         * 0 -> OrderEntity
         * 1 -> Long
         */

        if (type.getTypeArguments().isEmpty()) {
            return;
        }

        type.getTypeArguments()
                .ifPresent(arguments -> {

                    if (arguments.isEmpty()) {
                        return;
                    }

                    String entityType =
                            arguments.get(0)
                                    .asString();

                    classInfo.setRepositoryEntityType(
                            normalizeTypeName(entityType)
                    );
                });
    }

    // ==========================================
    // ENTITY RELATIONSHIP DETECTION
    // ==========================================

    private boolean isEntityRelationship(
            AnnotationExpr annotation) {

        String annotationName =
                annotation.getNameAsString();

        return annotationName.equals("OneToOne")
                || annotationName.equals("OneToMany")
                || annotationName.equals("ManyToOne")
                || annotationName.equals("ManyToMany");
    }

    // ==========================================
    // TYPE NORMALIZATION
    // ==========================================

    private String normalizeTypeName(
            String typeName) {

        if (typeName == null) {
            return null;
        }

        String normalized =
                typeName.trim();

        /*
         * Remove package name if present.
         *
         * com.example.OrderEntity
         *
         * becomes:
         *
         * OrderEntity
         */
        int lastDot =
                normalized.lastIndexOf(".");

        if (lastDot >= 0) {

            normalized =
                    normalized.substring(
                            lastDot + 1
                    );
        }

        return normalized;
    }
}