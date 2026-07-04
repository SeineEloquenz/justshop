{ clippy
, rustPlatform
, rust
, ... }:

rustPlatform.buildRustPackage {

  pname = "justshop-backend";
  version = "0.3.0";

  nativeBuildInputs = [
    clippy
  ];

  src = ./.;

  cargoLock = {
    lockFile = ./Cargo.lock;
  };

  RUST_SRC_PATH = "${rust.packages.stable.rustPlatform.rustLibSrc}";
}
