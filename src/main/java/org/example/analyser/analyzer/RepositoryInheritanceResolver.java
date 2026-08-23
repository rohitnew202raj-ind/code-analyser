package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Propagates REPOSITORY classification and repositoryEntityType
 * through a project's own base-repository interfaces, e.g.:
 *
 * interface BaseRepository<T, ID> extends JpaRepository<T, ID> { }
 * interface OrderRepository extends BaseRepository<OrderEntity, Long> { }
 *
 * ClassAnalyzer only recognizes a repository when it directly
 * extends a hardcoded Spring Data marker interface, so
 * OrderRepository above would otherwise be missed entirely.
 * This runs once, after every class in the project has been
 * parsed, and fixes that up for arbitrarily deep custom
 * base-repository chains via a small fixed-point iteration
 * (cheap at real-project scale - repository hierarchies are
 * never more than a few levels deep).
 *
 * LIMITATION (documented): entity-type inference here assumes
 * the Spring Data convention that the *first* generic type
 * argument is the entity type at every level of the chain.
 * That holds for the common case but is not guaranteed by the
 * language - a base repository that reorders or adds type
 * parameters before the entity type would confuse this.
 */
@Component
public class RepositoryInheritanceResolver {

    private static final Pattern LIKELY_TYPE_VARIABLE =
            Pattern.compile("^[A-Z]{1,2}[0-9]?$");

    public void resolve(List<ClassInfo> classes) {

        Map<String, ClassInfo> byName =
                classes.stream()
                        .collect(
                                Collectors.toMap(
                                        ClassInfo::getName,
                                        c -> c,
                                        (a, b) -> a
                                )
                        );

        boolean changed = true;
        int iterations = 0;

        while (changed && iterations < 20) {

            changed = false;
            iterations++;

            for (ClassInfo classInfo : classes) {

                if (promoteToRepository(classInfo, byName)) {
                    changed = true;
                }

                if (propagateEntityType(classInfo, byName)) {
                    changed = true;
                }
            }
        }
    }

    private boolean promoteToRepository(
            ClassInfo classInfo,
            Map<String, ClassInfo> byName) {

        if ("REPOSITORY".equals(classInfo.getType())) {
            return false;
        }

        boolean parentIsRepository =
                classInfo.getExtendedTypes()
                        .stream()
                        .map(byName::get)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(parent ->
                                "REPOSITORY".equals(
                                        parent.getType()
                                )
                        );

        if (!parentIsRepository) {
            return false;
        }

        classInfo.setType("REPOSITORY");
        return true;
    }

    private boolean propagateEntityType(
            ClassInfo classInfo,
            Map<String, ClassInfo> byName) {

        if (!"REPOSITORY".equals(classInfo.getType())) {
            return false;
        }

        if (classInfo.getRepositoryEntityType() != null) {
            return false;
        }

        for (String extendedType : classInfo.getExtendedTypes()) {

            String typeArgument =
                    classInfo.getExtendsTypeArguments()
                            .get(extendedType);

            if (typeArgument != null
                    && !isLikelyTypeVariable(typeArgument)) {

                classInfo.setRepositoryEntityType(typeArgument);
                return true;
            }

            ClassInfo parent = byName.get(extendedType);

            if (parent != null
                    && parent.getRepositoryEntityType() != null) {

                classInfo.setRepositoryEntityType(
                        parent.getRepositoryEntityType()
                );
                return true;
            }
        }

        return false;
    }

    private boolean isLikelyTypeVariable(String value) {
        return LIKELY_TYPE_VARIABLE.matcher(value).matches();
    }
}
