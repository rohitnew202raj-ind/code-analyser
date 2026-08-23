package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.MethodCallInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CrudAnalyzer {

    public List<CrudOperationInfo> analyze(
            List<MethodCallInfo> methodCalls,
            List<ClassInfo> classes) {

        List<CrudOperationInfo> operations =
                new ArrayList<>();

        for (MethodCallInfo call : methodCalls) {

            if (!isRepository(call, classes)) {
                continue;
            }

            String operation =
                    detectOperation(
                            call.getTargetMethod()
                    );

            ClassInfo repository =
                    findClass(
                            classes,
                            call.getTargetClass()
                    );

            if (repository == null) {
                continue;
            }

            String entityClass =
                    findEntityForRepository(
                            repository,
                            classes
                    );

            String tableName =
                    findTableName(
                            entityClass,
                            classes
                    );

            operations.add(
                    new CrudOperationInfo(
                            call.getSourceClass(),
                            call.getSourceMethod(),
                            call.getTargetClass(),
                            call.getTargetMethod(),
                            operation,
                            entityClass,
                            tableName
                    )
            );
        }

        return operations;
    }

    private boolean isRepository(
            MethodCallInfo call,
            List<ClassInfo> classes) {

        ClassInfo target =
                findClass(
                        classes,
                        call.getTargetClass()
                );

        return target != null
                && "REPOSITORY".equals(
                target.getType()
        );
    }

    /*
     * LIMITATION (documented): this covers Spring Data's
     * built-in and derived-query method name conventions.
     * Anything matching none of them - a hand-written
     * @Query-annotated method with an arbitrary name, for
     * instance - is still reported (as CUSTOM_QUERY) rather
     * than silently dropped, which is the actual bug fix here:
     * the previous version returned null for anything
     * unrecognized, which the caller then discarded entirely,
     * *including* existsById() (it only matched the exact
     * literal "exists", not the "existsBy..." family Spring
     * Data actually generates).
     */
    private String detectOperation(
            String methodName) {

        if (methodName.equals("save")
                || methodName.equals("saveAll")
                || methodName.equals("saveAndFlush")) {

            return "CREATE_OR_UPDATE";
        }

        if (methodName.startsWith("update")) {
            return "UPDATE";
        }

        if (methodName.startsWith("find")
                || methodName.startsWith("get")
                || methodName.startsWith("read")
                || methodName.startsWith("query")
                || methodName.startsWith("count")
                || methodName.startsWith("exists")) {

            return "READ";
        }

        if (methodName.startsWith("delete")
                || methodName.startsWith("remove")) {

            return "DELETE";
        }

        if (methodName.equals("flush")) {
            return "FLUSH";
        }

        return "CUSTOM_QUERY";
    }

    private ClassInfo findClass(
            List<ClassInfo> classes,
            String className) {

        return classes.stream()
                .filter(clazz ->
                        clazz.getName()
                                .equals(className))
                .findFirst()
                .orElse(null);
    }

    /*
     * LIMITATION (documented, partially addressed): prefer
     * the entity type ClassAnalyzer already derived from the
     * repository's actual generic declaration
     * (JpaRepository<Entity, Id>, resolved transitively
     * through custom base repositories too - see
     * RepositoryInheritanceResolver). Only fall back to
     * guessing from the repository's *name* when that isn't
     * available, since a repository named e.g. "OrdersDao" or
     * "IOrderRepository" won't match any name-based guess.
     */
    private String findEntityForRepository(
            ClassInfo repository,
            List<ClassInfo> classes) {

        if (repository.getRepositoryEntityType() != null) {
            return repository.getRepositoryEntityType();
        }

        String repositoryName =
                repository.getName();

        String candidate =
                repositoryName.replace(
                        "Repository",
                        ""
                );

        ClassInfo entity =
                classes.stream()
                        .filter(clazz ->
                                "ENTITY".equals(
                                        clazz.getType()
                                ))
                        .filter(clazz ->
                                clazz.getName()
                                        .equals(candidate)
                                        || clazz.getName()
                                        .equals(
                                                candidate + "Entity"
                                        ))
                        .findFirst()
                        .orElse(null);

        return entity != null
                ? entity.getName()
                : null;
    }

    private String findTableName(
            String entityClass,
            List<ClassInfo> classes) {

        if (entityClass == null) {
            return null;
        }

        ClassInfo entity =
                findClass(
                        classes,
                        entityClass
                );

        return findTableName(entity, classes, 0);
    }

    /*
     * LIMITATION (documented): only handles @Table declared
     * directly on the entity or inherited from a
     * @MappedSuperclass in its extends chain, then falls back
     * to a snake_case of the class name. A project-wide custom
     * Hibernate PhysicalNamingStrategy bean (which can rename
     * tables using arbitrary rules) is not something a static
     * analyzer can know about without interpreting arbitrary
     * Spring configuration - not attempted here.
     */
    private String findTableName(
            ClassInfo entity,
            List<ClassInfo> classes,
            int depth) {

        if (entity == null || depth > 10) {
            return null;
        }

        String explicitTable =
                entity.getAnnotations()
                        .stream()
                        .filter(annotation ->
                                annotation.startsWith("@Table"))
                        .map(this::extractTableName)
                        .filter(name -> name != null)
                        .findFirst()
                        .orElse(null);

        if (explicitTable != null) {
            return explicitTable;
        }

        boolean mappedSuperclass =
                entity.getAnnotationSimpleNames()
                        .contains("MappedSuperclass");

        for (String extendedType : entity.getExtendedTypes()) {

            ClassInfo parent =
                    findClass(classes, extendedType);

            if (parent == null) {
                continue;
            }

            boolean parentIsMappedSuperclassOrEntity =
                    parent.getAnnotationSimpleNames()
                            .contains("MappedSuperclass")
                            || "ENTITY".equals(parent.getType());

            if (mappedSuperclass
                    || parentIsMappedSuperclassOrEntity) {

                String inherited =
                        findTableName(
                                parent,
                                classes,
                                depth + 1
                        );

                if (inherited != null) {
                    return inherited;
                }
            }
        }

        if (depth == 0) {
            return SnakeCaseConverter.toSnakeCase(
                    entity.getName()
            );
        }

        return null;
    }

    private String extractTableName(
            String annotation) {

        /*
         * @Table(name = "customers")
         */

        int nameIndex =
                annotation.indexOf("name");

        if (nameIndex < 0) {
            return null;
        }

        int quoteStart =
                annotation.indexOf(
                        '"',
                        nameIndex
                );

        int quoteEnd =
                annotation.indexOf(
                        '"',
                        quoteStart + 1
                );

        if (quoteStart < 0 || quoteEnd < 0) {
            return null;
        }

        return annotation.substring(
                quoteStart + 1,
                quoteEnd
        );
    }
}
