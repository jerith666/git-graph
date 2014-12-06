package com.github.jerith666;

import static com.github.jerith666.MonadUtils.*;
import static com.google.common.collect.Maps.*;
import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static org.eclipse.jgit.lib.Constants.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

public final class GitGraph {
    public static void main(String[] args) throws IOException {
        final Repository repo = new FileRepositoryBuilder().findGitDir(new File(args[0]))
                                                           .setMustExist(true)
                                                           .build();

        reduceStages(Stream.of(R_HEADS, R_REMOTES, R_TAGS),
                     Collections.emptyMap(),
                     repo.getRefDatabase()::getRefs,
                     mapCollapser()).thenAccept(refMap -> processSrcCommits(repo, refMap))
                                    .exceptionally(t -> { System.out.println("failed with: " + t); return null; } );
    }

    private static void processSrcCommits(Repository repo, Map<String, Ref> srcCommitNames){
        RevWalk rw = new RevWalk(repo);

        Set<RevCommit> srcCommits = srcCommitNames.values().stream()
                                                  .map(Ref::getObjectId)
                                                  .map(rw::lookupCommit)
                                                  .collect(toSet());

        //TODO these make the following stream op stateful
        SetMultimap<RevCommit, RevCommit> children = LinkedHashMultimap.create();
        Set<RevCommit> visited = new LinkedHashSet<RevCommit>();
        reduceStages(srcCommits.stream(),
                     null,
                     c -> findChildren(c, rw, children, visited),
                     (null1, null2) -> null).exceptionally(t -> { System.out.println("failed with: " + t); return null; } );

        SetMultimap<ObjectId, String> refNames = Multimaps.invertFrom(Multimaps.forMap(transformValues(repo.getAllRefs(),
                                                                                                       Ref::getObjectId)),
                                                                      LinkedHashMultimap.create());

        Set<RevCommit> plotted = new LinkedHashSet<>();
        for(RevCommit srcCommit : srcCommits){
            plotTree(srcCommit, children, plotted, refNames);
        }

        for(Entry<RevCommit, Collection<RevCommit>> childEntry : children.asMap().entrySet()){
            if(refNames.containsKey(childEntry.getKey().getId())){
                System.out.println(refNames.get(childEntry.getKey().getId()) + " = " + childEntry.getKey().getId());
                for(RevCommit c : childEntry.getValue()){
                    System.out.println("  -> " + c);
                }
            }
        }
    }

    private static void plotTree(RevCommit srcCommit, SetMultimap<RevCommit, RevCommit> children, Set<RevCommit> plotted, SetMultimap<ObjectId, String> refNames){

    }

    private static Void findChildren(RevCommit srcCommit, RevWalk rw, SetMultimap<RevCommit, RevCommit> children, Set<RevCommit> visited) throws MissingObjectException, IncorrectObjectTypeException, IOException{
        Deque<RevCommit> commits = new LinkedList<>();
        commits.push(srcCommit);

        while(!commits.isEmpty()){
            RevCommit commit = rw.parseCommit(commits.pop().getId());

            if(visited.contains(commit)){
                continue;
            }
            visited.add(commit);

            asList(commit.getParents()).forEach(parent -> {
                children.put(parent, commit);
                commits.push(parent);
            });
        }

        return null;
    }
}
