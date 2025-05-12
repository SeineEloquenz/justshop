{ rustPlatform
, rust
, openssl
, ... }:

rustPlatform.buildRustPackage rec {

  pname = "justshop-backend";
  version = "0.1.0";

  src = ./.;

  cargoLock = {
    lockFile = ./Cargo.lock;
  };

  RUST_SRC_PATH = "${rust.packages.stable.rustPlatform.rustLibSrc}";
}
