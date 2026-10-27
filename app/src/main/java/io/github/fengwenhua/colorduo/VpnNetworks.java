/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.HashSet;
import java.util.Set;

/** Retain VPN presence across duplicate callbacks and overlapping network handovers. */
final class VpnNetworks<T> {
    private final Set<T> networks = new HashSet<>();
    boolean active() { return !networks.isEmpty(); }
    boolean update(T network, boolean vpn) {
        boolean before = active();
        if (vpn) networks.add(network); else networks.remove(network);
        return before != active();
    }
    void clear() { networks.clear(); }
}
