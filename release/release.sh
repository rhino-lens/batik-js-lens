#!/usr/bin/env bash
# Build, test and bundle rhino-lens for the Maven Central Portal.
# Needs Maven and Java 11+ (JAVA_HOME). Set GPG_KEY (key id or e-mail) to sign.
# Output: target/central-bundle.zip, to upload at https://central.sonatype.com ("Publish Component").
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -n "$(git status --porcelain)" ]; then
  echo "working tree has uncommitted changes; commit them first so the release matches a commit" >&2
  exit 1
fi

mvn -B clean verify          # tests (compiled + interpreted), docs checks, jar, sources, javadoc
python3 -I release/make_bundle.py .
