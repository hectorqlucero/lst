# Devcontainer for LST with JDK 17 and Leiningen
FROM mcr.microsoft.com/devcontainers/base:ubuntu

USER root
RUN apt-get update -y \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jdk curl \
    && curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein \
    && chmod +x /usr/local/bin/lein \
    && echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' > /etc/profile.d/java-home.sh \
    && echo 'export PATH=$JAVA_HOME/bin:$PATH' >> /etc/profile.d/java-home.sh \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

RUN java -version && javac -version && lein -v

USER vscode
