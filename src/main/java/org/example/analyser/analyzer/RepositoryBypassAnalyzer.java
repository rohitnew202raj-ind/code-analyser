package org.example.analyser.analyzer;

import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DependencyType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flags a controller depending directly on a repository,
 * skipping the service layer - the single clearest, most
 * concrete instance of a "layer violation" in a typical layered
 * Spring application (controller -&gt; service -&gt; repository).
 *
 * Reuses roles/types {@code SpringComponentAnalyzer} and {@code
 * DependencyAnalyzer} already compute (the {@code
 * REST_CONTROLLER}/{@code MVC_CONTROLLER} role and the {@code
 * REPOSITORY_DEPENDENCY} edge type), so this needs no resolution
 * of its own - it's a structural query over data the rest of the
 * analyzer already produces correctly.
 *
 * SCOPE (deliberate): broader, configurable layer-ordering rules
 * (e.g. "domain X must never depend on domain Y") are not
 * attempted here - that would require a project-specific
 * layering policy this analyzer has no way to infer on its own,
 * and a wrong guess would be actively misleading rather than
 * merely incomplete. Repository bypass is the one layer
 * violation concrete and universal enough across Spring
 * applications to check without that policy.
 */
@Component
public class RepositoryBypassAnalyzer {

    public List<ArchitectureFinding> analyze(
            List<DependencyInfo> dependencies,
            List<ClassInfo> classes) {

        Map<String, ClassInfo> byName = new HashMap<>();
        classes.forEach(classInfo -> byName.put(classInfo.getName(), classInfo));

        List<ArchitectureFinding> findings = new ArrayList<>();

        for (DependencyInfo dependency : dependencies) {

            if (dependency.getType()
                    != DependencyType.REPOSITORY_DEPENDENCY) {

                continue;
            }

            ClassInfo source = byName.get(dependency.getSourceClass());

            boolean isController =
                    source != null
                            && (source.hasRole("REST_CONTROLLER")
                                    || source.hasRole("MVC_CONTROLLER"));

            if (!isController) {
                continue;
            }

            findings.add(
                    new ArchitectureFinding(
                            ArchitectureFindingType.REPOSITORY_BYPASS,
                            List.of(
                                    dependency.getSourceClass(),
                                    dependency.getTargetClass()
                            ),
                            dependency.getSourceClass() + " calls "
                                    + dependency.getTargetClass()
                                    + " directly, skipping the "
                                    + "service layer"
                    )
            );
        }

        return findings;
    }
}
