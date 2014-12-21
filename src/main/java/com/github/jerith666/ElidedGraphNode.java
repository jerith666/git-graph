package com.github.jerith666;

import java.util.List;

import org.eclipse.jgit.revwalk.RevCommit;

public interface ElidedGraphNode extends GraphNode {
    List<RevCommit> getBoringCommits();

    static ElidedGraphNode forCommits(List<RevCommit> boringCommits){
        return () -> boringCommits;
    }
}
