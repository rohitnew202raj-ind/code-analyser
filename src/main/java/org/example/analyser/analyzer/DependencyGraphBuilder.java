package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyGraph;
import org.example.analyser.model.DependencyInfo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DependencyGraphBuilder {

    public DependencyGraph build(
            List<ClassInfo> classes,
            List<DependencyInfo> dependencies) {

        return new DependencyGraph(
                classes,
                dependencies
        );
    }
}