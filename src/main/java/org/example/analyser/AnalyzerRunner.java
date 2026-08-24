package org.example.analyser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import org.example.analyser.analyzer.AnnotationNames;
import org.example.analyser.analyzer.ApiAnalyzer;
import org.example.analyser.analyzer.ArchitectureInsightsAnalyzer;
import org.example.analyser.analyzer.BatchAnalyzer;
import org.example.analyser.analyzer.BeanResolutionAnalyzer;
import org.example.analyser.analyzer.CircularDependencyAnalyzer;
import org.example.analyser.analyzer.ClassAnalyzer;
import org.example.analyser.analyzer.CouplingAnalyzer;
import org.example.analyser.analyzer.CrudAnalyzer;
import org.example.analyser.analyzer.DeadComponentAnalyzer;
import org.example.analyser.analyzer.DependencyAnalyzer;
import org.example.analyser.analyzer.DependencyGraphBuilder;
import org.example.analyser.analyzer.DomainAnalyzer;
import org.example.analyser.analyzer.DomainBoundaryAnalyzer;
import org.example.analyser.analyzer.DomainCircularDependencyAnalyzer;
import org.example.analyser.analyzer.DomainDependencyAnalyzer;
import org.example.analyser.analyzer.EntityMutationAnalyzer;
import org.example.analyser.analyzer.EntryPointBehaviorAnalyzer;
import org.example.analyser.analyzer.FlowEngine;
import org.example.analyser.analyzer.GodClassAnalyzer;
import org.example.analyser.analyzer.InterfaceRoleResolver;
import org.example.analyser.analyzer.MetaAnnotationResolver;
import org.example.analyser.analyzer.MethodCallAnalyzer;
import org.example.analyser.analyzer.NPlusOneQueryAnalyzer;
import org.example.analyser.analyzer.PackageDomainExtractor;
import org.example.analyser.analyzer.RepositoryBypassAnalyzer;
import org.example.analyser.analyzer.RepositoryInheritanceResolver;
import org.example.analyser.analyzer.ReportExporter;
import org.example.analyser.analyzer.RuntimeDependencyAnalyzer;
import org.example.analyser.analyzer.SharedEntityHotspotAnalyzer;
import org.example.analyser.analyzer.SpringBatchBuilderAnalyzer;
import org.example.analyser.analyzer.SpringComponentAnalyzer;
import org.example.analyser.analyzer.WebFluxRouterAnalyzer;

import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.BeanResolution;
import org.example.analyser.model.ClassCouplingInfo;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.DependencyGraph;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DomainBoundaryInfo;
import org.example.analyser.model.DomainCycle;
import org.example.analyser.model.DomainDependency;
import org.example.analyser.model.DomainExtractionResult;
import org.example.analyser.model.DomainInfo;
import org.example.analyser.model.InsightsReport;
import org.example.analyser.model.EntityMutationInfo;
import org.example.analyser.model.EntryPointBehavior;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.FlowPath;
import org.example.analyser.model.MethodCallInfo;
import org.example.analyser.model.PersistenceFinding;

import org.example.analyser.parser.JavaSourceParser;
import org.example.analyser.scanner.BuildConfigReader;
import org.example.analyser.scanner.MavenDependencyResolver;
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
import java.util.stream.Collectors;

@Component
public class AnalyzerRunner implements CommandLineRunner {

    private final ProjectScanner projectScanner;
    private final BuildConfigReader buildConfigReader;
    private final MavenDependencyResolver mavenDependencyResolver;
    private final JavaSourceParser javaSourceParser;
    private final ClassAnalyzer classAnalyzer;
    private final SpringComponentAnalyzer springComponentAnalyzer;
    private final RepositoryInheritanceResolver repositoryInheritanceResolver;
    private final InterfaceRoleResolver interfaceRoleResolver;
    private final BeanResolutionAnalyzer beanResolutionAnalyzer;
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
    private final FlowEngine flowEngine;
    private final EntryPointBehaviorAnalyzer entryPointBehaviorAnalyzer;
    private final CircularDependencyAnalyzer circularDependencyAnalyzer;
    private final GodClassAnalyzer godClassAnalyzer;
    private final RepositoryBypassAnalyzer repositoryBypassAnalyzer;
    private final DeadComponentAnalyzer deadComponentAnalyzer;
    private final DomainCircularDependencyAnalyzer domainCircularDependencyAnalyzer;
    private final DomainBoundaryAnalyzer domainBoundaryAnalyzer;
    private final NPlusOneQueryAnalyzer nPlusOneQueryAnalyzer;
    private final SharedEntityHotspotAnalyzer sharedEntityHotspotAnalyzer;
    private final SpringBatchBuilderAnalyzer springBatchBuilderAnalyzer;
    private final WebFluxRouterAnalyzer webFluxRouterAnalyzer;
    private final ArchitectureInsightsAnalyzer architectureInsightsAnalyzer;
    private final ReportExporter reportExporter;

