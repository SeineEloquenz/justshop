{
  system,
  nixpkgs,
}:

let
  buildToolsVersion = "37.0.0";

  buildToolsVersions = [ buildToolsVersion ];
  platformVersions = [ "37" ];

  pkgs = import nixpkgs {
    inherit system;

    config.allowUnfree = true;
    config.android_sdk.accept_license = true;
  };

  jdk = pkgs.jdk21;

  androidSdk = pkgs.androidenv.composeAndroidPackages {
    inherit buildToolsVersions platformVersions;
  };
in
{
  default = pkgs.mkShell {
    packages = [
      # Android app (app/)
      jdk
      pkgs.gradle
      androidSdk.androidsdk

      # Rust backend (backend/)
      pkgs.cargo
      pkgs.rustc
      pkgs.clippy
      pkgs.rustfmt
      pkgs.rust-analyzer
    ];

    env = {
      ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";

      JAVA_HOME = "${jdk}";

      # aapt2 bundled in the AGP Maven artifact is a generic-Linux binary
      # that NixOS cannot run. Override it with the Nix-patched copy.
      GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk.androidsdk}/libexec/android-sdk/build-tools/${buildToolsVersion}/aapt2";

      RUST_SRC_PATH = "${pkgs.rustPlatform.rustLibSrc}";

      LOCAL_COMPOSE_KIT = "../../compose-kit";
    };

    shellHook = ''
      cat > "$PWD/app/local.properties" <<EOF
      sdk.dir=${androidSdk.androidsdk}/libexec/android-sdk
      EOF
    '';
  };
}
