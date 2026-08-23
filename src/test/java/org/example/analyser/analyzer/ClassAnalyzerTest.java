package org.example.analyser.analyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.example.analyser.model.ClassInfo;
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
    void redactsSecretLikeFieldLiterals() {

        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme;

                public class ApiConfig {
                    private String apiKey = "sk-live-super-secret";
                    private String displayName = "Public Name";
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = classAnalyzer.analyze(cu, declaration);

        String apiKeyField =
                classInfo.getFields().stream()
                        .filter(f -> f.contains("apiKey"))
                        .findFirst()
                        .orElseThrow();

        assertThat(apiKeyField)
                .doesNotContain("sk-live-super-secret")
                .contains("REDACTED");

        String displayNameField =
                classInfo.getFields().stream()
                        .filter(f -> f.contains("displayName"))
                        .findFirst()
                        .orElseThrow();

        assertThat(displayNameField).contains("Public Name");
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
