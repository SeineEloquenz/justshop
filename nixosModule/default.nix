{ justshop }:

{
  config,
  pkgs,
  lib,
  ...
}:

let

  cfg = config.services.justshop;

  oidcArgs = lib.optionals cfg.oidc.enable [
    "--session-file ${cfg.stateDir}/sessions.json"
    "--oidc-issuer ${cfg.oidc.issuer}"
    "--oidc-client-id ${cfg.oidc.clientId}"
    "--oidc-client-secret-file %d/oidc-client-secret"
    "--oidc-scopes ${lib.concatStringsSep "," cfg.oidc.scopes}"
    "--session-idle-days ${toString cfg.oidc.sessionIdleDays}"
  ];

in
{

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
    listen = mkOption {
      type = types.str;
      default = "127.0.0.1:3030";
      example = "0.0.0.0:3030";
      description = "Address and port the server listens on";
    };
    oidc = {
      enable = mkEnableOption "login through an OpenID Connect provider";
      issuer = mkOption {
        type = types.str;
        example = "https://auth.example.com/oauth2/openid/justshop";
        description = "Issuer URL of the OpenID Connect provider";
      };
      clientId = mkOption {
        type = types.str;
        default = "justshop";
        description = "OAuth2 client ID registered with the provider";
      };
      clientSecretFile = mkOption {
        type = types.path;
        example = "/run/secrets/justshop-oidc";
        description = "File containing the OAuth2 client secret. Do not use a path in the Nix store.";
      };
      scopes = mkOption {
        type = types.listOf types.str;
        default = [
          "openid"
          "profile"
        ];
        description = "Scopes requested at login";
      };
      sessionIdleDays = mkOption {
        type = types.ints.positive;
        default = 90;
        description = "Days a login may go unused before the app has to sign in again";
      };
    };
  };

  config = lib.mkIf cfg.enable {

    systemd.tmpfiles.rules = [
      "d '${cfg.stateDir}' 0750 ${cfg.user} justshop - -"
    ];

    systemd.services."justshop" = {
      description = "justshop | a simple shopping list synchronization server";
      wants = [ "network-online.target" ];
      after = [ "network-online.target" ];
      wantedBy = [ "multi-user.target" ];

      serviceConfig = {
        ExecStart = lib.concatStringsSep " " (
          [ "${justshop}/bin/justshop-backend --listen ${cfg.listen} --state-file ${cfg.stateDir}/state.json" ] ++ oidcArgs
        );
        LoadCredential = lib.mkIf cfg.oidc.enable [ "oidc-client-secret:${cfg.oidc.clientSecretFile}" ];
        User = cfg.user;
        Type = "simple";
        KillMode = "process";
        Restart = "on-failure";
      };
    };

    users.users."${cfg.user}" = {
      isSystemUser = true;
      group = "justshop";
    };

    users.groups.justshop = { };
  };
}
