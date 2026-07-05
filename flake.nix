{
  description = "justshop is a simple shopping list app";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];

      forAllSystems =
        f:
        builtins.listToAttrs (
          map (system: {
            name = system;
            value = f system;
          }) systems
        );
    in
    {
      packages = forAllSystems (
        system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config.allowUnfree = true;
          };
          backend = pkgs.callPackage ./backend/default.nix { };
        in
        {
          inherit backend;
          default = backend;
        }
      );

      devShells = forAllSystems (
        system:
        import ./shell.nix {
          inherit system nixpkgs;
        }
      );

      nixosModules.default = import ./nixosModule { justshop = self.packages.x86_64-linux.backend; };
    };
}
