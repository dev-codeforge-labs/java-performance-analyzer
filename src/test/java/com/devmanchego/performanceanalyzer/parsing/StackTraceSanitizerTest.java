package com.devmanchego.performanceanalyzer.parsing;

import com.devmanchego.performanceanalyzer.model.StackFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StackTraceSanitizerTest {

    private final StackTraceSanitizer sanitizer = new StackTraceSanitizer();

    @Test
    void collapsesConsecutiveCglibFramesIntoOnePlaceholder() {
        List<StackFrame> input = List.of(
                frame("com.example.Service", "doWork"),
                frame("com.example.Service$$EnhancerBySpringCGLIB$$abc123", "doWork"),
                frame("com.example.Service$$EnhancerBySpringCGLIB$$abc123", "intercept"),
                frame("com.example.OtherService", "call")
        );

        List<StackFrame> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).className()).isEqualTo("com.example.Service");
        assertThat(result.get(1).className()).isEqualTo(StackTraceSanitizer.SYNTHETIC_PLACEHOLDER);
        assertThat(result.get(2).className()).isEqualTo("com.example.OtherService");
    }

    @Test
    void keepsNonSyntheticFramesUnchanged() {
        List<StackFrame> input = List.of(
                frame("com.example.Foo", "bar"),
                frame("com.example.Baz", "qux")
        );

        List<StackFrame> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).className()).isEqualTo("com.example.Foo");
        assertThat(result.get(1).className()).isEqualTo("com.example.Baz");
    }

    @Test
    void collapsesTwoSeparateSyntheticRunsIntoTwoPlaceholders() {
        List<StackFrame> input = List.of(
                frame("com.example.A", "m"),
                frame("org.springframework.aop.framework.ReflectiveMethodInvocation", "proceed"),
                frame("com.example.B", "m"),
                frame("sun.reflect.NativeMethodAccessorImpl", "invoke"),
                frame("com.example.C", "m")
        );

        List<StackFrame> result = sanitizer.sanitize(input);

        assertThat(result).hasSize(5);
        assertThat(result.get(1).synthetic()).isTrue();
        assertThat(result.get(3).synthetic()).isTrue();
    }

    private StackFrame frame(String cls, String method) {
        return new StackFrame(cls, method, cls + ".java", 1, false);
    }
}
