package org.example.analyser.analyzer;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.EntryPointInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ApiAnalyzer {

    public List<EntryPointInfo> analyze(
            ClassOrInterfaceDeclaration clazz,
            ClassInfo classInfo,
            Map<String, ClassOrInterfaceDeclaration> declarationsByName,
            PackageDomainExtractor domainExtractor) {

        List<EntryPointInfo> apis = new ArrayList<>();

        /*
         * Analyze REST controllers and plain MVC/GraphQL
         * controllers alike - GraphQL endpoints are typically
         * declared on a plain @Controller, not @RestController.
         */
        if (!classInfo.hasRole("REST_CONTROLLER")
                && !classInfo.hasRole("MVC_CONTROLLER")) {

            return apis;
        }

        String basePath =
                findRequestMappingPath(clazz)
                        .or(() ->
                                findBasePathFromInterfaces(
                                        classInfo,
                                        declarationsByName
                                )
                        )
                        .orElse("");

        for (MethodDeclaration method :
                methodSources(clazz, classInfo, declarationsByName)) {

            List<ApiMapping> mappings =
                    findMethodMappings(method);

            for (ApiMapping mapping : mappings) {

                String fullPath =
                        mapping.isPathBased()
                                ? combinePaths(
                                basePath,
                                mapping.path()
                        )
                                : mapping.path();

                EntryPointInfo api =
                        new EntryPointInfo(
                                classInfo.getName(),
                                classInfo.getPackageName(),
                                method.getNameAsString(),
                                mapping.httpMethod(),
                                fullPath,
                                domainExtractor.domainOf(
                                        classInfo.getPackageName()
                                )
                        );

                apis.add(api);
            }
        }

        return apis;
    }

    // ==========================================
    // INTERFACE-DECLARED MAPPINGS
    //
    // Spring supports putting @RequestMapping /
    // @GetMapping etc. on an interface and having the
    // controller simply implement it without repeating the
    // annotations. Only the concrete class's own methods
    // were scanned before, so those endpoints were invisible.
    // ==========================================

    private List<MethodDeclaration> methodSources(
            ClassOrInterfaceDeclaration clazz,
            ClassInfo classInfo,
            Map<String, ClassOrInterfaceDeclaration> declarationsByName) {

        List<MethodDeclaration> sources =
                new ArrayList<>(clazz.getMethods());

        for (String implementedType : classInfo.getImplementedTypes()) {

            ClassOrInterfaceDeclaration iface =
                    declarationsByName.get(implementedType);

            if (iface == null) {
                continue;
            }

            for (MethodDeclaration ifaceMethod : iface.getMethods()) {

                if (findMethodMappings(ifaceMethod).isEmpty()) {
                    continue;
                }

                boolean overriddenWithOwnMapping =
                        clazz.getMethods()
                                .stream()
                                .anyMatch(m ->
                                        m.getNameAsString()
                                                .equals(
                                                        ifaceMethod
                                                                .getNameAsString()
                                                )
                                                && !findMethodMappings(m)
                                                .isEmpty()
                                );

                if (!overriddenWithOwnMapping) {
                    sources.add(ifaceMethod);
                }
            }
        }

        return sources;
    }

    private Optional<String> findBasePathFromInterfaces(
            ClassInfo classInfo,
            Map<String, ClassOrInterfaceDeclaration> declarationsByName) {

        for (String implementedType : classInfo.getImplementedTypes()) {

            ClassOrInterfaceDeclaration iface =
                    declarationsByName.get(implementedType);

            if (iface == null) {
                continue;
            }

            Optional<String> path =
                    findRequestMappingPath(iface);

            if (path.isPresent()) {
                return path;
            }
        }

        return Optional.empty();
    }

    // ==========================================
    // CLASS LEVEL @RequestMapping
    // ==========================================

    private Optional<String> findRequestMappingPath(
            ClassOrInterfaceDeclaration clazz) {

        for (AnnotationExpr annotation :
                clazz.getAnnotations()) {

            if (!AnnotationNames.simpleName(annotation)
                    .equals("RequestMapping")) {

                continue;
            }

            return extractPath(annotation);
        }

        return Optional.empty();
    }

    // ==========================================
    // METHOD LEVEL MAPPINGS
    // ==========================================

    private List<ApiMapping> findMethodMappings(
            MethodDeclaration method) {

        List<ApiMapping> mappings =
                new ArrayList<>();

        for (AnnotationExpr annotation :
                method.getAnnotations()) {

            String annotationName =
                    AnnotationNames.simpleName(annotation);

            switch (annotationName) {

                case "GetMapping":
                    mappings.add(
                            new ApiMapping(
                                    "GET",
                                    extractPath(
                                            annotation
                                    ).orElse(""),
                                    true
                            )
                    );
                    break;

                case "PostMapping":
                    mappings.add(
                            new ApiMapping(
                                    "POST",
                                    extractPath(
                                            annotation
                                    ).orElse(""),
                                    true
                            )
                    );
                    break;

                case "PutMapping":
                    mappings.add(
                            new ApiMapping(
                                    "PUT",
                                    extractPath(
                                            annotation
                                    ).orElse(""),
                                    true
                            )
                    );
                    break;

                case "PatchMapping":
                    mappings.add(
                            new ApiMapping(
                                    "PATCH",
                                    extractPath(
                                            annotation
                                    ).orElse(""),
                                    true
                            )
                    );
                    break;

                case "DeleteMapping":
                    mappings.add(
                            new ApiMapping(
                                    "DELETE",
                                    extractPath(
                                            annotation
                                    ).orElse(""),
                                    true
                            )
                    );
                    break;

                case "RequestMapping":

                    mappings.addAll(
                            extractRequestMappingMethods(
                                    annotation
                            )
                    );
                    break;

                case "QueryMapping":
                    mappings.add(
                            graphQlMapping(
                                    "GRAPHQL_QUERY",
                                    annotation,
                                    method
                            )
                    );
                    break;

                case "MutationMapping":
                    mappings.add(
                            graphQlMapping(
                                    "GRAPHQL_MUTATION",
                                    annotation,
                                    method
                            )
                    );
                    break;

                case "SubscriptionMapping":
                    mappings.add(
                            graphQlMapping(
                                    "GRAPHQL_SUBSCRIPTION",
                                    annotation,
                                    method
                            )
                    );
                    break;

                case "SchemaMapping":
                    mappings.add(
                            graphQlMapping(
                                    "GRAPHQL_SCHEMA_MAPPING",
                                    annotation,
                                    method
                            )
                    );
                    break;

                default:
                    break;
            }
        }

        return mappings;
    }

    /*
     * LIMITATION (documented, not implemented): WebFlux
     * functional routing (RouterFunction / route(GET("/x"),
     * handler)) is not annotation-based, so it isn't detected
     * here - recognizing it would mean following arbitrary
     * fluent method-chain composition, a data-flow analysis
     * problem rather than a per-annotation check. Likewise,
     * full gRPC endpoint inventory would require reading
     * generated .proto stubs, which usually aren't part of
     * the scanned Java sources; only the @GrpcService class
     * itself is tagged (see SpringComponentAnalyzer).
     */
    private ApiMapping graphQlMapping(
            String kind,
            AnnotationExpr annotation,
            MethodDeclaration method) {

        String field =
                extractNamedAttribute(annotation, "field")
                        .or(() ->
                                extractNamedAttribute(
                                        annotation,
                                        "value"
                                )
                        )
                        .orElse(method.getNameAsString());

        return new ApiMapping(kind, field, false);
    }

    // ==========================================
    // @RequestMapping HTTP METHODS
    // ==========================================

    private List<ApiMapping>
    extractRequestMappingMethods(
            AnnotationExpr annotation) {

        List<ApiMapping> mappings =
                new ArrayList<>();

        String path =
                extractPath(annotation)
                        .orElse("");

        if (!annotation.isNormalAnnotationExpr()) {

            mappings.add(
                    new ApiMapping("ANY", path, true)
            );

            return mappings;
        }

        var normal =
                annotation.asNormalAnnotationExpr();

        Optional<MemberValuePair> methodPair =
                normal.getPairs()
                        .stream()
                        .filter(pair ->
                                pair.getNameAsString()
                                        .equals("method")
                        )
                        .findFirst();

        if (methodPair.isEmpty()) {

            mappings.add(
                    new ApiMapping("ANY", path, true)
            );

            return mappings;
        }

        String value =
                methodPair.get()
                        .getValue()
                        .toString();

        if (value.contains("GET")) {
            mappings.add(
                    new ApiMapping("GET", path, true)
            );
        }

        if (value.contains("POST")) {
            mappings.add(
                    new ApiMapping("POST", path, true)
            );
        }

        if (value.contains("PUT")) {
            mappings.add(
                    new ApiMapping("PUT", path, true)
            );
        }

        if (value.contains("PATCH")) {
            mappings.add(
                    new ApiMapping("PATCH", path, true)
            );
        }

        if (value.contains("DELETE")) {
            mappings.add(
                    new ApiMapping("DELETE", path, true)
            );
        }

        return mappings;
    }

    // ==========================================
    // PATH / ATTRIBUTE EXTRACTION
    // ==========================================

    private Optional<String> extractPath(
            AnnotationExpr annotation) {

        return extractNamedAttribute(annotation, "value")
                .or(() ->
                        extractNamedAttribute(
                                annotation,
                                "path"
                        )
                );
    }

    private Optional<String> extractNamedAttribute(
            AnnotationExpr annotation,
            String attributeName) {

        // @GetMapping("/customers")
        if (attributeName.equals("value")
                && annotation.isSingleMemberAnnotationExpr()) {

            return Optional.of(
                    annotation
                            .asSingleMemberAnnotationExpr()
                            .getMemberValue()
                            .toString()
                            .replace("\"", "")
            );
        }

        // @GetMapping(path = "/customers")
        // @RequestMapping(value = "/customers")
        if (annotation.isNormalAnnotationExpr()) {

            return annotation
                    .asNormalAnnotationExpr()
                    .getPairs()
                    .stream()
                    .filter(pair ->
                            pair.getNameAsString()
                                    .equals(attributeName)
                    )
                    .findFirst()
                    .map(pair ->
                            pair.getValue()
                                    .toString()
                                    .replace("\"", "")
                    );
        }

        return Optional.empty();
    }

    // ==========================================
    // PATH COMBINATION
    // ==========================================

    private String combinePaths(
            String basePath,
            String methodPath) {

        if (basePath.isBlank()
                && methodPath.isBlank()) {

            return "/";
        }

        if (basePath.isBlank()) {
            return normalizePath(methodPath);
        }

        if (methodPath.isBlank()) {
            return normalizePath(basePath);
        }

        return normalizePath(
                basePath + "/" + methodPath
        );
    }

    private String normalizePath(String path) {

        String result =
                path.replaceAll(
                        "/+",
                        "/"
                );

        if (!result.startsWith("/")) {
            result = "/" + result;
        }

        if (result.length() > 1
                && result.endsWith("/")) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }

        return result;
    }

    // ==========================================
    // INTERNAL RECORD
    // ==========================================

    private record ApiMapping(
            String httpMethod,
            String path,
            boolean isPathBased) {
    }
}
