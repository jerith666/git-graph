package com.github.jerith666;

import static com.google.common.collect.Maps.*;
import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static org.eclipse.jgit.lib.Constants.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
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
    @FunctionalInterface
    private static interface ExceptionalFunction<T,R,E extends Throwable>{
        R apply(T t) throws E;
    }

    private static <T,R,E extends Throwable> CompletionStage<R> applyOrDie(T t, ExceptionalFunction<T,R,E> ef){
        CompletableFuture<R> cf = new CompletableFuture<R>();
        try{
            cf.complete(ef.apply(t));
        }
        catch(Throwable e){
            cf.completeExceptionally(e);
        }
        return cf;
    }

    private static <K,V> BiFunction<Map<? extends K,? extends V>, Map<? extends K,? extends V>, Map<K, V>> mapCollapser(){
        return (m1, m2) -> {
            Map<K,V> m = new LinkedHashMap<K,V>(m1);
            m.putAll(m2);
            return m;
         };
    }

    public static void main(String[] args) throws IOException {
        final Repository repo = new FileRepositoryBuilder().findGitDir(new File(args[0]))
                                                           .setMustExist(true)
                                                           .build();

        Stream.of(R_HEADS, R_REMOTES, R_TAGS)
              .map(prefix -> applyOrDie(prefix, repo.getRefDatabase()::getRefs))
              .reduce((refGetter1, refGetter2) -> refGetter1.thenCombine(refGetter2, mapCollapser()))
              .ifPresent(allRefsGetter -> allRefsGetter.thenAccept(refMap -> processSrcCommits(repo, refMap))
                                                       .exceptionally(t -> { System.out.println("failed with: " + t); return null; } ));
    }

    private static void processSrcCommits(Repository repo, Map<String, Ref> refMap){
        //new LinkedHashMap<>(refMap).entrySet().forEach(System.out::println);
        SetMultimap<RevCommit, RevCommit> children = LinkedHashMultimap.create();

        RevWalk rw = new RevWalk(repo);

        Set<RevCommit> srcCommits = refMap.values().stream()
                                          .map(Ref::getObjectId)
                                          .map(rw::lookupCommit)
                                          .collect(toSet());

        Set<RevCommit> visited = new LinkedHashSet<RevCommit>();//TODO this makes the following stream op stateful
        srcCommits.stream()
                  .map(srcCommit -> applyOrDie(srcCommit, c -> findChildren(c, rw, children, visited)))
                  .reduce((childFinder1, childFinder2) -> childFinder1.thenCombine(childFinder2, (t, u) -> null))
                  .ifPresent(childFinder -> childFinder.thenAccept(nulll -> {;})
                                                       .exceptionally(t -> { System.out.println("failed with: " + t); return null; } ));

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
            RevCommit commit = commits.pop();
            rw.parseCommit(commit.getId());

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

    private static Set<String> readStringsFrom(InputStream is) throws IOException{
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        Set<String> strings = new LinkedHashSet<>();
        String nextLine;
        while((nextLine = br.readLine()) != null){
            strings.add(nextLine);
        }
        return strings;
    }
}
