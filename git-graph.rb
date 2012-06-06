#!/usr/bin/env ruby

require 'grit'

$commits = {}

def find_children (commit, children)
  commit.parents.each do |c|
    puts "proc #{c} -> #{commit}"
    if children.has_key? c.id
      puts "found #{c}"
      children[c.id].push [commit.id]
    else
      puts "new #{c}"
      children[c.id] = [commit.id]
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
  puts "branch #{b.name}"
#  puts "\"#{b.name}\" -> \"#{b.commit.id.slice 0,7}\";"
#  puts "\"#{b.name}\" [shape=polygon, sides=6, style=filled, color = red];"
#  plot_tree(b.commit)
  find_children(b.commit, children)
end
children.each do |key,value|
  print "#{key} -> ["
  value.each do |v|
    print "#{v}, "
  end
  puts "]"
  #puts "#{key} -> #{value}"
end
#plot_tags(repo)
#puts "}"  
  
