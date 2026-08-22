package org.example.analyser.model;

import java.util.ArrayList;
import java.util.List;

public class DomainInfo {

    private final String name;

    private final List<ClassInfo> classes =
            new ArrayList<>();

    public DomainInfo(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<ClassInfo> getClasses() {
        return classes;
    }

    public int getClassCount() {
        return classes.size();
    }
}