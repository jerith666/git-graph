package com.github.jerith666;

import static org.eclipse.jgit.lib.Constants.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.google.common.base.Function;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
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
        
        ExceptionalFunction<String, Map<String, Ref>, IOException> getRefsByPrefix = repo.getRefDatabase()::getRefs;
        //BiFunction<T, U, R> x = (m1, m2) -> {LinkedHashMap<String, Ref> m = new LinkedHashMap<>(m1); m.putAll(m2); return m;}
        applyOrDie(R_HEADS, getRefsByPrefix).thenCombine(applyOrDie(R_REMOTES, getRefsByPrefix).thenCombine(applyOrDie(R_TAGS, getRefsByPrefix),
                                                                                                            mapCollapser()),
                                                         mapCollapser())
                                            .thenAccept(refMap -> new LinkedHashMap<>(refMap).entrySet().forEach(System.out::println));
        System.exit(0);
//        Stream.of(R_HEADS, R_REMOTES, R_TAGS)
//              .flatMap(prefix -> repo.getRefDatabase().getRefs(prefix).entrySet().stream());
        repo.getRefDatabase().getRefs(Constants.R_HEADS);
        repo.getRefDatabase().getRefs(Constants.R_HEADS);
        repo.getRefDatabase().getRefs(Constants.R_HEADS);
        new LinkedHashMap<>(repo.getAllRefs()).entrySet().forEach(System.out::println);
        
        Set<String> graphSrcCommits;
        if(args.length > 1){
            graphSrcCommits = readStringsFrom(new FileInputStream(new File(args[1])));
        }
        else{
            graphSrcCommits = readStringsFrom(System.in);
        }

        SetMultimap<RevCommit, RevCommit> children = LinkedHashMultimap.create();

        final RevWalk rw = new RevWalk(repo);
        Set<RevCommit> srcCommits = new LinkedHashSet<>();
        for(String commitStr : graphSrcCommits){
            srcCommits.add(rw.parseCommit(repo.resolve(commitStr)));
        }
        //		Set<RevCommit> srcCommits = ImmutableSet.copyOf(Lists.transform(ImmutableList.copyOf(graphSrcCommits), new Function<String,RevCommit>(){
        //			public RevCommit apply(String commitStr) {
        //				return rw.parseCommit(repo.resolve(commitStr));
        //			}}));

        Set<RevCommit> visited = new LinkedHashSet<RevCommit>();
        for(RevCommit srcCommit : srcCommits){
            findChildren(srcCommit, rw, children, visited);
        }

        SetMultimap<ObjectId, String> refNames = Multimaps.invertFrom(Multimaps.forMap(Maps.transformValues(repo.getAllRefs(),
                                                                                                            (Function<Ref, ObjectId>) ref -> ref.getObjectId())),
                                                                      LinkedHashMultimap.<ObjectId,String>create());

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

    private static void findChildren(RevCommit srcCommit, RevWalk rw, SetMultimap<RevCommit, RevCommit> children, Set<RevCommit> visited) throws MissingObjectException, IncorrectObjectTypeException, IOException{
        Deque<RevCommit> commits = new LinkedList<>();
        commits.push(srcCommit);

        while(!commits.isEmpty()){
            RevCommit commit = commits.pop();
            rw.parseCommit(commit.getId());

            if(visited.contains(commit)){
                continue;
            }
            visited.add(commit);

            for(RevCommit parent : commit.getParents()){
                children.put(parent, commit);
                commits.push(parent);
            }
        }
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
