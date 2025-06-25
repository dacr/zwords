# Project guidelines

- User interfaces requirements are defined in the [user interface requirements](../docs/requirements.md) document.
- Ignore the directory `static-user-interfaces-prompt`
- Any tools can be installed and executed using the nix-shell approach :
    - example : `nix-shell -p imagemagick --command "convert --version"`
- A `Makefile` is provided for shortcuts to common tasks
    - build : `make build`
    - run : `make run`
    - test : `make test`
      - `sbt test` can also be used 
- NIX development environment is defined in the `flake.nix` file
    - to enter the development environment run : `nix develop`
- All requirement env variables are defined in the local private .envrc file (direnv)