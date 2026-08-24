package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Groups classes into "domains" by package.
 *
 * The previous approach hardcoded "domain = the 4th package
 * segment", which only works for one specific convention
 * (com.company.x.<domain>...). Real projects vary a lot:
 * domain-first packages, layered/technical packaging with no
 * domain segment at all, deeper nesting, etc.
 *
 * Instead, this derives the split point from the data itself:
 * it computes the longest package prefix common to every
 * scanned class, and treats the segment immediately after
 * that shared prefix as the domain. That adapts automatically
 * to whatever convention the target project actually uses.
 *
 * One specific failure mode of that basic rule: a project that
 * wraps everything in one extra "grouping" package that isn't
 * itself a domain - {@code com.acme.core.service.X},
 * {@code com.acme.core.repository.X}, {@code com.acme.core.dto.X}
 * - would have every one of those collapse into a single "core"
 * domain, hiding that they're different technical layers rather
 * than one undifferentiated blob. {@link #GENERIC_WRAPPER_SEGMENTS}
 * is a small, fixed list of segment names ({@code core},
 * {@code common}, {@code base}, {@code internal}, {@code shared},
 * {@code impl}) that {@code domainOf} will skip past - but only
 * when doing so cannot destroy real information: it skips a
 * wrapper-named segment only if no scanned class's package
 * actually ends exactly there. If even one class lives directly
 * in {@code com.acme.common} with nothing nested underneath it,
 * "common" is a real, flat, terminal domain in this project (as
 * in this project's own synthetic-monolith test fixture) and is
 * used as-is, exactly like before this class knew about wrapper
 * segments at all. Only when a wrapper-named segment is purely a
 * pass-through - every class beneath it goes at least one level
 * deeper, like {@code core.service}/{@code core.repository}/
 * {@code core.dto} all sitting under an otherwise-empty
 * {@code core} - does it get skipped in favor of the segment
 * underneath. This is what keeps the fix data-driven rather than
 * a blind word-list override: "common" and "core" are candidates
 * for skipping, never a guarantee.
 *
 * LIMITATION (documented, not solved): this is still a
 * heuristic. A project that mixes domain-first packages with
 * purely technical/layered packages in the same tree will
 * still get an imperfect split, and a purely layered project
 * (no business-domain segment anywhere in its package names,
 * at any depth) has no domain signal for this class to recover
 * - the best it can honestly do there is surface the technical
 * layer names themselves, which is what happens once wrapper
 * segments are skipped. There is no fully general way to infer
 * "domain" from package names alone without project-specific
 * configuration.
 */
public class PackageDomainExtractor {

    private static final Set<String> GENERIC_WRAPPER_SEGMENTS =
            Set.of("core", "common", "base", "internal", "shared", "impl");

    private final int domainSegmentIndex;
    private final Set<String> exactPackagesInUse;

    private PackageDomainExtractor(
            int domainSegmentIndex,
            Set<String> exactPackagesInUse) {

        this.domainSegmentIndex = domainSegmentIndex;
        this.exactPackagesInUse = exactPackagesInUse;
    }

    public static PackageDomainExtractor fit(
            List<ClassInfo> classes) {

        String[] commonPrefix = null;
        Set<String> exactPackagesInUse = new HashSet<>();

        for (ClassInfo classInfo : classes) {

            String packageName =
                    classInfo.getPackageName();

            if (packageName == null
                    || packageName.isBlank()) {

                continue;
            }

            exactPackagesInUse.add(packageName);

            String[] parts =
                    packageName.split("\\.");

            if (commonPrefix == null) {
                commonPrefix = parts;
                continue;
            }

            commonPrefix =
                    commonPrefixOf(commonPrefix, parts);
        }

        int splitIndex =
                commonPrefix == null
                        ? 0
                        : commonPrefix.length;

        return new PackageDomainExtractor(
                splitIndex, exactPackagesInUse
        );
    }

    private static String[] commonPrefixOf(
            String[] a,
            String[] b) {

        int max =
                Math.min(a.length, b.length);

        int matched = 0;

        while (matched < max
                && a[matched].equals(b[matched])) {

            matched++;
        }

        String[] result =
                new String[matched];

        System.arraycopy(a, 0, result, 0, matched);

        return result;
    }

    public String domainOf(String packageName) {

        if (packageName == null
                || packageName.isBlank()) {

            return "core";
        }

        String[] parts =
                packageName.split("\\.");

        int index = domainSegmentIndex;

        while (index < parts.length
                && GENERIC_WRAPPER_SEGMENTS.contains(
                        parts[index].toLowerCase(Locale.ROOT))
                && !exactPackagesInUse.contains(
                        joinUpTo(parts, index))) {

            index++;
        }

        if (parts.length <= index) {
            return "core";
        }

        return parts[index];
    }

    /**
     * Segments 0..index (inclusive), rejoined with '.' - the
     * exact package name a class would have if it lived directly
     * at this depth, used to check whether any scanned class
     * actually does. That's what tells a wrapper-named segment
     * ({@code core}, {@code common}, ...) apart from a real,
     * flat, terminal domain of the same name.
     */
    private static String joinUpTo(String[] parts, int index) {

        return String.join(
                ".", Arrays.copyOfRange(parts, 0, index + 1)
        );
    }
}
