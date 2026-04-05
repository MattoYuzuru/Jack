FROM ubuntu:24.04

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"

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
      librsvg2-bin \
      tar; \
    mkdir -p /opt/java; \
    curl -fsSL https://corretto.aws/downloads/latest/amazon-corretto-26-x64-linux-jdk.tar.gz -o /tmp/amazon-corretto.tar.gz; \
    tar -xzf /tmp/amazon-corretto.tar.gz -C /opt/java; \
    CORRETTO_DIR="$(find /opt/java -maxdepth 1 -type d -name 'amazon-corretto-*' | head -n 1)"; \
    mv "$CORRETTO_DIR" "${JAVA_HOME}"; \
    rm -f /tmp/amazon-corretto.tar.gz; \
    rm -rf /var/lib/apt/lists/*; \
    # Разрешаем PDF/PS family в policy, иначе ImageMagick в контейнере режет Phase 2 targets.
    for policy in /etc/ImageMagick-6/policy.xml /etc/ImageMagick-7/policy.xml; do \
      if [ -f "$policy" ]; then \
        sed -i 's/rights="none" pattern="PDF"/rights="read|write" pattern="PDF"/g' "$policy"; \
        sed -i 's/rights="none" pattern="PS"/rights="read|write" pattern="PS"/g' "$policy"; \
        sed -i 's/rights="none" pattern="PS2"/rights="read|write" pattern="PS2"/g' "$policy"; \
        sed -i 's/rights="none" pattern="PS3"/rights="read|write" pattern="PS3"/g' "$policy"; \
        sed -i 's/rights="none" pattern="EPS"/rights="read|write" pattern="EPS"/g' "$policy"; \
        sed -i 's/rights="none" pattern="XPS"/rights="read|write" pattern="XPS"/g' "$policy"; \
      fi; \
    done; \
    java -version

WORKDIR /workspace

EXPOSE 8080
