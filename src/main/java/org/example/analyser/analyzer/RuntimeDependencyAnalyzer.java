package org.example.analyser.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DependencyType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds dependencies that bypass field injection entirely:
 *
 *  - context.getBean(SomeService.class) - a runtime lookup
 *  - new SomeService() - direct instantiation of an
 *    application class, common in older/messy code that
 *    predates consistent DI usage
 *
 * DependencyAnalyzer only ever looked at declared fields, so
 * both of these were previously invisible to the dependency
 * graph and coupling metrics.
 *
 * LIMITATION (documented, not solved): this still can't tell
 * *which* concrete implementation gets injected when a field
 * is declared as an interface with multiple @Qualifier-
 * selected beans - that requires actual Spring bean-resolution
 * semantics (profiles, conditionals, qualifiers), which is
 * fundamentally runtime information, not something derivable
 * from static AST analysis alone.
 */
@Component
public class RuntimeDependencyAnalyzer {

    private final TypeResolver typeResolver;

    public RuntimeDependencyAnalyzer(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

    public List<DependencyInfo> analyze(
            MethodDeclaration method,
            ClassInfo sourceClass,
            List<ClassInfo> allClasses) {

        List<DependencyInfo> results =
                new ArrayList<>();

        method.findAll(ObjectCreationExpr.class)
                .forEach(creation -> {

                    String typeName =
                            creation.getType()
                                    .getNameAsString();

                    if (typeResolver.isApplicationClass(typeName, allClasses)) {

                        results.add(
                                new DependencyInfo(
                                        sourceClass.getName(),
                                        typeName,
                                        null,
                                        DependencyType
                                                .DIRECT_INSTANTIATION_DEPENDENCY
                                )
                        );
                    }
                });

        method.findAll(MethodCallExpr.class)
                .stream()
                .filter(call ->
                        call.getNameAsString()
                                .equals("getBean")
                )
                .forEach(call ->
                        call.getArguments()
                                .stream()
                                .filter(arg -> arg instanceof ClassExpr)
                                .map(arg -> (ClassExpr) arg)
                                .forEach(classExpr -> {

                                    String typeName =
                                            classExpr.getType()
                                                    .asString();

                                    if (typeResolver.isApplicationClass(
                                            typeName,
                                            allClasses
                                    )) {

                                        results.add(
                                                new DependencyInfo(
                                                        sourceClass.getName(),
                                                        typeName,
                                                        null,
                                                        DependencyType
                                                                .RUNTIME_LOOKUP_DEPENDENCY
                                                )
                                        );
                                    }
                                })
                );

        return results;
    }
}
