package com.devmanchego.performanceanalyzer.model;

import java.util.List;

public record ThreadInfo(
        String id,
        String name,
        boolean daemon,
        int priority,
        String nid,
        ThreadState state,
        String waitingOnMonitor,
        List<String> lockedMonitors,
        List<StackFrame> stackTrace
) {
    public boolean isBlocked() {
        return state == ThreadState.BLOCKED;
    }

    public StackFrame topFrame() {
        return stackTrace.isEmpty() ? null : stackTrace.get(0);
    }
}
