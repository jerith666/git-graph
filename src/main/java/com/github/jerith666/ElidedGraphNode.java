package com.github.jerith666;

import org.eclipse.jgit.revwalk.RevCommit;

public interface ElidedGraphNode extends GraphNode {
    RevCommit getChild();
    RevCommit getParent();
}
