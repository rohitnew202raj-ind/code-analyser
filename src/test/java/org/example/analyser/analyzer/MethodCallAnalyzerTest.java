package org.example.analyser.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.MethodCallInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MethodCallAnalyzerTest {

    private final MethodCallAnalyzer methodCallAnalyzer =
            new MethodCallAnalyzer(new TypeResolver());

    @Test
    void flagsARepositoryCallInsideAForLoopAsInsideLoop() {

        List<MethodCallInfo> calls = analyzeMethod(
                """
                package com.acme;

                public class OrderService {

                    public void processAll() {

                        OrderRepository repo = new OrderRepository();

                        for (int i = 0; i < 10; i++) {
                            repo.findById(i);
                        }

                        repo.count();
                    }
                }
                """,
                "processAll"
        );

        MethodCallInfo findById = callTo(calls, "findById");
        MethodCallInfo count = callTo(calls, "count");

        assertThat(findById.isInsideLoop()).isTrue();
        assertThat(count.isInsideLoop()).isFalse();
    }

    @Test
    void flagsARepositoryCallInsideAForEachLoopAsInsideLoop() {

        List<MethodCallInfo> calls = analyzeMethod(
                """
                package com.acme;

                public class OrderService {

                    public void processAll(java.util.List<Long> ids) {

                        OrderRepository repo = new OrderRepository();

                        for (Long id : ids) {
                            repo.findById(id);
                        }
                    }
                }
                """,
                "processAll"
        );

        assertThat(callTo(calls, "findById").isInsideLoop()).isTrue();
    }

    @Test
    void flagsARepositoryCallInsideAWhileLoopAsInsideLoop() {

        List<MethodCallInfo> calls = analyzeMethod(
                """
                package com.acme;

                public class OrderService {

                    public void processAll() {

                        OrderRepository repo = new OrderRepository();
                        int i = 0;

                        while (i < 10) {
                            repo.findById(i);
                            i++;
                        }
                    }
                }
                """,
                "processAll"
        );

        assertThat(callTo(calls, "findById").isInsideLoop()).isTrue();
    }

    @Test
    void doesNotFlagACallOutsideAnyLoop() {

        List<MethodCallInfo> calls = analyzeMethod(
                """
                package com.acme;

                public class OrderService {

                    public void processOne() {

                        OrderRepository repo = new OrderRepository();
                        repo.findById(1);
                    }
                }
                """,
                "processOne"
        );

        assertThat(callTo(calls, "findById").isInsideLoop()).isFalse();
    }

    private List<MethodCallInfo> analyzeMethod(
            String source, String methodName) {

        CompilationUnit cu = StaticJavaParser.parse(source);

        ClassInfo sourceClass = new ClassInfo();
        sourceClass.setName("OrderService");
        sourceClass.setPackageName("com.acme");

        ClassInfo repository = new ClassInfo();
        repository.setName("OrderRepository");
        repository.setType("REPOSITORY");

        MethodDeclaration method =
                findMethod(cu, methodName);

        return methodCallAnalyzer.analyze(
                method,
                sourceClass,
                List.of(sourceClass, repository)
        );
    }

    private MethodDeclaration findMethod(
            CompilationUnit cu, String methodName) {

        Optional<MethodDeclaration> method =
                cu.findAll(MethodDeclaration.class).stream()
                        .filter(candidate ->
                                candidate.getNameAsString()
                                        .equals(methodName)
                        )
                        .findFirst();

        return method.orElseThrow();
    }

    private MethodCallInfo callTo(
            List<MethodCallInfo> calls, String methodName) {

        return calls.stream()
                .filter(call ->
                        call.getTargetMethod().equals(methodName)
                )
                .findFirst()
                .orElseThrow();
    }
}
