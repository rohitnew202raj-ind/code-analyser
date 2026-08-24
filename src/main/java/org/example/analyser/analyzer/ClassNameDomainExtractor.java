package org.example.analyser.analyzer;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Derives a candidate domain from a class's simple name alone, by
 * stripping a known technical-layer suffix off the end of it -
 * {@code PatientController}, {@code PatientService} and
 * {@code PatientRepository} all reduce to {@code Patient}.
 *
 * This exists for projects where {@link PackageDomainExtractor}
 * has nothing to work with: packages that are purely
 * technical/layered (`controller`, `service`, `entity`, ...) with
 * no business-domain segment anywhere, so every class in the
 * project ends up in a domain named after its own layer. Class
 * names in that kind of project usually still carry the domain
 * signal that the packages don't (a team names classes
 * `PatientController` / `PatientService` even when both live in
 * flat `controller` / `service` packages), so this recovers it
 * from spelling instead of structure.
 *
 * {@link #SUFFIXES_BY_LENGTH_DESC} is a fixed, ordered list of
 * layer-word suffixes, tried longest-first so a compound suffix
 * like {@code ServiceImpl} is matched before the shorter
 * {@code Impl} would grab it and leave a misleading remainder
 * ({@code PatientService} instead of {@code Patient}). A class
 * name that doesn't end with any recognized suffix yields no
 * candidate at all ({@code null}) rather than a guess - callers
 * are expected to fall back to another signal in that case.
 *
 * LIMITATION (documented, not solved): this is a fixed word list
 * against English-style Java naming conventions. A codebase that
 * doesn't follow them (non-English names, unconventional suffixes,
 * abbreviations not in the list) gets no signal from this class,
 * same as if it were run against a project with no domain words in
 * its class names at all.
 */
public final class ClassNameDomainExtractor {

    private static final List<String> RAW_SUFFIXES = List.of(
            "RepositoryImpl", "ServiceImpl", "ControllerImpl", "DaoImpl", "FacadeImpl",
            "RestController", "Controller",
            "Repository", "Service", "Dao", "Facade",
            "Entity", "DTO", "Dto", "VO", "Vo",
            "Mapper", "Converter",
            "Configuration", "Config",
            "Exception",
            "Handler", "Listener", "Filter", "Interceptor", "Aspect",
            "Validator", "Factory", "Builder", "Provider",
            "Utils", "Util", "Helper",
            "Scheduler", "Job",
            "Request", "Response", "Form",
            "Publisher", "Consumer", "Event",
            "Constants", "Constant", "Enums", "Enum",
            "Initializer", "Application", "Properties", "Props",
            "Resource", "Endpoint", "Component",
            "Impl"
    );

    private static final List<String> SUFFIXES_BY_LENGTH_DESC =
            RAW_SUFFIXES.stream()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .toList();

    private static final Set<String> SUFFIX_WORDS_LOWERCASE =
            RAW_SUFFIXES.stream()
                    .map(suffix -> suffix.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());

    private ClassNameDomainExtractor() {
    }

    /**
     * The candidate domain for a class's simple name, or
     * {@code null} if no known layer suffix matches (either the
     * name is null/blank, or it doesn't end with any recognized
     * suffix, or the suffix consumes the whole name with nothing
     * left as a candidate).
     */
    public static String domainOf(String simpleClassName) {

        if (simpleClassName == null
                || simpleClassName.isBlank()) {

            return null;
        }

        for (String suffix : SUFFIXES_BY_LENGTH_DESC) {

            boolean longEnoughToLeaveARemainder =
                    simpleClassName.length() > suffix.length();

            if (longEnoughToLeaveARemainder
                    && simpleClassName.regionMatches(
                            true,
                            simpleClassName.length() - suffix.length(),
                            suffix,
                            0,
                            suffix.length())) {

                return simpleClassName.substring(
                        0, simpleClassName.length() - suffix.length()
                );
            }
        }

        return null;
    }

    /**
     * True if the given word (case-insensitive) is one of the
     * known technical-layer suffixes - used to recognize when a
     * *package*-derived domain name (from {@link
     * PackageDomainExtractor}) is actually just a layer name in
     * disguise (`controller`, `service`, ...), the signal that
     * package-based extraction found no real domain boundary.
     */
    public static boolean isTechnicalLayerWord(String word) {

        if (word == null) {
            return false;
        }

        return SUFFIX_WORDS_LOWERCASE.contains(
                word.toLowerCase(Locale.ROOT)
        );
    }
}
