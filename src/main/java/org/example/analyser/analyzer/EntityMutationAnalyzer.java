package org.example.analyser.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.EntityMutationInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EntityMutationAnalyzer {

    private final TypeResolver typeResolver;

    public EntityMutationAnalyzer(
            TypeResolver typeResolver) {

        this.typeResolver = typeResolver;
    }

    public List<EntityMutationInfo> analyze(
            MethodDeclaration method,
            ClassInfo sourceClass,
            List<ClassInfo> classes) {

        List<EntityMutationInfo> mutations =
                new ArrayList<>();

        // ======================================
        // FIND ALL METHOD CALLS
        // ======================================

        method.findAll(MethodCallExpr.class)
                .forEach(call -> {

                    String methodName =
                            call.getNameAsString();

                    // ==================================
                    // ONLY INTERESTED IN SETTERS
                    // ==================================

                    if (!isSetter(methodName)) {
                        return;
                    }

                    // ==================================
                    // FIND TARGET VARIABLE
                    // ==================================

                    String variableName =
                            extractScopeVariable(call);

                    if (variableName == null) {
                        return;
                    }

                    // ==================================
                    // RESOLVE VARIABLE TYPE
                    // ==================================

                    String variableType =
                            typeResolver.resolveVariableType(
                                    variableName,
                                    method,
                                    sourceClass,
                                    classes
                            );

                    /*
                     * Example:
                     *
                     * order -> OrderEntity
                     *
                     * customer -> Customer
                     */
                    if (variableType == null) {

                        /*
                         * The variable may be a repository
                         * result represented by:
                         *
                         * var order = repository.findById(...)
                         *
                         * Try repository/entity resolution.
                         */
                        variableType =
                                resolveRepositoryResultType(
                                        variableName,
                                        method,
                                        sourceClass,
                                        classes
                                );
                    }

                    if (variableType == null) {
                        return;
                    }

                    // ==================================
                    // FIND ENTITY
                    // ==================================

                    ClassInfo entity =
                            findEntity(
                                    variableType,
                                    classes
                            );

                    if (entity == null) {
                        return;
                    }

                    // ==================================
                    // FIELD NAME
                    // ==================================

                    String fieldName =
                            setterToFieldName(
                                    methodName
                            );

                    // ==================================
                    // TABLE NAME
                    // ==================================

                    String tableName =
                            extractTableName(
                                    entity
                            );

                    // ==================================
                    // CREATE MUTATION
                    // ==================================

                    mutations.add(
                            new EntityMutationInfo(
                                    sourceClass.getName(),
                                    method.getNameAsString(),
                                    entity.getName(),
                                    fieldName,
                                    "UPDATE",
                                    tableName
                            )
                    );
                });

        return mutations;
    }

    // =========================================================
    // SETTER DETECTION
    // =========================================================

    private boolean isSetter(
            String methodName) {

        return methodName.startsWith("set")
                && methodName.length() > 3;
    }

    // =========================================================
    // EXTRACT VARIABLE FROM METHOD CALL
    // =========================================================

    private String extractScopeVariable(
            MethodCallExpr call) {

        /*
         * Example:
         *
         * order.setStatus(...)
         *
         * scope = order
         */

        return call.getScope()
                .filter(scope ->
                        scope instanceof NameExpr)
                .map(scope ->
                        ((NameExpr) scope)
                                .getNameAsString())
                .orElse(null);
    }

    // =========================================================
    // REPOSITORY RESULT RESOLUTION
    // =========================================================

    private String resolveRepositoryResultType(
            String variableName,
            MethodDeclaration method,
            ClassInfo sourceClass,
            List<ClassInfo> classes) {

        /*
         * Search for the declaration of the variable.
         *
         * Example:
         *
         * OrderEntity order =
         *     orders.findById(id).orElseThrow();
         *
         * We need to find:
         *
         * orders.findById(...)
         */

        return method
                .findAll(
                        com.github.javaparser.ast.body
                                .VariableDeclarator.class
                )
                .stream()
                .filter(variable ->
                        variable
                                .getNameAsString()
                                .equals(variableName))
                .map(variable ->
                        variable.getInitializer()
                                .orElse(null))
                .filter(initializer ->
                        initializer != null)
                .map(initializer ->
                        resolveRepositoryFromExpression(
                                initializer,
                                method,
                                sourceClass,
                                classes
                        ))
                .filter(type ->
                        type != null)
                .findFirst()
                .orElse(null);
    }

    private String resolveRepositoryFromExpression(
            com.github.javaparser.ast.expr.Expression expression,
            MethodDeclaration method,
            ClassInfo sourceClass,
            List<ClassInfo> classes) {

        /*
         * Find repository calls anywhere inside
         * the initializer.
         *
         * Example:
         *
         * orders.findById(id).orElseThrow()
         *
         * AST contains:
         *
         * orders.findById(id)
         * orElseThrow()
         */

        List<MethodCallExpr> calls =
                expression.findAll(
                        MethodCallExpr.class
                );

        /*
         * Check from inner calls outward.
         */
        for (MethodCallExpr call : calls) {

            String methodName =
                    call.getNameAsString();

            if (!isRepositoryLookup(methodName)) {
                continue;
            }

            String repositoryVariable =
                    call.getScope()
                            .filter(scope ->
                                    scope instanceof NameExpr)
                            .map(scope ->
                                    ((NameExpr) scope)
                                            .getNameAsString())
                            .orElse(null);

            if (repositoryVariable == null) {
                continue;
            }

            return typeResolver
                    .resolveRepositoryEntityType(
                            repositoryVariable,
                            method,
                            sourceClass,
                            classes
                    );
        }

        return null;
    }

    // =========================================================
    // REPOSITORY LOOKUP METHODS
    // =========================================================

    private boolean isRepositoryLookup(
            String methodName) {

        return methodName.equals("findById")
                || methodName.equals("findOne")
                || methodName.equals("getReferenceById")
                || methodName.equals("getById")
                || methodName.equals("getOne");
    }

    // =========================================================
    // ENTITY LOOKUP
    // =========================================================

    private ClassInfo findEntity(
            String typeName,
            List<ClassInfo> classes) {

        String normalized =
                typeResolver.normalizeTypeName(
                        typeName
                );

        if (normalized == null) {
            return null;
        }

        for (ClassInfo classInfo :
                classes) {

            if (!"ENTITY".equals(
                    classInfo.getType())) {

                continue;
            }

            if (classInfo.getName()
                    .equals(normalized)) {

                return classInfo;
            }
        }

        return null;
    }

    // =========================================================
    // SETTER -> FIELD
    // =========================================================

    private String setterToFieldName(
            String setterName) {

        /*
         * setStatus()
         *
         * -> status
         */

        return Character.toLowerCase(
                setterName.charAt(3)
        ) + setterName.substring(4);
    }

    // =========================================================
    // TABLE NAME
    // =========================================================

    private String extractTableName(
            ClassInfo entity) {

        /*
         * Prefer:
         *
         * @Table(name = "orders")
         */

        String explicitTable =
                entity.getAnnotations()
                        .stream()
                        .filter(annotation ->
                                annotation.startsWith(
                                        "@Table"
                                ))
                        .map(this::parseTableName)
                        .filter(name ->
                                name != null)
                        .findFirst()
                        .orElse(null);

        if (explicitTable != null) {
            return explicitTable;
        }

        /*
         * Fallback:
         *
         * OrderEntity
         * -> order_entity
         */

        return toSnakeCase(
                entity.getName()
        );
    }

    private String parseTableName(
            String annotation) {

        int nameIndex =
                annotation.indexOf("name");

        if (nameIndex < 0) {
            return null;
        }

        int quoteStart =
                annotation.indexOf(
                        '"',
                        nameIndex
                );

        if (quoteStart < 0) {
            return null;
        }

        int quoteEnd =
                annotation.indexOf(
                        '"',
                        quoteStart + 1
                );

        if (quoteEnd < 0) {
            return null;
        }

        return annotation.substring(
                quoteStart + 1,
                quoteEnd
        );
    }

    // =========================================================
    // SNAKE CASE
    // =========================================================

    private String toSnakeCase(
            String value) {

        if (value == null
                || value.isEmpty()) {

            return value;
        }

        StringBuilder result =
                new StringBuilder();

        for (int i = 0;
             i < value.length();
             i++) {

            char current =
                    value.charAt(i);

            if (Character.isUpperCase(current)
                    && i > 0) {

                result.append('_');
            }

            result.append(
                    Character.toLowerCase(current)
            );
        }

        return result.toString();
    }
}