package org.example.analyser.analyzer;

import org.example.analyser.model.ClassificationSource;
import org.example.analyser.model.ClassInfo;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SpringComponentAnalyzer {

    private static final Set<String> EXCEPTION_SUPERTYPES =
            Set.of("Exception", "RuntimeException", "Error", "Throwable");

    /**
     * Classifies a class's primary type, and separately
     * records every applicable Spring "role" it carries
     * (a class can legitimately be more than one thing at
     * once, e.g. a @Component that is also @Aspect).
     *
     * @param resolver resolves composed/meta annotations
     *                  declared inside the scanned project;
     *                  pass {@link MetaAnnotationResolver#EMPTY}
     *                  if that registry hasn't been built yet.
     */
    public void classify(
            ClassInfo classInfo,
            MetaAnnotationResolver resolver) {

        // Roles: every stereotype this class carries,
        // directly or through a composed annotation.
        addRoleIfPresent(classInfo, resolver, "APPLICATION", "SpringBootApplication");
        addRoleIfPresent(classInfo, resolver, "REST_CONTROLLER", "RestController");
        addRoleIfPresent(classInfo, resolver, "MVC_CONTROLLER", "Controller");
        addRoleIfPresent(classInfo, resolver, "SERVICE", "Service");
        addRoleIfPresent(classInfo, resolver, "REPOSITORY", "Repository");
        addRoleIfPresent(classInfo, resolver, "ENTITY", "Entity");
        addRoleIfPresent(classInfo, resolver, "CONFIGURATION", "Configuration");
        addRoleIfPresent(classInfo, resolver, "COMPONENT", "Component");
        addRoleIfPresent(classInfo, resolver, "ASPECT", "Aspect");
        addRoleIfPresent(classInfo, resolver, "CONTROLLER_ADVICE", "ControllerAdvice");
        addRoleIfPresent(classInfo, resolver, "CONTROLLER_ADVICE", "RestControllerAdvice");
        addRoleIfPresent(classInfo, resolver, "FEIGN_CLIENT", "FeignClient");
        addRoleIfPresent(classInfo, resolver, "GRPC_SERVICE", "GrpcService");

        if (extendsRepository(classInfo)) {
            classInfo.getRoles().add("REPOSITORY");
        }

        // Primary type: first-match priority, kept for
        // backward compatibility with existing consumers
        // that only look at a single `type`. Paired with
        // *why* it was determined - see ClassificationSource -
        // so a consumer of report.json can tell a confirmed
        // fact from an educated guess.
        Classification classification =
                determineType(classInfo, resolver);

        classInfo.setType(classification.type());
        classInfo.setTypeSource(classification.source());
    }

    private record Classification(
            String type,
            ClassificationSource source) {
    }

    private Classification determineType(
            ClassInfo classInfo,
            MetaAnnotationResolver resolver) {

        if (classInfo.hasRole("APPLICATION")) {
            return new Classification("APPLICATION", ClassificationSource.ANNOTATION);
        }

        if (classInfo.hasRole("REST_CONTROLLER")) {
            return new Classification("CONTROLLER", ClassificationSource.ANNOTATION);
        }

        if (classInfo.hasRole("MVC_CONTROLLER")) {
            return new Classification("MVC_CONTROLLER", ClassificationSource.ANNOTATION);
        }

        if (classInfo.hasRole("SERVICE")) {
            return new Classification("SERVICE", ClassificationSource.ANNOTATION);
        }

        if (classInfo.hasRole("REPOSITORY")) {

            /*
             * REPOSITORY is added two ways: directly annotated
             * (@Repository, ANNOTATION) or by extending a known
             * Spring Data base interface with no annotation of
             * its own (STRUCTURAL) - see extendsRepository().
             * Distinguish them rather than reporting every
             * repository as equally "confirmed by annotation."
             */
            ClassificationSource source =
                    hasAnnotation(classInfo, resolver, "Repository")
                            ? ClassificationSource.ANNOTATION
                            : ClassificationSource.STRUCTURAL;

            return new Classification("REPOSITORY", source);
        }

        if (classInfo.hasRole("ENTITY")) {
            return new Classification("ENTITY", ClassificationSource.ANNOTATION);
        }

        if (classInfo.hasRole("CONFIGURATION")) {
            return new Classification("CONFIGURATION", ClassificationSource.ANNOTATION);
        }

        if (classInfo.hasRole("ASPECT")) {
            return new Classification("ASPECT", ClassificationSource.ANNOTATION);
        }

        if (classInfo.hasRole("CONTROLLER_ADVICE")) {
            return new Classification("CONTROLLER_ADVICE", ClassificationSource.ANNOTATION);
        }

        if (classInfo.hasRole("FEIGN_CLIENT")) {
            return new Classification("FEIGN_CLIENT", ClassificationSource.ANNOTATION);
        }

        if (classInfo.hasRole("GRPC_SERVICE")) {
            return new Classification("GRPC_SERVICE", ClassificationSource.ANNOTATION);
        }

        if (classInfo.hasRole("COMPONENT")) {
            return new Classification("COMPONENT", ClassificationSource.ANNOTATION);
        }

        return fallbackClassification(classInfo);
    }

    /**
     * Classifies a class that carries no recognized Spring
     * stereotype. These are still real, distinct kinds of
     * class - DTOs, domain events, exceptions, constants
     * holders, plain interfaces, and so on - so collapsing all
     * of them into a single "UNKNOWN" bucket would throw away
     * information the report could otherwise surface. Ordered
     * from most to least specific; POJO is the final catch-all
     * for anything that matches none of the patterns below, and
     * is tagged {@code NONE} rather than {@code NAMING_HEURISTIC}
     * since no heuristic actually matched to produce it.
     */
    private Classification fallbackClassification(ClassInfo classInfo) {

        if (isException(classInfo)) {

            ClassificationSource source =
                    extendsKnownExceptionSupertype(classInfo)
                            ? ClassificationSource.STRUCTURAL
                            : ClassificationSource.NAMING_HEURISTIC;

            return new Classification("EXCEPTION", source);
        }

        String name = classInfo.getName();

        if (name.endsWith("Event")) {
            return new Classification("EVENT", ClassificationSource.NAMING_HEURISTIC);
        }

        if (name.endsWith("Dto")) {
            return new Classification("DTO", ClassificationSource.NAMING_HEURISTIC);
        }

        if (name.endsWith("Constants")) {
            return new Classification("CONSTANTS", ClassificationSource.NAMING_HEURISTIC);
        }

        if (name.endsWith("Specification")) {
            return new Classification("SPECIFICATION", ClassificationSource.NAMING_HEURISTIC);
        }

        if (name.endsWith("Helper")
                || name.endsWith("Utils")
                || name.endsWith("Util")) {

            return new Classification("UTILITY", ClassificationSource.NAMING_HEURISTIC);
        }

        if (classInfo.isInterfaceDeclaration()) {

            // A confirmed AST fact (this is literally an
            // `interface` declaration), not a name-based guess.
            return new Classification("INTERFACE", ClassificationSource.STRUCTURAL);
        }

        return new Classification("POJO", ClassificationSource.NONE);
    }

    private boolean isException(ClassInfo classInfo) {

        return classInfo.getName().endsWith("Exception")
                || extendsKnownExceptionSupertype(classInfo);
    }

    private boolean extendsKnownExceptionSupertype(ClassInfo classInfo) {

        return classInfo.getExtendedTypes()
                .stream()
                .anyMatch(EXCEPTION_SUPERTYPES::contains);
    }

    private void addRoleIfPresent(
            ClassInfo classInfo,
            MetaAnnotationResolver resolver,
            String role,
            String stereotypeAnnotation) {

        if (hasAnnotation(classInfo, resolver, stereotypeAnnotation)) {
            classInfo.getRoles().add(role);
        }
    }

    /**
     * True if the class is directly annotated with
     * {@code stereotypeAnnotation} (matched by simple name,
     * so a fully-qualified usage still matches), or carries a
     * composed annotation that transitively carries it.
     */
    private boolean hasAnnotation(
            ClassInfo classInfo,
            MetaAnnotationResolver resolver,
            String stereotypeAnnotation) {

        return classInfo.getAnnotationSimpleNames()
                .stream()
                .anyMatch(annotationName ->
                        resolver.carries(
                                annotationName,
                                stereotypeAnnotation
                        )
                );
    }

    private boolean extendsRepository(ClassInfo classInfo) {

        return classInfo.getExtendedTypes()
                .stream()
                .anyMatch(type ->
                        type.equals("JpaRepository")
                                || type.equals("CrudRepository")
                                || type.equals("PagingAndSortingRepository")
                );
    }
}
