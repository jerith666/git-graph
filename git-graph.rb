#!/usr/bin/env ruby

require 'grit'

$commits = {}

def find_children (head_commit, children, visited)
  commits = [head_commit]

  while not commits.empty? do
    if children.size > 0 and children.size % 1000 == 0 then
      puts "#processed child info for #{children.size} commits"
    end

    commit = commits.slice! 0
    if visited.has_key? commit.id then
      next
    end
    visited[commit.id] = true

    commit.parents.each do |c|
      if not children.has_key? c.id
        children[c.id] = []
      end

      if children[c.id].count{|com| com.id == commit.id} == 0
        children[c.id].push commit
      end

      #find_children(c, children)
      commits.push c
    end
  end
end

def plot_tree (head_commit, children, boring, plotted, decorations)
  to_plot = [[head_commit, []]]

  while not to_plot.empty? do
    nextdata = to_plot.slice! 0
    commit = nextdata[0]
    boring = nextdata[1]

    if plotted.has_key? commit.id
      next
    end

    if is_interesting(commit, children, decorations)
      make_node(commit, decorations)
      boring = []
    else
      boring += [commit]
    end

    parents_to_plot = []
    commit.parents.each do |c|
      if is_interesting(c, children, decorations)
        if is_interesting(commit, children, decorations)
          make_edge(commit, c)
        else
          puts "#boring commit #{commit.id}, interesting parent #{c.id}"
          make_elision(boring, c)
        end
        boring = [] #TODO not quite right ... need sub-boring list within inner loop
      else
        if is_interesting(commit, children, decorations)
          make_edge_to_elision(commit, c)
        end
      end

      parents_to_plot = [[c, boring]] + parents_to_plot
      #plot_tree(c, children, boring, plotted, decorations)
    end
    to_plot = parents_to_plot + to_plot

    if commit.parents.length == 0
      make_elision(boring, nil)
    end

    plotted[commit.id] = true
  end
end

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

#def plot_tags(repo)
#  repo.tags.each do |tag|
#    puts "\"#{tag.name}\" -> \"#{tag.commit.id.slice 0,7}\";"
#    puts "\"#{tag.name}\" [shape=box, style=filled, color = yellow];"
#  end
#end
#      print "\""
#      print commit.message.gsub(%r|\n|, "\\n")
#      puts "\" -> \"#{commit.id.slice 0,7}\" [arrowhead=dot, color= lightgray, arrowtail=vee];"
#      print "\""
#      print commit.message.gsub(%r|\n|, "\\n")
#      puts "\" [shape=box, fontname=courier, fontsize = 8, color=lightgray, fontcolor=lightgray];"
#      puts "\"#{commit.id.slice 0,7}\" -> \"#{c.id.slice 0,7}\";"


def id_for(commit)
  commit.id.slice 0,6
end

def fixed(str)
  "<font face=\"Courier\">#{str}</font>"
end

def small(str)
  "<font point-size=\"9\">#{str}</font>"
end

def smaller(str)
  "<font point-size=\"8\">#{str}</font>"
end

def fmt_decor(d)
  case
    when d.is_a?(Grit::Tag) then color = "gold3"
    when d.is_a?(Grit::Head) then color = "forestgreen"
    else color = "orange3"
  end

  "<font color=\"#{color}\">#{d.name}</font>"
end

def color(commit)
  "color=\"##{commit.id.slice 0,6}\""
end

def make_node(commit, decorations, prefix="")
  label = smaller fixed id_for commit
  if decorations.has_key? commit.id
    label = decorations[commit.id].collect{|d| fmt_decor d} * "<br/>"
  end

  puts "\"#{prefix}#{commit.id}\" [label=<<font>#{label}</font>>];"
end

def edge_weight(parent, child)
  1.0 - child.parents.index{|p| p.id == parent.id}.to_f / child.parents.length
end

def make_edge(c1, c2)
  puts "\"#{c2.id}\" -> \"#{c1.id}\" [weight=#{edge_weight(c2, c1)} #{color(c2)}];"
end

def make_edge_to_elision(commit, first_boring)
  puts "\"elide.#{first_boring.id}\" -> \"#{commit.id}\" [weight=#{edge_weight(first_boring,commit)} #{color(first_boring)}];"
end

def make_elision(boring_commits, interesting_commit)
  if boring_commits.length <= 0
    return
  end

  if boring_commits.length == 1
    make_node(boring_commits[0], {}, "elide.")
  else
    rangeids = smaller fixed "#{id_for(boring_commits.first)}..#{id_for(boring_commits.last)}"
    rangedesc = small "#{boring_commits.size} commits"
    puts "\"elide.#{boring_commits.first.id}\" [label=<<font>#{rangedesc}<br/>#{rangeids}</font>>];"
  end

  if not interesting_commit.nil?
    puts "\"#{interesting_commit.id}\" -> \"elide.#{boring_commits.first.id}\" [weight=#{edge_weight(interesting_commit,boring_commits.first)} #{color(interesting_commit)}];"
  end
end

repo = Grit::Repo.new(ARGV[0]);

decorated = (repo.branches + repo.remotes + repo.tags).collect{|r| r.commit}
decorated.reject!{|ref|
begin
  ref.parents
  false
rescue NoMethodError
  puts "#omitting #{ref} that doesn't know its parents"
  true
end}

puts "##{decorated.length} decorations"

children = {}
visited = {}
decorated.each do |c|
  puts "#finding children for #{c.id}"
  find_children(c, children, visited)
end

decorations = {}
repo.branches.each do |b|
  puts "#noting decoration info for branch #{b.name}"
  if not decorations.has_key? b.commit.id
    decorations[b.commit.id] = []
  end
  decorations[b.commit.id].push b
end
repo.remotes.each do |r|
  puts "#noting decoration info for remote branch #{r.name}"
  if not decorations.has_key? r.commit.id
    decorations[r.commit.id] = []
  end
  decorations[r.commit.id].push r
end
repo.tags.each do |t|
  puts "#noting decoration info for tag #{t.name}"
  if not decorations.has_key? t.commit.id
    decorations[t.commit.id] = []
  end
  decorations[t.commit.id].push t
end

puts "Digraph Git { rankdir=LR;"
plotted={}
decorated.each do |c|
  plot_tree(c, children, [], plotted, decorations)
  #nodes_for_interesting(c, children)
end
puts "}"
