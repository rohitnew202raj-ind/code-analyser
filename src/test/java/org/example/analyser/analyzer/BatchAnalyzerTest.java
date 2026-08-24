package org.example.analyser.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.TriggerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

        List<EntryPointInfo> results =
                batchAnalyzer.analyze(
                        declaration,
                        classInfo,
                        PackageDomainExtractor.fit(List.of(classInfo))
                );

        assertThat(results)
                .extracting(EntryPointInfo::getTriggerType)
                .contains(TriggerType.MAIN_ENTRY_POINT);
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

        List<EntryPointInfo> results =
                batchAnalyzer.analyze(
                        declaration,
                        classInfo,
                        PackageDomainExtractor.fit(List.of(classInfo))
                );

        assertThat(results)
                .extracting(EntryPointInfo::getTriggerType)
                .doesNotContain(TriggerType.MAIN_ENTRY_POINT);
    }

    @Test
    void detectsCommandLineRunnerAsStartupRunner() {

        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme.seed;

                import org.springframework.boot.CommandLineRunner;
                import org.springframework.stereotype.Component;

                @Component
                public class DataSeeder implements CommandLineRunner {
                    @Override
                    public void run(String... args) {
                    }
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("DataSeeder");
        classInfo.setPackageName("com.acme.seed");
        classInfo.getImplementedTypes().add("CommandLineRunner");

        List<EntryPointInfo> results =
                batchAnalyzer.analyze(
                        declaration,
                        classInfo,
                        PackageDomainExtractor.fit(List.of(classInfo))
                );

        assertThat(results)
                .extracting(
                        EntryPointInfo::getTriggerType,
                        EntryPointInfo::getMethodName
                )
                .containsExactly(tuple(TriggerType.STARTUP_RUNNER, "run"));
    }

    @Test
    void detectsApplicationRunnerAsStartupRunner() {

        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme.seed;

                import org.springframework.boot.ApplicationRunner;
                import org.springframework.stereotype.Component;

                @Component
                public class MigrationRunner implements ApplicationRunner {
                    @Override
                    public void run(org.springframework.boot.ApplicationArguments args) {
                    }
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("MigrationRunner");
        classInfo.setPackageName("com.acme.seed");
        classInfo.getImplementedTypes().add("ApplicationRunner");

        List<EntryPointInfo> results =
                batchAnalyzer.analyze(
                        declaration,
                        classInfo,
                        PackageDomainExtractor.fit(List.of(classInfo))
                );

        assertThat(results)
                .extracting(EntryPointInfo::getTriggerType)
                .containsExactly(TriggerType.STARTUP_RUNNER);
    }

    @Test
    void mapsEachTriggerAnnotationToItsOwnDistinctTriggerType() {

        // Regression coverage for the bug this fixes: these six
        // annotations used to collapse into ad hoc uppercased
        // strings (some missing an underscore entirely, e.g.
        // "EVENTLISTENER"/"KAFKALISTENER") rather than being
        // genuinely distinct, typo-proof enum constants.
        CompilationUnit cu = StaticJavaParser.parse(
                """
                package com.acme;

                public class Triggers {
                    @org.springframework.scheduling.annotation.Scheduled
                    public void onSchedule() {}

                    @org.springframework.scheduling.annotation.Async
                    public void onAsync() {}

                    @org.springframework.context.event.EventListener
                    public void onEvent() {}

                    @org.springframework.kafka.annotation.KafkaListener
                    public void onKafka() {}

                    @org.springframework.jms.annotation.JmsListener
                    public void onJms() {}

                    @org.springframework.amqp.rabbit.annotation.RabbitListener
                    public void onRabbit() {}
                }
                """
        );

        TypeDeclaration<?> declaration =
                cu.findAll(TypeDeclaration.class).get(0);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName("Triggers");
        classInfo.setPackageName("com.acme");

        List<EntryPointInfo> results =
                batchAnalyzer.analyze(
                        declaration,
                        classInfo,
                        PackageDomainExtractor.fit(List.of(classInfo))
                );

        assertThat(results)
                .extracting(
                        EntryPointInfo::getTriggerType,
                        EntryPointInfo::getMethodName
                )
                .containsExactlyInAnyOrder(
                        tuple(TriggerType.SCHEDULED, "onSchedule"),
                        tuple(TriggerType.ASYNC, "onAsync"),
                        tuple(TriggerType.EVENT_LISTENER, "onEvent"),
                        tuple(TriggerType.KAFKA_CONSUMER, "onKafka"),
                        tuple(TriggerType.JMS_CONSUMER, "onJms"),
                        tuple(TriggerType.RABBIT_CONSUMER, "onRabbit")
                );
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

        List<EntryPointInfo> results =
                batchAnalyzer.analyze(
                        declaration,
                        classInfo,
                        PackageDomainExtractor.fit(List.of(classInfo))
                );

        assertThat(results).isEmpty();
    }
}
