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
        List<HotNode> hotNodes = new ArrayList<>();
        collectHotNodes(root, null, hotNodes);

        hotNodes.sort(Comparator.comparingDouble(hn -> -hn.node().selfTimePercent(totalSamples)));

        List<HotspotDto> result = new ArrayList<>();
        for (int i = 0; i < hotNodes.size(); i++) {
            CallTreeNode node = hotNodes.get(i).node();
            CallTreeNode parent = hotNodes.get(i).parent();
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
                    topCaller(parent),
                    null,
                    null
            ));
        }
        return result;
    }

    private void collectHotNodes(CallTreeNode node, CallTreeNode parent, List<HotNode> acc) {
        if (node.getSelfSamples().get() > 0 && !"[root]".equals(node.getMethodSignature())) {
            acc.add(new HotNode(node, parent));
        }
        node.getChildren().values().forEach(child -> collectHotNodes(child, node, acc));
    }

    /** The direct caller of this node in its own branch, or null if called from the root. */
    private String topCaller(CallTreeNode parent) {
        if (parent == null || "[root]".equals(parent.getMethodSignature())) return null;
        return parent.getMethodSignature();
    }

    /** A hot node paired with the parent it was reached through, so the caller is unambiguous. */
    private record HotNode(CallTreeNode node, CallTreeNode parent) {}

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
