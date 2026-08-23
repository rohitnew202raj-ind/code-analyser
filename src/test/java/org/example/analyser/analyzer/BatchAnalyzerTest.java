package org.example.analyser.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.example.analyser.model.BatchProgramInfo;
import org.example.analyser.model.ClassInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BatchAnalyzerTest {

    private final BatchAnalyzer batchAnalyzer = new BatchAnalyzer();

    @Test
    void detectsPlainMainMethodAsBatchEntryPoint() {

        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme.reporting;

                public class NightlyReportRunner {
                    public static void main(String[] args) {
                        System.out.println("running");
                    }
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("NightlyReportRunner");
        classInfo.setPackageName("com.acme.reporting");

        List<BatchProgramInfo> results =
                batchAnalyzer.analyze(
                        declaration,
                        classInfo,
                        PackageDomainExtractor.fit(List.of(classInfo))
                );

        assertThat(results)
                .extracting(BatchProgramInfo::getTrigger)
                .contains("MAIN_ENTRY_POINT");
    }

    @Test
    void doesNotFlagTheSpringBootApplicationEntryPointAsBatch() {

        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme;

                public class MyApplication {
                    public static void main(String[] args) {
                    }
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("MyApplication");
        classInfo.setPackageName("com.acme");
        classInfo.getRoles().add("APPLICATION");
        classInfo.setType("APPLICATION");

        List<BatchProgramInfo> results =
                batchAnalyzer.analyze(
                        declaration,
                        classInfo,
                        PackageDomainExtractor.fit(List.of(classInfo))
                );

        assertThat(results)
                .extracting(BatchProgramInfo::getTrigger)
                .doesNotContain("MAIN_ENTRY_POINT");
    }

    @Test
    void ignoresNonMainMethodsNamedMain() {

        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme;

                public class NotAnEntryPoint {
                    private void main() {
                    }
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("NotAnEntryPoint");
        classInfo.setPackageName("com.acme");

        List<BatchProgramInfo> results =
                batchAnalyzer.analyze(
                        declaration,
                        classInfo,
                        PackageDomainExtractor.fit(List.of(classInfo))
                );

        assertThat(results).isEmpty();
    }
}
