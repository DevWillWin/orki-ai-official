#!/usr/bin/env bash
# Kotlin CLI smoke test for actual pure JVM language code; build outputs stay ignored.
# Requires java and kotlinc on PATH. Android/Gradle tests remain a separate requirement.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .build-outputs/bodo-jvm
kotlinc \
  app/src/main/java/com/example/data/language/BodoLexicon.kt \
  app/src/main/java/com/example/data/language/VisualIntentDetector.kt \
  app/src/main/java/com/example/data/audio/BodoTtsNormalizer.kt \
  tests/BodoJvmSmoke.kt \
  -include-runtime -d .build-outputs/bodo-jvm/smoke.jar
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar .build-outputs/bodo-jvm/smoke.jar
