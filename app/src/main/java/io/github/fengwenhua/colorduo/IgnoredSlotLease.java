/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Share a native ignored slot between hosts without undoing a pre-existing exclusion. */
final class IgnoredSlotLease<O, C> {
    interface Sink<C> {
        boolean ignored(C container);
        void hide(C container);
        void restore(C container);
    }
    private final Sink<C> sink;
    private final Map<O, List<WeakReference<C>>> owners = new WeakHashMap<>();
    private final Map<C, Boolean> added = new WeakHashMap<>();

    IgnoredSlotLease(Sink<C> sink) { this.sink = sink; }

    void set(O owner, List<C> containers) {
        if (containers.isEmpty()) owners.remove(owner);
        else {
            List<WeakReference<C>> refs = new ArrayList<>();
            for (C container : containers) refs.add(new WeakReference<>(container));
            owners.put(owner, refs);
        }
        for (C container : containers) {
            boolean ignored = sink.ignored(container);
            if (!added.containsKey(container)) added.put(container, !ignored);
            if (!ignored) sink.hide(container);
        }
        sweep();
    }

    void release(O owner) { owners.remove(owner); sweep(); }
    void releaseAll() { owners.clear(); sweep(); }
    int size() { return added.size(); }

    private boolean owned(C container) {
        for (List<WeakReference<C>> refs : owners.values())
            for (WeakReference<C> ref : refs) if (ref.get() == container) return true;
        return false;
    }

    private void sweep() {
        for (C container : new ArrayList<>(added.keySet())) {
            if (owned(container)) continue;
            if (Boolean.TRUE.equals(added.get(container))) sink.restore(container);
            added.remove(container);
        }
    }
}
