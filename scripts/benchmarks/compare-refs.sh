#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <baseline-ref> <candidate-ref>" >&2
    exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd -P)"
BASELINE_REF="$1"
CANDIDATE_REF="$2"
BENCHMARK_REGEX='(org\.mage\.benchmark|mage\.server\.game)\..*Benchmark.*'

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
BASELINE_COMMIT="$(git -C "$REPO_ROOT" rev-parse --verify --end-of-options "${BASELINE_REF}^{commit}")"
CANDIDATE_COMMIT="$(git -C "$REPO_ROOT" rev-parse --verify --end-of-options "${CANDIDATE_REF}^{commit}")"
BASELINE_WORKLOAD_TREE="$(git -C "$REPO_ROOT" rev-parse --verify "${BASELINE_COMMIT}:Mage.Benchmarks")"
CANDIDATE_WORKLOAD_TREE="$(git -C "$REPO_ROOT" rev-parse --verify "${CANDIDATE_COMMIT}:Mage.Benchmarks")"
if [[ "$BASELINE_WORKLOAD_TREE" != "$CANDIDATE_WORKLOAD_TREE" ]]; then
    echo "Benchmark workload definitions differ between refs." >&2
    echo "Land benchmark-contract changes separately, then compare from that new baseline." >&2
    exit 2
fi
BASELINE_SHORT="$(git -C "$REPO_ROOT" rev-parse --short "$BASELINE_COMMIT")"
CANDIDATE_SHORT="$(git -C "$REPO_ROOT" rev-parse --short "$CANDIDATE_COMMIT")"

RESULTS_ROOT="$REPO_ROOT/.benchmark-results"
RUN_ID="compare-$(date -u +%Y%m%dT%H%M%SZ)-${BASELINE_SHORT}-${CANDIDATE_SHORT}"
RESULT_DIR="$RESULTS_ROOT/$RUN_ID"
mkdir -p "$RESULTS_ROOT"
if [[ -e "$RESULT_DIR" ]]; then
    echo "Benchmark result directory already exists: $RESULT_DIR" >&2
    exit 2
fi
mkdir "$RESULT_DIR"

TEMP_PARENT="$(mktemp -d "${TMPDIR:-/tmp}/xmage-benchmarks.XXXXXX")"
BASELINE_WORKTREE="$TEMP_PARENT/baseline"
CANDIDATE_WORKTREE="$TEMP_PARENT/candidate"
BASELINE_ADDED=false
CANDIDATE_ADDED=false
COMPLETED=false

retain_on_failure() {
    local status=$?
    if [[ "$COMPLETED" == true ]]; then
        return
    fi
    echo "Benchmark comparison did not complete; retained artifacts:" >&2
    echo "  results: $RESULT_DIR" >&2
    echo "  temporary parent: $TEMP_PARENT" >&2
    if [[ "$BASELINE_ADDED" == true ]]; then
        printf '  cleanup: git -C %q worktree remove --force %q\n' \
            "$REPO_ROOT" "$BASELINE_WORKTREE" >&2
    fi
    if [[ "$CANDIDATE_ADDED" == true ]]; then
        printf '  cleanup: git -C %q worktree remove --force %q\n' \
            "$REPO_ROOT" "$CANDIDATE_WORKTREE" >&2
    fi
    printf '  cleanup parent after worktrees: rmdir %q\n' "$TEMP_PARENT" >&2
    return "$status"
}
trap retain_on_failure EXIT

git -C "$REPO_ROOT" worktree add --detach "$BASELINE_WORKTREE" "$BASELINE_COMMIT"
BASELINE_ADDED=true
git -C "$REPO_ROOT" worktree add --detach "$CANDIDATE_WORKTREE" "$CANDIDATE_COMMIT"
CANDIDATE_ADDED=true

(
    cd "$BASELINE_WORKTREE"
    mvn -Pbenchmarks -pl Mage.Benchmarks -am \
        -Dtest=PayloadRoundTripTest,DeterministicGameFixtureTest \
        -Dsurefire.failIfNoSpecifiedTests=false package
) 2>&1 | tee "$RESULT_DIR/build-baseline.log"
(
    cd "$CANDIDATE_WORKTREE"
    mvn -Pbenchmarks -pl Mage.Benchmarks -am \
        -Dtest=PayloadRoundTripTest,DeterministicGameFixtureTest \
        -Dsurefire.failIfNoSpecifiedTests=false package
) 2>&1 | tee "$RESULT_DIR/build-candidate.log"

