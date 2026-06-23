package com.devmanchego.performanceanalyzer.model;

public record StackFrame(
        String className,
        String methodName,
        String sourceFile,
        int lineNumber,
        boolean synthetic
) {
    public String signature() {
        return className + "." + methodName + "()";
    }

    public String packagePrefix() {
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : className;
    }
}
