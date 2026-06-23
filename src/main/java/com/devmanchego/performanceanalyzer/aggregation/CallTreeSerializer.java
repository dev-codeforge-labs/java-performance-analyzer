package com.devmanchego.performanceanalyzer.aggregation;

import com.devmanchego.performanceanalyzer.model.CallTreeNode;
import com.devmanchego.performanceanalyzer.model.CallTreeNodeDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CallTreeSerializer {

    @Value("${performanceanalyzer.thresholds.calltree-max-depth:200}")
    private int maxDepth;

    public CallTreeNodeDto serialize(CallTreeNode node, int totalSamples) {
        return toDto(node, totalSamples, 0);
    }

    /** Builds a reverse (bottom-up) tree: leaves become roots. */
    public CallTreeNodeDto serializeInverted(CallTreeNode root, int totalSamples) {
        CallTreeNode invertedRoot = new CallTreeNode("[inverted-root]");
        invertTree(root, List.of(), invertedRoot);
        return toDto(invertedRoot, totalSamples, 0);
    }

    private CallTreeNodeDto toDto(CallTreeNode node, int totalSamples, int depth) {
        List<CallTreeNodeDto> childDtos = depth >= maxDepth
                ? List.of()
                : node.getChildren().values().stream()
                        .map(child -> toDto(child, totalSamples, depth + 1))
                        .toList();

        return new CallTreeNodeDto(
                node.getMethodSignature(),
                node.getLayerCategory(),
                node.getSamples().get(),
                node.getSelfSamples().get(),
                node.totalTimePercent(totalSamples),
                node.selfTimePercent(totalSamples),
                childDtos
        );
    }

    private void invertTree(CallTreeNode node, List<CallTreeNode> ancestorPath,
                            CallTreeNode invertedRoot) {
        if (node.getSelfSamples().get() > 0 && !"[root]".equals(node.getMethodSignature())) {
            // Build the path from leaf → ancestors as a chain under invertedRoot
            CallTreeNode current = invertedRoot.getOrCreateChild(node.getMethodSignature());
            current.getSamples().addAndGet(node.getSamples().get());
            current.getSelfSamples().addAndGet(node.getSelfSamples().get());
            for (int i = ancestorPath.size() - 1; i >= 0; i--) {
                CallTreeNode ancestor = ancestorPath.get(i);
                CallTreeNode next = current.getOrCreateChild(ancestor.getMethodSignature());
                next.getSamples().addAndGet(ancestor.getSamples().get());
                current = next;
            }
        }

        List<CallTreeNode> newPath = new java.util.ArrayList<>(ancestorPath);
        newPath.add(node);
        for (CallTreeNode child : node.getChildren().values()) {
            invertTree(child, newPath, invertedRoot);
        }
    }
}
