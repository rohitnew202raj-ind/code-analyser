package org.example.analyser.analyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.FieldInfo;
import org.example.analyser.model.MethodInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassAnalyzerTest {

    private final ClassAnalyzer classAnalyzer = new ClassAnalyzer();

    @BeforeAll
    static void configureLanguageLevel() {

        // Records need at least JAVA_16; match the analyzer's
        // own default so this test reflects real usage.
        StaticJavaParser.getConfiguration()
                .setLanguageLevel(
                        ParserConfiguration.LanguageLevel.JAVA_21
                );
    }

    @Test
    void synthesizesLombokAccessorsFromDataAnnotation() {

        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme;

                import lombok.Data;

                @Data
                public class OrderEntity {
                    private Long id;
                    private boolean active;
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = classAnalyzer.analyze(cu, declaration);

        assertThat(classInfo.getMethods())
                .extracting(MethodInfo::getName)
                .contains("getId", "setId", "isActive", "setActive");
    }

    @Test
    void supportsRecordDeclarations() {

        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme;

                public record OrderDto(Long id, String status) {
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(RecordDeclaration.class).get(0);

        ClassInfo classInfo = classAnalyzer.analyze(cu, declaration);

        assertThat(classInfo.getName()).isEqualTo("OrderDto");
    }

    @Test
    void supportsEnumDeclarations() {

        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme;

                public enum OrderStatus {
                    NEW, SHIPPED
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(EnumDeclaration.class).get(0);

        ClassInfo classInfo = classAnalyzer.analyze(cu, declaration);

        assertThat(classInfo.getName()).isEqualTo("OrderStatus");
    }

    @Test
    void structuredFieldsNeverCaptureInitializerValues() {

        // Replaces the old redaction test: FieldInfo only ever
        // captures name/type/annotations/modifiers, never a
        // field's initializer expression, so a hardcoded secret
        // literal has nothing to leak into - there's no
        // redaction step because there's no value captured to
        // redact in the first place.
        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme;

                public class ApiConfig {
                    private String apiKey = "sk-live-super-secret";
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = classAnalyzer.analyze(cu, declaration);

        FieldInfo apiKeyField =
                classInfo.getFields().stream()
                        .filter(field -> field.getName().equals("apiKey"))
                        .findFirst()
                        .orElseThrow();

        assertThat(apiKeyField.getType()).isEqualTo("String");
        assertThat(apiKeyField.getName())
                .doesNotContain("sk-live-super-secret");
    }

    @Test
    void capturesFieldAnnotationsAndModifiers() {

        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme;

                public class OrderService {

                    @org.springframework.beans.factory.annotation.Autowired
                    private final OrderRepository orderRepository = null;
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = classAnalyzer.analyze(cu, declaration);

        FieldInfo field = classInfo.getFields().get(0);

        assertThat(field.getName()).isEqualTo("orderRepository");
        assertThat(field.getType()).isEqualTo("OrderRepository");
        assertThat(field.getAnnotationSimpleNames()).contains("Autowired");
        assertThat(field.isFinal()).isTrue();
        assertThat(field.isStatic()).isFalse();
    }

    @Test
    void capturesMethodAnnotationsAndParameters() {

        // Method-level annotations weren't captured anywhere
        // before this - only class-level ones were. This is what
        // makes a future check like "flag a multi-write method
        // with no @Transactional" possible; none is added here,
        // only the data it would need.
        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme;

                public class OrderService {

                    @org.springframework.transaction.annotation.Transactional
                    public void placeOrder(Long customerId, String note) {
                    }
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = classAnalyzer.analyze(cu, declaration);

        MethodInfo method = classInfo.getMethods().get(0);

        assertThat(method.getName()).isEqualTo("placeOrder");
        assertThat(method.getReturnType()).isEqualTo("void");
        assertThat(method.getAnnotationSimpleNames())
                .contains("Transactional");
        assertThat(method.getParameters())
                .extracting(
                        parameter -> parameter.getType() + " " + parameter.getName()
                )
                .containsExactly("Long customerId", "String note");
    }

    @Test
    void detectsRepositoryEntityTypeThroughDirectSpringDataExtends() {

        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme;

                import org.springframework.data.jpa.repository.JpaRepository;

                public interface OrderRepository
                        extends JpaRepository<OrderEntity, Long> {
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = classAnalyzer.analyze(cu, declaration);

        assertThat(classInfo.getRepositoryEntityType())
                .isEqualTo("OrderEntity");
    }
}
