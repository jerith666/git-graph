package com.github.jerith666;

import org.eclipse.jgit.revwalk.RevCommit;

public interface GraphEdge extends GraphEntity {
    RevCommit getParent();
    RevCommit getChild();
    boolean parentIsBoring();

    static GraphEdge forParentChild(RevCommit parent, RevCommit child){
        return makeImpl(parent, child, false);
    }

    static GraphEdge forBoringParentChild(RevCommit parent, RevCommit child){
        return makeImpl(parent, child, true);
    }

    static GraphEdge makeImpl(RevCommit parent, RevCommit child, boolean parentIsBoring){
        return new GraphEdge() {
            @Override
            public RevCommit getParent() {
                return parent;
            }
            
            @Override
            public RevCommit getChild() {
                return child;
            }

            @Override
            public boolean parentIsBoring() {
                return parentIsBoring;
            }
        };
    }
}
