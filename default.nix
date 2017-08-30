with import <nixpkgs> {};

# { lib, bundlerEnv, ruby }:

let
  version = "0.0.1";

  env = bundlerEnv {
    name = "git-graph-${version}";

    inherit ruby;
    # expects Gemfile, Gemfile.lock and gemset.nix in the same directory
    gemdir = ./.;
  };

in

  stdenv.mkDerivation {
    name = "git-graph-${version}";

    buildInputs = [ env.wrappedRuby ];

    script = ./git-graph.rb;

    buildCommand = ''
      mkdir -p $out/bin
      install -D -m755 $script $out/bin/git-graph
      patchShebangs $out/bin/git-graph
    '';
  }
