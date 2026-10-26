# AAtoKombi build toolchain — reproducible "one button" build of both artifacts.
#
# Contains everything the two build.sh scripts need:
#   * JDK 8        — javac that still accepts -source/-target 1.3 (jar/build.sh)
#   * clang + lld  — freestanding armv7 cross-compile + ld.lld (shim/build.sh)
#   * python3 + pyelftools — the ELF injector (shim/inject.py)

# Pin the OS variant. The floating 8-jdk tag can move to a short-lived Ubuntu release whose apt
# repositories disappear, breaking otherwise reproducible PC builds.
FROM eclipse-temurin:8-jdk-jammy

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      clang \
      lld \
      python3 \
      python3-pyelftools \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /work
