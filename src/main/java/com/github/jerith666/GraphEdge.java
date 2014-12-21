package com.github.jerith666;

import org.eclipse.jgit.revwalk.RevCommit;

public interface GraphEdge extends GraphEntity {
    RevCommit getParent();
    RevCommit getChild();
    boolean parentIsBoring();
    boolean childIsBoring();

    static GraphEdge forParentChild(RevCommit parent, RevCommit child){
        return makeImpl(parent, child, false, false);
    }

    static GraphEdge forBoringParentChild(RevCommit parent, RevCommit child){
        return makeImpl(parent, child, true, false);
    }

    static GraphEdge forParentBoringChild(RevCommit parent, RevCommit child){
        return makeImpl(parent, child, false, true);
    }

    static GraphEdge makeImpl(RevCommit parent, RevCommit child, boolean parentIsBoring, boolean childIsBoring){
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

            @Override
            public boolean childIsBoring() {
                return childIsBoring;
            }
        };
    }
}
