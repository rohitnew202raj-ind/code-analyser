package org.example.analyser.analyzer;

import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
import org.example.analyser.model.ClassCouplingInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Flags classes with unusually high outgoing (efferent) coupling
 * - a structural proxy for "this class knows about, and depends
 * on, too many other things to reasonably be doing one job."
 *
 * THRESHOLD (documented, not configurable in v1): {@value
 * #OUTGOING_DEPENDENCY_THRESHOLD} outgoing dependencies. This is
 * a fixed starting point drawn from the common static-analysis
 * rule of thumb that efferent coupling above ~10 correlates with
 * reduced cohesion - not a scientifically precise cutoff. A
 * codebase with a genuinely different baseline class size may
 * want a different number; that's a real limitation of shipping
 * a fixed constant instead of a configurable one; see
 * LIMITATIONS.md.
 *
 * Validated against the ~860-class synthetic monolith referenced
 * throughout this codebase: {@code GodFacade} (outgoing=14, a
 * deliberately planted "does everything" anti-pattern class) is
 * correctly flagged, while every ordinary {@code *ServiceImpl}
 * (outgoing=9 - repository + mapper + validator + event
 * publisher + legacy-V1 delegate, the normal shape) is correctly
 * left alone.
 */
@Component
public class GodClassAnalyzer {

    static final int OUTGOING_DEPENDENCY_THRESHOLD = 10;

    public List<ArchitectureFinding> analyze(
            List<ClassCouplingInfo> coupling) {

        List<ArchitectureFinding> findings = new ArrayList<>();

        for (ClassCouplingInfo info : coupling) {

            if (info.getOutgoingDependencies()
                    < OUTGOING_DEPENDENCY_THRESHOLD) {

                continue;
            }

            findings.add(
                    new ArchitectureFinding(
                            ArchitectureFindingType.GOD_CLASS,
                            List.of(info.getClassName()),
                            info.getClassName() + " depends on "
                                    + info.getOutgoingDependencies()
                                    + " other classes (threshold: "
                                    + OUTGOING_DEPENDENCY_THRESHOLD
                                    + ") - likely doing too much"
                    )
            );
        }

        return findings;
    }
}
