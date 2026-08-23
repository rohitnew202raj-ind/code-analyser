package org.example.analyser.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import org.example.analyser.model.ClassInfo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TypeResolver {

    /**
     * Resolve the Java type of an arbitrary expression using
     * the JavaParser Symbol Solver.
     *
     * This is the preferred resolution path: unlike the
     * heuristics below, it correctly handles chained calls
     * (a.b().c()), overload-aware method resolution, and
     * expressions inside lambda bodies.
     *
     * LIMITATION (documented, expected): the solver is only
     * configured with the target project's own source plus
     * the JDK. Types that only exist in external framework
     * jars (Spring, JPA, etc. themselves - as opposed to the
     * target project's classes that use them) will fail to
     * resolve, since those jars are not on the analyzer's
     * classpath. That's why every call site falls back to the
     * older heuristics below when this returns empty, rather
     * than replacing them outright.
     */
    public Optional<String> resolveType(Expression expression) {

        try {

            String described =
                    expression
                            .calculateResolvedType()
                            .describe();

            return Optional.ofNullable(
                    normalizeTypeName(described)
            );

        } catch (RuntimeException unresolved) {

            return Optional.empty();
        }
    }

    /**
     * Resolve the class that actually declares the method
     * being called (accounts for inheritance - a call
     * inherited from a base class resolves to the class that
     * declares it), via the Symbol Solver. Falls back to null
     * when the call can't be resolved (typically because it
     * targets a type outside the configured classpath, e.g.
     * an external framework interface).
     */
    public Optional<String> resolveDeclaringType(
            MethodCallExpr call) {

        try {

            String qualifiedName =
                    call.resolve()
                            .declaringType()
                            .getQualifiedName();

            return Optional.ofNullable(
                    normalizeTypeName(qualifiedName)
            );

        } catch (RuntimeException unresolved) {

            return Optional.empty();
        }
    }

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
        // 0. SYMBOL SOLVER (preferred when it works)
        // ==========================================

        Optional<String> resolved =
                resolveViaSymbolSolver(variableName, method);

        if (resolved.isPresent()) {
            return resolved.get();
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

    private Optional<String> resolveViaSymbolSolver(
            String variableName,
            MethodDeclaration method) {

        /*
         * Try every usage of the variable, not just the
         * first one - a given occurrence can fail to resolve
         * (e.g. it's involved in a call chain into an
         * external, unresolvable framework type) while a
         * different occurrence of the same variable resolves
         * fine.
         */
        return method.findAll(NameExpr.class)
                .stream()
                .filter(nameExpr ->
                        nameExpr.getNameAsString()
                                .equals(variableName)
                )
                .map(this::resolveType)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
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
            Expression expression,
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
         * The actual ClassInfo lookup happens through
         * the resolver overload below - this AST-only
         * layer can't determine it without ClassInfo.
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
