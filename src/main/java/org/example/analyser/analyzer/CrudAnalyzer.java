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

            if (operation == null) {
                continue;
            }

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

    private String detectOperation(
            String methodName) {

        if (methodName.equals("save")) {
            return "CREATE_OR_UPDATE";
        }

        if (methodName.startsWith("find")
                || methodName.startsWith("get")
                || methodName.startsWith("read")
                || methodName.equals("count")
                || methodName.equals("exists")) {

            return "READ";
        }

        if (methodName.startsWith("delete")
                || methodName.startsWith("remove")) {

            return "DELETE";
        }

        return null;
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

    private String findEntityForRepository(
            ClassInfo repository,
            List<ClassInfo> classes) {

        /*
         * First version:
         *
         * Infer entity from repository package/domain.
         *
         * CustomerRepository -> Customer
         * OrderRepository    -> OrderEntity
         */

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

        if (entity == null) {
            return null;
        }

        return entity.getAnnotations()
                .stream()
                .filter(annotation ->
                        annotation.startsWith("@Table"))
                .map(this::extractTableName)
                .findFirst()
                .orElse(null);
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