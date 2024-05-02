{ rustPlatform
, openssl
, ... }:

rustPlatform.buildRustPackage rec {

  pname = "justshop-backend";
  version = "0.1.0";

  src = ./.;

  cargoLock = {
    lockFile = ./Cargo.lock;
  };
}
