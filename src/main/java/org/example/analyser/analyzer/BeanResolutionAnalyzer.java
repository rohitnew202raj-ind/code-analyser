package org.example.analyser.analyzer;

import org.example.analyser.model.BeanResolution;
import org.example.analyser.model.BeanResolutionVerdict;
import org.example.analyser.model.ClassInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * For every interface with two or more Spring-managed
 * implementations, works out which concrete class actually gets
 * wired when code {@code @Autowire}s the interface - or reports
 * the candidates instead of guessing when it can't.
 *
 * Only {@code @Primary} is used to resolve ambiguity. That's the
 * one Spring bean-selection mechanism this tool can evaluate
 * unconditionally: it's a static fact about the implementation
 * class itself, true regardless of how or where the interface is
 * injected. {@code @Qualifier} and Spring profiles are
 * deliberately NOT used to eliminate candidates:
 *
 * <ul>
 * <li>{@code @Qualifier} disambiguates at each individual
 * injection site (a field/parameter), not at the interface
 * level - the same interface can resolve differently at two
 * different call sites. Modeling that would mean per-injection-
 * site analysis, a larger feature than this pass.</li>
 * <li>Which Spring profile is active is a deployment-time
 * decision this static analysis has no way to know - eliminating
 * a {@code @Profile}-restricted candidate would be a guess about
 * how the application is actually run, not a fact read from the
 * source.</li>
 * </ul>
 *
 * When ambiguous, each candidate's {@code @Profile} value (if
 * any) is still surfaced in the description as useful context -
 * it just isn't used to eliminate anything.
 *
 * SCOPE (documented, not a bug): only implementations classified
 * SERVICE/REPOSITORY/COMPONENT (i.e. confirmed Spring-managed
 * candidates) count - a class that merely implements an
 * interface without being a Spring bean itself (a test double, a
 * plain value object) is not a real candidate and would produce
 * a false "ambiguous" finding otherwise. {@code @Bean}-annotated
 * factory methods inside {@code @Configuration} classes are not
 * modeled - only class-level {@code implements} plus
 * class-level stereotype annotations.
 */
@Component
public class BeanResolutionAnalyzer {

    private static final Set<String> CANDIDATE_TYPES =
            Set.of("SERVICE", "REPOSITORY", "COMPONENT");

    public List<BeanResolution> analyze(List<ClassInfo> classes) {

        Map<String, List<ClassInfo>> implementationsByInterface =
                new LinkedHashMap<>();

        for (ClassInfo classInfo : classes) {

            if (!CANDIDATE_TYPES.contains(classInfo.getType())) {
                continue;
            }

            for (String implementedType : classInfo.getImplementedTypes()) {

                implementationsByInterface
                        .computeIfAbsent(
                                implementedType,
                                key -> new ArrayList<>()
                        )
                        .add(classInfo);
            }
        }

        List<BeanResolution> resolutions = new ArrayList<>();

        for (Map.Entry<String, List<ClassInfo>> entry :
                implementationsByInterface.entrySet()) {

            List<ClassInfo> implementations = entry.getValue();

            if (implementations.size() < 2) {
                continue;
            }

            resolutions.add(
                    resolve(entry.getKey(), implementations)
            );
        }

        return resolutions;
    }

    private BeanResolution resolve(
            String interfaceName,
            List<ClassInfo> implementations) {

        List<String> candidateNames =
                implementations.stream()
                        .map(ClassInfo::getName)
                        .toList();

        List<ClassInfo> primaryImplementations =
                implementations.stream()
                        .filter(impl ->
                                impl.getAnnotationSimpleNames()
                                        .contains("Primary")
                        )
                        .toList();

        if (primaryImplementations.size() == 1) {

            ClassInfo resolved = primaryImplementations.get(0);

            return new BeanResolution(
                    interfaceName,
                    candidateNames,
                    BeanResolutionVerdict.RESOLVED_BY_PRIMARY,
                    resolved.getName(),
                    resolved.getName() + " is @Primary among "
                            + implementations.size()
                            + " implementations of " + interfaceName
            );
        }

        String candidateDescriptions =
                implementations.stream()
                        .map(this::describeCandidate)
                        .collect(Collectors.joining(", "));

        return new BeanResolution(
                interfaceName,
                candidateNames,
                BeanResolutionVerdict.AMBIGUOUS,
                null,
                interfaceName + " has " + implementations.size()
                        + " implementations with no single @Primary ("
                        + candidateDescriptions
                        + ") - which one is actually wired depends on "
                        + "@Qualifier at each injection site and/or "
                        + "the active Spring profile, neither of "
                        + "which this tool resolves"
        );
    }

    private String describeCandidate(ClassInfo implementation) {

        String profile = extractProfile(implementation);

        return profile != null
                ? implementation.getName() + " [@Profile(" + profile + ")]"
                : implementation.getName();
    }

    private String extractProfile(ClassInfo implementation) {

        return implementation.getAnnotations()
                .stream()
                .filter(annotation -> annotation.startsWith("@Profile"))
                .map(this::parseProfileValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String parseProfileValue(String annotation) {

        int quoteStart = annotation.indexOf('"');

        if (quoteStart < 0) {
            return null;
        }

        int quoteEnd = annotation.indexOf('"', quoteStart + 1);

        if (quoteEnd < 0) {
            return null;
        }

        return annotation.substring(quoteStart + 1, quoteEnd);
    }
}
