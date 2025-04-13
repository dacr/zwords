all:

nix-update:
	nix flake update

nix-build:
	# do not use the sandbox as sbt-derivation requires to download dependencies
	nix build --option sandbox false .