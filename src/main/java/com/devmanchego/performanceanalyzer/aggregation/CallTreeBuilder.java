package com.devmanchego.performanceanalyzer.aggregation;

import com.devmanchego.performanceanalyzer.model.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Merges stack traces from all threads across all snapshots into a single call tree.
 * Each unique path from root to leaf is counted once per thread-snapshot occurrence.
 */
@Component
public class CallTreeBuilder {

    public CallTreeNode build(List<SnapshotResult> snapshots) {
        CallTreeNode root = new CallTreeNode("[root]");

        for (SnapshotResult snapshot : snapshots) {
            for (ThreadInfo thread : snapshot.threads()) {
                List<StackFrame> frames = thread.stackTrace();
                if (frames.isEmpty()) continue;

                // Stack frames are top-of-stack first; we walk root→leaf (reverse order)
                List<StackFrame> reversed = frames.reversed();

                CallTreeNode current = root;
                root.getSamples().incrementAndGet();

                for (int i = 0; i < reversed.size(); i++) {
                    StackFrame frame = reversed.get(i);
                    String sig = frame.signature();
                    CallTreeNode child = current.getOrCreateChild(sig);
                    child.getSamples().incrementAndGet();

                    // Self-sample: this frame is at the top of the actual stack
                    if (i == reversed.size() - 1) {
                        child.getSelfSamples().incrementAndGet();
                    }
                    current = child;
                }
            }
        }

        return root;
    }

    /** Total number of thread-snapshot observations (denominator for % calculations). */
    public int totalSamples(List<SnapshotResult> snapshots) {
        return snapshots.stream()
                .mapToInt(s -> s.threads().size())
                .sum();
    }
}
