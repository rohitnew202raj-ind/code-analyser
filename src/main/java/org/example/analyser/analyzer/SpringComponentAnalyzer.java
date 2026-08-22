package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.springframework.stereotype.Component;

@Component
public class SpringComponentAnalyzer {

    public void classify(ClassInfo classInfo) {

        if (hasAnnotation(classInfo, "@SpringBootApplication")) {
            classInfo.setType("APPLICATION");
            return;
        }

        if (hasAnnotation(classInfo, "@RestController")) {
            classInfo.setType("CONTROLLER");
            return;
        }

        if (hasAnnotation(classInfo, "@Service")) {
            classInfo.setType("SERVICE");
            return;
        }

        if (hasAnnotation(classInfo, "@Repository")
                || extendsRepository(classInfo)) {

            classInfo.setType("REPOSITORY");
            return;
        }

        if (hasAnnotation(classInfo, "@Entity")) {
            classInfo.setType("ENTITY");
            return;
        }

        if (hasAnnotation(classInfo, "@Configuration")) {
            classInfo.setType("CONFIGURATION");
            return;
        }

        if (hasAnnotation(classInfo, "@Component")) {
            classInfo.setType("COMPONENT");
            return;
        }

        classInfo.setType("UNKNOWN");
    }

    private boolean hasAnnotation(
            ClassInfo classInfo,
            String annotation) {

        return classInfo.getAnnotations()
                .stream()
                .anyMatch(a -> a.equals(annotation));
    }

    private boolean extendsRepository(ClassInfo classInfo) {

        return classInfo.getInterfaces()
                .stream()
                .anyMatch(type ->
                        type.equals("JpaRepository")
                                || type.equals("CrudRepository")
                                || type.equals("PagingAndSortingRepository")
                );
    }
}