BASELINE_JAR="$BASELINE_WORKTREE/Mage.Benchmarks/target/benchmarks.jar"
POLICY="$BASELINE_WORKTREE/Mage.Benchmarks/benchmark-policy.json"
CLAIM_FIXTURE="$RESULT_DIR/claim-fixture.bin"
(
    cd "$BASELINE_WORKTREE/Mage.Tests"
    "$JAVA_EXECUTABLE" -cp "$BASELINE_JAR" org.mage.benchmark.fixture.FixtureMain \
        "$CLAIM_FIXTURE"
)
test -s "$CLAIM_FIXTURE"
if command -v shasum >/dev/null 2>&1; then
    FIXTURE_SHA256="$(shasum -a 256 "$CLAIM_FIXTURE" | awk '{print $1}')"
elif command -v sha256sum >/dev/null 2>&1; then
    FIXTURE_SHA256="$(sha256sum "$CLAIM_FIXTURE" | awk '{print $1}')"
else
    echo "Unable to find shasum or sha256sum for fixture verification" >&2
    exit 2
fi
RUN_CONFIG="comparison:f3:wi5:i10:w1s:r1s:t1:gc:full:xbatch:workload-tree=$BASELINE_WORKLOAD_TREE:fixture=$FIXTURE_SHA256"

run_jmh() {
    local label="$1" worktree="$2" jar="$3" output="$4"
    mkdir "$output"
    echo "Running $label"
    (
        cd "$worktree/Mage.Tests"
        "$JAVA_EXECUTABLE" -jar "$jar" "$BENCHMARK_REGEX" \
            -f 3 -wi 5 -i 10 -w 1s -r 1s -t 1 -prof gc \
            -jvmArgsAppend "-Xbatch -Dxmage.benchmark.fixture=$CLAIM_FIXTURE" \
            -rf json -rff "$output/results.json"
    ) 2>&1 | tee "$output/jmh.log"
    test -s "$output/results.json"
}

write_manifest() {
    local worktree="$1" ref="$2" commit="$3" output="$4"
    local dirty
    if [[ -n "$(git -C "$worktree" status --porcelain --untracked-files=normal)" ]]; then
        dirty=true
    else
        dirty=false
    fi
    (
        cd "$worktree/Mage.Tests"
        "$JAVA_EXECUTABLE" -cp "$BASELINE_JAR" org.mage.benchmark.comparison.ManifestMain \
            "$output/manifest.json" "$ref" "$commit" "$dirty" "$RUN_CONFIG"
    )
    test -s "$output/manifest.json"
}

AB_BASELINE="$RESULT_DIR/ab-baseline"
AB_CANDIDATE="$RESULT_DIR/ab-candidate"
BA_CANDIDATE="$RESULT_DIR/ba-candidate"
BA_BASELINE="$RESULT_DIR/ba-baseline"

run_jmh "AB baseline" "$BASELINE_WORKTREE" "$BASELINE_JAR" "$AB_BASELINE"
write_manifest "$BASELINE_WORKTREE" "$BASELINE_REF" "$BASELINE_COMMIT" "$AB_BASELINE"
run_jmh "AB candidate" "$CANDIDATE_WORKTREE" \
    "$CANDIDATE_WORKTREE/Mage.Benchmarks/target/benchmarks.jar" "$AB_CANDIDATE"
write_manifest "$CANDIDATE_WORKTREE" "$CANDIDATE_REF" "$CANDIDATE_COMMIT" "$AB_CANDIDATE"
run_jmh "BA candidate" "$CANDIDATE_WORKTREE" \
    "$CANDIDATE_WORKTREE/Mage.Benchmarks/target/benchmarks.jar" "$BA_CANDIDATE"
write_manifest "$CANDIDATE_WORKTREE" "$CANDIDATE_REF" "$CANDIDATE_COMMIT" "$BA_CANDIDATE"
run_jmh "BA baseline" "$BASELINE_WORKTREE" "$BASELINE_JAR" "$BA_BASELINE"
write_manifest "$BASELINE_WORKTREE" "$BASELINE_REF" "$BASELINE_COMMIT" "$BA_BASELINE"

(
    cd "$BASELINE_WORKTREE/Mage.Tests"
    "$JAVA_EXECUTABLE" -cp "$BASELINE_JAR" org.mage.benchmark.comparison.CompareMain \
        "$POLICY" "$RESULT_DIR/comparison-report.json" \
        "$AB_BASELINE/results.json" "$AB_BASELINE/manifest.json" \
        "$AB_CANDIDATE/results.json" "$AB_CANDIDATE/manifest.json" \
        "$BA_BASELINE/results.json" "$BA_BASELINE/manifest.json" \
        "$BA_CANDIDATE/results.json" "$BA_CANDIDATE/manifest.json"
) 2>&1 | tee "$RESULT_DIR/comparison.log"

git -C "$REPO_ROOT" worktree remove --force "$BASELINE_WORKTREE"
BASELINE_ADDED=false
git -C "$REPO_ROOT" worktree remove --force "$CANDIDATE_WORKTREE"
CANDIDATE_ADDED=false
rmdir "$TEMP_PARENT"
COMPLETED=true
trap - EXIT

echo "Benchmark comparison passed: $RESULT_DIR"
