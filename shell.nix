{
  pkgs ? import (fetchTarball "https://github.com/nixos/nixpkgs/archive/fe51d34885f7b5e3e7b59572796e1bcb427eccb1.tar.gz") { config.allowUnfree = true; },
  nixGL ? import (fetchTarball "https://github.com/nix-community/nixGL/archive/310f8e49a149e4c9ea52f1adf70cdc768ec53f8a.tar.gz") { inherit pkgs; },
}:

pkgs.mkShell {
  name = "hexic";
  JAVA_HOME = pkgs.zulu21;
  LD_LIBRARY_PATH = if pkgs.system == "x86_64-linux" then pkgs.symlinkJoin {
    name = "extraLibs";
    paths = [
      "${pkgs.apulse}/lib/apulse"
      "${pkgs.libGL}/lib"
      "${pkgs.libgcc.lib}/lib"
      "${pkgs.openal}/lib"
      "${pkgs.xorg.libX11}/lib"
    ];
  } else null;
  buildInputs = [
    pkgs.aseprite
    pkgs.bashInteractive
    pkgs.go
    pkgs.gradle_8
    pkgs.imagemagick
    pkgs.jujutsu
    pkgs.zulu21
  ] ++ (if pkgs.system == "x86_64-linux" then [
    nixGL.auto.nixGLDefault
    pkgs.jetbrains.idea-community
  ] else []);
}