    public AnalyzerRunner(
            ProjectScanner projectScanner,
            BuildConfigReader buildConfigReader,
            MavenDependencyResolver mavenDependencyResolver,
            JavaSourceParser javaSourceParser,
            ClassAnalyzer classAnalyzer,
            SpringComponentAnalyzer springComponentAnalyzer,
            RepositoryInheritanceResolver repositoryInheritanceResolver,
            InterfaceRoleResolver interfaceRoleResolver,
            BeanResolutionAnalyzer beanResolutionAnalyzer,
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
            FlowEngine flowEngine,
            EntryPointBehaviorAnalyzer entryPointBehaviorAnalyzer,
            CircularDependencyAnalyzer circularDependencyAnalyzer,
            GodClassAnalyzer godClassAnalyzer,
            RepositoryBypassAnalyzer repositoryBypassAnalyzer,
            DeadComponentAnalyzer deadComponentAnalyzer,
            DomainCircularDependencyAnalyzer domainCircularDependencyAnalyzer,
            DomainBoundaryAnalyzer domainBoundaryAnalyzer,
            NPlusOneQueryAnalyzer nPlusOneQueryAnalyzer,
            SharedEntityHotspotAnalyzer sharedEntityHotspotAnalyzer,
            SpringBatchBuilderAnalyzer springBatchBuilderAnalyzer,
            WebFluxRouterAnalyzer webFluxRouterAnalyzer,
            ArchitectureInsightsAnalyzer architectureInsightsAnalyzer,
            ReportExporter reportExporter) {

        this.projectScanner = projectScanner;
        this.buildConfigReader = buildConfigReader;
        this.mavenDependencyResolver = mavenDependencyResolver;
        this.javaSourceParser = javaSourceParser;
        this.classAnalyzer = classAnalyzer;
        this.springComponentAnalyzer = springComponentAnalyzer;
        this.repositoryInheritanceResolver = repositoryInheritanceResolver;
        this.interfaceRoleResolver = interfaceRoleResolver;
        this.beanResolutionAnalyzer = beanResolutionAnalyzer;
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
        this.flowEngine = flowEngine;
        this.entryPointBehaviorAnalyzer = entryPointBehaviorAnalyzer;
        this.circularDependencyAnalyzer = circularDependencyAnalyzer;
        this.godClassAnalyzer = godClassAnalyzer;
        this.repositoryBypassAnalyzer = repositoryBypassAnalyzer;
        this.deadComponentAnalyzer = deadComponentAnalyzer;
        this.domainCircularDependencyAnalyzer = domainCircularDependencyAnalyzer;
        this.domainBoundaryAnalyzer = domainBoundaryAnalyzer;
        this.nPlusOneQueryAnalyzer = nPlusOneQueryAnalyzer;
        this.sharedEntityHotspotAnalyzer = sharedEntityHotspotAnalyzer;
        this.springBatchBuilderAnalyzer = springBatchBuilderAnalyzer;
        this.webFluxRouterAnalyzer = webFluxRouterAnalyzer;
        this.architectureInsightsAnalyzer = architectureInsightsAnalyzer;
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

        List<Path> dependencyJars =
                mavenDependencyResolver.resolve(targetProject);

        javaSourceParser.configureSymbolSolver(
                modules.stream()
                        .map(BuildConfigReader.ModuleConfig::sourceRoot)
                        .toList(),
                dependencyJars
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
        System.out.println("Dependency jars: " + dependencyJars.size()
                + (dependencyJars.isEmpty()
                        ? " (Maven-only; none resolved or not a Maven project)"
                        : " (resolved via mvn dependency:build-classpath)"));

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
        // STEP 3c: BEAN RESOLUTION
        //
        // For every interface with 2+ Spring-managed
        // implementations, works out which one actually gets
        // wired (via @Primary) or reports the candidates instead
        // of guessing. See BeanResolutionAnalyzer for exactly
        // what is and isn't resolved.
        // ======================================

        List<BeanResolution> beanResolutions =
                beanResolutionAnalyzer.analyze(classes);

        // ======================================
        // STEP 4: ENTRY POINTS - REST/GRAPHQL APIS
        // + BATCH/SCHEDULED/STARTUP PROGRAMS
        //
        // One unified list: ApiAnalyzer and BatchAnalyzer
        // both answer "where does execution start", just for
        // different trigger kinds. Keeping them as one list
        // from here on (rather than two the flow engine, and
        // every future consumer, would each have to know about
        // and merge separately) is the whole point of
        // EntryPointInfo.
        // ======================================

        PackageDomainExtractor domainExtractor =
                PackageDomainExtractor.fit(classes);

        List<EntryPointInfo> entryPoints = new ArrayList<>();

        for (ParsedClass parsedClass : parsedClasses) {

            entryPoints.addAll(
                    batchAnalyzer.analyze(
                            parsedClass.declaration(),
                            parsedClass.classInfo(),
                            domainExtractor
                    )
            );

            entryPoints.addAll(
                    springBatchBuilderAnalyzer.analyze(
                            parsedClass.declaration(),
                            parsedClass.classInfo(),
                            domainExtractor
                    )
            );

            entryPoints.addAll(
                    webFluxRouterAnalyzer.analyze(
                            parsedClass.declaration(),
                            parsedClass.classInfo(),
                            classes,
                            domainExtractor
                    )
            );

            if (parsedClass.declaration()
                    instanceof ClassOrInterfaceDeclaration coid) {

                entryPoints.addAll(
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
        // STEP 6a: PERSISTENCE INTELLIGENCE
        //
        // Both of these are structural queries over the CRUD
        // data just computed above - no new parsing, same
        // "document scope, don't guess" approach as the
        // architecture/domain intelligence checks.
        // ======================================

        List<PersistenceFinding> persistenceFindings =
                new ArrayList<>();

        persistenceFindings.addAll(
                nPlusOneQueryAnalyzer.analyze(crudOperations)
        );

        persistenceFindings.addAll(
                sharedEntityHotspotAnalyzer.analyze(crudOperations)
        );

        // ======================================
        // STEP 6b: ENTRY POINT FLOWS
        //
        // For each entry point, walk the method-call graph
        // outward to find every database operation and entity
        // mutation reachable from it - "when this API/job runs,
        // what actually happens" instead of just the disconnected
        // facts above. See FlowEngine for how the walk works and
        // its documented limitations.
        // ======================================

        List<FlowPath> flows =
                flowEngine.analyze(
                        entryPoints,
                        methodCalls,
                        crudOperations,
                        entityMutations,
                        classes
                );

        // ======================================
        // STEP 6c: ENTRY POINT BEHAVIOR MODEL
        //
        // Classifies each entry point as READ_ONLY or MUTATING
        // from the flow FlowEngine already traced for it, and
        // renders each flow as a Mermaid sequence diagram (see
        // ReportExporter) - "what does calling this actually do"
        // as behavior, not just structure.
        // ======================================

        List<EntryPointBehavior> entryPointBehaviors =
                entryPointBehaviorAnalyzer.analyze(flows);

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
        // STEP 8b: ARCHITECTURE INTELLIGENCE
        //
        // Each of these is a structural query over data already
        // computed above (the dependency graph, coupling numbers,
        // roles, entry points) - none of them re-resolve anything
        // themselves, and each documents its own scope/thresholds.
        // ======================================

        List<ArchitectureFinding> architectureFindings =
                new ArrayList<>();

        architectureFindings.addAll(
                circularDependencyAnalyzer.analyze(dependencyGraph)
        );

        architectureFindings.addAll(
                godClassAnalyzer.analyze(coupling)
        );

        architectureFindings.addAll(
                repositoryBypassAnalyzer.analyze(dependencies, classes)
        );

        architectureFindings.addAll(
                deadComponentAnalyzer.analyze(
                        classes, coupling, entryPoints
                )
        );

        // ======================================
        // STEP 9: DOMAINS
        // ======================================

        DomainExtractionResult domainExtractionResult =
                domainAnalyzer.analyze(classes, crudOperations);

        List<DomainInfo> domains =
                domainExtractionResult.getDomains();

        List<DomainDependency> domainDependencies =
                domainDependencyAnalyzer.analyze(domains, dependencies);

        // ======================================
        // STEP 9b: DOMAIN INTELLIGENCE
        //
        // Cycle detection runs first because DomainBoundaryAnalyzer
        // treats cycle membership as an automatic disqualifier,
        // overriding whatever the raw coupling count would
        // otherwise suggest.
        // ======================================

        List<DomainCycle> domainCycles =
                domainCircularDependencyAnalyzer.analyze(
                        domains, domainDependencies
                );

        List<DomainBoundaryInfo> domainBoundaries =
                domainBoundaryAnalyzer.analyze(
                        domains, domainDependencies, domainCycles
                );

        // ======================================
        // STEP 9c: ARCHITECTURE INSIGHTS
        //
        // Cross-cutting questions ("what does this API touch",
        // "which domain owns this table", "what's cheapest to
        // extract first") answered by re-querying data already
        // computed above - no new parsing.
        // ======================================

        InsightsReport insightsReport =
                architectureInsightsAnalyzer.analyze(
                        domains, domainBoundaries, flows, crudOperations
                );

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
                        entryPoints,
                        methodCalls,
                        crudOperations,
                        entityMutations,
                        flows,
                        architectureFindings,
                        domainCycles,
                        domainBoundaries,
                        persistenceFindings,
                        entryPointBehaviors,
                        beanResolutions,
                        domainExtractionResult.getStrategy(),
                        domainExtractionResult.getStrategyConfidence()
                );

        try {

            reportExporter.export(
                    outputDirectory,
                    report,
                    dependencyGraph,
                    insightsReport
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
            System.out.println("TYPE: " + classInfo.getType()
                    + " (" + classInfo.getTypeSource() + ")");
            System.out.println("ROLES: " + classInfo.getRoles());

            System.out.println("ANNOTATIONS:");
            classInfo.getAnnotations()
                    .forEach(a -> System.out.println("  " + a));

            System.out.println("FIELDS:");
            classInfo.getFields().forEach(field ->
                    System.out.println(
                            "  " + prefixWithAnnotations(field.getAnnotations())
                                    + field.getType() + " " + field.getName()
                    )
            );

            System.out.println("METHODS:");
            classInfo.getMethods().forEach(method ->
                    System.out.println(
                            "  " + prefixWithAnnotations(method.getAnnotations())
                                    + method.getReturnType() + " " + method.getName()
                                    + "(" + method.getParameters().stream()
                                            .map(parameter ->
                                                    parameter.getType() + " "
                                                            + parameter.getName()
                                            )
                                            .collect(Collectors.joining(", "))
                                    + ")"
                    )
            );
        }

        // ======================================
        // PRINT: BEAN RESOLUTION
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("          BEAN RESOLUTION");
        System.out.println("======================================");

        if (beanResolutions.isEmpty()) {

            System.out.println("(no interfaces with multiple "
                    + "implementations found)");

        } else {

            beanResolutions.forEach(resolution ->
                    System.out.println(
                            resolution.getInterfaceName()
                                    + " | " + resolution.getVerdict()
                                    + " | " + resolution.getDescription()
                    )
            );
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

        System.out.println();
        System.out.println(
                "Grouping strategy: "
                        + domainExtractionResult.getStrategy()
                        + " (confidence scores: "
                        + domainExtractionResult.getStrategyConfidence()
                        + ")"
        );

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
        // PRINT: DOMAIN BOUNDARY ANALYSIS
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("      DOMAIN BOUNDARY ANALYSIS");
        System.out.println("======================================");

        if (!domainCycles.isEmpty()) {

            System.out.println("CYCLES:");
            domainCycles.forEach(cycle ->
                    System.out.println("  " + cycle.getDescription())
            );
            System.out.println();
        }

        domainBoundaries.forEach(boundary ->
                System.out.println(
                        boundary.getDomainName()
                                + " | " + boundary.getVerdict()
                                + " | classes=" + boundary.getClassCount()
                                + " | outgoing=" + boundary.getOutgoingDomainDependencies()
                                + " | incoming=" + boundary.getIncomingDomainDependencies()
                                + " | " + boundary.getReason()
                )
        );

        // ======================================
        // PRINT: ENTRY POINT INVENTORY
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("        ENTRY POINT INVENTORY");
        System.out.println("======================================");

        entryPoints.forEach(entryPoint ->
                System.out.println(
                        entryPoint.getTriggerType()
                                + (entryPoint.getPath() != null
                                        ? " " + entryPoint.getPath()
                                        : "")
                                + " -> " + entryPoint.getClassName()
                                + "." + entryPoint.getMethodName()
                                + " [" + entryPoint.getDomain() + "]"
                )
        );

        // ======================================
        // PRINT: ENTRY POINT BEHAVIOR
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("        ENTRY POINT BEHAVIOR");
        System.out.println("======================================");

        entryPointBehaviors.forEach(behavior -> {

            EntryPointInfo entryPoint = behavior.getEntryPoint();

            System.out.println(
                    entryPoint.getClassName()
                            + "." + entryPoint.getMethodName()
                            + " | " + behavior.getClassification()
                            + " | writeOperations="
                            + behavior.getWriteOperationCount()
            );
        });

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
                                + (operation.isInsideLoop()
                                        ? " | insideLoop=true"
                                        : "")
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

        // ======================================
        // PRINT: ENTRY POINT FLOWS
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("         ENTRY POINT FLOWS");
        System.out.println("======================================");

        for (FlowPath flow : flows) {

            EntryPointInfo entryPoint = flow.getEntryPoint();

            System.out.println("--------------------------------------");
            System.out.println(
                    "ENTRY POINT: " + entryPoint.getTriggerType()
                            + (entryPoint.getPath() != null
                                    ? " " + entryPoint.getPath()
                                    : "")
                            + " -> " + entryPoint.getClassName()
                            + "." + entryPoint.getMethodName()
                            + " [" + entryPoint.getDomain() + "]"
            );

            System.out.println("CALLS:");
            flow.getSteps().forEach(call ->
                    System.out.println(
                            "  " + call.getSourceClass()
                                    + "." + call.getSourceMethod()
                                    + " -> " + call.getTargetClass()
                                    + "." + call.getTargetMethod()
                    )
            );

            System.out.println("DATABASE OPERATIONS:");
            flow.getDatabaseOperations().forEach(operation ->
                    System.out.println(
                            "  " + operation.getSourceClass()
                                    + "." + operation.getSourceMethod()
                                    + " -> " + operation.getRepositoryClass()
                                    + "." + operation.getRepositoryMethod()
                                    + " | " + operation.getOperation()
                                    + " | table=" + operation.getTableName()
                    )
            );

            System.out.println("ENTITY MUTATIONS:");
            flow.getEntityMutations().forEach(mutation ->
                    System.out.println(
                            "  " + mutation.getSourceClass()
                                    + "." + mutation.getSourceMethod()
                                    + " -> " + mutation.getEntityClass()
                                    + "." + mutation.getFieldName()
                                    + " | " + mutation.getOperation()
                                    + " | table=" + mutation.getTableName()
                    )
            );

            if (flow.isTruncated()) {
                System.out.println(
                        "  (WARNING: flow truncated - reachable "
                                + "call graph exceeded the safety "
                                + "limit, results are incomplete)"
                );
            }
        }

        // ======================================
        // PRINT: ARCHITECTURE FINDINGS
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("       ARCHITECTURE FINDINGS");
        System.out.println("======================================");

        if (architectureFindings.isEmpty()) {

            System.out.println("(none found)");

        } else {

            architectureFindings.forEach(finding ->
                    System.out.println(
                            "[" + finding.getType() + "] "
                                    + finding.getDescription()
                    )
            );
        }

        // ======================================
        // PRINT: PERSISTENCE FINDINGS
        // ======================================

        System.out.println();
        System.out.println("======================================");
        System.out.println("       PERSISTENCE FINDINGS");
        System.out.println("======================================");

        if (persistenceFindings.isEmpty()) {

            System.out.println("(none found)");

        } else {

            persistenceFindings.forEach(finding ->
                    System.out.println(
                            "[" + finding.getType() + "] "
                                    + finding.getDescription()
                    )
            );
        }

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

    private String prefixWithAnnotations(List<String> annotations) {

        return annotations.isEmpty()
                ? ""
                : String.join(" ", annotations) + " ";
    }
}
