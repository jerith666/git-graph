package com.github.jerith666;

import org.eclipse.jgit.revwalk.RevCommit;

public interface ElidedGraphNode {
    RevCommit getChild();
    RevCommit getParent();
}
