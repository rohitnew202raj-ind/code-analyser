package org.example.analyser.analyzer;

import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.PersistenceFinding;
import org.example.analyser.model.PersistenceFindingType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Flags repository READ calls made from inside a loop - the
 * classic SELECT N+1 pattern: one query per loop iteration
 * instead of a single batched query (e.g. {@code findAllById},
 * a {@code JOIN FETCH}, or an {@code IN (...)} clause).
 *
 * SCOPE (documented, not a bug): only READ operations are
 * flagged. A write call (save/update/delete) inside a loop is a
 * related but distinct performance concern - it doesn't cause
 * extra *queries* the way a lazy read does, and whether Spring
 * Data batches repeated writes depends on JPA batch-size
 * configuration this tool has no visibility into - so it's left
 * out rather than folded into a finding type its name doesn't
 * describe.
 *
 * Loop detection itself happens once, upstream, in
 * MethodCallAnalyzer (see its {@code isInsideLoop} javadoc for
 * exactly what counts as "inside a loop"); this analyzer is a
 * pure filter over data CrudAnalyzer already collected.
 */
@Component
public class NPlusOneQueryAnalyzer {

    public List<PersistenceFinding> analyze(
            List<CrudOperationInfo> crudOperations) {

        List<PersistenceFinding> findings = new ArrayList<>();

        for (CrudOperationInfo operation : crudOperations) {

            if (!operation.isInsideLoop()) {
                continue;
            }

            if (!"READ".equals(operation.getOperation())) {
                continue;
            }

            findings.add(
                    new PersistenceFinding(
                            PersistenceFindingType.N_PLUS_ONE_QUERY_RISK,
                            List.of(operation.getSourceClass()),
                            operation.getSourceClass() + "."
                                    + operation.getSourceMethod()
                                    + " calls " + operation.getRepositoryClass()
                                    + "." + operation.getRepositoryMethod()
                                    + " inside a loop - potential N+1 "
                                    + "query pattern"
                    )
            );
        }

        return findings;
    }
}
