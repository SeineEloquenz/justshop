{ justshop }:

{ config
, pkgs
, lib
, ... }:

let

  cfg = config.services.justshop;

in {

  options.services.justshop = with lib; {
    enable = mkOption {
      type = types.bool;
      default = false;
      description = "Whether to enable the justshop server";
    };
    user = mkOption {
      type = types.str;
      default = "justshop";
      description = "The system user the server runs under";
    };
    stateDir = mkOption {
      type = types.str;
      default = "/var/lib/justshop";
      description = "Path to server's state directory";
    };
  };

  config = lib.mkIf cfg.enable {

    systemd.services."matrix-spotdl" = {
      description = "justshop | a simple shopping list synchronization server";
      after = [ "network.target" ];
      wantedBy = [ "multi-user.target" ];

      serviceConfig = {
        ExecStart = "${justshop}/bin/justshop ${cfg.stateDir}";
        User = cfg.user;
        Type = "simple";
        KillMode = "process";
        Restart = "on-failure";
      };
    };
  };
}
