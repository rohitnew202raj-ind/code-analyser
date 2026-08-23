package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;

import java.util.List;

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
 * LIMITATION (documented, not solved): this is still a
 * heuristic. A project that mixes domain-first packages with
 * purely technical/layered packages in the same tree will
 * still get an imperfect split - there is no fully general
 * way to infer "domain" from package names alone without
 * project-specific configuration.
 */
public class PackageDomainExtractor {

    private final int domainSegmentIndex;

    private PackageDomainExtractor(int domainSegmentIndex) {
        this.domainSegmentIndex = domainSegmentIndex;
    }

    public static PackageDomainExtractor fit(
            List<ClassInfo> classes) {

        String[] commonPrefix = null;

        for (ClassInfo classInfo : classes) {

            String packageName =
                    classInfo.getPackageName();

            if (packageName == null
                    || packageName.isBlank()) {

                continue;
            }

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

        return new PackageDomainExtractor(splitIndex);
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

        if (parts.length <= domainSegmentIndex) {
            return "core";
        }

        return parts[domainSegmentIndex];
    }
}
