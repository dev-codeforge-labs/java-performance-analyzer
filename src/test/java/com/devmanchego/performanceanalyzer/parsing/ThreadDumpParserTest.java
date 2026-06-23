package com.devmanchego.performanceanalyzer.parsing;

import com.devmanchego.performanceanalyzer.model.ThreadDumpResult;
import com.devmanchego.performanceanalyzer.model.ThreadState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadDumpParserTest {

    private final ThreadDumpParser parser = new ThreadDumpParser(
            new SnapshotParser(new StackTraceSanitizer()));

    @Test
    void parsesSingleSnapshotWithTwoThreads() throws IOException {
        String dump = """
                Full thread dump OpenJDK 64-Bit Server VM (21.0.2+13-LTS mixed mode):

                "main" #1 prio=5 os_prio=0 tid=0x00007f1 nid=0x1234 runnable [0x00007fff]
                   java.lang.Thread.State: RUNNABLE
                \tat com.example.App.main(App.java:10)

                "http-worker-1" #10 daemon prio=5 os_prio=0 tid=0x00007f2 nid=0x1235 waiting on condition [0x00007ffe]
                   java.lang.Thread.State: TIMED_WAITING (sleeping)
                \tat java.lang.Thread.sleep(Native Method)
                \tat com.example.Worker.run(Worker.java:42)
                """;

        ThreadDumpResult result = parser.parse(toStream(dump));

        assertThat(result.snapshots()).hasSize(1);
        var snapshot = result.snapshots().get(0);
        assertThat(snapshot.threads()).hasSize(2);

        var main = snapshot.threads().get(0);
        assertThat(main.name()).isEqualTo("main");
        assertThat(main.state()).isEqualTo(ThreadState.RUNNABLE);
        assertThat(main.daemon()).isFalse();

        var worker = snapshot.threads().get(1);
        assertThat(worker.name()).isEqualTo("http-worker-1");
        assertThat(worker.state()).isEqualTo(ThreadState.TIMED_WAITING);
        assertThat(worker.daemon()).isTrue();
    }

    @Test
    void detectsBlockedThreadWithMonitorAddress() throws IOException {
        String dump = """
                Full thread dump OpenJDK 64-Bit Server VM (21.0.2+13-LTS mixed mode):

                "thread-A" #1 prio=5 os_prio=0 tid=0x001 nid=0x100 waiting for monitor entry [0x00007fff]
                   java.lang.Thread.State: BLOCKED (on object monitor)
                \tat com.example.Service.process(Service.java:20)
                \t- waiting to lock <0xdeadbeef> (a java.util.HashMap)
                """;

        ThreadDumpResult result = parser.parse(toStream(dump));

        var thread = result.snapshots().get(0).threads().get(0);
        assertThat(thread.state()).isEqualTo(ThreadState.BLOCKED);
        assertThat(thread.waitingOnMonitor()).isEqualTo("0xdeadbeef");
    }

    @Test
    void sanitizesCglibFrames() throws IOException {
        String dump = """
                Full thread dump OpenJDK 64-Bit Server VM (21.0.2+13-LTS mixed mode):

                "main" #1 prio=5 os_prio=0 tid=0x001 nid=0x100 runnable [0x00007fff]
                   java.lang.Thread.State: RUNNABLE
                \tat com.example.Service.doWork(Service.java:5)
                \tat com.example.Service$$EnhancerBySpringCGLIB$$abc.doWork(Service.java:1)
                \tat com.example.Service$$EnhancerBySpringCGLIB$$abc.intercept(Service.java:1)
                \tat com.example.Controller.handle(Controller.java:15)
                """;

        ThreadDumpResult result = parser.parse(toStream(dump));

        var frames = result.snapshots().get(0).threads().get(0).stackTrace();
        // 4 raw frames → 3 after sanitization (2 CGLIB collapsed into 1 placeholder)
        assertThat(frames).hasSize(3);
    }

    @Test
    void handlesEmptyInputGracefully() throws IOException {
        ThreadDumpResult result = parser.parse(toStream(""));
        assertThat(result.snapshots()).isEmpty();
        assertThat(result.globalWarnings()).isNotEmpty();
    }

    private ByteArrayInputStream toStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
