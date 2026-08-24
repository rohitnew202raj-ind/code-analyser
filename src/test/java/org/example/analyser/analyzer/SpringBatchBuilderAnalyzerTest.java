package org.example.analyser.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.TriggerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SpringBatchBuilderAnalyzerTest {

    private final SpringBatchBuilderAnalyzer analyzer =
            new SpringBatchBuilderAnalyzer();

    @Test
    void detectsAJobBuiltViaJobBuilderInABeanMethod() {

        List<EntryPointInfo> results = analyze(
                """
                package com.acme.batch;

                public class BatchConfig {

                    @org.springframework.context.annotation.Bean
                    public Job importUserJob(JobRepository jobRepository, Step step1) {
                        return new JobBuilder("importUserJob", jobRepository)
                                .start(step1)
                                .build();
                    }
                }
                """
        );

        assertThat(results)
                .extracting(
                        EntryPointInfo::getTriggerType,
                        EntryPointInfo::getMethodName
                )
                .containsExactly(
                        tuple(TriggerType.SPRING_BATCH_JOB_BUILDER, "importUserJob")
                );
    }

    @Test
    void detectsAStepBuiltViaStepBuilderInABeanMethod() {

        List<EntryPointInfo> results = analyze(
                """
                package com.acme.batch;

                public class BatchConfig {

                    @org.springframework.context.annotation.Bean
                    public Step step1(JobRepository jobRepository) {
                        return new StepBuilder("step1", jobRepository)
                                .tasklet(tasklet, transactionManager)
                                .build();
                    }
                }
                """
        );

        assertThat(results)
                .extracting(EntryPointInfo::getTriggerType)
                .containsExactly(TriggerType.SPRING_BATCH_STEP_BUILDER);
    }

    @Test
    void ignoresAJobBuilderConstructedOutsideABeanMethod() {

        List<EntryPointInfo> results = analyze(
                """
                package com.acme.batch;

                public class BatchConfig {

                    public Job importUserJob(JobRepository jobRepository, Step step1) {
                        return new JobBuilder("importUserJob", jobRepository)
                                .start(step1)
                                .build();
                    }
                }
                """
        );

        assertThat(results).isEmpty();
    }

    @Test
    void ignoresAPlainBeanMethodWithNoBuilder() {

        List<EntryPointInfo> results = analyze(
                """
                package com.acme.batch;

                public class BatchConfig {

                    @org.springframework.context.annotation.Bean
                    public DataSource dataSource() {
                        return new HikariDataSource();
                    }
                }
                """
        );

        assertThat(results).isEmpty();
    }

    private List<EntryPointInfo> analyze(String source) {

        CompilationUnit cu = StaticJavaParser.parse(source);

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("BatchConfig");
        classInfo.setPackageName("com.acme.batch");

        return analyzer.analyze(
                declaration,
                classInfo,
                PackageDomainExtractor.fit(List.of(classInfo))
        );
    }
}
