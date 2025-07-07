{
  pkgs ? import (fetchTarball "https://github.com/nixos/nixpkgs/archive/fe51d34885f7b5e3e7b59572796e1bcb427eccb1.tar.gz") {},
  nixGL ? import (fetchTarball "https://github.com/nix-community/nixGL/archive/310f8e49a149e4c9ea52f1adf70cdc768ec53f8a.tar.gz") { inherit pkgs; },
}:

pkgs.mkShell {
  name = "hexic";
  JAVA_HOME = pkgs.zulu21;
  extraLibs = pkgs.symlinkJoin {
    name = "extraLibs";
    paths = [
      pkgs.apulse
      pkgs.openal
    ];
  };
  buildInputs = [
    pkgs.bashInteractive
    pkgs.gradle_8
    pkgs.jetbrains.idea-community
    pkgs.zulu21
    nixGL.auto.nixGLDefault
  ];
}
