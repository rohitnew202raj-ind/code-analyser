package org.example.analyser.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.TriggerType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds WebFlux functional endpoints - {@code RouterFunction}
 * beans built with {@code RouterFunctions.route()}'s fluent
 * builder API, e.g.:
 *
 * <pre>{@code
 * @Bean
 * public RouterFunction<ServerResponse> routes(OrderHandler handler) {
 *     return RouterFunctions.route()
 *             .GET("/orders/{id}", handler::getOrder)
 *             .POST("/orders", handler::createOrder)
 *             .build();
 * }
 * }</pre>
 *
 * {@code ApiAnalyzer} only recognizes annotation-based endpoints
 * (@GetMapping et al.); functional routing declares no
 * annotations on the handler methods at all, so it was previously
 * entirely invisible to this tool.
 *
 * SCOPE (documented, not a bug): only the modern builder-style
 * chain ({@code RouterFunctions.route()} or a static-imported bare
 * {@code route()}, followed by chained {@code .GET(path, handler)}/
 * {@code .POST(...)}/etc. calls) is recognized - the older,
 * lower-level {@code route(predicate, handler).andRoute(...)}
 * chain and {@code .nest(...)} composition are not. Only a method
 * *reference* handler ({@code handler::getOrder}) is recognized;
 * an inline lambda handler body carries no method name to report
 * an entry point against, so those routes are silently skipped
 * rather than guessed at.
 */
@Component
public class WebFluxRouterAnalyzer {

    private static final Map<String, TriggerType> ROUTE_METHODS =
            Map.of(
                    "GET", TriggerType.GET,
                    "POST", TriggerType.POST,
                    "PUT", TriggerType.PUT,
                    "PATCH", TriggerType.PATCH,
                    "DELETE", TriggerType.DELETE
            );

    private final TypeResolver typeResolver;

    public WebFluxRouterAnalyzer(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

    public List<EntryPointInfo> analyze(
            TypeDeclaration<?> clazz,
            ClassInfo classInfo,
            List<ClassInfo> classes,
            PackageDomainExtractor domainExtractor) {

        List<EntryPointInfo> results = new ArrayList<>();

        for (MethodDeclaration method : clazz.getMethods()) {

            if (!buildsARouterFunction(method)) {
                continue;
            }

            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {

                EntryPointInfo entryPoint =
                        toEntryPoint(call, method, classInfo, classes, domainExtractor);

                if (entryPoint != null) {
                    results.add(entryPoint);
                }
            }
        }

        return results;
    }

    private boolean buildsARouterFunction(MethodDeclaration method) {

        return method.findAll(MethodCallExpr.class)
                .stream()
                .anyMatch(call -> call.getNameAsString().equals("route"));
    }

    private EntryPointInfo toEntryPoint(
            MethodCallExpr call,
            MethodDeclaration method,
            ClassInfo sourceClass,
            List<ClassInfo> classes,
            PackageDomainExtractor domainExtractor) {

        TriggerType triggerType = ROUTE_METHODS.get(call.getNameAsString());

        if (triggerType == null) {
            return null;
        }

        if (call.getArguments().size() < 2) {
            return null;
        }

        Expression pathArgument = call.getArgument(0);

        if (!pathArgument.isStringLiteralExpr()) {
            return null;
        }

        String path = pathArgument.asStringLiteralExpr().asString();

        Expression handlerArgument =
                call.getArgument(call.getArguments().size() - 1);

        if (!handlerArgument.isMethodReferenceExpr()) {
            return null;
        }

        MethodReferenceExpr handlerReference =
                handlerArgument.asMethodReferenceExpr();

        /*
         * JavaParser always parses the left side of `X::method`
         * as a TypeExpr, never a NameExpr - even when X is a
         * lowercase local variable/parameter like `handler`
         * rather than an actual type name. The variable's simple
         * name is still just the type's text (`handler::getOrder`
         * parses with a ClassOrInterfaceType named "handler").
         */
        if (!(handlerReference.getScope() instanceof TypeExpr scopeType)) {
            return null;
        }

        String handlerVariableName = scopeType.getType().asString();

        String handlerType =
                typeResolver.resolveVariableType(
                        handlerVariableName,
                        method,
                        sourceClass,
                        classes
                );

        if (handlerType == null) {
            return null;
        }

        String handlerPackage = packageOf(handlerType, classes);

        return new EntryPointInfo(
                handlerType,
                handlerPackage,
                handlerReference.getIdentifier(),
                triggerType,
                path,
                domainExtractor.domainOf(handlerPackage)
        );
    }

    private String packageOf(String className, List<ClassInfo> classes) {

        return classes.stream()
                .filter(classInfo -> classInfo.getName().equals(className))
                .map(ClassInfo::getPackageName)
                .findFirst()
                .orElse(null);
    }
}
