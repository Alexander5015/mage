#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd -P)"

refuse_disabled_compression() {
    local variable value
    for variable in JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS; do
        value="${!variable-}"
        if [[ "$value" == *xmage.network.nocompress* ]]; then
            echo "Refusing to benchmark with xmage.network.nocompress set in $variable" >&2
            exit 2
        fi
    done
}

resolve_java() {
    local candidate maven_version runtime java_home
    if [[ -n "${JAVA_HOME:-}" ]]; then
        candidate="$JAVA_HOME/bin/java"
        if [[ -x "$candidate" ]] && "$candidate" -version >/dev/null 2>&1; then
            printf '%s\n' "$candidate"
            return
        fi
    fi

    maven_version="$(mvn -version 2>&1)"
    runtime="$(printf '%s\n' "$maven_version" | sed -n 's/^Java version:.*runtime: //p')"
    java_home="$(printf '%s\n' "$maven_version" | sed -n 's/^Java home: //p')"
    for candidate in "${runtime:+$runtime/bin/java}" "${java_home:+$java_home/bin/java}"; do
        if [[ -n "$candidate" && -x "$candidate" ]] && "$candidate" -version >/dev/null 2>&1; then
            printf '%s\n' "$candidate"
            return
        fi
    done

    candidate="$(command -v java || true)"
    if [[ -n "$candidate" && -x "$candidate" ]] && "$candidate" -version >/dev/null 2>&1; then
        printf '%s\n' "$candidate"
        return
    fi
    echo "Unable to resolve a working Java executable" >&2
    exit 2
}

refuse_disabled_compression
JAVA_EXECUTABLE="$(resolve_java)"

cd "$REPO_ROOT"
mvn -Pbenchmarks -pl Mage.Benchmarks -am -DskipTests package

RESULTS_ROOT="$REPO_ROOT/.benchmark-results"
RUN_ID="smoke-$(date -u +%Y%m%dT%H%M%SZ)"
RESULT_DIR="$RESULTS_ROOT/$RUN_ID"
mkdir -p "$RESULTS_ROOT"
if [[ -e "$RESULT_DIR" ]]; then
    echo "Benchmark result directory already exists: $RESULT_DIR" >&2
    exit 2
fi
mkdir "$RESULT_DIR"

echo "Running non-claim smoke benchmark with $JAVA_EXECUTABLE"
(
    cd "$REPO_ROOT/Mage.Tests"
    "$JAVA_EXECUTABLE" -jar ../Mage.Benchmarks/target/benchmarks.jar 'org.mage.benchmark.*' \
        -f 1 -wi 1 -i 1 -w 100ms -r 100ms -t 1 -prof gc \
        -rf json -rff "$RESULT_DIR/results.json"
) 2>&1 | tee "$RESULT_DIR/jmh.log"
test -s "$RESULT_DIR/results.json"

REF="$(git -C "$REPO_ROOT" symbolic-ref --quiet --short HEAD \
    || git -C "$REPO_ROOT" rev-parse --short HEAD)"
COMMIT="$(git -C "$REPO_ROOT" rev-parse HEAD)"
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain --untracked-files=normal)" ]]; then
    DIRTY=true
else
    DIRTY=false
fi
RUN_CONFIG='smoke:f1:wi1:i1:w100ms:r100ms:t1:gc'
"$JAVA_EXECUTABLE" -cp "$REPO_ROOT/Mage.Benchmarks/target/benchmarks.jar" \
    org.mage.benchmark.comparison.ManifestMain \
    "$RESULT_DIR/manifest.json" "$REF" "$COMMIT" "$DIRTY" "$RUN_CONFIG"
test -s "$RESULT_DIR/manifest.json"

echo "Smoke complete (non-claim): $RESULT_DIR"
