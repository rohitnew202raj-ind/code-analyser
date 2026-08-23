package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Propagates SERVICE/REPOSITORY classification from an
 * implementing class down to the interface it implements.
 *
 * The overwhelmingly common Spring pattern is to program to an
 * interface:
 *
 * @Service
 * class OrderServiceImpl implements OrderService { ... }
 *
 * class OrderController {
 *     private final OrderService orderService; // the interface
 * }
 *
 * Fields are (correctly) declared with the interface type, but
 * the interface itself carries no @Service annotation - only
 * OrderServiceImpl does. Without this resolver,
 * DependencyAnalyzer.classifyDependency looks at
 * OrderService.getType() (a generic INTERFACE fallback, since
 * interfaces aren't annotated) instead of OrderServiceImpl's,
 * so almost every service-layer dependency edge in a codebase
 * that programs to interfaces - which is most of them - would
 * be misclassified as a plain, unnamed dependency. Confirmed
 * on a ~860-class synthetic monolith: every controller/facade
 * -> service edge came back unclassified before this fix.
 *
 * LIMITATION (documented): when an interface has multiple
 * implementors with different roles (rare, but possible - a
 * test double or a second, differently-annotated
 * implementation), this can't know which one a given caller
 * actually gets wired to. It only propagates a role when every
 * implementor that has a recognized role agrees on the same
 * one; a genuine conflict is left unresolved (kept at its
 * fallback classification) rather than guessed.
 */
@Component
public class InterfaceRoleResolver {

    private static final Set<String> PROPAGATABLE_TYPES =
            Set.of("SERVICE", "REPOSITORY");

    public void resolve(List<ClassInfo> classes) {

        Map<String, ClassInfo> byName = new HashMap<>();

        for (ClassInfo classInfo : classes) {
            byName.put(classInfo.getName(), classInfo);
        }

        Map<String, Set<String>> typesByInterface = new HashMap<>();

        for (ClassInfo classInfo : classes) {

            if (!PROPAGATABLE_TYPES.contains(classInfo.getType())) {
                continue;
            }

            for (String implementedType : classInfo.getImplementedTypes()) {

                typesByInterface
                        .computeIfAbsent(
                                implementedType,
                                key -> new HashSet<>()
                        )
                        .add(classInfo.getType());
            }
        }

        for (Map.Entry<String, Set<String>> entry :
                typesByInterface.entrySet()) {

            if (entry.getValue().size() != 1) {
                // Conflicting roles across implementors -
                // leave unresolved rather than guess.
                continue;
            }

            ClassInfo interfaceInfo = byName.get(entry.getKey());

            if (interfaceInfo == null) {
                // The interface isn't part of the scanned
                // project (e.g. a JDK/library interface).
                continue;
            }

            if (PROPAGATABLE_TYPES.contains(interfaceInfo.getType())) {
                // Already classified directly - don't override.
                continue;
            }

            String resolvedType = entry.getValue().iterator().next();

            interfaceInfo.setType(resolvedType);

            if (!interfaceInfo.getRoles().contains(resolvedType)) {
                interfaceInfo.getRoles().add(resolvedType);
            }
        }
    }
}
