package org.example.analyser.model;

public class ApiInfo {

    private String controllerClass;
    private String controllerPackage;

    private String methodName;

    private String httpMethod;
    private String path;

    private String domain;

    public ApiInfo() {
    }

    public ApiInfo(
            String controllerClass,
            String controllerPackage,
            String methodName,
            String httpMethod,
            String path,
            String domain) {

        this.controllerClass = controllerClass;
        this.controllerPackage = controllerPackage;
        this.methodName = methodName;
        this.httpMethod = httpMethod;
        this.path = path;
        this.domain = domain;
    }

    public String getControllerClass() {
        return controllerClass;
    }

    public void setControllerClass(String controllerClass) {
        this.controllerClass = controllerClass;
    }

    public String getControllerPackage() {
        return controllerPackage;
    }

    public void setControllerPackage(String controllerPackage) {
        this.controllerPackage = controllerPackage;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }
}