package org.example.analyser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import org.example.analyser.analyzer.ApiAnalyzer;
import org.example.analyser.analyzer.ClassAnalyzer;
import org.example.analyser.analyzer.CouplingAnalyzer;
import org.example.analyser.analyzer.CrudAnalyzer;
import org.example.analyser.analyzer.DependencyAnalyzer;
import org.example.analyser.analyzer.DependencyGraphBuilder;
import org.example.analyser.analyzer.DomainAnalyzer;
import org.example.analyser.analyzer.DomainDependencyAnalyzer;
import org.example.analyser.analyzer.EntityMutationAnalyzer;
import org.example.analyser.analyzer.MethodCallAnalyzer;
import org.example.analyser.analyzer.SpringComponentAnalyzer;

import org.example.analyser.model.ApiInfo;
import org.example.analyser.model.ClassCouplingInfo;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.DependencyGraph;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DomainDependency;
import org.example.analyser.model.DomainInfo;
import org.example.analyser.model.EntityMutationInfo;
import org.example.analyser.model.MethodCallInfo;

import org.example.analyser.parser.JavaSourceParser;
import org.example.analyser.scanner.ProjectScanner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class AnalyzerRunner implements CommandLineRunner {

    private final ProjectScanner projectScanner;
    private final JavaSourceParser javaSourceParser;
    private final ClassAnalyzer classAnalyzer;
    private final SpringComponentAnalyzer springComponentAnalyzer;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final DependencyGraphBuilder dependencyGraphBuilder;
    private final CouplingAnalyzer couplingAnalyzer;
    private final DomainAnalyzer domainAnalyzer;
    private final DomainDependencyAnalyzer domainDependencyAnalyzer;
    private final ApiAnalyzer apiAnalyzer;
    private final MethodCallAnalyzer methodCallAnalyzer;
    private final CrudAnalyzer crudAnalyzer;
    private final EntityMutationAnalyzer entityMutationAnalyzer;

    public AnalyzerRunner(
            ProjectScanner projectScanner,
            JavaSourceParser javaSourceParser,
            ClassAnalyzer classAnalyzer,
            SpringComponentAnalyzer springComponentAnalyzer,
            DependencyAnalyzer dependencyAnalyzer,
            DependencyGraphBuilder dependencyGraphBuilder,
            CouplingAnalyzer couplingAnalyzer,
            DomainAnalyzer domainAnalyzer,
            DomainDependencyAnalyzer domainDependencyAnalyzer,
            ApiAnalyzer apiAnalyzer,
            MethodCallAnalyzer methodCallAnalyzer,
            CrudAnalyzer crudAnalyzer,
            EntityMutationAnalyzer entityMutationAnalyzer) {

        this.projectScanner = projectScanner;
        this.javaSourceParser = javaSourceParser;
        this.classAnalyzer = classAnalyzer;
        this.springComponentAnalyzer = springComponentAnalyzer;
        this.dependencyAnalyzer = dependencyAnalyzer;
        this.dependencyGraphBuilder = dependencyGraphBuilder;
        this.couplingAnalyzer = couplingAnalyzer;
        this.domainAnalyzer = domainAnalyzer;
        this.domainDependencyAnalyzer = domainDependencyAnalyzer;
        this.apiAnalyzer = apiAnalyzer;
        this.methodCallAnalyzer = methodCallAnalyzer;
        this.crudAnalyzer = crudAnalyzer;
        this.entityMutationAnalyzer = entityMutationAnalyzer;
    }

    @Override
    public void run(String... args) throws Exception {

        // ======================================
        // TARGET PROJECT
        // ======================================

        Path targetProject =
//                Path.of("C:/test/heavy-monolith-springboot21");
//                  Path.of("C:/test/messy-monolith");
                  Path.of("C:/test/messy-hospital");

        int javaVersion =
                detectJavaVersion(targetProject);

        var javaFiles =
                projectScanner.scanJavaFiles(targetProject);

        System.out.println();
        System.out.println("======================================");
        System.out.println("      ARCHITECTURE ANALYZER");
        System.out.println("======================================");

        System.out.println(
                "Target project : " + targetProject
        );

        System.out.println(
                "Java version   : " + javaVersion
        );

        System.out.println(
                "Java files     : " + javaFiles.size()
        );

        // ======================================
        // STEP 1: PARSE AND ANALYZE ALL CLASSES
        // ======================================

        List<ClassInfo> classes =
                new ArrayList<>();

        List<ApiInfo> apis =
                new ArrayList<>();

        List<MethodCallInfo> methodCalls =
                new ArrayList<>();

        List<EntityMutationInfo> entityMutations =
                new ArrayList<>();

        for (Path javaFile : javaFiles) {

            CompilationUnit compilationUnit =
                    javaSourceParser.parse(
                            javaFile,
                            javaVersion
                    );

            compilationUnit
                    .findAll(ClassOrInterfaceDeclaration.class)
                    .forEach(clazz -> {

                        ClassInfo classInfo =
                                classAnalyzer.analyze(
                                        compilationUnit,
                                        clazz
                                );

                        springComponentAnalyzer
                                .classify(classInfo);

                        classes.add(classInfo);

                        // ======================================
                        // ANALYZE REST APIs
                        // ======================================

                        List<ApiInfo> discoveredApis =
                                apiAnalyzer.analyze(
                                        compilationUnit,
                                        clazz,
                                        classInfo
                                );

                        apis.addAll(discoveredApis);
                    });
        }

        // ======================================
        // STEP 2: ANALYZE METHOD CALLS
        // ======================================

        /*
         * Second pass.
         *
         * At this point ALL application classes
         * have already been discovered.
         *
         * This allows us to resolve:
         *
         * Controller -> Service
         * Service    -> Service
         * Service    -> Repository
         */

        for (Path javaFile : javaFiles) {

            CompilationUnit compilationUnit =
                    javaSourceParser.parse(
                            javaFile,
                            javaVersion
                    );

            compilationUnit
                    .findAll(ClassOrInterfaceDeclaration.class)
                    .forEach(clazz -> {

                        ClassInfo sourceClass =
                                classes.stream()
                                        .filter(info ->
                                                info.getName()
                                                        .equals(
                                                                clazz.getNameAsString()
                                                        )
                                        )
                                        .findFirst()
                                        .orElse(null);

                        if (sourceClass == null) {
                            return;
                        }

                        clazz.getMethods()
                                .forEach(method -> {

                                    // ======================================
                                    // METHOD CALL ANALYSIS
                                    // ======================================

                                    List<MethodCallInfo> calls =
                                            methodCallAnalyzer.analyze(
                                                    method,
                                                    sourceClass,
                                                    classes
                                            );

                                    methodCalls.addAll(calls);

                                    // ======================================
                                    // ENTITY MUTATION ANALYSIS
                                    // ======================================

                                    List<EntityMutationInfo> mutations =
                                            entityMutationAnalyzer.analyze(
                                                    method,
                                                    sourceClass,
                                                    classes
                                            );

                                    entityMutations.addAll(mutations);
                                });
                    });
        }

        // ======================================
        // STEP 3: ANALYZE CRUD OPERATIONS
        // ======================================

        List<CrudOperationInfo> crudOperations =
                crudAnalyzer.analyze(
                        methodCalls,
                        classes
                );

        // ======================================
        // STEP 4: ANALYZE DEPENDENCIES
        // ======================================

        List<DependencyInfo> dependencies =
                dependencyAnalyzer.analyze(classes);

        // ======================================
        // STEP 5: BUILD DEPENDENCY GRAPH
        // ======================================

        DependencyGraph dependencyGraph =
                dependencyGraphBuilder.build(
                        classes,
                        dependencies
                );

        // ======================================
        // STEP 6: ANALYZE COUPLING
        // ======================================

        List<ClassCouplingInfo> coupling =
                couplingAnalyzer.analyze(
                        dependencyGraph
                );

        // ======================================
        // STEP 7: ANALYZE DOMAINS
        // ======================================

        List<DomainInfo> domains =
                domainAnalyzer.analyze(classes);

        List<DomainDependency> domainDependencies =
                domainDependencyAnalyzer.analyze(
                        domains,
                        dependencies
                );

        // ======================================
        // STEP 8: PRINT CLASS INVENTORY
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("          CLASS INVENTORY");
        System.out.println("======================================");

        for (ClassInfo classInfo : classes) {

            System.out.println("--------------------------------------");

            System.out.println(
                    "CLASS: " + classInfo.getName()
            );

            System.out.println(
                    "PACKAGE: " + classInfo.getPackageName()
            );

            System.out.println(
                    "TYPE: " + classInfo.getType()
            );

            System.out.println("ANNOTATIONS:");

            classInfo.getAnnotations()
                    .forEach(annotation ->
                            System.out.println(
                                    "  " + annotation
                            )
                    );

            System.out.println("FIELDS:");

            classInfo.getFields()
                    .forEach(field ->
                            System.out.println(
                                    "  " + field
                            )
                    );

            System.out.println("METHODS:");

            classInfo.getMethods()
                    .forEach(method ->
                            System.out.println(
                                    "  " + method
                            )
                    );
        }

        // ======================================
        // STEP 9: PRINT DEPENDENCY GRAPH
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("      APPLICATION DEPENDENCY GRAPH");
        System.out.println("======================================");

        dependencyGraph.getEdges()
                .forEach(dependency ->
                        System.out.println(
                                dependency.getSourceClass()
                                        + " -> "
                                        + dependency.getTargetClass()
                                        + " ["
                                        + dependency.getFieldName()
                                        + "]"
                                        + " {"
                                        + dependency.getType()
                                        + "}"
                        )
                );

        // ======================================
        // STEP 10: PRINT COUPLING ANALYSIS
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("          COUPLING ANALYSIS");
        System.out.println("======================================");

        coupling.forEach(info ->
                System.out.println(
                        info.getClassName()
                                + " | "
                                + info.getType()
                                + " | outgoing="
                                + info.getOutgoingDependencies()
                                + " | incoming="
                                + info.getIncomingDependencies()
                                + " | total="
                                + info.getTotalCoupling()
                )
        );

        // ======================================
        // STEP 11: PRINT DOMAIN INVENTORY
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("          DOMAIN INVENTORY");
        System.out.println("======================================");

        for (DomainInfo domain : domains) {

            System.out.println();

            System.out.println(
                    "DOMAIN: " + domain.getName()
            );

            System.out.println(
                    "CLASSES: " + domain.getClassCount()
            );

            domain.getClasses()
                    .forEach(classInfo ->
                            System.out.println(
                                    "  "
                                            + classInfo.getName()
                                            + " ["
                                            + classInfo.getType()
                                            + "]"
                            )
                    );
        }

        // ======================================
        // STEP 12: PRINT DOMAIN DEPENDENCY GRAPH
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("      DOMAIN DEPENDENCY GRAPH");
        System.out.println("======================================");

        domainDependencies.forEach(dependency ->
                System.out.println(
                        dependency.getSourceDomain()
                                + " -> "
                                + dependency.getTargetDomain()
                                + " ["
                                + dependency.getType()
                                + "]"
                                + " count="
                                + dependency.getCount()
                )
        );

        // ======================================
        // STEP 13: PRINT API INVENTORY
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("            API INVENTORY");
        System.out.println("======================================");

        apis.forEach(api ->
                System.out.println(
                        api.getHttpMethod()
                                + " "
                                + api.getPath()
                                + " -> "
                                + api.getControllerClass()
                                + "."
                                + api.getMethodName()
                                + " ["
                                + api.getDomain()
                                + "]"
                )
        );

        // ======================================
        // STEP 14: PRINT METHOD CALL GRAPH
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("          METHOD CALL GRAPH");
        System.out.println("======================================");

        methodCalls.forEach(call ->
                System.out.println(
                        call.getSourceClass()
                                + "."
                                + call.getSourceMethod()
                                + " -> "
                                + call.getTargetClass()
                                + "."
                                + call.getTargetMethod()
                )
        );

        // ======================================
        // STEP 15: PRINT CRUD / DATABASE
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("          CRUD / DATABASE");
        System.out.println("======================================");

        crudOperations.forEach(operation ->
                System.out.println(
                        operation.getSourceClass()
                                + "."
                                + operation.getSourceMethod()
                                + " -> "
                                + operation.getRepositoryClass()
                                + "."
                                + operation.getRepositoryMethod()
                                + " | "
                                + operation.getOperation()
                                + " | entity="
                                + operation.getEntityClass()
                                + " | table="
                                + operation.getTableName()
                )
        );

        // ======================================
        // STEP 16: PRINT ENTITY MUTATIONS
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("        ENTITY MUTATIONS");
        System.out.println("======================================");

        entityMutations.forEach(mutation ->
                System.out.println(
                        mutation.getSourceClass()
                                + "."
                                + mutation.getSourceMethod()
                                + " -> "
                                + mutation.getEntityClass()
                                + "."
                                + mutation.getFieldName()
                                + " | "
                                + mutation.getOperation()
                                + " | table="
                                + mutation.getTableName()
                )
        );

        System.out.println("--------------------------------------");
    }

    private int detectJavaVersion(
            Path targetProject) {

        // Temporary implementation.
        // We will replace this with automatic
        // pom.xml / build.gradle analysis.

        return 21;
    }
}