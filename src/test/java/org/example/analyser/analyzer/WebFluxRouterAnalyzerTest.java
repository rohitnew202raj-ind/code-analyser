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

class WebFluxRouterAnalyzerTest {

    private final WebFluxRouterAnalyzer analyzer =
            new WebFluxRouterAnalyzer(new TypeResolver());

    @Test
    void findsMethodReferenceHandlersInARouterFunctionBuilderChain() {

        List<EntryPointInfo> results = analyze(
                """
                package com.acme.order;

                public class OrderRouter {

                    public RouterFunction<ServerResponse> routes(OrderHandler handler) {
                        return RouterFunctions.route()
                                .GET("/orders/{id}", handler::getOrder)
                                .POST("/orders", handler::createOrder)
                                .build();
                    }
                }
                """
        );

        assertThat(results)
                .extracting(
                        EntryPointInfo::getClassName,
                        EntryPointInfo::getMethodName,
                        EntryPointInfo::getTriggerType,
                        EntryPointInfo::getPath
                )
                .containsExactlyInAnyOrder(
                        tuple("OrderHandler", "getOrder", TriggerType.GET, "/orders/{id}"),
                        tuple("OrderHandler", "createOrder", TriggerType.POST, "/orders")
                );
    }

    @Test
    void ignoresLambdaHandlersSinceTheyCarryNoMethodNameToReport() {

        List<EntryPointInfo> results = analyze(
                """
                package com.acme.order;

                public class OrderRouter {

                    public RouterFunction<ServerResponse> routes(OrderHandler handler) {
                        return RouterFunctions.route()
                                .GET("/orders/{id}", request -> ServerResponse.ok().build())
                                .build();
                    }
                }
                """
        );

        assertThat(results).isEmpty();
    }

    @Test
    void ignoresGetPostCallsWithNoRouteBuilderInTheSameMethod() {

        List<EntryPointInfo> results = analyze(
                """
                package com.acme.order;

                public class OrderRouter {

                    public void unrelated(OrderHandler handler) {
                        handler.GET("/orders/{id}", handler::getOrder);
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

        ClassInfo sourceClass = new ClassInfo();
        sourceClass.setName("OrderRouter");
        sourceClass.setPackageName("com.acme.order");

        ClassInfo handlerClass = new ClassInfo();
        handlerClass.setName("OrderHandler");
        handlerClass.setPackageName("com.acme.order");

        return analyzer.analyze(
                declaration,
                sourceClass,
                List.of(sourceClass, handlerClass),
                PackageDomainExtractor.fit(List.of(sourceClass, handlerClass))
        );
    }
}
