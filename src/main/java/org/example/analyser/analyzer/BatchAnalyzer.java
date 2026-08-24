package org.example.analyser.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.TriggerType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Finds batch/scheduled/integration entry points - the
 * counterpart to ApiAnalyzer for REST endpoints.
 *
 * Previously, "batch" was guessed from a class being a plain
 * @Component that depends on a repository - a heuristic that
 * only worked because the one test-project batch class
 * happened to be a @Component. This instead looks at actual
 * batch/scheduling signals:
 *
 *  - @Scheduled / @Async / @EventListener / @KafkaListener /
 *    @JmsListener annotated methods
 *  - classes implementing well-known Spring Batch interfaces
 *    (Tasklet, ItemReader, ItemWriter, ItemProcessor)
 *  - classes implementing CommandLineRunner/ApplicationRunner -
 *    Spring's own hook for "run this once at startup," a
 *    common shape for a one-off data-seeding or migration
 *    class that carries no other batch/scheduling signal
 *  - a plain "public static void main(String[])" method - the
 *    standard shape for a cron-invoked batch JAR that has no
 *    Spring annotations of its own (confirmed missing on a
 *    real synthetic monolith: a BatchRunnerMain/main() class
 *    was invisible to every one of the signals above)
 *
 * LIMITATION (documented, not solved): Spring Batch jobs
 * assembled via Job/Step builders in a @Configuration class
 * (rather than a dedicated Tasklet/ItemReader/Writer class)
 * are not detected here - recognizing those would require
 * following builder-style fluent method chains
 * (.get("step1").tasklet(...).build()), which is a data-flow
 * analysis problem, not a per-node AST check.
 *
 * Each trigger annotation maps to its own {@link TriggerType}
 * constant (KAFKA_CONSUMER, JMS_CONSUMER, RABBIT_CONSUMER,
 * EVENT_LISTENER, ...) rather than one generic "batch" label -
 * these are genuinely different execution models with different
 * retry/ordering/idempotency implications, even though they were
 * all treated as one thing earlier in this tool's history. See
 * LIMITATIONS.md.
 */
@Component
public class BatchAnalyzer {

    private static final Map<String, TriggerType> TRIGGER_ANNOTATIONS =
            Map.of(
                    "Scheduled", TriggerType.SCHEDULED,
                    "Async", TriggerType.ASYNC,
                    "EventListener", TriggerType.EVENT_LISTENER,
                    "KafkaListener", TriggerType.KAFKA_CONSUMER,
                    "JmsListener", TriggerType.JMS_CONSUMER,
                    "RabbitListener", TriggerType.RABBIT_CONSUMER
            );

    private static final Set<String> BATCH_INTERFACES =
            Set.of(
                    "Tasklet",
                    "ItemReader",
                    "ItemWriter",
                    "ItemProcessor"
            );

    private static final Set<String> STARTUP_RUNNER_INTERFACES =
            Set.of(
                    "CommandLineRunner",
                    "ApplicationRunner"
            );

    public List<EntryPointInfo> analyze(
            TypeDeclaration<?> clazz,
            ClassInfo classInfo,
            PackageDomainExtractor domainExtractor) {

        List<EntryPointInfo> results =
                new ArrayList<>();

        String domain =
                domainExtractor.domainOf(
                        classInfo.getPackageName()
                );

        boolean implementsBatchInterface =
                classInfo.getImplementedTypes()
                        .stream()
                        .anyMatch(BATCH_INTERFACES::contains);

        if (implementsBatchInterface) {

            results.add(
                    new EntryPointInfo(
                            classInfo.getName(),
                            classInfo.getPackageName(),
                            "execute",
                            TriggerType.SPRING_BATCH_STEP_COMPONENT,
                            null,
                            domain
                    )
            );
        }

        boolean isStartupRunner =
                classInfo.getImplementedTypes()
                        .stream()
                        .anyMatch(STARTUP_RUNNER_INTERFACES::contains);

        if (isStartupRunner) {

            results.add(
                    new EntryPointInfo(
                            classInfo.getName(),
                            classInfo.getPackageName(),
                            "run",
                            TriggerType.STARTUP_RUNNER,
                            null,
                            domain
                    )
            );
        }

        boolean isSpringBootApplicationEntryPoint =
                classInfo.hasRole("APPLICATION");

        clazz.getMethods()
                .stream()
                .filter(this::isMainMethod)
                .filter(method -> !isSpringBootApplicationEntryPoint)
                .forEach(method ->
                        results.add(
                                new EntryPointInfo(
                                        classInfo.getName(),
                                        classInfo.getPackageName(),
                                        method.getNameAsString(),
                                        TriggerType.MAIN_ENTRY_POINT,
                                        null,
                                        domain
                                )
                        )
                );

        for (MethodDeclaration method : clazz.getMethods()) {

            method.getAnnotations()
                    .stream()
                    .map(AnnotationNames::simpleName)
                    .map(TRIGGER_ANNOTATIONS::get)
                    .filter(Objects::nonNull)
                    .forEach(trigger ->
                            results.add(
                                    new EntryPointInfo(
                                            classInfo.getName(),
                                            classInfo.getPackageName(),
                                            method.getNameAsString(),
                                            trigger,
                                            null,
                                            domain
                                    )
                            )
                    );
        }

        return results;
    }

    private boolean isMainMethod(MethodDeclaration method) {

        return method.getNameAsString().equals("main")
                && method.isStatic()
                && method.isPublic()
                && method.getParameters().size() == 1;
    }
}
