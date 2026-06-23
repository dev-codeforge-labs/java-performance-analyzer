package com.devmanchego.performanceanalyzer.parsing;

import com.devmanchego.performanceanalyzer.model.GcLogResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GcLogParserTest {

    private final GcLogParser parser = new GcLogParser();

    @Test
    void parsesUnifiedG1Log() throws IOException {
        String log = """
                [0.123s][info][gc] Using G1 GC
                [1.500s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 512M->256M(1024M) 12.345ms
                [5.000s][info][gc] GC(1) Pause Full (System.gc()) 900M->400M(1024M) 250.000ms
                """;

        GcLogResult result = parser.parse(toStream(log));

        assertThat(result.gcAlgorithm()).isEqualTo("G1");
        assertThat(result.events()).hasSize(2);
        assertThat(result.maxPauseMs()).isEqualTo(250.0);
        assertThat(result.promotionFailureDetected()).isFalse();
    }

    @Test
    void detectsPromotionFailure() throws IOException {
        String log = """
                [0.100s][info][gc] Using G1 GC
                [2.000s][info][gc] GC(0) Pause Young 512M->400M(1024M) 15.000ms
                [2.001s][info][gc] promotion failed
                """;

        GcLogResult result = parser.parse(toStream(log));

        assertThat(result.promotionFailureDetected()).isTrue();
    }

    @Test
    void parsesLegacyGcLog() throws IOException {
        String log = """
                1.234: [GC (Allocation Failure) [PSYoungGen: 512K->256K(1024K)] 1024K->768K(2048K), 0.012345 secs]
                5.678: [Full GC (System.gc()) [PSYoungGen: 10K->0K(512K)] [ParOldGen: 900K->500K(1536K)] 910K->500K(2048K), 0.250000 secs]
                """;

        GcLogResult result = parser.parse(toStream(log));

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).pauseMs()).isCloseTo(12.345, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.events().get(1).pauseMs()).isCloseTo(250.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void returnsWarningForEmptyLog() throws IOException {
        GcLogResult result = parser.parse(toStream(""));
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.events()).isEmpty();
    }

    private ByteArrayInputStream toStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
