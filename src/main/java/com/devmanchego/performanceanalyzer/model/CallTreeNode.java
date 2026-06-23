package com.devmanchego.performanceanalyzer.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CallTreeNode {

    private final String methodSignature;
    private final ConcurrentHashMap<String, CallTreeNode> children = new ConcurrentHashMap<>();
    private final AtomicInteger samples = new AtomicInteger(0);
    private final AtomicInteger selfSamples = new AtomicInteger(0);
    private volatile String layerCategory = "Unknown";

    public CallTreeNode(String methodSignature) {
        this.methodSignature = methodSignature;
    }

    public String getMethodSignature() { return methodSignature; }
    public ConcurrentHashMap<String, CallTreeNode> getChildren() { return children; }
    public AtomicInteger getSamples() { return samples; }
    public AtomicInteger getSelfSamples() { return selfSamples; }
    public String getLayerCategory() { return layerCategory; }
    public void setLayerCategory(String layerCategory) { this.layerCategory = layerCategory; }

    public CallTreeNode getOrCreateChild(String signature) {
        return children.computeIfAbsent(signature, CallTreeNode::new);
    }

    public double totalTimePercent(int totalSamples) {
        if (totalSamples == 0) return 0.0;
        return (double) samples.get() / totalSamples * 100.0;
    }

    public double selfTimePercent(int totalSamples) {
        if (totalSamples == 0) return 0.0;
        return (double) selfSamples.get() / totalSamples * 100.0;
    }
}
