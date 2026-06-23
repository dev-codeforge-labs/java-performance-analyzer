package com.devmanchego.performanceanalyzer.model;

public record ParseWarning(
        String code,
        String message,
        int lineNumber
) {
    public static ParseWarning of(String code, String message) {
        return new ParseWarning(code, message, -1);
    }

    public static ParseWarning at(String code, String message, int lineNumber) {
        return new ParseWarning(code, message, lineNumber);
    }
}
