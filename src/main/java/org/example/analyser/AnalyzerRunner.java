package org.example.analyser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import org.example.analyser.analyzer.AnnotationNames;
import org.example.analyser.analyzer.ApiAnalyzer;
import org.example.analyser.analyzer.BatchAnalyzer;
import org.example.analyser.analyzer.ClassAnalyzer;
import org.example.analyser.analyzer.CouplingAnalyzer;
import org.example.analyser.analyzer.CrudAnalyzer;
import org.example.analyser.analyzer.DependencyAnalyzer;
import org.example.analyser.analyzer.DependencyGraphBuilder;
import org.example.analyser.analyzer.DomainAnalyzer;
import org.example.analyser.analyzer.DomainDependencyAnalyzer;
import org.example.analyser.analyzer.EntityMutationAnalyzer;
import org.example.analyser.analyzer.InterfaceRoleResolver;
import org.example.analyser.analyzer.MetaAnnotationResolver;
import org.example.analyser.analyzer.MethodCallAnalyzer;
import org.example.analyser.analyzer.PackageDomainExtractor;
import org.example.analyser.analyzer.RepositoryInheritanceResolver;
import org.example.analyser.analyzer.ReportExporter;
import org.example.analyser.analyzer.RuntimeDependencyAnalyzer;
import org.example.analyser.analyzer.SpringComponentAnalyzer;

