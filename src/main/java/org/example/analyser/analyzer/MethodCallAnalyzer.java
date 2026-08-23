package org.example.analyser.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.MethodCallInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class MethodCallAnalyzer {

    private final TypeResolver typeResolver;

    public MethodCallAnalyzer(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

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
                                    method,
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
            MethodDeclaration method,
            ClassInfo sourceClass,
            List<ClassInfo> allClasses) {

        /*
         * Preferred path: ask the Symbol Solver which class
         * actually declares the resolved method. This is the
         * only way to correctly resolve chained calls
         * (a.b().c()) and calls on a local variable, both of
         * which the scope-based heuristic below can't handle.
         * It naturally fails for calls into external framework
         * types (not on our classpath), which is fine - we
         * fall through to the heuristic for those.
         */
        Optional<String> declaringType =
                typeResolver.resolveDeclaringType(call);

        if (declaringType.isPresent()
                && isApplicationClass(
                declaringType.get(),
                allClasses
        )) {

            return declaringType.get();
        }

        /*
         * Case 1:
         *
         * this.place()
         *
         * or
         *
         * place()
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
         * Resolve the scope variable's declared type via
         * TypeResolver - covers fields, method parameters,
         * and local variables (the old implementation here
         * only ever checked fields).
         */

        Expression scope =
                call.getScope().get();

        if (!(scope instanceof NameExpr nameExpr)) {
            return null;
        }

        String variableType =
                typeResolver.resolveVariableType(
                        nameExpr.getNameAsString(),
                        method,
                        sourceClass,
                        allClasses
                );

        if (variableType == null) {
            return null;
        }

        if (isApplicationClass(variableType, allClasses)) {
            return variableType;
        }

        return null;
    }

    private boolean isApplicationClass(
            String className,
            List<ClassInfo> allClasses) {

        return allClasses.stream()
                .anyMatch(clazz ->
                        clazz.getName()
                                .equals(className)
                );
    }
}
