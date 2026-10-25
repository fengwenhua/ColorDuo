#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_output="$project_root/work/coloros-observer/classes"
source_dir="$project_root/app/src/main/java/io/github/fengwenhua/colorduo"
case_dir="$project_root/app/src/test/java/io/github/fengwenhua/colorduo"
mkdir -p "$test_output"
javac --release 21 -encoding UTF-8 -d "$test_output" \
  "$source_dir/RomProfile.java" "$source_dir/ObservationLedger.java" "$source_dir/ConfigRevisionGate.java" \
  "$source_dir/ColorOsNetworkPolicy.java" "$case_dir/ColorOsNetworkPolicyTest.java" \
  "$source_dir/ColorOsTypeLabels.java" "$case_dir/ColorOsTypeLabelsTest.java" \
  "$case_dir/ColorOsObserverTest.java" "$case_dir/ConfigRevisionGateTest.java"
java -cp "$test_output" io.github.fengwenhua.colorduo.ColorOsObserverTest
java -cp "$test_output" io.github.fengwenhua.colorduo.ConfigRevisionGateTest
java -cp "$test_output" io.github.fengwenhua.colorduo.ColorOsNetworkPolicyTest
java -cp "$test_output" io.github.fengwenhua.colorduo.ColorOsTypeLabelsTest
