{
  description = "Media Platform development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            # JDK 25 (or latest available)
            jdk25

            # Gradle (for wrapper use, not as primary build)
            gradle

            # OpenTofu (IaC tool)
            opentofu

            # Docker client (for interacting with Docker daemon)
            docker

            # Additional utilities commonly needed
            git
            curl
            jq
          ];

          shellHook = ''
            echo "Media Platform development environment loaded"
            echo "Java version: $(java -version 2>&1 | head -1)"
            echo "Gradle version: $(gradle --version 2>&1 | head -1)"
            echo ""
            echo "Note: Gradle Wrapper (./gradlew) remains the authoritative build entry point."
            echo "Use './gradlew' instead of 'gradle' for builds."
          '';
        };
      }
    );
}
