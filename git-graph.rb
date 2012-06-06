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

def plot_tree (commit)
  if $commits.has_key? commit.id
    return
  else
    $commits[commit.id] = 1
    commit.parents.each do |c|
      print "\""
      print commit.message.gsub(%r|\n|, "\\n")
      puts "\" -> \"#{commit.id.slice 0,7}\" [arrowhead=dot, color= lightgray, arrowtail=vee];"
      print "\""
      print commit.message.gsub(%r|\n|, "\\n")
      puts "\" [shape=box, fontname=courier, fontsize = 8, color=lightgray, fontcolor=lightgray];"
      puts "\"#{commit.id.slice 0,7}\" -> \"#{c.id.slice 0,7}\";"
      plot_tree(c)
    end
  end
end

def is_interesting(commit, children, neighbor=0)
  if commit.parents.length > 1
    return true
  end
  if children[commit.id].length > 1
    return true
  end
  if neighbor <= 0
    return false
  end
  commit.parents.each do |p|
    if is_interesting(p, children, neighbor - 1)
      return true
    end
  end
  children[commit.id].each do |c|
    if is_interesting(c, children, neighbor - 1)
      return true
    end
  end
  return false
end

def show_interesting(commit, children, shown={})
  if shown.has_key? commit.id
    return
  end
  if is_interesting(commit, children)
    puts commit.id
    shown[commit.id] = true
  end
  commit.parents.each do |p|
    show_interesting(p, children, shown)
  end
end

def plot_tags(repo)
  repo.tags.each do |tag|
    puts "\"#{tag.name}\" -> \"#{tag.commit.id.slice 0,7}\";"
    puts "\"#{tag.name}\" [shape=box, style=filled, color = yellow];"
  end
end

#puts "Digraph F {"
#puts 'ranksep=0.2;'
repo = Grit::Repo.new(ARGV[0]);
children = {}
repo.branches.each do |b| 
  find_children(b.commit, children)
end
repo.branches.each do |b| 
  show_interesting(b.commit, children)
  #puts "\"#{b.name}\" -> \"#{b.commit.id.slice 0,7}\";"
  #puts "\"#{b.name}\" [shape=polygon, sides=6, style=filled, color = red];"
  #plot_tree(b.commit)
end
#puts "}"  
  
