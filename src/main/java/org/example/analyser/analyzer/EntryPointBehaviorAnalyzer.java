package org.example.analyser.analyzer;

import org.example.analyser.model.BehaviorClassification;
import org.example.analyser.model.EntryPointBehavior;
import org.example.analyser.model.FlowPath;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Classifies each entry point as READ_ONLY or MUTATING by
 * inspecting the {@link FlowPath} {@code FlowEngine} already
 * traced for it - a query over the reachable database operations
 * and entity mutations already collected, not a new walk of its
 * own. Answers a question none of the earlier phases did: "if I
 * call this, can it change anything?" - useful for reasoning
 * about what's safe to retry, cache, or call speculatively.
 *
 * SCOPE (documented, not a bug): {@code CUSTOM_QUERY} - a
 * repository method CrudAnalyzer couldn't classify from its name
 * (e.g. a hand-written {@code @Query}-annotated method with an
 * arbitrary name) - is conservatively treated as a write. There's
 * no way to know from the method name alone whether it reads or
 * writes, and for a classification meant to answer "is this safe
 * to treat as read-only," guessing READ_ONLY is the unsafe
 * direction; guessing MUTATING is not. This means some genuinely
 * read-only custom queries will be classified MUTATING - a false
 * positive in the conservative direction, not a false negative.
 */
@Component
public class EntryPointBehaviorAnalyzer {

    private static final Set<String> WRITE_OPERATIONS = Set.of(
            "CREATE_OR_UPDATE", "UPDATE", "DELETE", "CUSTOM_QUERY"
    );

    public List<EntryPointBehavior> analyze(List<FlowPath> flows) {

        List<EntryPointBehavior> behaviors = new ArrayList<>();

        for (FlowPath flow : flows) {

            long writeOperationCount =
                    flow.getDatabaseOperations().stream()
                            .filter(operation ->
                                    WRITE_OPERATIONS.contains(
                                            operation.getOperation()
                                    )
                            )
                            .count();

            boolean mutating =
                    writeOperationCount > 0
                            || !flow.getEntityMutations().isEmpty();

            behaviors.add(
                    new EntryPointBehavior(
                            flow.getEntryPoint(),
                            mutating
                                    ? BehaviorClassification.MUTATING
                                    : BehaviorClassification.READ_ONLY,
                            (int) writeOperationCount
                    )
            );
        }

        return behaviors;
    }
}
