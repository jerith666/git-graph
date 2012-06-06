#!/usr/bin/env ruby

require 'grit'

$commits = {}

def find_children (commit, children)
  commit.parents.each do |c|
    if children.has_key? c.id
      children[c.id].push [commit]
    else
      children[c.id] = [commit]
    end
    find_children(c, children)
  end
end

def plot_tree (commit, children, boring=[], plotted={})
  if plotted.has_key? commit.id
    return
  else
    if is_interesting(commit, children)
      make_node(commit)
      boring = []
    else
      boring += [commit]
    end

    commit.parents.each do |c|
      if is_interesting(c, children)
        if is_interesting(commit, children)
          make_edge(commit, c)
        else
          make_elision(boring, c)
        end
        boring = []
      else
        boring += [c]
      end

      plot_tree(c, children, boring, plotted)
    end
    plotted[commit.id] = true
  end
end

#      print "\""
#      print commit.message.gsub(%r|\n|, "\\n")
#      puts "\" -> \"#{commit.id.slice 0,7}\" [arrowhead=dot, color= lightgray, arrowtail=vee];"
#      print "\""
#      print commit.message.gsub(%r|\n|, "\\n")
#      puts "\" [shape=box, fontname=courier, fontsize = 8, color=lightgray, fontcolor=lightgray];"
#      puts "\"#{commit.id.slice 0,7}\" -> \"#{c.id.slice 0,7}\";"

def is_interesting(commit, children, neighbor=0)
  #merge or branch point
  if commit.parents.length > 1
    return true
  end
  if children[commit.id].length > 1
    return true
  end

  if neighbor <= 0
    return false
  end

  #parent is interesting
  commit.parents.each do |p|
    if is_interesting(p, children, neighbor - 1)
      return true
    end
  end

  #child is interesting
  children[commit.id].each do |c|
    if is_interesting(c, children, neighbor - 1)
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
    make_node(commit)
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

def make_node(commit)
  puts "\"#{commit.id}\" [label=\"#{commit.id.slice 0,7}\"];"
end

def make_edge(c1, c2)
  puts "\"#{c1.id}\" -> \"#{c2.id}\";"
end

def make_elision(boring_commits, interesting_commit)
  puts "\"#{boring_commits.first.id}\" [label=\"boring: #{boring_commits.first.id.slice 0,7}\"];"
  puts "\"#{boring_commits.first.id}\" -> \"#{interesting_commit.id}\";"
end

repo = Grit::Repo.new(ARGV[0]);

#decorated = (repo.branches + repo.remotes + repo.tags).collect{|r| r.commit}
decorated = (repo.branches + repo.tags).collect{|r| r.commit}
d2 = decorated.collect{|c| c.parents}

children = {}
decorated.each do |c|
  find_children(c, children)
end

puts "Digraph Git {"
plotted={}
decorated.each do |c|
  plot_tree(c, children, [], plotted)
  #nodes_for_interesting(c, children)
end
puts "}"

#puts "Digraph F {"
#puts 'ranksep=0.2;'
  #puts "\"#{b.name}\" -> \"#{b.commit.id.slice 0,7}\";"
  #puts "\"#{b.name}\" [shape=polygon, sides=6, style=filled, color = red];"
  #plot_tree(b.commit)
#puts "}"  
  
