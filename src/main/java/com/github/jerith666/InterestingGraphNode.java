package com.github.jerith666;

import java.util.Set;

import org.eclipse.jgit.revwalk.RevCommit;

public interface InterestingGraphNode {
    RevCommit getCommit();
    Set<String> getNames();
}
