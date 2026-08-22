package org.example.analyser.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.MethodCallInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MethodCallAnalyzer {

    public List<MethodCallInfo> analyze(
            MethodDeclaration method,
            ClassInfo sourceClass,
            List<ClassInfo> allClasses) {

        List<MethodCallInfo> calls =
                new ArrayList<>();

        method.findAll(MethodCallExpr.class)
                .forEach(call -> {

                    String methodName =
                            call.getNameAsString();

                    String targetClass =
                            resolveTargetClass(
                                    call,
                                    sourceClass,
                                    allClasses
                            );

                    if (targetClass == null) {
                        return;
                    }

                    calls.add(
                            new MethodCallInfo(
                                    sourceClass.getName(),
                                    method.getNameAsString(),
                                    targetClass,
                                    methodName
                            )
                    );
                });

        return calls;
    }

    private String resolveTargetClass(
            MethodCallExpr call,
            ClassInfo sourceClass,
            List<ClassInfo> allClasses) {

        /*
         * Case 1:
         *
         * this.place()
         *
         * or
         *
         * place()
         *
         */
        if (call.getScope().isEmpty()) {

            boolean existsInSource =
                    sourceClass.getMethods()
                            .contains(
                                    call.getNameAsString()
                            );

            if (existsInSource) {
                return sourceClass.getName();
            }

            return null;
        }

        /*
         * Case 2:
         *
         * orders.save()
         * customers.find()
         *
         * We currently resolve the variable
         * against fields in ClassInfo.
         */

        String scope =
                call.getScope()
                        .get()
                        .toString();

        for (String field :
                sourceClass.getFields()) {

            String fieldName =
                    extractFieldName(field);

            if (!scope.equals(fieldName)) {
                continue;
            }

            String fieldType =
                    extractFieldType(field);

            if (fieldType == null) {
                return null;
            }

            boolean applicationClass =
                    allClasses.stream()
                            .anyMatch(clazz ->
                                    clazz.getName()
                                            .equals(fieldType)
                            );

            if (applicationClass) {
                return fieldType;
            }
        }

        return null;
    }

    private String extractFieldName(
            String fieldDeclaration) {

        /*
         * Example:
         *
         * private final OrderRepository orders;
         *
         * We want:
         *
         * orders
         */

        String declaration =
                fieldDeclaration
                        .replace(";", "")
                        .trim();

        int equalsIndex =
                declaration.indexOf("=");

        if (equalsIndex >= 0) {
            declaration =
                    declaration.substring(
                            0,
                            equalsIndex
                    ).trim();
        }

        String[] parts =
                declaration.split("\\s+");

        if (parts.length == 0) {
            return null;
        }

        String last =
                parts[parts.length - 1];

        return last;
    }

    private String extractFieldType(
            String fieldDeclaration) {

        /*
         * Example:
         *
         * private final OrderRepository orders;
         *
         * parts:
         *
         * private
         * final
         * OrderRepository
         * orders
         */

        String declaration =
                fieldDeclaration
                        .replace(";", "")
                        .trim();

        int equalsIndex =
                declaration.indexOf("=");

        if (equalsIndex >= 0) {
            declaration =
                    declaration.substring(
                            0,
                            equalsIndex
                    ).trim();
        }

        String[] parts =
                declaration.split("\\s+");

        if (parts.length < 2) {
            return null;
        }

        return parts[parts.length - 2];
    }
}