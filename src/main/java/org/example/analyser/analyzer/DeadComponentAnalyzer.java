package org.example.analyser.analyzer;

import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
import org.example.analyser.model.ClassCouplingInfo;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.EntryPointInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flags SERVICE/REPOSITORY/COMPONENT classes that nothing in the
 * codebase depends on and that aren't themselves an entry point
 * (a scheduled job, event listener, startup runner, etc. that
 * Spring invokes directly rather than being called by other
 * application code) - a strong signal of genuinely dead code.
 *
 * SCOPE, and important for correctness: a class that implements
 * a SERVICE/REPOSITORY-classified interface is excluded even if
 * it individually shows zero incoming edges. This is not an edge
 * case to patch around - it's the normal, correct shape of the
 * standard Spring "program to an interface" pattern (see {@code
 * InterfaceRoleResolver}): a field is declared as {@code
 * OrderService}, never as {@code OrderServiceImpl}, so every real
 * incoming reference is recorded against the interface, not the
 * implementation. Checking the implementation class directly for
 * zero incoming edges would flag essentially every correctly
 * wired {@code *Impl} class in a typical codebase as "dead" -
 * exactly the false positive this analyzer exists to avoid.
 *
 * {@code CONTROLLER}/{@code CONFIGURATION}/{@code APPLICATION}/
 * {@code ASPECT}/{@code CONTROLLER_ADVICE} types are excluded
 * entirely: Spring wires those into the application via
 * classpath scanning and framework hooks, not via another class
 * depending on them, so zero incoming edges is their normal,
 * expected state and carries no signal either way.
 */
@Component
public class DeadComponentAnalyzer {

    private static final Set<String> CANDIDATE_TYPES =
            Set.of("SERVICE", "REPOSITORY", "COMPONENT");

    public List<ArchitectureFinding> analyze(
            List<ClassInfo> classes,
            List<ClassCouplingInfo> coupling,
            List<EntryPointInfo> entryPoints) {

        Set<String> entryPointClasses = new HashSet<>();
        entryPoints.forEach(
                entryPoint -> entryPointClasses.add(entryPoint.getClassName())
        );

        Map<String, ClassInfo> byName = new HashMap<>();
        classes.forEach(classInfo -> byName.put(classInfo.getName(), classInfo));

        Set<String> implementationsOfClassifiedInterfaces =
                implementationsOfClassifiedInterfaces(classes, byName);

        List<ArchitectureFinding> findings = new ArrayList<>();

        for (ClassCouplingInfo info : coupling) {

            if (!CANDIDATE_TYPES.contains(info.getType())) {
                continue;
            }

            if (info.getIncomingDependencies() > 0) {
                continue;
            }

            if (entryPointClasses.contains(info.getClassName())) {
                continue;
            }

            if (implementationsOfClassifiedInterfaces
                    .contains(info.getClassName())) {

                continue;
            }

            findings.add(
                    new ArchitectureFinding(
                            ArchitectureFindingType.DEAD_COMPONENT,
                            List.of(info.getClassName()),
                            info.getClassName() + " (" + info.getType()
                                    + ") has no incoming dependencies and "
                                    + "is not an entry point - possibly "
                                    + "dead code"
                    )
            );
        }

        return findings;
    }

    private Set<String> implementationsOfClassifiedInterfaces(
            List<ClassInfo> classes,
            Map<String, ClassInfo> byName) {

        Set<String> implementations = new HashSet<>();

        for (ClassInfo classInfo : classes) {

            for (String implementedType : classInfo.getImplementedTypes()) {

                ClassInfo implementedInterface = byName.get(implementedType);

                if (implementedInterface != null
                        && CANDIDATE_TYPES.contains(
                                implementedInterface.getType()
                        )) {

                    implementations.add(classInfo.getName());
                    break;
                }
            }
        }

        return implementations;
    }
}
