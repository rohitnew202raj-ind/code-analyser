package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.ClassCouplingInfo;
import org.example.analyser.model.DependencyGraph;
import org.example.analyser.model.DependencyInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CouplingAnalyzer {

    public List<ClassCouplingInfo> analyze(
            DependencyGraph graph) {

        List<ClassCouplingInfo> results =
                new ArrayList<>();

        for (ClassInfo classInfo : graph.getNodes()) {

            int outgoing = 0;
            int incoming = 0;

            for (DependencyInfo edge : graph.getEdges()) {

                if (edge.getSourceClass()
                        .equals(classInfo.getName())) {

                    outgoing++;
                }

                if (edge.getTargetClass()
                        .equals(classInfo.getName())) {

                    incoming++;
                }
            }

            results.add(
                    new ClassCouplingInfo(
                            classInfo.getName(),
                            classInfo.getPackageName(),
                            classInfo.getType(),
                            outgoing,
                            incoming
                    )
            );
        }

        return results;
    }
}