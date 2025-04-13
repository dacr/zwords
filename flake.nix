{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.11";
    utils.url = "github:numtide/flake-utils";
    sbt.url = "github:zaninime/sbt-derivation";
    sbt.inputs.nixpkgs.follows = "nixpkgs";
  };

  outputs = { self, nixpkgs, utils, sbt }:
  utils.lib.eachDefaultSystem (system:
  let
    pkgs = import nixpkgs { inherit system; };
  in {
    # ---------------------------------------------------------------------------
    # nix develop
    devShells.default = pkgs.mkShell {
      buildInputs = [pkgs.sbt pkgs.metals pkgs.jdk21 pkgs.hello];
    };

    # ---------------------------------------------------------------------------
    # nix build
    packages.default = sbt.mkSbtDerivation.${system} {
      pname = "nix-zwords";
      version = builtins.elemAt (builtins.match ''[^"]+"(.*)".*'' (builtins.readFile ./version.sbt)) 0;
      depsSha256 = "sha256-pXwtO5bh/rB0MZggsg59x9W0ExGO0D7yxtcZqZLTX0U=";

      src = ./.;

      buildInputs = [pkgs.sbt pkgs.jdk21_headless pkgs.makeWrapper];

      buildPhase = "sbt Universal/packageZipTarball";

      installPhase = ''
          mkdir -p $out
          tar xf webapi/target/universal/zwords-webapi.tgz --directory $out
          makeWrapper $out/bin/zwords-webapi $out/bin/nix-zwords \
            --set PATH ${pkgs.lib.makeBinPath [
              pkgs.gnused
              pkgs.gawk
              pkgs.coreutils
              pkgs.bash
              pkgs.jdk21_headless
            ]}
      '';
    };

    # ---------------------------------------------------------------------------
    # simple nixos services integration
    nixosModules.default = { config, pkgs, lib, ... }: {
      options = {
        services.zwords = {
          enable = lib.mkEnableOption "zwords";
          user = lib.mkOption {
            type = lib.types.str;
            description = "User name that will run the zwords service";
          };
          ip = lib.mkOption {
            type = lib.types.str;
            description = "Listening network interface - 0.0.0.0 for all interfaces";
            default = "127.0.0.1";
          };
          port = lib.mkOption {
            type = lib.types.int;
            description = "Service zwords listing port";
            default = 8080;
          };
          datastore = lib.mkOption {
            type = lib.types.str;
            description = "where zwords stores its data";
            default = "/data/zwords";
          };
          clientResourcesPath = lib.mkOption {
            type = lib.types.str;
            default = "user-interface";
          };
          frAffFilename = lib.mkOption {
            type = lib.types.str;
            default = "dicos/fr-classique.aff";
          };
          frDicFilename = lib.mkOption {
            type = lib.types.str;
            default = "dicos/fr-classique.dic";
          };
          enAffFilename = lib.mkOption {
            type = lib.types.str;
            default = "dicos/en-classique.aff";
          };
          enDicFilename = lib.mkOption {
            type = lib.types.str;
            default = "dicos/en-classique.dic";
          };
          frSubsetFilename = lib.mkOption {
            type = lib.types.str;
            default = "dicos/subset-french-10000.txt";
          };
          enSubsetFilename = lib.mkOption {
            type = lib.types.str;
            default = "dicos/subset-english-10000.txt";
          };
          lmdbPath = lib.mkOption {
            type = lib.types.str;
            default = "lmdb-data/";
          };
          lmdbName = lib.mkOption {
            type = lib.types.str;
            default = "zwords";
          };
        };
      };
      config = lib.mkIf config.services.zwords.enable {
        systemd.tmpfiles.rules = [
              "d ${config.services.zwords.datastore} 0750 ${config.services.zwords.user} ${config.services.zwords.user} -"
        ];
        systemd.services.zwords = {
          description = "ZWords wordle like game";
          environment = {
            ZWORDS_LISTEN_IP             = config.services.zwords.ip;
            ZWORDS_LISTEN_PORT           = (toString config.services.zwords.port);
            ZWORDS_CLIENT_RESOURCES_PATH = config.services.zwords.clientResourcesPath;
            ZWORDS_STORE_PATH            = config.services.zwords.datastore;
            ZWORDS_FR_AFF_FILENAME       = (lib.strings.concatStrings [config.services.zwords.datastore "/" config.services.zwords.frAffFilename]);
            ZWORDS_FR_DIC_FILENAME       = (lib.strings.concatStrings [config.services.zwords.datastore "/" config.services.zwords.frDicFilename]);
            ZWORDS_EN_AFF_FILENAME       = (lib.strings.concatStrings [config.services.zwords.datastore "/" config.services.zwords.enAffFilename]);
            ZWORDS_EN_DIC_FILENAME       = (lib.strings.concatStrings [config.services.zwords.datastore "/" config.services.zwords.enDicFilename]);
            ZWORDS_FR_SUBSET_FILENAME    = (lib.strings.concatStrings [config.services.zwords.datastore "/" config.services.zwords.frSubsetFilename]);
            ZWORDS_EN_SUBSET_FILENAME    = (lib.strings.concatStrings [config.services.zwords.datastore "/" config.services.zwords.enSubsetFilename]);
            ZWORDS_LMDB_PATH             = (lib.strings.concatStrings [config.services.zwords.datastore "/" config.services.zwords.lmdbPath]);
            ZWORDS_LMDB_NAME             = config.services.zwords.lmdbName;
          };
          serviceConfig = {
            ExecStart = "${self.packages.${pkgs.system}.default}/bin/nix-zwords";
            User = config.services.zwords.user;
            Restart = "on-failure";
          };
          wantedBy = [ "multi-user.target" ];
        };
      };
    };
    # ---------------------------------------------------------------------------
  });
}
