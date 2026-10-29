/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/** Native StateFlow is authoritative; the callback cache is only a compatibility fallback. */
final class ColorOsTypeState {
    static final int UNKNOWN = -1;
    static final int UNSUPPORTED = -2;
    private final Method value, resourceId;
    private final Class<?> resourceType;
    private WeakReference<Object> source = new WeakReference<>(null);
    private int cached = UNKNOWN;
    private boolean live;

    ColorOsTypeState(Method value, Class<?> resourceType, Method resourceId) {
        this.value = value; this.resourceType = resourceType; this.resourceId = resourceId;
    }

    void bind(Object flow) { source = new WeakReference<>(flow); }
    void observe(Integer id) { cached = id == null ? 0 : id; }
    boolean live() { return live; }

    int resource() {
        Object flow = source.get();
        live = false;
        if (flow != null && value != null) {
            try {
                Object icon = value.invoke(flow);
                cached = icon == null ? 0 : resourceType.isInstance(icon)
                        ? ((Number) resourceId.invoke(icon)).intValue() : UNSUPPORTED;
                live = true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A failed optional source never invents a radio technology.
            }
        }
        return cached;
    }
}
