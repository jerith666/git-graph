package com.github.jerith666;

import java.util.Collection;
import java.util.Set;

import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.collect.ImmutableSet;

public interface InterestingGraphNode {
    RevCommit getCommit();
    Set<String> getNames();

    public static InterestingGraphNode forNode(RevCommit c, Collection<String> names){
        Set<String> nameSet = ImmutableSet.copyOf(names);
        return new InterestingGraphNode() {
            @Override
            public Set<String> getNames() {
                return nameSet;
            }
            
            @Override
            public RevCommit getCommit() {
                return c;
            }
        };
    }
}