import org.example.analyser.model.ApiInfo;
import org.example.analyser.model.BatchProgramInfo;
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
import org.example.analyser.scanner.BuildConfigReader;
import org.example.analyser.scanner.ProjectScanner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AnalyzerRunner implements CommandLineRunner {

    private final ProjectScanner projectScanner;
    private final BuildConfigReader buildConfigReader;
    private final JavaSourceParser javaSourceParser;
    private final ClassAnalyzer classAnalyzer;
    private final SpringComponentAnalyzer springComponentAnalyzer;
    private final RepositoryInheritanceResolver repositoryInheritanceResolver;
    private final InterfaceRoleResolver interfaceRoleResolver;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final RuntimeDependencyAnalyzer runtimeDependencyAnalyzer;
    private final DependencyGraphBuilder dependencyGraphBuilder;
    private final CouplingAnalyzer couplingAnalyzer;
    private final DomainAnalyzer domainAnalyzer;
    private final DomainDependencyAnalyzer domainDependencyAnalyzer;
    private final ApiAnalyzer apiAnalyzer;
    private final BatchAnalyzer batchAnalyzer;
    private final MethodCallAnalyzer methodCallAnalyzer;
    private final CrudAnalyzer crudAnalyzer;
    private final EntityMutationAnalyzer entityMutationAnalyzer;
    private final ReportExporter reportExporter;

    public AnalyzerRunner(
            ProjectScanner projectScanner,
            BuildConfigReader buildConfigReader,
            JavaSourceParser javaSourceParser,
            ClassAnalyzer classAnalyzer,
            SpringComponentAnalyzer springComponentAnalyzer,
            RepositoryInheritanceResolver repositoryInheritanceResolver,
            InterfaceRoleResolver interfaceRoleResolver,
            DependencyAnalyzer dependencyAnalyzer,
            RuntimeDependencyAnalyzer runtimeDependencyAnalyzer,
            DependencyGraphBuilder dependencyGraphBuilder,
            CouplingAnalyzer couplingAnalyzer,
            DomainAnalyzer domainAnalyzer,
            DomainDependencyAnalyzer domainDependencyAnalyzer,
            ApiAnalyzer apiAnalyzer,
            BatchAnalyzer batchAnalyzer,
            MethodCallAnalyzer methodCallAnalyzer,
            CrudAnalyzer crudAnalyzer,
            EntityMutationAnalyzer entityMutationAnalyzer,
            ReportExporter reportExporter) {

        this.projectScanner = projectScanner;
        this.buildConfigReader = buildConfigReader;
        this.javaSourceParser = javaSourceParser;
        this.classAnalyzer = classAnalyzer;
        this.springComponentAnalyzer = springComponentAnalyzer;
        this.repositoryInheritanceResolver = repositoryInheritanceResolver;
        this.interfaceRoleResolver = interfaceRoleResolver;
        this.dependencyAnalyzer = dependencyAnalyzer;
        this.runtimeDependencyAnalyzer = runtimeDependencyAnalyzer;
        this.dependencyGraphBuilder = dependencyGraphBuilder;
        this.couplingAnalyzer = couplingAnalyzer;
        this.domainAnalyzer = domainAnalyzer;
        this.domainDependencyAnalyzer = domainDependencyAnalyzer;
        this.apiAnalyzer = apiAnalyzer;
        this.batchAnalyzer = batchAnalyzer;
        this.methodCallAnalyzer = methodCallAnalyzer;
        this.crudAnalyzer = crudAnalyzer;
        this.entityMutationAnalyzer = entityMutationAnalyzer;
        this.reportExporter = reportExporter;
    }

    private record ParsedClass(
            TypeDeclaration<?> declaration,
            ClassInfo classInfo) {
    }

    private record FileParseResult(
            Path file,
            CompilationUnit compilationUnit,
            List<ParsedClass> parsedClasses,
            Map<String, ClassOrInterfaceDeclaration> declarations,
            Map<String, List<String>> annotationMeta,
            String parseError) {
    }

    @Override
    public void run(String... args) throws Exception {

        // ======================================
        // TARGET PROJECT
        // ======================================

        if (args.length < 1) {

            System.out.println(
                    "Usage: architecture-analyzer "
                            + "<target-project-path> "
                            + "[output-directory]"
            );

            return;
        }

        Path targetProject = Path.of(args[0]);

        Path outputDirectory =
                args.length > 1
                        ? Path.of(args[1])
                        : Path.of("analysis-output");

        List<BuildConfigReader.ModuleConfig> modules =
                buildConfigReader.detect(targetProject);

        javaSourceParser.configureSymbolSolver(
                modules.stream()
                        .map(BuildConfigReader.ModuleConfig::sourceRoot)
                        .toList()
        );

        var javaFiles =
                projectScanner.scanJavaFiles(targetProject);

        System.out.println();
        System.out.println("======================================");
        System.out.println("      ARCHITECTURE ANALYZER");
        System.out.println("======================================");

        System.out.println("Target project : " + targetProject);
        System.out.println("Modules found  : " + modules.size());
        System.out.println("Java files     : " + javaFiles.size());

        // ======================================
        // STEP 1: PARSE + PER-CLASS ANALYSIS
        // (parallel - no shared mutable state, no
        // symbol resolution touched yet)
        // ======================================

        List<FileParseResult> parseResults =
                javaFiles.parallelStream()
                        .map(file ->
                                parseFile(file, modules)
                        )
                        .toList();

        List<ClassInfo> classes = new ArrayList<>();
        List<ParsedClass> parsedClasses = new ArrayList<>();
        Map<String, ClassOrInterfaceDeclaration> declarationsByName =
                new HashMap<>();
        Map<String, List<String>> annotationMetaRegistry =
                new HashMap<>();
        List<String> parseErrors = new ArrayList<>();

        for (FileParseResult result : parseResults) {

            if (result.parseError() != null) {
                parseErrors.add(result.parseError());
                continue;
            }

            for (ParsedClass parsedClass : result.parsedClasses()) {
                classes.add(parsedClass.classInfo());
                parsedClasses.add(parsedClass);
            }

            declarationsByName.putAll(result.declarations());
            annotationMetaRegistry.putAll(result.annotationMeta());
        }

        // ======================================
        // STEP 2: CLASSIFY (needs the full
        // meta-annotation registry, so it can't
        // happen until every file is parsed)
        // ======================================

        MetaAnnotationResolver metaAnnotationResolver =
                new MetaAnnotationResolver(annotationMetaRegistry);

        for (ClassInfo classInfo : classes) {
            springComponentAnalyzer.classify(
                    classInfo,
                    metaAnnotationResolver
            );
        }

        // ======================================
        // STEP 3: RESOLVE CUSTOM BASE-REPOSITORY
        // CHAINS (needs every class classified first)
        // ======================================

        repositoryInheritanceResolver.resolve(classes);

        // ======================================
        // STEP 3b: PROPAGATE SERVICE/REPOSITORY ROLE
        // FROM IMPLEMENTATION TO INTERFACE
        //
        // Most Spring code programs to an interface
        // (OrderService, not OrderServiceImpl), and only
        // the implementation carries the @Service/
        // @Repository annotation. Without this, dependency
        // classification of interface-typed fields - the
        // normal case - falls back to a generic classification.
        // ======================================

        interfaceRoleResolver.resolve(classes);

        // ======================================
        // STEP 4: REST/GRAPHQL APIS + BATCH PROGRAMS
        // ======================================

        PackageDomainExtractor domainExtractor =
                PackageDomainExtractor.fit(classes);

        List<ApiInfo> apis = new ArrayList<>();
        List<BatchProgramInfo> batchPrograms = new ArrayList<>();

        for (ParsedClass parsedClass : parsedClasses) {

            batchPrograms.addAll(
                    batchAnalyzer.analyze(
                            parsedClass.declaration(),
                            parsedClass.classInfo(),
                            domainExtractor
                    )
            );

            if (parsedClass.declaration()
                    instanceof ClassOrInterfaceDeclaration coid) {

                apis.addAll(
                        apiAnalyzer.analyze(
                                coid,
                                parsedClass.classInfo(),
                                declarationsByName,
                                domainExtractor
                        )
                );
            }
        }

        // ======================================
        // STEP 5: METHOD CALLS / ENTITY MUTATIONS /
        // RUNTIME DEPENDENCIES
        //
        // Sequential: this is where Symbol Solver
        // resolution actually happens, and its
        // internal caches aren't guaranteed
        // thread-safe under concurrent access.
        // ======================================

        List<MethodCallInfo> methodCalls = new ArrayList<>();
        List<EntityMutationInfo> entityMutations = new ArrayList<>();
        List<DependencyInfo> runtimeDependencies = new ArrayList<>();

        for (ParsedClass parsedClass : parsedClasses) {

            ClassInfo sourceClass = parsedClass.classInfo();

            for (MethodDeclaration method :
                    parsedClass.declaration().getMethods()) {

                methodCalls.addAll(
                        methodCallAnalyzer.analyze(
                                method,
                                sourceClass,
                                classes
                        )
                );

                entityMutations.addAll(
                        entityMutationAnalyzer.analyze(
                                method,
                                sourceClass,
                                classes
                        )
                );

                runtimeDependencies.addAll(
                        runtimeDependencyAnalyzer.analyze(
                                method,
                                sourceClass,
                                classes
                        )
                );
            }
        }

        // ======================================
        // STEP 6: CRUD OPERATIONS
        // ======================================

        List<CrudOperationInfo> crudOperations =
                crudAnalyzer.analyze(methodCalls, classes);

        // ======================================
        // STEP 7: DEPENDENCIES + GRAPH
        // ======================================

        List<DependencyInfo> dependencies =
                dependencyAnalyzer.analyze(classes);

        dependencies.addAll(runtimeDependencies);

        DependencyGraph dependencyGraph =
                dependencyGraphBuilder.build(classes, dependencies);

        // ======================================
        // STEP 8: COUPLING
        // ======================================

        List<ClassCouplingInfo> coupling =
                couplingAnalyzer.analyze(dependencyGraph, methodCalls);

        // ======================================
        // STEP 9: DOMAINS
        // ======================================

        List<DomainInfo> domains =
                domainAnalyzer.analyze(classes);

        List<DomainDependency> domainDependencies =
                domainDependencyAnalyzer.analyze(domains, dependencies);

        // ======================================
        // STEP 10: EXPORT (JSON + DOT)
        // ======================================

        ReportExporter.AnalysisReport report =
                new ReportExporter.AnalysisReport(
                        classes,
                        dependencies,
                        coupling,
                        domains,
                        domainDependencies,
                        apis,
                        batchPrograms,
                        methodCalls,
                        crudOperations,
                        entityMutations
                );

        try {

            reportExporter.export(
                    outputDirectory,
                    report,
                    dependencyGraph
            );

            System.out.println();
            System.out.println(
                    "Structured report written to: "
                            + outputDirectory.toAbsolutePath()
            );

        } catch (IOException exportFailure) {

            System.out.println();
            System.out.println(
                    "WARNING: failed to write structured "
                            + "report: "
                            + exportFailure.getMessage()
            );
        }

        // ======================================
        // PRINT: PARSE ERRORS
        // ======================================

        if (!parseErrors.isEmpty()) {

            System.out.println();
            System.out.println("======================================");
            System.out.println("      FILES SKIPPED (PARSE ERRORS)");
            System.out.println("======================================");

            parseErrors.forEach(System.out::println);
        }

        // ======================================
        // PRINT: CLASS INVENTORY
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("          CLASS INVENTORY");
        System.out.println("======================================");

        for (ClassInfo classInfo : classes) {

            System.out.println("--------------------------------------");
            System.out.println("CLASS: " + classInfo.getName());
            System.out.println("PACKAGE: " + classInfo.getPackageName());
            System.out.println("TYPE: " + classInfo.getType());
            System.out.println("ROLES: " + classInfo.getRoles());

            System.out.println("ANNOTATIONS:");
            classInfo.getAnnotations()
                    .forEach(a -> System.out.println("  " + a));

            System.out.println("FIELDS:");
            classInfo.getFields()
                    .forEach(f -> System.out.println("  " + f));

            System.out.println("METHODS:");
            classInfo.getMethods()
                    .forEach(m -> System.out.println("  " + m));
        }

        // ======================================
        // PRINT: DEPENDENCY GRAPH
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("      APPLICATION DEPENDENCY GRAPH");
        System.out.println("======================================");

        dependencyGraph.getEdges().forEach(dependency ->
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
        // PRINT: COUPLING ANALYSIS
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("          COUPLING ANALYSIS");
        System.out.println("======================================");

        coupling.forEach(info ->
                System.out.println(
                        info.getClassName()
                                + " | " + info.getType()
                                + " | outgoing=" + info.getOutgoingDependencies()
                                + " | incoming=" + info.getIncomingDependencies()
                                + " | total=" + info.getTotalCoupling()
                                + " | callWeight=" + info.getCallWeight()
                )
        );

        // ======================================
        // PRINT: DOMAIN INVENTORY
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("          DOMAIN INVENTORY");
        System.out.println("======================================");

        for (DomainInfo domain : domains) {

            System.out.println();
            System.out.println("DOMAIN: " + domain.getName());
            System.out.println("CLASSES: " + domain.getClassCount());

            domain.getClasses().forEach(classInfo ->
                    System.out.println(
                            "  " + classInfo.getName()
                                    + " [" + classInfo.getType() + "]"
                    )
            );
        }

        // ======================================
        // PRINT: DOMAIN DEPENDENCY GRAPH
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("      DOMAIN DEPENDENCY GRAPH");
        System.out.println("======================================");

        domainDependencies.forEach(dependency ->
                System.out.println(
                        dependency.getSourceDomain()
                                + " -> " + dependency.getTargetDomain()
                                + " [" + dependency.getType() + "]"
                                + " count=" + dependency.getCount()
                )
        );

        // ======================================
        // PRINT: API INVENTORY
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("            API INVENTORY");
        System.out.println("======================================");

        apis.forEach(api ->
                System.out.println(
                        api.getHttpMethod() + " " + api.getPath()
                                + " -> " + api.getControllerClass()
                                + "." + api.getMethodName()
                                + " [" + api.getDomain() + "]"
                )
        );

        // ======================================
        // PRINT: BATCH PROGRAM INVENTORY
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("        BATCH PROGRAM INVENTORY");
        System.out.println("======================================");

        batchPrograms.forEach(batch ->
                System.out.println(
                        batch.getTrigger() + " "
                                + batch.getClassName()
                                + "." + batch.getMethodName()
                                + " [" + batch.getDomain() + "]"
                )
        );

        // ======================================
        // PRINT: METHOD CALL GRAPH
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("          METHOD CALL GRAPH");
        System.out.println("======================================");

        methodCalls.forEach(call ->
                System.out.println(
                        call.getSourceClass() + "." + call.getSourceMethod()
                                + " -> " + call.getTargetClass()
                                + "." + call.getTargetMethod()
                )
        );

        // ======================================
        // PRINT: CRUD / DATABASE
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("          CRUD / DATABASE");
        System.out.println("======================================");

        crudOperations.forEach(operation ->
                System.out.println(
                        operation.getSourceClass() + "." + operation.getSourceMethod()
                                + " -> " + operation.getRepositoryClass()
                                + "." + operation.getRepositoryMethod()
                                + " | " + operation.getOperation()
                                + " | entity=" + operation.getEntityClass()
                                + " | table=" + operation.getTableName()
                )
        );

        // ======================================
        // PRINT: ENTITY MUTATIONS
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("        ENTITY MUTATIONS");
        System.out.println("======================================");

        entityMutations.forEach(mutation ->
                System.out.println(
                        mutation.getSourceClass() + "." + mutation.getSourceMethod()
                                + " -> " + mutation.getEntityClass()
                                + "." + mutation.getFieldName()
                                + " | " + mutation.getOperation()
                                + " | table=" + mutation.getTableName()
                )
        );

        System.out.println("--------------------------------------");
    }

    private FileParseResult parseFile(
            Path file,
            List<BuildConfigReader.ModuleConfig> modules) {

        try {

            int javaVersion = javaVersionFor(file, modules);

            CompilationUnit compilationUnit =
                    javaSourceParser.parse(file, javaVersion);

            List<ParsedClass> parsedClasses = new ArrayList<>();

            for (TypeDeclaration<?> declaration :
                    compilationUnit.findAll(TypeDeclaration.class)) {

                if (declaration instanceof AnnotationDeclaration) {
                    continue;
                }

                ClassInfo classInfo =
                        classAnalyzer.analyze(
                                compilationUnit,
                                declaration
                        );

                parsedClasses.add(
                        new ParsedClass(declaration, classInfo)
                );
            }

            Map<String, ClassOrInterfaceDeclaration> declarations =
                    new HashMap<>();

            for (ClassOrInterfaceDeclaration coid :
                    compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {

                declarations.put(coid.getNameAsString(), coid);
            }

            Map<String, List<String>> annotationMeta =
                    new HashMap<>();

            for (AnnotationDeclaration annotationDeclaration :
                    compilationUnit.findAll(AnnotationDeclaration.class)) {

                List<String> ownAnnotations =
                        annotationDeclaration.getAnnotations()
                                .stream()
                                .map(AnnotationNames::simpleName)
                                .toList();

                annotationMeta.put(
                        annotationDeclaration.getNameAsString(),
                        ownAnnotations
                );
            }

            return new FileParseResult(
                    file,
                    compilationUnit,
                    parsedClasses,
                    declarations,
                    annotationMeta,
                    null
            );

        } catch (IOException | RuntimeException failure) {

            return new FileParseResult(
                    file,
                    null,
                    List.of(),
                    Map.of(),
                    Map.of(),
                    file + ": " + failure.getMessage()
            );
        }
    }

    private int javaVersionFor(
            Path file,
            List<BuildConfigReader.ModuleConfig> modules) {

        return modules.stream()
                .filter(module -> file.startsWith(module.sourceRoot()))
                .max(Comparator.comparingInt(
                        module -> module.sourceRoot().getNameCount()
                ))
                .map(BuildConfigReader.ModuleConfig::javaVersion)
                .orElse(21);
    }
}
