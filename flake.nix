{
  description = "justshop is a simple shopping list app";

  outputs = { self, nixpkgs }:
  let

    system = "x86_64-linux";
    pkgs = import nixpkgs {
      inherit system;
      config = { allowUnfree = true; };
    };

  in {
    packages.${system} = {
      backend = pkgs.callPackage ./backend/default.nix {};
    };

    devShells.${system} = {
      app = pkgs.callPackage ./app {};
    };

    nixosModules.default = import ./nixosModule;
  };
}
