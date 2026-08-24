package org.example.analyser.analyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DependencyType;
import org.example.analyser.model.FieldInfo;
import org.example.analyser.model.MethodInfo;
import org.example.analyser.model.ParameterInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ClassAnalyzer {

    // ==========================================
    // LOMBOK SYNTHESIS
    //
    // JavaParser does not run annotation processors, so
    // Lombok-generated accessors never appear as declared
    // methods. That breaks any method-existence check
    // downstream. We can't know the *exact* generated method
    // (Lombok's own rules for boolean fields etc. can differ
    // slightly by version), but synthesizing the conventional
    // getter/setter names for @Data/@Getter/@Setter/@Value
    // covers the overwhelming majority of real usage.
    // ==========================================

    private static final Set<String> GETTER_ANNOTATIONS =
            Set.of("Data", "Getter", "Value");

    private static final Set<String> SETTER_ANNOTATIONS =
            Set.of("Data", "Setter");

    public ClassInfo analyze(
            CompilationUnit compilationUnit,
            TypeDeclaration<?> clazz) {

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

        for (AnnotationExpr annotation : clazz.getAnnotations()) {

            classInfo.getAnnotations()
                    .add(annotation.toString());

            classInfo.getAnnotationSimpleNames()
                    .add(AnnotationNames.simpleName(annotation));
        }

        // ======================================
        // FIELDS
        // ======================================

        for (FieldDeclaration field : clazz.getFields()) {

            String targetType =
                    field.getElementType()
                            .asString();

            List<String> fieldAnnotations =
                    field.getAnnotations()
                            .stream()
                            .map(AnnotationExpr::toString)
                            .toList();

            List<String> fieldAnnotationSimpleNames =
                    field.getAnnotations()
                            .stream()
                            .map(AnnotationNames::simpleName)
                            .toList();

            for (VariableDeclarator variable :
                    field.getVariables()) {

                String fieldName =
                        variable.getNameAsString();

                classInfo.getFields()
                        .add(
                                new FieldInfo(
                                        fieldName,
                                        targetType,
                                        fieldAnnotations,
                                        fieldAnnotationSimpleNames,
                                        field.isStatic(),
                                        field.isFinal()
                                )
                        );

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
                                DependencyType.OTHER_DEPENDENCY
                        );

                classInfo.getDependencies()
                        .add(dependency);
            }
        }

        // ======================================
        // METHODS (including Lombok-synthesized)
        // ======================================

        clazz.getMethods()
                .forEach(method ->
                        classInfo.getMethods()
                                .add(toMethodInfo(method))
                );

        synthesizeLombokMethods(clazz, classInfo);

        // ======================================
        // EXTENDS / IMPLEMENTS
        //
        // Kept as two distinct lists (inheritance vs.
        // contract), plus the old combined list for
        // backward compatibility.
        // ======================================

        if (clazz instanceof ClassOrInterfaceDeclaration coid) {

            classInfo.setInterfaceDeclaration(coid.isInterface());

            coid.getExtendedTypes().forEach(type -> {

                classInfo.getExtendedTypes()
                        .add(type.getNameAsString());

                classInfo.getInterfaces()
                        .add(type.getNameAsString());

                extractRepositoryEntityType(type, classInfo);
                recordFirstTypeArgument(type, classInfo);
            });

            coid.getImplementedTypes().forEach(type -> {

                classInfo.getImplementedTypes()
                        .add(type.getNameAsString());

                classInfo.getInterfaces()
                        .add(type.getNameAsString());
            });

        } else if (clazz instanceof EnumDeclaration enumDecl) {

            enumDecl.getImplementedTypes().forEach(type -> {

                classInfo.getImplementedTypes()
                        .add(type.getNameAsString());

                classInfo.getInterfaces()
                        .add(type.getNameAsString());
            });

        } else if (clazz instanceof RecordDeclaration recordDecl) {

            recordDecl.getImplementedTypes().forEach(type -> {

                classInfo.getImplementedTypes()
                        .add(type.getNameAsString());

                classInfo.getInterfaces()
                        .add(type.getNameAsString());
            });
        }

        return classInfo;
    }

    // ==========================================
    // METHOD -> STRUCTURED METHOD INFO
    // ==========================================

    private MethodInfo toMethodInfo(MethodDeclaration method) {

        List<ParameterInfo> parameters =
                method.getParameters()
                        .stream()
                        .map(this::toParameterInfo)
                        .toList();

        List<String> annotations =
                method.getAnnotations()
                        .stream()
                        .map(AnnotationExpr::toString)
                        .toList();

        List<String> annotationSimpleNames =
                method.getAnnotations()
                        .stream()
                        .map(AnnotationNames::simpleName)
                        .toList();

        return new MethodInfo(
                method.getNameAsString(),
                method.getType().asString(),
                parameters,
                annotations,
                annotationSimpleNames
        );
    }

    private ParameterInfo toParameterInfo(Parameter parameter) {

        return new ParameterInfo(
                parameter.getNameAsString(),
                parameter.getType().asString()
        );
    }

    // ==========================================
    // LOMBOK METHOD SYNTHESIS
    // ==========================================

    private void synthesizeLombokMethods(
            TypeDeclaration<?> clazz,
            ClassInfo classInfo) {

        boolean classLevelGetters =
                hasAnyAnnotation(clazz, GETTER_ANNOTATIONS);

        boolean classLevelSetters =
                hasAnyAnnotation(clazz, SETTER_ANNOTATIONS);

        for (FieldDeclaration field : clazz.getFields()) {

            boolean fieldStatic =
                    field.isStatic();

            if (fieldStatic) {
                continue;
            }

            boolean fieldGetter =
                    classLevelGetters
                            || hasAnyAnnotation(
                            field,
                            GETTER_ANNOTATIONS
                    );

            boolean fieldSetter =
                    (classLevelSetters && !field.isFinal())
                            || hasAnyAnnotation(
                            field,
                            SETTER_ANNOTATIONS
                    );

            String fieldType =
                    field.getElementType()
                            .asString();

            boolean booleanField =
                    fieldType.equalsIgnoreCase("boolean")
                            || fieldType.equals("Boolean");

            for (VariableDeclarator variable :
                    field.getVariables()) {

                String fieldName =
                        variable.getNameAsString();

                String capitalized =
                        capitalize(fieldName);

                if (fieldGetter) {

                    String prefix =
                            booleanField ? "is" : "get";

                    classInfo.getMethods()
                            .add(
                                    new MethodInfo(
                                            prefix + capitalized,
                                            fieldType,
                                            List.of(),
                                            List.of(),
                                            List.of()
                                    )
                            );
                }

                if (fieldSetter) {

                    classInfo.getMethods()
                            .add(
                                    new MethodInfo(
                                            "set" + capitalized,
                                            "void",
                                            List.of(
                                                    new ParameterInfo(
                                                            fieldName,
                                                            fieldType
                                                    )
                                            ),
                                            List.of(),
                                            List.of()
                                    )
                            );
                }
            }
        }
    }

    private boolean hasAnyAnnotation(
            com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> node,
            Set<String> simpleNames) {

        return node.getAnnotations()
                .stream()
                .map(AnnotationNames::simpleName)
                .anyMatch(simpleNames::contains);
    }

    private String capitalize(String value) {

        if (value == null || value.isEmpty()) {
            return value;
        }

        return Character.toUpperCase(value.charAt(0))
                + value.substring(1);
    }

    // ==========================================
    // REPOSITORY ENTITY TYPE
    // ==========================================

    /*
     * LIMITATION (documented, partially addressed): this
     * only detects the entity type when a repository directly
     * extends one of the hardcoded Spring Data marker
     * interfaces below.
     *
     * If the project uses its own base repository, e.g.:
     *
     *   interface BaseRepository<T, ID>
     *           extends JpaRepository<T, ID> { }
     *
     *   interface OrderRepository
     *           extends BaseRepository<OrderEntity, Long> { }
     *
     * then OrderRepository's "extends" type is BaseRepository,
     * not JpaRepository, so this check alone misses it. That
     * indirection is now resolved separately, after all
     * classes are parsed, by RepositoryInheritanceResolver -
     * see that class for the transitive propagation.
     */
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
    // GENERIC TYPE ARGUMENT (for repository-chain
    // resolution, see RepositoryInheritanceResolver)
    // ==========================================

    private void recordFirstTypeArgument(
            ClassOrInterfaceType type,
            ClassInfo classInfo) {

        type.getTypeArguments()
                .filter(arguments -> !arguments.isEmpty())
                .ifPresent(arguments ->
                        classInfo.getExtendsTypeArguments()
                                .put(
                                        type.getNameAsString(),
                                        normalizeTypeName(
                                                arguments.get(0)
                                                        .asString()
                                        )
                                )
                );
    }

    // ==========================================
    // ENTITY RELATIONSHIP DETECTION
    // ==========================================

    private static final Set<String> RELATIONSHIP_ANNOTATIONS =
            Set.of(
                    "OneToOne",
                    "OneToMany",
                    "ManyToOne",
                    "ManyToMany",
                    "ElementCollection",
                    "Embedded",
                    "EmbeddedId",
                    "DBRef"
            );

    private boolean isEntityRelationship(
            AnnotationExpr annotation) {

        return RELATIONSHIP_ANNOTATIONS.contains(
                AnnotationNames.simpleName(annotation)
        );
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
