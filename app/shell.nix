{ pkgs
, ... }:

pkgs.mkShell {
  buildInputs = [ pkgs.android-studio ];  
}
