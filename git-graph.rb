#!/usr/bin/env ruby

require 'grit'

$commits = {}

def plot_tree (commit)
  if $commits.has_key? commit.id
    return
  else
    $commits[commit.id] = 1
    commit.parents.each do |c|
      puts "\"#{commit.message}\" -> \"#{commit.id.slice 0,7}\" [arrowhead=dot, color= lightgray, arrowtail=vee];"
      puts "\"#{commit.message}\" [shape=box, fontname=courier, fontsize = 8, color=lightgray, fontcolor=lightgray];"
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

puts "Digraph F {"
puts 'ranksep=0.2;'
repo = Grit::Repo.new(ARGV[0]);
repo.branches.each do |b| 
  puts "\"#{b.name}\" -> \"#{b.commit.id.slice 0,7}\";"
  puts "\"#{b.name}\" [shape=polygon, sides=6, style=filled, color = red];"
  plot_tree(b.commit)
end
plot_tags(repo)
puts "}"  
  
