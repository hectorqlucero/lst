# Use a base image and bake in Java 17 and Leiningen so they're available before tasks start
FROM gitpod/workspace-full:latest

USER root

# Install JDK 17 and curl, set Java 17 as default, install leiningen to /usr/local/bin
RUN apt-get update -y \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jdk curl \
    && if [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then \
         update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java || true; \
         update-alternatives --set javac /usr/lib/jvm/java-17-openjdk-amd64/bin/javac || true; \
       fi \
    && curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein \
    && chmod +x /usr/local/bin/lein \
    && echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' > /etc/profile.d/java-home.sh \
    && echo 'export PATH=$JAVA_HOME/bin:$PATH' >> /etc/profile.d/java-home.sh \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

USER gitpod

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

# Verify tools are present (optional build-time checks)
RUN java -version && javac -version && lein -v
