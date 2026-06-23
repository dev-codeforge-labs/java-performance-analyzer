package com.devmanchego.performanceanalyzer.aggregation;

import com.devmanchego.performanceanalyzer.model.CallTreeNode;
import com.devmanchego.performanceanalyzer.model.HotspotDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class HotspotExtractor {

    public List<HotspotDto> extract(CallTreeNode root, int totalSamples) {
        List<CallTreeNode> hotNodes = new ArrayList<>();
        collectHotNodes(root, hotNodes);

        hotNodes.sort(Comparator.comparingDouble(n -> -n.selfTimePercent(totalSamples)));

        List<HotspotDto> result = new ArrayList<>();
        for (int i = 0; i < hotNodes.size(); i++) {
            CallTreeNode node = hotNodes.get(i);
            String sig = node.getMethodSignature();
            String[] parts = splitSignature(sig);
            result.add(new HotspotDto(
                    i + 1,
                    sig,
                    parts[0],
                    parts[1],
                    node.getLayerCategory(),
                    node.selfTimePercent(totalSamples),
                    node.totalTimePercent(totalSamples),
                    findTopCaller(node, root),
                    null,
                    null
            ));
        }
        return result;
    }

    private void collectHotNodes(CallTreeNode node, List<CallTreeNode> acc) {
        if (node.getSelfSamples().get() > 0 && !"[root]".equals(node.getMethodSignature())) {
            acc.add(node);
        }
        node.getChildren().values().forEach(child -> collectHotNodes(child, acc));
    }

    /** Best-effort: returns the first parent that is NOT the root. */
    private String findTopCaller(CallTreeNode node, CallTreeNode root) {
        // Walk the tree to find this node's parent
        return findParentSignature(root, node.getMethodSignature());
    }

    private String findParentSignature(CallTreeNode current, String targetSig) {
        for (var entry : current.getChildren().entrySet()) {
            if (entry.getKey().equals(targetSig)) {
                return "[root]".equals(current.getMethodSignature()) ? null : current.getMethodSignature();
            }
            String found = findParentSignature(entry.getValue(), targetSig);
            if (found != null) return found;
        }
        return null;
    }

    private String[] splitSignature(String sig) {
        // "com.example.Foo.bar()" → className="com.example.Foo", package="com.example"
        int parenIdx = sig.indexOf('(');
        String withoutArgs = parenIdx > 0 ? sig.substring(0, parenIdx) : sig;
        int lastDot = withoutArgs.lastIndexOf('.');
        if (lastDot < 0) return new String[]{sig, ""};
        String className = withoutArgs.substring(0, lastDot);
        int pkgDot = className.lastIndexOf('.');
        String pkg = pkgDot > 0 ? className.substring(0, pkgDot) : className;
        return new String[]{className, pkg};
    }
}
