/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Exercises the real app-to-SystemUI request; contains only the allowlisted report. */
public final class CompatibilityReportInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        runOnMainSync(() -> CompatibilityReport.request(getTargetContext(), report -> {
            Bundle result = new Bundle();
            result.putString("stream", report);
            finish(report.contains("runtime.report=true\n") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        }));
    }
}
