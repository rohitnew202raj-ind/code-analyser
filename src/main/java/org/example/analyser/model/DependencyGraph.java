package org.example.analyser.model;

import java.util.ArrayList;
import java.util.List;

public class DependencyGraph {

    private final List<ClassInfo> nodes;

    private final List<DependencyInfo> edges;

    public DependencyGraph(
            List<ClassInfo> nodes,
            List<DependencyInfo> edges) {

        this.nodes = new ArrayList<>(nodes);
        this.edges = new ArrayList<>(edges);
    }

    public List<ClassInfo> getNodes() {
        return nodes;
    }

    public List<DependencyInfo> getEdges() {
        return edges;
    }
}