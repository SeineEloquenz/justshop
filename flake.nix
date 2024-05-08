{
  description = "justshop is a simple shopping list app";

  outputs = { self, nixpkgs }:
  let

    system = "x86_64-linux";
    pkgs = import nixpkgs {
      inherit system;
      config = { allowUnfree = true; };
    };

    backend = pkgs.callPackage ./backend/default.nix {};

  in {
    packages.${system} = {
      inherit backend;
    };

    devShells.${system} = {
      app = pkgs.callPackage ./app {};
    };

    nixosModules.default = pkgs.callPackage ./nixosModule { justshop = backend; };
  };
}
