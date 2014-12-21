package com.github.jerith666;

import static com.github.jerith666.MonadUtils.*;
import static com.google.common.collect.Maps.*;
import static com.nurkiewicz.typeof.TypeOf.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static org.apache.commons.lang.StringEscapeUtils.*;
import static org.eclipse.jgit.lib.Constants.*;
import static org.eclipse.jgit.lib.Repository.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
                     mapCollapser()).thenCompose(refMap -> processSrcCommits(repo, refMap))
                                    .thenAccept(System.out::println)
                                    .exceptionally(t -> { System.out.println("failed with: " + t); return null; } );
    }

    private static CompletableFuture<String> processSrcCommits(Repository repo, Map<String, Ref> srcCommitNames){
        RevWalk rw = new RevWalk(repo);

        Set<RevCommit> srcCommits = srcCommitNames.values().stream()
                                                  .map(Ref::getObjectId)
                                                  .map(rw::lookupCommit)
                                                  .collect(toSet());

        return reduceStages(srcCommits.stream(),
                     LinkedHashMultimap.create(),
                     c -> findChildren(c, rw),
                     multimapCollapser()).thenApply(children -> processChildren(repo, srcCommits, children))
                                         .thenApply(entities -> entities.stream()
                                                                        .map(GitGraph::formatEntity)
                                                                        .collect(joining("\n",
                                                                                         "Digraph Git { rankdir=LR;\n",
                                                                                         "\n}")));
    }

    private static Set<GraphEntity> processChildren(Repository repo,
                                                    Set<RevCommit> srcCommits,
                                                    SetMultimap<RevCommit, RevCommit> children){
        SetMultimap<ObjectId, String> refNames = Multimaps.invertFrom(Multimaps.forMap(transformValues(repo.getAllRefs(),
                                                                                                       Ref::getObjectId)),
                                                                      LinkedHashMultimap.create());

        Set<RevCommit> plotted = new LinkedHashSet<>();
        Set<GraphEntity> entities = new LinkedHashSet<GraphEntity>();
        for(RevCommit srcCommit : srcCommits){
            entities.addAll(plotTree(srcCommit, children, plotted, refNames));
        }
        return entities;
    }

    private static Set<GraphEntity> plotTree(RevCommit srcCommit,
                                             SetMultimap<RevCommit, RevCommit> children,
                                             Set<RevCommit> plotted,
                                             SetMultimap<ObjectId, String> refNames){
        Set<GraphEntity> entities = new LinkedHashSet<>();
        List<Pair<RevCommit,List<RevCommit>>> toPlot = new ArrayList<>(singletonList(new Pair<>(srcCommit, new ArrayList<>())));

        while(!toPlot.isEmpty()){
            Pair<RevCommit, List<RevCommit>> nextData = toPlot.get(0);
            RevCommit commit = nextData._t;
            List<RevCommit> boring = nextData._u;
            toPlot = toPlot.subList(1, toPlot.size());

            if(plotted.contains(commit)){
                continue;
            }

            boolean commitInteresting = isInteresting(commit, children, refNames);
            if(commitInteresting){
                entities.add(makeNode(commit, refNames, ""));
                boring = new ArrayList<>();
            }
            else{
                boring.add(commit);
            }

            List<Pair<RevCommit,List<RevCommit>>> parentsToPlot = new ArrayList<>();
            for(RevCommit parent : commit.getParents()){
                if(isInteresting(parent, children, refNames)){
                    if(commitInteresting){
                        entities.add(makeEdge(commit, parent));
                    }
                    else if(!boring.isEmpty()){
                        entities.addAll(makeElision(boring, parent));
                    }
                    boring = new ArrayList<>();//TODO not quite right ... need sub-boring list within inner loop
                }
                else if(commitInteresting){
                    entities.add(makeEdgeToElision(commit, parent));
                }

                parentsToPlot.add(0, new Pair<>(parent, boring));
            }

            toPlot.addAll(0, parentsToPlot);

            if(commit.getParents().length == 0 && !boring.isEmpty()){
                entities.addAll(makeElision(boring, null));
            }

            plotted.add(commit);
        }

        return entities;
    }

    private static GraphEdge makeEdgeToElision(RevCommit child, RevCommit boringParent) {
        return GraphEdge.forBoringParentChild(boringParent, child);
    }

    private static List<GraphEntity> makeElision(List<RevCommit> boringChildren, RevCommit interestingParent) {
        GraphNode boringNode = ElidedGraphNode.forCommits(boringChildren);

        if(interestingParent != null){
            return asList(boringNode,
                          GraphEdge.forParentBoringChild(interestingParent, boringChildren.get(0)));
        }
        else{
            return asList(boringNode);
        }
    }

    private static GraphEdge makeEdge(RevCommit child, RevCommit parent) {
        return GraphEdge.forParentChild(parent, child);
    }

    private static InterestingGraphNode makeNode(RevCommit commit,
                                                 SetMultimap<ObjectId, String> refNames,
                                                 String prefix) {
        return InterestingGraphNode.forNode(commit,
                                            refNames.get(commit.getId()));
    }

    private static String colorForType(String name) {
        if(name.startsWith(Constants.R_TAGS)){
            return "gold3";
        }
        else if(name.startsWith(Constants.R_HEADS)){
            return "forestgreen";
        }
        else{
            return "orange3";
        }
    }

    private static String formatEntity(GraphEntity entity){
        return whenTypeOf(entity)

          .is(GraphEdge.class)
          .thenReturn(GitGraph::formatEdge)

          .is(InterestingGraphNode.class)
          .thenReturn(GitGraph::formatInterestingNode)

          .is(ElidedGraphNode.class)
          .thenReturn(GitGraph::formatElidedGraphNode)

          .get();
    }

    private static String formatElidedGraphNode(ElidedGraphNode enode){
        List<RevCommit> boringChildren = enode.getBoringCommits();
        if(boringChildren.size() == 1){
            return "\"elide." + boringChildren.get(0).getId().name() +
                   "\" [label=<<font>" + escapeXml(boringChildren.get(0).getShortMessage()) +
                   "</font>>" + " style=filled fillcolor=gray75" + "];";
        }

        /* since we're traversing backwards in time by following parent links,
         * the boring_commits list is in reverse chronological order (see issue 2) */
        String rangeids = boringChildren.get(boringChildren.size()-1).abbreviate(6).name() +
                          ".." +
                          boringChildren.get(0).abbreviate(6).name();
        String rangedesc = boringChildren.size() + " commits";
        String fill = "style=filled fillcolor=gray75";
        return "\"elide." + boringChildren.get(0).name() +
                "\" [label=<<font>" + rangedesc +
                "<br/>" + rangeids + "</font>> " + fill + "];";
    }

    private static String formatInterestingNode(InterestingGraphNode inode){
        String label;
        if(inode.getNames().isEmpty()){
            label = escapeXml(inode.getCommit().getShortMessage());
        }
        else{
            label = inode.getNames().stream()
                    .map(name -> "<font color=\"" + colorForType(name) + "\">" + shortenRefName(name) + "</font>")
                    .collect(joining("<br/>"));
        }

        String style = inode.getNames().isEmpty() ? " style=filled fillcolor=gray75" : "";

        return "\"" + inode.getCommit().getId().name() +
                "\" [label=<<font>" + label + "</font>>" + style + "];";
    }

    private static String formatEdge(GraphEdge edge) {
        if(edge.parentIsBoring()){
            return "\"elide." + edge.getParent().getId().name() +
                    "\" -> \"" +
                    edge.getChild().getId().name() + "\"";//TODO weight, color [weight=#{edge_weight(first_boring,commit)} #{color(first_boring)}];");
        }
        else if(edge.childIsBoring()){
            return "\"" + edge.getParent().name() +
                   "\" -> \"elide." +
                   edge.getChild().name() + "\"";//TODO weight, color [weight=#{edge_weight(interesting_commit,boring_commits.first)} #{color(interesting_commit)}];");
        }
        else{
            return "\"" + edge.getChild().getId().name() +
                   "\" -> \"" +
                   edge.getParent().getId().name() + "\"";//TODO weight, color
        }
    }

    private static boolean isInteresting(RevCommit commit,
                                         SetMultimap<RevCommit, RevCommit> children,
                                         SetMultimap<ObjectId, String> refNames) {
        return commit.getParentCount() > 1 ||
               children.get(commit).size() > 1 ||
               refNames.containsKey(commit.getId());
    }

    private static SetMultimap<RevCommit, RevCommit> findChildren(RevCommit srcCommit, RevWalk rw) throws MissingObjectException, IncorrectObjectTypeException, IOException{
        SetMultimap<RevCommit, RevCommit> children = LinkedHashMultimap.create();
        Set<RevCommit> visited = new LinkedHashSet<RevCommit>();

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

        return children;
    }
}
