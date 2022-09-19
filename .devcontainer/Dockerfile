# See here for image contents: https://github.com/microsoft/vscode-dev-containers/tree/v0.245.2/containers/ubuntu/.devcontainer/base.Dockerfile

# [Choice] Ubuntu version (use ubuntu-22.04 or ubuntu-18.04 on local arm64/Apple Silicon): ubuntu-22.04, ubuntu-20.04, ubuntu-18.04
ARG VARIANT="jammy"
FROM mcr.microsoft.com/vscode/devcontainers/base:0-${VARIANT}

RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
     && apt-get -y install --no-install-recommends \
     bash-completion

SHELL ["/bin/bash", "-c"]
USER vscode

#RUN curl -s "https://get.sdkman.io" | bash
#RUN . $HOME/.sdkman/bin/sdkman-init.sh && \
#    sdk selfupdate force && \
#    sdk install java $(sdk list java | grep -oE "\S+r17-grl" | head -1) && \
#    sdk install java $(sdk list java | grep -oE "\S+r11-grl" | head -1) && \
#    sdk install sbt

RUN mkdir -p $HOME/.local/share/bash-completion/completions

RUN cd /tmp && curl -fL https://github.com/coursier/launchers/raw/master/cs-"$(uname -m)"-pc-"$(uname | tr LD ld)".gz | gzip -d > cs && \
    chmod a+x ./cs && \
    ./cs setup -y --jvm "$(./cs java --available | grep graalvm | grep java17 | tail -n1)" && \
    echo "export PATH=\"$PATH:$HOME/.local/share/coursier/bin\"" >> ~/.bashrc && \
    ./cs java --jvm $(./cs java --installed | head -n 1 | awk '{print $1}') --env >> ~/.bashrc && \
    ./cs install bloop scalafmt scalafix ammonite && \
    curl -o ~/.local/share/bash-completion/completions/bloop https://raw.githubusercontent.com/scalacenter/bloop/master/etc/bash-completions

