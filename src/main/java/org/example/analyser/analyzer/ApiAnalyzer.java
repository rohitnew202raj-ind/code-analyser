package org.example.analyser.analyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import org.example.analyser.model.ApiInfo;
import org.example.analyser.model.ClassInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ApiAnalyzer {

    public List<ApiInfo> analyze(
            CompilationUnit compilationUnit,
            ClassOrInterfaceDeclaration clazz,
            ClassInfo classInfo) {

        List<ApiInfo> apis = new ArrayList<>();

        // Only analyze REST controllers
        if (!"CONTROLLER".equals(classInfo.getType())) {
            return apis;
        }

        String basePath =
                findRequestMappingPath(clazz)
                        .orElse("");

        for (MethodDeclaration method :
                clazz.getMethods()) {

            List<ApiMapping> mappings =
                    findMethodMappings(method);

            for (ApiMapping mapping : mappings) {

                String fullPath =
                        combinePaths(
                                basePath,
                                mapping.path()
                        );

                ApiInfo api =
                        new ApiInfo(
                                classInfo.getName(),
                                classInfo.getPackageName(),
                                method.getNameAsString(),
                                mapping.httpMethod(),
                                fullPath,
                                extractDomain(
                                        classInfo
                                )
                        );

                apis.add(api);
            }
        }

        return apis;
    }

    // ==========================================
    // CLASS LEVEL @RequestMapping
    // ==========================================

    private Optional<String> findRequestMappingPath(
            ClassOrInterfaceDeclaration clazz) {

        for (AnnotationExpr annotation :
                clazz.getAnnotations()) {

            if (!annotation.getNameAsString()
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
                    annotation.getNameAsString();

            switch (annotationName) {

                case "GetMapping":
                    mappings.add(
                            new ApiMapping(
                                    "GET",
                                    extractPath(
                                            annotation
                                    ).orElse("")
                            )
                    );
                    break;

                case "PostMapping":
                    mappings.add(
                            new ApiMapping(
                                    "POST",
                                    extractPath(
                                            annotation
                                    ).orElse("")
                            )
                    );
                    break;

                case "PutMapping":
                    mappings.add(
                            new ApiMapping(
                                    "PUT",
                                    extractPath(
                                            annotation
                                    ).orElse("")
                            )
                    );
                    break;

                case "PatchMapping":
                    mappings.add(
                            new ApiMapping(
                                    "PATCH",
                                    extractPath(
                                            annotation
                                    ).orElse("")
                            )
                    );
                    break;

                case "DeleteMapping":
                    mappings.add(
                            new ApiMapping(
                                    "DELETE",
                                    extractPath(
                                            annotation
                                    ).orElse("")
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

                default:
                    break;
            }
        }

        return mappings;
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
                    new ApiMapping(
                            "ANY",
                            path
                    )
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
                    new ApiMapping(
                            "ANY",
                            path
                    )
            );

            return mappings;
        }

        String value =
                methodPair.get()
                        .getValue()
                        .toString();

        if (value.contains("GET")) {
            mappings.add(
                    new ApiMapping("GET", path)
            );
        }

        if (value.contains("POST")) {
            mappings.add(
                    new ApiMapping("POST", path)
            );
        }

        if (value.contains("PUT")) {
            mappings.add(
                    new ApiMapping("PUT", path)
            );
        }

        if (value.contains("PATCH")) {
            mappings.add(
                    new ApiMapping("PATCH", path)
            );
        }

        if (value.contains("DELETE")) {
            mappings.add(
                    new ApiMapping("DELETE", path)
            );
        }

        return mappings;
    }

    // ==========================================
    // PATH EXTRACTION
    // ==========================================

    private Optional<String> extractPath(
            AnnotationExpr annotation) {

        // @GetMapping("/customers")
        if (annotation.isSingleMemberAnnotationExpr()) {

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
                                    .equals("value")
                                    || pair.getNameAsString()
                                    .equals("path")
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
    // DOMAIN
    // ==========================================

    private String extractDomain(
            ClassInfo classInfo) {

        String packageName =
                classInfo.getPackageName();

        String[] parts =
                packageName.split("\\.");

        if (parts.length >= 4) {
            return parts[3];
        }

        return "core";
    }

    // ==========================================
    // INTERNAL RECORD
    // ==========================================

    private record ApiMapping(
            String httpMethod,
            String path) {
    }
}