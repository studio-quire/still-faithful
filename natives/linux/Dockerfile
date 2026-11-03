# Old Faithful's Linux x64 native build image (docs/natives.md > Linux). Built and run by `python natives/build.py
# linux-x64`. AlmaLinux 8 (glibc 2.28) with GCC 15 from gcc-toolset, the floor and compiler LWJGL's own Linux builds
# use, so our libraries load wherever LWJGL 3.4.3's official ones do. gcc-toolset links what newer GCCs add to
# libstdc++ statically, so OpenAL Soft (C++) needs only the system's libstdc++ of GCC 8.
FROM almalinux:8@sha256:2d0f2eb2a83cc09dbc2eb92fade4dbe8f709628149e81aa00aec70630406603b

# Package names here; the versions this resolved to are in packages-x64.lock beside this file, and the build
# compares the image against it (a different set still builds, but the output bytes may differ).
RUN dnf -y install epel-release && dnf config-manager --set-enabled powertools && dnf -y install \
      gcc-toolset-15-gcc gcc-toolset-15-gcc-c++ gcc-toolset-15-binutils make cmake ninja-build git pkgconf-pkg-config \
      python3.11 python3.11-pip autoconf automake libtool texinfo patchelf \
      alsa-lib-devel pulseaudio-libs-devel jack-audio-connection-kit-devel \
      libX11-devel libXext-devel libXrandr-devel libXcursor-devel libXfixes-devel libXi-devel libXScrnSaver-devel \
      libXtst-devel libxkbcommon-devel wayland-devel wayland-protocols-devel libdrm-devel mesa-libgbm-devel \
      libglvnd-devel dbus-devel ibus-devel systemd-devel libusbx-devel fribidi-devel libthai-devel \
    && dnf clean all

# Meson for PipeWire and libdecor below (AlmaLinux 8's is too old), pinned by hash.
RUN echo "meson==1.12.1 --hash=sha256:930bc7542cbd9f57009e182fd014eba48cf1a0180a7b9006d2d32d8f168d8b02" > /tmp/req.txt \
    && python3.11 -m pip install --no-deps --require-hashes -r /tmp/req.txt && rm /tmp/req.txt

# PipeWire and libdecor: SDL and OpenAL Soft load them at runtime from the player's system, but need their headers,
# pkg-config files and (SDL) the library's soname at build time, newer than AlmaLinux 8 has (SDL wants PipeWire
# 0.3.44+, OpenAL Soft 0.3.23+; libdecor isn't packaged). Built from pinned commits into /opt/deps; never shipped.
RUN source /opt/rh/gcc-toolset-15/enable && mkdir /tmp/pw && cd /tmp/pw && git init -q \
    && git fetch -q --depth 1 https://gitlab.freedesktop.org/pipewire/pipewire.git 4287f31510771fc999f8a58a024c81ca10b8d55f \
    && git checkout -q FETCH_HEAD \
    && meson setup build --prefix=/opt/deps --libdir=lib --buildtype=release --auto-features=disabled \
         -Dsession-managers=[] -Dexamples=disabled -Dtests=disabled -Dman=disabled -Ddocs=disabled \
    && ninja -C build -j4 install && mkdir -p /opt/deps/licenses && cp COPYING /opt/deps/licenses/pipewire-COPYING \
    && cd / && rm -rf /tmp/pw
# libdecor's cairo plugin (its fallback decorations) needs cairo and pango to build.
RUN dnf -y install cairo-devel pango-devel && dnf clean all \
    && source /opt/rh/gcc-toolset-15/enable && mkdir /tmp/ld && cd /tmp/ld && git init -q \
    && git fetch -q --depth 1 https://gitlab.freedesktop.org/libdecor/libdecor.git 38ddc360baf3c8a9b418eb0304b2ac705fa78d9d \
    && git checkout -q FETCH_HEAD \
    && meson setup build --prefix=/opt/deps --libdir=lib --buildtype=release --auto-features=disabled \
         -Ddemo=false -Dinstall_demo=false -Dgtk=disabled -Ddbus=disabled \
    && ninja -C build -j4 install && cp LICENSE /opt/deps/licenses/libdecor-LICENSE && cd / && rm -rf /tmp/ld

# Temurin 25 for LWJGL's JNI headers, as on Windows (local/tools/jdk25), hash from Adoptium's API.
RUN curl -fsSL -o /tmp/jdk.tar.gz https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz \
    && echo "68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb  /tmp/jdk.tar.gz" | sha256sum -c - \
    && mkdir /opt/jdk25 && tar -xzf /tmp/jdk.tar.gz -C /opt/jdk25 --strip-components=1 && rm /tmp/jdk.tar.gz

ENV PKG_CONFIG_PATH=/opt/deps/lib/pkgconfig:/opt/deps/share/pkgconfig
