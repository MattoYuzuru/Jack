FROM ubuntu:24.04

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
      ca-certificates \
      curl \
      ffmpeg \
      fontconfig \
      ghostscript \
      imagemagick \
      libraw-bin \
      potrace \
      qpdf \
      librsvg2-bin \
      tesseract-ocr \
      tesseract-ocr-rus \
      tar; \
    mkdir -p /opt/java; \
    curl -fsSL https://corretto.aws/downloads/latest/amazon-corretto-26-x64-linux-jdk.tar.gz -o /tmp/amazon-corretto.tar.gz; \
    tar -xzf /tmp/amazon-corretto.tar.gz -C /opt/java; \
    CORRETTO_DIR="$(find /opt/java -maxdepth 1 -type d -name 'amazon-corretto-*' | head -n 1)"; \
    mv "$CORRETTO_DIR" "${JAVA_HOME}"; \
    rm -f /tmp/amazon-corretto.tar.gz; \
    rm -rf /var/lib/apt/lists/*; \
    java -version

WORKDIR /workspace

EXPOSE 8080
