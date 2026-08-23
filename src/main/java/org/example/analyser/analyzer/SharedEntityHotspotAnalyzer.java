package org.example.analyser.analyzer;

import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.PersistenceFinding;
import org.example.analyser.model.PersistenceFindingType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flags entities/tables read or written by an unusually large
 * number of distinct classes - a structural proxy for "this
 * table is a shared kernel that's hard to change without
 * touching half the codebase," the persistence-layer equivalent
 * of {@link GodClassAnalyzer}'s coupling signal.
 *
 * THRESHOLD (documented, not configurable in v1): {@value
 * #SHARED_ENTITY_THRESHOLD} or more distinct classes performing
 * CRUD operations against the same entity. This is a fixed
 * starting point, not a precise cutoff - see LIMITATIONS.md.
 * This was explicitly called out as "not attempted" in Phase 3
 * (Architecture Intelligence); it closes that gap using data
 * {@link CrudAnalyzer} already collects, with no new parsing.
 */
@Component
public class SharedEntityHotspotAnalyzer {

    static final int SHARED_ENTITY_THRESHOLD = 3;

    public List<PersistenceFinding> analyze(
            List<CrudOperationInfo> crudOperations) {

        Map<String, Set<String>> classesByEntity =
                new LinkedHashMap<>();

        for (CrudOperationInfo operation : crudOperations) {

            String entityClass = operation.getEntityClass();

            if (entityClass == null) {
                continue;
            }

            classesByEntity
                    .computeIfAbsent(
                            entityClass,
                            key -> new LinkedHashSet<>()
                    )
                    .add(operation.getSourceClass());
        }

        List<PersistenceFinding> findings = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry
                : classesByEntity.entrySet()) {

            Set<String> touchingClasses = entry.getValue();

            if (touchingClasses.size() < SHARED_ENTITY_THRESHOLD) {
                continue;
            }

            findings.add(
                    new PersistenceFinding(
                            PersistenceFindingType.SHARED_ENTITY_HOTSPOT,
                            List.copyOf(touchingClasses),
                            entry.getKey() + " is accessed by "
                                    + touchingClasses.size()
                                    + " different classes (threshold: "
                                    + SHARED_ENTITY_THRESHOLD
                                    + ") - a shared coupling point"
                    )
            );
        }

        return findings;
    }
}
