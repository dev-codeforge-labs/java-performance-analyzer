package com.devmanchego.performanceanalyzer.parsing;

import com.devmanchego.performanceanalyzer.model.StackFrame;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Strips synthetic proxy frames from stack traces (Spring AOP, CGLIB, reflection lambdas,
 * Hibernate interceptors) and collapses consecutive synthetic runs into a single placeholder.
 */
@Component
public class StackTraceSanitizer {

    private static final Pattern CGLIB_PATTERN =
            Pattern.compile(".*\\$\\$(?:EnhancerBySpringCGLIB|FastClassBySpringCGLIB|EnhancerByCGLIB)\\$\\$.*");

    private static final Pattern SPRING_AOP_PATTERN =
            Pattern.compile("org\\.springframework\\.aop\\..*|" +
                            "org\\.springframework\\.cglib\\..*|" +
                            "org\\.springframework\\.context\\.annotation\\.ConfigurationClassEnhancer.*");

    private static final Pattern REFLECTION_PATTERN =
            Pattern.compile("sun\\.reflect\\..*|" +
                            "jdk\\.internal\\.reflect\\..*|" +
                            "java\\.lang\\.reflect\\.Method\\.invoke.*");

    private static final Pattern HIBERNATE_PROXY_PATTERN =
            Pattern.compile("org\\.hibernate\\.proxy\\..*|" +
                            ".*\\$HibernateProxy\\$.*|" +
                            "org\\.hibernate\\.bytecode\\..*");

    private static final Pattern LAMBDA_METAFACTORY_PATTERN =
            Pattern.compile("java\\.lang\\.invoke\\.LambdaMetafactory.*|" +
                            "java\\.lang\\.invoke\\.InnerClassLambdaMetafactory.*");

    static final String SYNTHETIC_PLACEHOLDER = "[synthetic proxy frames omitted]";

    public List<StackFrame> sanitize(List<StackFrame> frames) {
        List<StackFrame> result = new ArrayList<>();
        boolean lastWasSynthetic = false;

        for (StackFrame frame : frames) {
            if (isSynthetic(frame)) {
                if (!lastWasSynthetic) {
                    result.add(placeholderFrame());
                    lastWasSynthetic = true;
                }
            } else {
                result.add(frame);
                lastWasSynthetic = false;
            }
        }
        return result;
    }

    private boolean isSynthetic(StackFrame frame) {
        String cls = frame.className();
        return CGLIB_PATTERN.matcher(cls).matches()
                || SPRING_AOP_PATTERN.matcher(cls).matches()
                || REFLECTION_PATTERN.matcher(cls).matches()
                || HIBERNATE_PROXY_PATTERN.matcher(cls).matches()
                || LAMBDA_METAFACTORY_PATTERN.matcher(cls).matches()
                || frame.synthetic();
    }

    private StackFrame placeholderFrame() {
        return new StackFrame(SYNTHETIC_PLACEHOLDER, "", null, -1, true);
    }
}
