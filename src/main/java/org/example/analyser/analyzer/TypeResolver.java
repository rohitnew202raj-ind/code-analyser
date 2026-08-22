package org.example.analyser.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import org.example.analyser.model.ClassInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TypeResolver {

    /**
     * Resolve the Java type represented by a variable
     * inside a method.
     *
     * Examples:
     *
     * OrderEntity order = ...
     *      -> OrderEntity
     *
     * cancel(OrderEntity order)
     *      -> OrderEntity
     *
     * orders.findById(...)
     *      -> OrderEntity
     */
    public String resolveVariableType(
            String variableName,
            MethodDeclaration method,
            ClassInfo sourceClass,
            List<ClassInfo> classes) {

        if (variableName == null) {
            return null;
        }

        // ==========================================
        // 1. LOCAL VARIABLES
        // ==========================================

        String localType =
                resolveLocalVariableType(
                        variableName,
                        method
                );

        if (localType != null) {
            return normalizeTypeName(localType);
        }

        // ==========================================
        // 2. METHOD PARAMETERS
        // ==========================================

        String parameterType =
                resolveParameterType(
                        variableName,
                        method
                );

        if (parameterType != null) {
            return normalizeTypeName(parameterType);
        }

        // ==========================================
        // 3. CLASS FIELDS
        // ==========================================

        if (sourceClass != null) {

            String fieldType =
                    resolveFieldType(
                            variableName,
                            sourceClass
                    );

            if (fieldType != null) {
                return normalizeTypeName(fieldType);
            }
        }

        return null;
    }

    // =========================================================
    // LOCAL VARIABLE
    // =========================================================

    private String resolveLocalVariableType(
            String variableName,
            MethodDeclaration method) {

        List<VariableDeclarator> variables =
                method.findAll(
                        VariableDeclarator.class
                );

        /*
         * Search from the method AST for:
         *
         * OrderEntity order = ...
         */
        for (VariableDeclarator variable :
                variables) {

            if (!variable.getNameAsString()
                    .equals(variableName)) {

                continue;
            }

            /*
             * Explicit declaration:
             *
             * OrderEntity order
             */
            if (variable.getType() != null) {

                String type =
                        variable.getType().asString();

                /*
                 * "var" requires initializer
                 * inference.
                 */
                if (!type.equals("var")) {
                    return type;
                }

                /*
                 * var order =
                 *     repository.findById(...);
                 */
                if (variable.getInitializer()
                        .isPresent()) {

                    String inferred =
                            inferExpressionType(
                                    variable
                                            .getInitializer()
                                            .get(),
                                    method
                            );

                    if (inferred != null) {
                        return inferred;
                    }
                }
            }
        }

        return null;
    }

    // =========================================================
    // METHOD PARAMETER
    // =========================================================

    private String resolveParameterType(
            String variableName,
            MethodDeclaration method) {

        return method.getParameters()
                .stream()
                .filter(parameter ->
                        parameter
                                .getNameAsString()
                                .equals(variableName))
                .map(parameter ->
                        parameter.getType().asString())
                .findFirst()
                .orElse(null);
    }

    // =========================================================
    // CLASS FIELD
    // =========================================================

    private String resolveFieldType(
            String variableName,
            ClassInfo sourceClass) {

        for (String field :
                sourceClass.getFields()) {

            String normalized =
                    field
                            .replace(";", "")
                            .trim();

            if (normalized.startsWith("@")) {
                continue;
            }

            /*
             * Remove initializer.
             *
             * private OrderRepository orders =
             *      ...
             */
            int equalsIndex =
                    normalized.indexOf("=");

            if (equalsIndex >= 0) {

                normalized =
                        normalized.substring(
                                        0,
                                        equalsIndex
                                )
                                .trim();
            }

            String[] parts =
                    normalized.split("\\s+");

            if (parts.length < 2) {
                continue;
            }

            String fieldName =
                    parts[parts.length - 1];

            String fieldType =
                    parts[parts.length - 2];

            if (fieldName.equals(variableName)) {
                return fieldType;
            }
        }

        return null;
    }

    // =========================================================
    // EXPRESSION TYPE INFERENCE
    // =========================================================

    private String inferExpressionType(
            com.github.javaparser.ast.expr.Expression expression,
            MethodDeclaration method) {

        /*
         * Handle:
         *
         * repository.findById(...)
         */
        if (expression instanceof MethodCallExpr call) {

            String methodName =
                    call.getNameAsString();

            if (isRepositoryLookup(methodName)) {

                return inferRepositoryReturnType(
                        call,
                        method
                );
            }
        }

        return null;
    }

    // =========================================================
    // REPOSITORY LOOKUP
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
    // REPOSITORY RETURN TYPE
    // =========================================================

    private String inferRepositoryReturnType(
            MethodCallExpr call,
            MethodDeclaration method) {

        /*
         * We need the repository variable.
         *
         * Example:
         *
         * orders.findById(id)
         *
         * repositoryVariable = orders
         */
        String repositoryVariable =
                call.getScope()
                        .filter(scope ->
                                scope instanceof NameExpr)
                        .map(scope ->
                                ((NameExpr) scope)
                                        .getNameAsString())
                        .orElse(null);

        if (repositoryVariable == null) {
            return null;
        }

        /*
         * At this layer we can determine the repository
         * variable's declared type from the method/class.
         *
         * The actual ClassInfo lookup happens through
         * the resolver overload below.
         */
        return null;
    }

    /**
     * Resolve the entity returned by a repository method.
     *
     * This method uses ClassInfo so that repository
     * names do not matter.
     *
     * Example:
     *
     * anything.findById(...)
     *
     * if "anything" is declared as:
     *
     * OrderRepository
     *
     * and ClassInfo contains:
     *
     * OrderRepository
     * repositoryEntityType = OrderEntity
     *
     * result:
     *
     * OrderEntity
     */
    public String resolveRepositoryEntityType(
            String repositoryVariable,
            MethodDeclaration method,
            ClassInfo sourceClass,
            List<ClassInfo> classes) {

        String repositoryType =
                resolveVariableType(
                        repositoryVariable,
                        method,
                        sourceClass,
                        classes
                );

        if (repositoryType == null) {
            return null;
        }

        repositoryType =
                normalizeTypeName(repositoryType);

        for (ClassInfo classInfo :
                classes) {

            if (!"REPOSITORY".equals(
                    classInfo.getType())) {

                continue;
            }

            if (!classInfo.getName()
                    .equals(repositoryType)) {

                continue;
            }

            return normalizeTypeName(
                    classInfo
                            .getRepositoryEntityType()
            );
        }

        return null;
    }

    // =========================================================
    // TYPE NORMALIZATION
    // =========================================================

    public String normalizeTypeName(
            String typeName) {

        if (typeName == null) {
            return null;
        }

        String normalized =
                typeName.trim();

        /*
         * Remove generic information.
         *
         * Example:
         *
         * Optional<OrderEntity>
         *
         * becomes:
         *
         * Optional
         *
         * Generic extraction will be handled
         * separately where required.
         */
        int genericStart =
                normalized.indexOf("<");

        if (genericStart >= 0) {

            normalized =
                    normalized.substring(
                            0,
                            genericStart
                    );
        }

        /*
         * Remove package prefix.
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