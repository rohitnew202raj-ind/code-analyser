package org.example.analyser.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.EntityMutationInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMutationAnalyzerTest {

    private final EntityMutationAnalyzer entityMutationAnalyzer =
            new EntityMutationAnalyzer(new TypeResolver());

    @Test
    void recordsASetterCallAsAFieldMutationNotAnUpdate() {

        // The bug this guards against: a setter call only proves
        // an in-memory change, not a confirmed database write, so
        // the recorded operation must not claim "UPDATE" - that's
        // CrudAnalyzer/CrudOperationInfo's vocabulary for an
        // actual repository write.
        List<EntityMutationInfo> mutations = analyzeMethod(
                """
                package com.acme;

                public class OrderService {

                    public void cancel() {
                        Order order = new Order();
                        order.setStatus("CANCELLED");
                    }
                }
                """,
                "cancel"
        );

        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0).getOperation())
                .isEqualTo("FIELD_MUTATION");
        assertThat(mutations.get(0).getOperation())
                .isNotEqualTo("UPDATE");
    }

    @Test
    void derivesTheFieldNameFromTheSetterName() {

        List<EntityMutationInfo> mutations = analyzeMethod(
                """
                package com.acme;

                public class OrderService {

                    public void cancel() {
                        Order order = new Order();
                        order.setStatus("CANCELLED");
                    }
                }
                """,
                "cancel"
        );

        assertThat(mutations.get(0).getFieldName()).isEqualTo("status");
        assertThat(mutations.get(0).getEntityClass()).isEqualTo("Order");
    }

    @Test
    void ignoresCallsThatAreNotSetters() {

        List<EntityMutationInfo> mutations = analyzeMethod(
                """
                package com.acme;

                public class OrderService {

                    public void cancel() {
                        Order order = new Order();
                        order.getStatus();
                    }
                }
                """,
                "cancel"
        );

        assertThat(mutations).isEmpty();
    }

    private List<EntityMutationInfo> analyzeMethod(
            String source, String methodName) {

        CompilationUnit cu = StaticJavaParser.parse(source);

        ClassInfo sourceClass = new ClassInfo();
        sourceClass.setName("OrderService");
        sourceClass.setPackageName("com.acme");

        ClassInfo order = new ClassInfo();
        order.setName("Order");
        order.setType("ENTITY");

        MethodDeclaration method =
                cu.findAll(MethodDeclaration.class).stream()
                        .filter(candidate ->
                                candidate.getNameAsString()
                                        .equals(methodName)
                        )
                        .findFirst()
                        .orElseThrow();

        return entityMutationAnalyzer.analyze(
                method, sourceClass, List.of(sourceClass, order)
        );
    }
}
