package com.github.jerith666;

import org.eclipse.jgit.revwalk.RevCommit;

public interface GraphEdge extends GraphEntity {
    RevCommit getParent();
    RevCommit getChild();

    static GraphEdge forParentChild(RevCommit parent, RevCommit child){
        return new GraphEdge() {
            @Override
            public RevCommit getParent() {
                return parent;
            }
            
            @Override
            public RevCommit getChild() {
                return child;
            }
        };
    }
}
