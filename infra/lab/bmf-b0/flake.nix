{
  description = "Bounded H2 B0 proof of an exact Nix-owned BMF CPU runtime closure";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/50ab793786d9de88ee30ec4e4c24fb4236fc2674";

    bmf = {
      url = "github:BabitMF/bmf/c39146c636c6b2b68ffaf741095ce737bf123254";
      flake = false;
    };
    nlohmannJson = {
      url = "github:nlohmann/json/v3.11.2";
      flake = false;
    };
    stduuid = {
      url = "github:mariusbancila/stduuid/v1.2.3";
      flake = false;
    };
    libuuidCmake = {
      url = "github:gershnik/libuuid-cmake/v2.39.1";
      flake = false;
    };
    utilLinux = {
      url = "github:util-linux/util-linux/v2.39.1";
      flake = false;
    };
    pybind11Source = {
      url = "github:pybind/pybind11/2e0815278cb899b20870a67ca8205996ef47e70f";
      flake = false;
    };
    fmtSource = {
      url = "github:fmtlib/fmt/6ae402fd0bf4e6491dc7b228401d531057dbb094";
      flake = false;
    };
    spdlogSource = {
      url = "github:gabime/spdlog/be14e60d9e8be31735dd9d2d132d8a4cd3482165";
      flake = false;
    };
    dlpackSource = {
      url = "github:dmlc/dlpack/ca4d00ad3e2e0f410eeab3264d21b8a39397f362";
      flake = false;
    };
    backwardCpp = {
      url = "github:bombela/backward-cpp/872350775655ad610f66aea325c319950daa7c95";
      flake = false;
    };
    googletestSource = {
      url = "github:google/googletest/3ff1e8b98a3d1d3abc24a5bacb7651c9b32faedd";
      flake = false;
    };
    benchmarkSource = {
      url = "github:google/benchmark/v1.9.0";
      flake = false;
    };
  };

  outputs = inputs@{ nixpkgs, bmf, nlohmannJson, stduuid, libuuidCmake, utilLinux
    , pybind11Source, fmtSource, spdlogSource, dlpackSource, backwardCpp
    , googletestSource, benchmarkSource, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
      inherit (pkgs) lib;

      python = pkgs.python3;
      numpy = pkgs.python3Packages.numpy;

      bmfVersion = "0.2.0";
      bmfCommit = "c39146c636c6b2b68ffaf741095ce737bf123254";

      ffmpegConfigureFlags = [
        "--disable-static"
        "--enable-shared"
        "--enable-pic"
        "--enable-pthreads"
        "--disable-autodetect"
        "--disable-doc"
        "--disable-debug"
        "--disable-stripping"
        "--disable-network"
        "--disable-gpl"
        "--disable-nonfree"
        "--enable-ffmpeg"
        "--enable-ffprobe"
      ];

      ffmpegConfigureEvidence = pkgs.writeText "bmf-b0-ffmpeg-configure.json"
        (builtins.toJSON {
          schema_version = 1;
          component = "ffmpeg";
          version = "4.4.8";
          source_url = "https://ffmpeg.org/releases/ffmpeg-4.4.8.tar.xz";
          source_sha256_sri = "sha256-xzhIxK4oPZ6u5747J2r/vDVDOASDVVUA0N0sm34cOcM=";
          source_sha256_hex = "c73848c4ae283d9eaee7be3b276affbc3543380483555500d0dd2c9b7e1c39c3";
          configure_flags = ffmpegConfigureFlags;
          configure_prefix_injected_by_nix = true;
          external_codec_libraries = [ ];
          license_posture = "LGPL_CONFIGURATION_CANDIDATE_REQUIRES_CLOSURE_REVIEW";
        });

      ffmpeg448 = pkgs.stdenv.mkDerivation {
        pname = "bmf-b0-ffmpeg";
        version = "4.4.8";
        outputs = [ "out" "dev" ];

        src = pkgs.fetchurl {
          url = "https://ffmpeg.org/releases/ffmpeg-4.4.8.tar.xz";
          hash = "sha256-xzhIxK4oPZ6u5747J2r/vDVDOASDVVUA0N0sm34cOcM=";
        };

        strictDeps = true;
        nativeBuildInputs = with pkgs; [ nasm perl pkg-config removeReferencesTo ];
        configurePlatforms = [ ];
        setOutputFlags = false;
        configureFlags = ffmpegConfigureFlags ++ [
          "--incdir=${placeholder "dev"}/include"
        ];
        enableParallelBuilding = true;

        postConfigure = ''
          remove-references-to -t ${placeholder "dev"} config.h
        '';

        postInstall = ''
          install -Dm444 ${ffmpegConfigureEvidence} \
            "$out/share/bmf-b0/ffmpeg-configure.json"
        '';

        doCheck = false;

        meta = {
          description = "FFmpeg 4.4.8 shared runtime for the bounded BMF B0 CPU proof";
          homepage = "https://ffmpeg.org/";
          license = lib.licenses.lgpl21Plus;
          platforms = [ system ];
        };
      };

      fetchContentFlags = [
        "-DFETCHCONTENT_FULLY_DISCONNECTED=ON"
        "-DFETCHCONTENT_UPDATES_DISCONNECTED=ON"
        "-DFETCHCONTENT_QUIET=OFF"
        "-DFETCHCONTENT_SOURCE_DIR_JSON=${nlohmannJson}"
        "-DFETCHCONTENT_SOURCE_DIR_STDUUID=${stduuid}"
        "-DFETCHCONTENT_SOURCE_DIR_LIBUUID-CMAKE=${libuuidCmake}"
        "-DFETCHCONTENT_SOURCE_DIR_UTIL-LINUX=${utilLinux}"
        "-DFETCHCONTENT_SOURCE_DIR_PYBIND11=${pybind11Source}"
        "-DFETCHCONTENT_SOURCE_DIR_FMT=${fmtSource}"
        "-DFETCHCONTENT_SOURCE_DIR_SPDLOG=${spdlogSource}"
        "-DFETCHCONTENT_SOURCE_DIR_DLPACK=${dlpackSource}"
        "-DFETCHCONTENT_SOURCE_DIR_BACKWARD=${backwardCpp}"
        "-DFETCHCONTENT_SOURCE_DIR_GTEST=${googletestSource}"
        "-DFETCHCONTENT_SOURCE_DIR_BENCHMARK=${benchmarkSource}"
      ];

      bmfFeatureFlags = [
        "-DBMF_LOCAL_DEPENDENCIES=ON"
        "-DBMF_ENABLE_CUDA=OFF"
        "-DBMF_ENABLE_TORCH=OFF"
        "-DBMF_ENABLE_PYTHON=ON"
        "-DBMF_ENABLE_TEST=OFF"
        "-DBMF_ENABLE_FUZZTEST=OFF"
        "-DBMF_ENABLE_BREAKPAD=OFF"
        "-DBMF_ENABLE_GLOG=OFF"
        "-DBMF_ENABLE_JNI=OFF"
        "-DBMF_ENABLE_FFMPEG=ON"
        "-DBMF_BUILD_VERSION=${bmfVersion}"
        "-DBMF_BUILD_COMMIT=${bmfCommit}"
      ];

      dlpackBuildFlags = [
        "-DBUILD_MOCK=OFF"
      ];

      bmfBuildEvidence = pkgs.writeText "bmf-b0-build.json" (builtins.toJSON {
        schema_version = 1;
        proof_scope = "BMF_BUILD_RUNTIME_REPRODUCIBILITY_AND_DEPENDENCY_CLOSURE_PROOF";
        provider_id = "bmf";
        future_stable_provider_implementation_id = "bmf.cpu.v1";
        source = {
          repository = "BabitMF/bmf";
          version_tag_evidence = "v0.2.0_NOT_CRYPTOGRAPHICALLY_ATTESTED";
          commit = bmfCommit;
          earlier_exact_git_tree_evidence = "f072467431ad2d5d571eeda04510b93d25156a3a";
          downloaded_github_archive_sha256_evidence_hex = "c7d9c029c50d93b020b085970cee8825d946e46b1d8b5673b2fc9f3ab393b871";
        };
        feature_flags = bmfFeatureFlags;
        dependency_feature_flags = dlpackBuildFlags;
        fetchcontent = {
          nlohmann_json = "v3.11.2";
          stduuid = "v1.2.3";
          libuuid_cmake = "v2.39.1";
          util_linux = "v2.39.1";
          pybind11 = "2e0815278cb899b20870a67ca8205996ef47e70f";
          fmt = "6ae402fd0bf4e6491dc7b228401d531057dbb094";
          spdlog = "be14e60d9e8be31735dd9d2d132d8a4cd3482165";
          dlpack = "ca4d00ad3e2e0f410eeab3264d21b8a39397f362";
          backward_cpp = "872350775655ad610f66aea325c319950daa7c95";
          googletest = "3ff1e8b98a3d1d3abc24a5bacb7651c9b32faedd";
          benchmark = "v1.9.0";
        };
        old_source_cmake_compatibility = {
          action = "COPY_EXACT_LOCKED_SOURCE_HEADER_BEFORE_CMAKE_GENERATION";
          reason = "CMAKE_3_30_VALIDATES_GENERATED_INTERFACE_SOURCE_BEFORE_CUSTOM_COMMAND";
          phase = "preConfigure";
          source_input = "utilLinux";
          source_version = "v2.39.1";
          source_relative_path = "libuuid/src/uuid.h";
          fetchcontent_base_dir = "\${CMAKE_SOURCE_DIR}/custom_deps";
          destination_relative_path = "custom_deps/libuuid-cmake-build/include/uuid/uuid.h";
          fetch_performed = false;
          header_content_altered = false;
          libuuid_cmake_build_target_preserved = true;
        };
        dlpack_runtime_output_compatibility = {
          action = "RESTORE_BMF_RUNTIME_OUTPUT_DIRECTORY_AFTER_DLPACK_FETCHCONTENT";
          reason = "DLPACK_SETS_RUNTIME_OUTPUT_DIRECTORY_TO_IMMUTABLE_LOCKED_SOURCE";
          phase = "source_patch";
          patch_file = "patches/restore-runtime-output-after-dlpack.patch";
          patched_bmf_path = "bmf/hmp/cmake/dependencies.cmake";
          restore_after = "FetchContent_MakeAvailable(dlpack)";
          restored_value = "\${BMF_ASSEMBLE_ROOT}/bmf/bin";
          dlpack_source_input = "dlpackSource";
          dlpack_source_commit = "ca4d00ad3e2e0f410eeab3264d21b8a39397f362";
          dlpack_source_identity_preserved = true;
          dlpack_source_mutated = false;
          dlpack_targets_altered = false;
          input_graph_changed = false;
        };
        dlpack_mock_executable_compatibility = {
          action = "DISABLE_DLPACK_MOCK_EXECUTABLE";
          reason = "DLPACK_MOCK_TARGET_CAPTURES_DLPACK_SOURCE_RUNTIME_OUTPUT_DIRECTORY_AND_IS_NOT_IN_BMF_RUNTIME_DEPENDENCY_CLOSURE";
          phase = "cmake_configure";
          cmake_flag = "-DBUILD_MOCK=OFF";
          enabled = false;
          target = "mock";
          runtime_dependency_closure_member = false;
          dlpack_source_identity_preserved = true;
          dlpack_source_mutated = false;
          input_graph_changed = false;
        };
        module_manager_path_compatibility = {
          action = "PRESERVE_ABI_SYMBOLS_WITH_FAIL_CLOSED_MODULE_INSTALL_DEFAULT";
          reason = "MODULE_MANAGER_COMMAND_LINKS_BMF_SDK_REPO_ROOT_ABI_SYMBOL";
          phase = "source_patch";
          patch_file = "patches/closure-only-default-search-paths.patch";
          patched_bmf_path = "bmf/sdk/cpp_sdk/src/module_manager.cpp";
          abi_symbols_preserved = [
            "bmf_sdk::s_bmf_repo_root"
            "bmf_sdk::s_bmf_mods_path"
          ];
          abi_symbol_value = "/nix/store/.bmf-disabled-module-install";
          abi_symbol_value_is_store_object = false;
          module_install_default_fail_closed = true;
          module_manager_init_adds_abi_symbol_roots = false;
          module_manager_init_adds_current_working_directory = false;
          ambient_module_search_absent = true;
          ambient_module_search_roots = [ ];
          store_relative_assembled_module_search_preserved = true;
          input_graph_changed = false;
        };
        system_dependencies = {
          python = python.version;
          numpy = numpy.version;
          elfutils_identity = "nixpkgs_locked_package";
        };
        policies = [
          "NIX_OWNS_PROVIDER_RUNTIME_BUILD_DEPENDENCY_CLOSURE_V1"
          "BASE_OS_IS_RUNTIME_COMPATIBILITY_MECHANISM_NOT_DEPENDENCY_AUTHORITY_V1"
          "PROVIDER_IMPLEMENTATION_ID_IS_INDEPENDENT_OF_RUNTIME_PACKAGING_V1"
          "NIX_GENERATED_MINIMAL_OCI_IS_THE_LONG_TERM_RUNTIME_PACKAGING_TARGET_V1"
          "BUILDABLE != DISTRIBUTABLE"
          "RUNTIME_ELIGIBILITY != SEMANTIC_CONFORMANCE"
          "CAN_RUN != OUTPUT_EQUIVALENCE"
        ];
      });

      bmfCpu = pkgs.stdenv.mkDerivation {
        pname = "bmf-b0-cpu-python-runtime";
        version = bmfVersion;
        src = bmf;
        patches = [
          ./patches/closure-only-default-search-paths.patch
          ./patches/restore-runtime-output-after-dlpack.patch
        ];

        strictDeps = true;
        nativeBuildInputs = with pkgs; [
          autoPatchelfHook
          cmake
          ninja
          patchelf
          pkg-config
          python
        ];
        buildInputs = [
          ffmpeg448
          ffmpeg448.dev
          python
          numpy
          pkgs.elfutils
          pkgs.stdenv.cc.cc.lib
        ];
        propagatedBuildInputs = [ numpy ];

        PKG_CONFIG_LIBDIR = "${ffmpeg448.dev}/lib/pkgconfig";

        cmakeFlags = bmfFeatureFlags ++ dlpackBuildFlags ++ fetchContentFlags ++ [
          "-DBMF_PYENV="
          "-DPython_EXECUTABLE=${python}/bin/python3"
          "-DPython_ROOT_DIR=${python}"
          "-DCMAKE_FIND_USE_PACKAGE_REGISTRY=OFF"
          "-DCMAKE_FIND_USE_SYSTEM_PACKAGE_REGISTRY=OFF"
          "-DCMAKE_IGNORE_PATH=/usr;/usr/local;/opt/conda;/opt/tiger"
          "-DCMAKE_BUILD_RPATH_USE_ORIGIN=ON"
        ];

        preConfigure = ''
          # CMake 3.30 validates libuuid-cmake's generated INTERFACE header
          # before its copy command can run. Seed that exact command output
          # from the locked util-linux source without replacing the target.
          install -Dm444 ${utilLinux}/libuuid/src/uuid.h \
            custom_deps/libuuid-cmake-build/include/uuid/uuid.h

          # BMF copies this declared FetchContent tree through a hard-coded path.
          mkdir -p 3rd_party
          ln -s ${nlohmannJson} 3rd_party/json

          # Upstream expects headers and shared libraries below one prefix.
          # This build-only view keeps FFmpeg's dev output out of the runtime.
          mkdir -p .ffmpeg-root
          ln -s ${ffmpeg448.dev}/include .ffmpeg-root/include
          ln -s ${ffmpeg448}/lib .ffmpeg-root/lib
          export FFMPEG_ROOT_PATH="$PWD/.ffmpeg-root"
        '';

        installPhase = ''
          runHook preInstall

          site="$out/${python.sitePackages}"
          mkdir -p "$site/bmf"
          find output/bmf -mindepth 1 -maxdepth 1 ! -name include \
            -exec cp -a {} "$site/bmf/" \;

          distInfo="$site/BabitMF-${bmfVersion}.dist-info"
          mkdir -p "$distInfo"
          printf '%s\n' \
            'Metadata-Version: 2.1' \
            'Name: BabitMF' \
            'Version: ${bmfVersion}' \
            'Requires-Python: >=3.6' \
            'Requires-Dist: numpy (>=1.19.5)' \
            > "$distInfo/METADATA"
          printf '%s\n' 'bmf' > "$distInfo/top_level.txt"

          install -Dm444 ${bmfBuildEvidence} "$out/share/bmf-b0/bmf-build.json"

          addAutoPatchelfSearchPath "$site/bmf/lib"
          while IFS= read -r -d "" moduleDir; do
            addAutoPatchelfSearchPath "$moduleDir"
          done < <(find "$site/bmf" -type d \
            \( -name cpp_modules -o -name python_modules -o -name 'Module_*' \) -print0)

          runHook postInstall
        '';

        postFixup = ''
          runtimeRpath="$out/${python.sitePackages}/bmf/lib:${lib.makeLibraryPath [
            ffmpeg448
            python
            pkgs.elfutils
            pkgs.stdenv.cc.cc.lib
          ]}"
          while IFS= read -r -d "" candidate; do
            if patchelf --print-rpath "$candidate" >/dev/null 2>&1; then
              patchelf --set-rpath "$runtimeRpath" "$candidate"
            fi
          done < <(find "$out/${python.sitePackages}/bmf" -type f -print0)
        '';

        enableParallelBuilding = true;
        doCheck = false;
        dontUseCmakeInstall = true;

        meta = {
          description = "Exact BMF v0.2.0 CPU/Python runtime assembled by Nix";
          homepage = "https://github.com/BabitMF/bmf";
          license = lib.licenses.asl20;
          platforms = [ system ];
        };
      };

      pythonRuntime = python.withPackages (ps: [ ps.numpy ]);
      smokeSource = pkgs.writeText "bmf-b0-smoke.py" (builtins.readFile ./smoke.py);

      smokeCommand = pkgs.writeShellScriptBin "bmf-b0-smoke" ''
        export PATH="${lib.makeBinPath [ ffmpeg448 pythonRuntime ]}"
        export PYTHONPATH="${bmfCpu}/${python.sitePackages}:${pythonRuntime}/${python.sitePackages}"
        export PYTHONNOUSERSITE=1
        export PYTHONDONTWRITEBYTECODE=1
        export LC_ALL=C.UTF-8
        export BMF_B0_EXPECTED_BMF_COMMIT="${bmfCommit}"
        export BMF_B0_EXPECTED_BMF_VERSION="${bmfVersion}"
        export BMF_B0_EXPECTED_FFMPEG_PREFIX="${ffmpeg448}"
        exec "${pythonRuntime}/bin/python3" "${smokeSource}"
      '';

      runtimeClosure = pkgs.buildEnv {
        name = "bmf-b0-runtime-closure";
        paths = [ bmfCpu ffmpeg448 pythonRuntime smokeCommand ];
        pathsToLink = [ "/bin" "/lib" "/${python.sitePackages}" "/share/bmf-b0" ];
        ignoreCollisions = true;
      };

      pureNixImage = pkgs.dockerTools.buildLayeredImage {
        name = "bmf-b0-pure-nix";
        tag = "poc";
        contents = [ runtimeClosure ];
        config = {
          Cmd = [ "${runtimeClosure}/bin/bmf-b0-smoke" ];
          Env = [
            "PATH=${runtimeClosure}/bin"
            "PYTHONNOUSERSITE=1"
            "PYTHONDONTWRITEBYTECODE=1"
            "LC_ALL=C.UTF-8"
          ];
        };
        maxLayers = 64;
      };

      # The manifest digest is fixed. dockerTools.pullImage additionally needs
      # the sha256 of the normalized archive it downloads.
      DEBIAN_BOOKWORM_SLIM_NIX_ARCHIVE_SHA256 = "sha256-ht1xUzva+LEZvjvOyHYyzZvHLLNG3tswP3i52+/Gk44=";

      debianBookwormSlimBase = pkgs.dockerTools.pullImage {
        imageName = "debian";
        imageDigest = "sha256:5ae3c39ebd15e229dcedd5cee596b2497182493d41ff162e824ba13fc1b2b867";
        sha256 = DEBIAN_BOOKWORM_SLIM_NIX_ARCHIVE_SHA256;
        finalImageName = "debian";
        finalImageTag = "bookworm-slim-bmf-b0-pinned";
      };

      debianBookwormSlimClosureImage = pkgs.dockerTools.buildImage {
        name = "bmf-b0-debian-bookworm-slim-compatibility";
        tag = "poc";
        fromImage = debianBookwormSlimBase;
        copyToRoot = runtimeClosure;
        config = {
          Cmd = [ "${runtimeClosure}/bin/bmf-b0-smoke" ];
          Env = [
            "PATH=${runtimeClosure}/bin"
            "PYTHONNOUSERSITE=1"
            "PYTHONDONTWRITEBYTECODE=1"
            "LC_ALL=C.UTF-8"
          ];
        };
      };
    in
    {
      packages.${system} = {
        inherit ffmpeg448 bmfCpu runtimeClosure pureNixImage
          debianBookwormSlimClosureImage;
        default = runtimeClosure;
      };

      apps.${system} = {
        smoke = {
          type = "app";
          program = "${runtimeClosure}/bin/bmf-b0-smoke";
        };
        default = {
          type = "app";
          program = "${runtimeClosure}/bin/bmf-b0-smoke";
        };
      };
    };
}
