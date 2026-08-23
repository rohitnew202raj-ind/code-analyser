package org.example.analyser.model;

/**
 * A read/write classification for a single entry point, derived
 * from the {@link FlowPath} {@code FlowEngine} already traced for
 * it - not a new resolution step, a query over data that already
 * exists.
 */
public class EntryPointBehavior {

    private EntryPointInfo entryPoint;
    private BehaviorClassification classification;

    /*
     * Number of write-shaped database operations
     * (CREATE_OR_UPDATE/UPDATE/DELETE/CUSTOM_QUERY) reachable
     * from this entry point. Does not include entity field
     * mutations, which are counted separately in the flow's own
     * entityMutations list - this is purely "how many write
     * calls", for a quick sense of scale beyond the binary
     * classification.
     */
    private int writeOperationCount;

    public EntryPointBehavior() {
    }

    public EntryPointBehavior(
            EntryPointInfo entryPoint,
            BehaviorClassification classification,
            int writeOperationCount) {

        this.entryPoint = entryPoint;
        this.classification = classification;
        this.writeOperationCount = writeOperationCount;
    }

    public EntryPointInfo getEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(EntryPointInfo entryPoint) {
        this.entryPoint = entryPoint;
    }

    public BehaviorClassification getClassification() {
        return classification;
    }

    public void setClassification(BehaviorClassification classification) {
        this.classification = classification;
    }

    public int getWriteOperationCount() {
        return writeOperationCount;
    }

    public void setWriteOperationCount(int writeOperationCount) {
        this.writeOperationCount = writeOperationCount;
    }
}
