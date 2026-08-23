package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.springframework.stereotype.Component;

@Component
public class SpringComponentAnalyzer {

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
        // that only look at a single `type`.
        classInfo.setType(primaryType(classInfo));
    }

    private String primaryType(ClassInfo classInfo) {

        if (classInfo.hasRole("APPLICATION")) {
            return "APPLICATION";
        }

        if (classInfo.hasRole("REST_CONTROLLER")) {
            return "CONTROLLER";
        }

        if (classInfo.hasRole("MVC_CONTROLLER")) {
            return "MVC_CONTROLLER";
        }

        if (classInfo.hasRole("SERVICE")) {
            return "SERVICE";
        }

        if (classInfo.hasRole("REPOSITORY")) {
            return "REPOSITORY";
        }

        if (classInfo.hasRole("ENTITY")) {
            return "ENTITY";
        }

        if (classInfo.hasRole("CONFIGURATION")) {
            return "CONFIGURATION";
        }

        if (classInfo.hasRole("ASPECT")) {
            return "ASPECT";
        }

        if (classInfo.hasRole("CONTROLLER_ADVICE")) {
            return "CONTROLLER_ADVICE";
        }

        if (classInfo.hasRole("FEIGN_CLIENT")) {
            return "FEIGN_CLIENT";
        }

        if (classInfo.hasRole("GRPC_SERVICE")) {
            return "GRPC_SERVICE";
        }

        if (classInfo.hasRole("COMPONENT")) {
            return "COMPONENT";
        }

        return "UNKNOWN";
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
