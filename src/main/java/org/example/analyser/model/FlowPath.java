package org.example.analyser.model;

import java.util.List;

/**
 * Everything reachable from a single entry point by walking the
 * method-call graph outward: every call hop, every database
 * operation, and every entity field mutation found along the
 * way - not a single linear chain, since a real method usually
 * fans out into several calls, but the whole reachable subgraph.
 *
 * Built by {@code FlowEngine}. See that class for how the walk
 * is done and its documented limitations.
 */
public class FlowPath {

    private EntryPointInfo entryPoint;
    private List<MethodCallInfo> steps;
    private List<CrudOperationInfo> databaseOperations;
    private List<EntityMutationInfo> entityMutations;

    /*
     * True if the walk hit FlowEngine's node-count safety cap
     * before exhausting every reachable node, so the lists
     * above are a partial, not complete, picture of what this
     * entry point can reach.
     */
    private boolean truncated;

    public FlowPath() {
    }

    public FlowPath(
            EntryPointInfo entryPoint,
            List<MethodCallInfo> steps,
            List<CrudOperationInfo> databaseOperations,
            List<EntityMutationInfo> entityMutations,
            boolean truncated) {

        this.entryPoint = entryPoint;
        this.steps = steps;
        this.databaseOperations = databaseOperations;
        this.entityMutations = entityMutations;
        this.truncated = truncated;
    }

    public EntryPointInfo getEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(EntryPointInfo entryPoint) {
        this.entryPoint = entryPoint;
    }

    public List<MethodCallInfo> getSteps() {
        return steps;
    }

    public void setSteps(List<MethodCallInfo> steps) {
        this.steps = steps;
    }

    public List<CrudOperationInfo> getDatabaseOperations() {
        return databaseOperations;
    }

    public void setDatabaseOperations(
            List<CrudOperationInfo> databaseOperations) {

        this.databaseOperations = databaseOperations;
    }

    public List<EntityMutationInfo> getEntityMutations() {
        return entityMutations;
    }

    public void setEntityMutations(
            List<EntityMutationInfo> entityMutations) {

        this.entityMutations = entityMutations;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }
}
