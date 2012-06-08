#!/usr/bin/env ruby

require 'grit'

$commits = {}

def find_children (commit, children)
  commit.parents.each do |c|
    if children.has_key? c.id
      if children[c.id].count{|com| com.id == commit.id} == 0
      #if not children[c.id].include? commit
        children[c.id].push [commit]
      end
    else
      children[c.id] = [commit]
    end
    find_children(c, children)
  end
end

def plot_tree (commit, children, boring, plotted, decorations)
  if plotted.has_key? commit.id
    return
  end

  if is_interesting(commit, children, decorations)
    make_node(commit, decorations)
    boring = []
  else
    boring += [commit]
  end

  commit.parents.each do |c|
    if is_interesting(c, children, decorations)
      if is_interesting(commit, children, decorations)
        make_edge(commit, c)
      else
        make_elision(boring, c)
      end
      boring = []
    else
      if is_interesting(commit, children, decorations)
        make_edge_to_elision(commit, c)
      end
    end

    plot_tree(c, children, boring, plotted, decorations)
  end
  if commit.parents.length == 0
    make_elision(boring, nil)
  end
  plotted[commit.id] = true
end

#      print "\""
#      print commit.message.gsub(%r|\n|, "\\n")
#      puts "\" -> \"#{commit.id.slice 0,7}\" [arrowhead=dot, color= lightgray, arrowtail=vee];"
#      print "\""
#      print commit.message.gsub(%r|\n|, "\\n")
#      puts "\" [shape=box, fontname=courier, fontsize = 8, color=lightgray, fontcolor=lightgray];"
#      puts "\"#{commit.id.slice 0,7}\" -> \"#{c.id.slice 0,7}\";"

def is_interesting(commit, children, decorations, neighbor=0)
  #merge or branch point
  if commit.parents.length > 1
    #puts "int true: #{commit.id} mult parents"
    return true
  end
  if children.include? commit.id and children[commit.id].length > 1
    #puts "int true: #{commit.id} mult children"
    #puts "int true: #{commit.id} child 1: #{children[commit.id][0]}"
    #puts "int true: #{commit.id} child 2: #{children[commit.id][2]}"
    return true
  end

  #decorated
  if decorations.has_key? commit.id
    #puts "int true: #{commit.id} decorated: #{decorations[commit.id]}"
    return true
  end

  if neighbor <= 0
    return false
  end

  #parent is interesting
  commit.parents.each do |p|
    if is_interesting(p, children, decorations, neighbor - 1)
      return true
    end
  end

  #child is interesting
  children[commit.id].each do |c|
    if is_interesting(c, children, decorations, neighbor - 1)
      return true
    end
  end

  return false
end

def nodes_for_interesting(commit, children, shown={})
  if shown.has_key? commit.id
    return
  end
  if is_interesting(commit, children)
    make_node(commit, {})
    shown[commit.id] = true
  end
  commit.parents.each do |p|
    nodes_for_interesting(p, children, shown)
  end
end

def plot_tags(repo)
  repo.tags.each do |tag|
    puts "\"#{tag.name}\" -> \"#{tag.commit.id.slice 0,7}\";"
    puts "\"#{tag.name}\" [shape=box, style=filled, color = yellow];"
  end
end

def make_node(commit, decorations)
  label = commit.id.slice 0,7
  if decorations.has_key? commit.id
    label = decorations[commit.id][0].name
  end

  puts "\"#{commit.id}\" [label=\"#{label}\"];"
end

def make_edge(c1, c2)
  puts "\"#{c1.id}\" -> \"#{c2.id}\";"
end

def make_edge_to_elision(commit, first_boring)
  puts "\"#{commit.id}\" -> \"elide.#{first_boring.id}\";"
end

def make_elision(boring_commits, interesting_commit)
  if boring_commits.length <= 0
    return
  end

  puts "\"elide.#{boring_commits.first.id}\" [label=\"#{boring_commits.size} commits\\n(#{boring_commits.first.id.slice 0,7} .. #{boring_commits.last.id.slice 0,7})\"];"
  if not interesting_commit.nil?
    puts "\"elide.#{boring_commits.first.id}\" -> \"#{interesting_commit.id}\";"
  end
end

repo = Grit::Repo.new(ARGV[0]);

#decorated = (repo.branches + repo.remotes + repo.tags).collect{|r| r.commit}
#d2 = decorated.collect{|c| c.parents} #TODO still necessary?
decorated = (repo.branches + repo.tags).collect{|r| r.commit}

children = {}
decorated.each do |c|
  find_children(c, children)
end

decorations = {}
repo.branches.each do |b|
  if not decorations.has_key? b.commit.id
    decorations[b.commit.id] = []
  end
  decorations[b.commit.id].push [b]
end
repo.tags.each do |t|
  if not decorations.has_key? t.commit.id
    decorations[t.commit.id] = []
  end
  decorations[t.commit.id].push [t]
end

puts "Digraph Git {"
plotted={}
decorated.each do |c|
  plot_tree(c, children, [], plotted, decorations)
  #nodes_for_interesting(c, children)
end
puts "}"
