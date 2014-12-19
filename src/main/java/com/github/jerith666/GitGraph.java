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
        System.out.println("Digraph Git { rankdir=LR;");
        final Repository repo = new FileRepositoryBuilder().findGitDir(new File(args[0]))
                                                           .setMustExist(true)
                                                           .build();

        reduceStages(Stream.of(R_HEADS, R_REMOTES, R_TAGS),
                     Collections.emptyMap(),
                     repo.getRefDatabase()::getRefs,
                     mapCollapser()).thenAccept(refMap -> processSrcCommits(repo, refMap))
                                    .exceptionally(t -> { System.out.println("failed with: " + t); return null; } );

        System.out.println("}");
    }

    private static void processSrcCommits(Repository repo, Map<String, Ref> srcCommitNames){
        RevWalk rw = new RevWalk(repo);

        Set<RevCommit> srcCommits = srcCommitNames.values().stream()
                                                  .map(Ref::getObjectId)
                                                  .map(rw::lookupCommit)
                                                  .collect(toSet());

        reduceStages(srcCommits.stream(),
                     LinkedHashMultimap.create(),
                     c -> findChildren(c, rw),
                     multimapCollapser()).thenAccept(children -> processChildren(repo, srcCommits, children))
                                         .exceptionally(t -> { System.out.println("failed with: " + t); return null; } );
    }

    private static void processChildren(Repository repo, Set<RevCommit> srcCommits, SetMultimap<RevCommit, RevCommit> children){
        SetMultimap<ObjectId, String> refNames = Multimaps.invertFrom(Multimaps.forMap(transformValues(repo.getAllRefs(),
                                                                                                       Ref::getObjectId)),
                                                                      LinkedHashMultimap.create());

        Set<RevCommit> plotted = new LinkedHashSet<>();
        for(RevCommit srcCommit : srcCommits){
            plotTree(srcCommit, children, plotted, refNames);
        }
    }

    private static void plotTree(RevCommit srcCommit,
                                 SetMultimap<RevCommit, RevCommit> children,
                                 Set<RevCommit> plotted,
                                 SetMultimap<ObjectId, String> refNames){
        List<Pair<RevCommit,List<RevCommit>>> toPlot = new ArrayList<>(singletonList(new Pair<>(srcCommit, new ArrayList<>())));

        while(!toPlot.isEmpty()){
            Pair<RevCommit, List<RevCommit>> nextData = toPlot.get(0);
            RevCommit commit = nextData._t;
            List<RevCommit> boring = nextData._u;
            toPlot = toPlot.subList(1, toPlot.size());

            if(plotted.contains(commit)){
                continue;
            }

            if(isInteresting(commit, children, refNames)){
                makeNode(commit, refNames, "");
                boring = new ArrayList<>();
            }
            else{
                boring.add(commit);
            }

            List<Pair<RevCommit,List<RevCommit>>> parentsToPlot = new ArrayList<>();
            for(RevCommit c : commit.getParents()){
                if(isInteresting(c, children, refNames)){
                    if(isInteresting(commit, children, refNames)){
                        makeEdge(commit, c);
                    }
                    else{
                        makeElision(boring, c);
                    }
                    boring = new ArrayList<>();//TODO not quite right ... need sub-boring list within inner loop
                }
                else if(isInteresting(commit, children, refNames)){
                    makeEdgeToElision(commit, c);
                }

                parentsToPlot.add(0, new Pair<>(c, boring));
            }

            toPlot.addAll(0, parentsToPlot);

            if(commit.getParents().length == 0){
                makeElision(boring, null);
            }

            plotted.add(commit);
        }
    }

    private static void makeEdgeToElision(RevCommit commit, RevCommit firstBoring) {
        System.out.println("\"elide." + firstBoring.name() + "\" -> \"" + commit.name() + "\"");//TODO weight, color [weight=#{edge_weight(first_boring,commit)} #{color(first_boring)}];");
    }

    private static void makeElision(List<RevCommit> boring, RevCommit c) {
        if(boring.size() == 0){
            return;
        }

        if(boring.size() == 1){
            makeNode(boring.get(0), Multimaps.forMap(emptyMap()), "elide.");
        }
        else{
            /*since we're traversing backwards in time by following parent links,
              the boring_commits list is in reverse chronological order
              (see issue 2)*/
            String rangeids = boring.get(boring.size()-1).abbreviate(6).name() +
                              ".." +
                              boring.get(0).abbreviate(6).name();
            String rangedesc = boring.size() + " commits";
            String fill = "style=filled fillcolor=gray75";
            System.out.println("\"elide." + boring.get(0).name() +
                               "\" [label=<<font>" + rangedesc +
                               "<br/>" + rangeids + "</font>> " + fill + "];");
        }

        if(c != null){
            System.out.println("\"" + c.name() + "\" -> \"elide." + boring.get(0).name() + "\"");//TODO weight, color [weight=#{edge_weight(interesting_commit,boring_commits.first)} #{color(interesting_commit)}];");
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

    private static String outputGraph(Set<GraphEntity> graphData){
        return graphData.stream()
                        .map(GitGraph::outputEntity)
                        .collect(joining("\n"));
    }

    private static String outputEntity(GraphEntity entity){
        return whenTypeOf(entity)

          .is(GraphEdge.class)
          .thenReturn(edge -> "\"" + edge.getChild().getId().name() +
                              "\" -> \"" +
                              edge.getParent().getId().name() + "\"")//TODO weight, color)

          .is(InterestingGraphNode.class)
          .thenReturn(inode -> {
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

              return "\"" + prefix + inode.getCommit().getId().name() +
                      "\" [label=<<font>" + label + "</font>>" + style + "];";
          })

          .is(ElidedGraphNode.class)
          .thenReturn(enode -> "")

          .get();
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
