#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_output="$project_root/work/coloros-observer/classes"
source_dir="$project_root/app/src/main/java/io/github/yixing233/hyperduo"
mkdir -p "$test_output"
javac --release 21 -encoding UTF-8 -d "$test_output" \
  "$source_dir/RomProfile.java" "$source_dir/ObservationLedger.java" "$source_dir/ConfigRevisionGate.java" \
  "$source_dir/ColorOsNetworkPolicy.java" "$project_root/tests/ColorOsNetworkPolicyTest.java" \
  "$source_dir/ColorOsTypeLabels.java" "$project_root/tests/ColorOsTypeLabelsTest.java" \
  "$project_root/tests/ColorOsObserverTest.java" "$project_root/tests/ConfigRevisionGateTest.java"
java -cp "$test_output" io.github.yixing233.hyperduo.ColorOsObserverTest
java -cp "$test_output" io.github.yixing233.hyperduo.ConfigRevisionGateTest
java -cp "$test_output" io.github.yixing233.hyperduo.ColorOsNetworkPolicyTest
java -cp "$test_output" io.github.yixing233.hyperduo.ColorOsTypeLabelsTest
