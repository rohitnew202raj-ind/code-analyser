package org.example.analyser.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.TriggerType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds Spring Batch jobs/steps assembled via the modern
 * {@code JobBuilder}/{@code StepBuilder} fluent API inside a
 * {@code @Bean} method - the exact gap {@link BatchAnalyzer}'s
 * own javadoc calls out as unsolved, since that class only
 * recognizes a class directly implementing {@code Tasklet}/
 * {@code ItemReader}/etc., not a builder chain assembled inside a
 * {@code @Configuration} method body.
 *
 * Detection: a {@code @Bean} method whose body constructs a
 * {@code new JobBuilder(...)} or {@code new StepBuilder(...)}
 * anywhere in it. Requiring {@code @Bean} keeps this to methods
 * Spring will actually register - nobody constructs a
 * {@code JobBuilder}/{@code StepBuilder} for any other reason in
 * practice, so this is a strong, low-false-positive signal on its
 * own.
 *
 * SCOPE (documented, not a bug): this only makes the job/step
 * *visible* as an entry point (className/methodName/domain) - it
 * does not attempt to trace which {@code ItemReader}/
 * {@code ItemProcessor}/{@code ItemWriter} beans a step actually
 * wires. Doing that would mean following each builder call's
 * arguments back to the parameter/field that produced them (a
 * data-flow problem, not a per-node AST check) - a real, larger
 * follow-up, not attempted here.
 */
@Component
public class SpringBatchBuilderAnalyzer {

    public List<EntryPointInfo> analyze(
            TypeDeclaration<?> clazz,
            ClassInfo classInfo,
            PackageDomainExtractor domainExtractor) {

        List<EntryPointInfo> results = new ArrayList<>();

        String domain =
                domainExtractor.domainOf(classInfo.getPackageName());

        for (MethodDeclaration method : clazz.getMethods()) {

            boolean isBeanMethod =
                    method.getAnnotations()
                            .stream()
                            .map(AnnotationNames::simpleName)
                            .anyMatch(name -> name.equals("Bean"));

            if (!isBeanMethod) {
                continue;
            }

            TriggerType triggerType = builderTriggerType(method);

            if (triggerType == null) {
                continue;
            }

            results.add(
                    new EntryPointInfo(
                            classInfo.getName(),
                            classInfo.getPackageName(),
                            method.getNameAsString(),
                            triggerType,
                            null,
                            domain
                    )
            );
        }

        return results;
    }

    private TriggerType builderTriggerType(MethodDeclaration method) {

        if (constructs(method, "JobBuilder")) {
            return TriggerType.SPRING_BATCH_JOB_BUILDER;
        }

        if (constructs(method, "StepBuilder")) {
            return TriggerType.SPRING_BATCH_STEP_BUILDER;
        }

        return null;
    }

    private boolean constructs(MethodDeclaration method, String typeName) {

        return method.findAll(ObjectCreationExpr.class)
                .stream()
                .anyMatch(creation ->
                        creation.getType()
                                .getNameAsString()
                                .equals(typeName)
                );
    }
}
