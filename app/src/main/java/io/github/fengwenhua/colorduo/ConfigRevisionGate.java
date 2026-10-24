/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.HashSet;
import java.util.Set;

/** Single-writer gate: only a trusted remote store may establish or rotate an epoch. */
final class ConfigRevisionGate {
    enum Result { ACCEPTED, DUPLICATE, STALE, COLLISION, UNTRUSTED_EPOCH, INVALID }
    private final Set<String> retiredEpochs = new HashSet<>();
    private String epoch;
    private long revision;
    private String packet;

    Result offer(String nextEpoch, long nextRevision, String nextPacket, boolean trustedRemote) {
        if (nextEpoch == null || nextEpoch.isEmpty() || nextRevision <= 0 || nextPacket == null) {
            return Result.INVALID;
        }
        if (!nextEpoch.equals(epoch)) {
            if (!trustedRemote || retiredEpochs.contains(nextEpoch) || retiredEpochs.size() >= 16) {
                return Result.UNTRUSTED_EPOCH;
            }
            if (epoch != null) retiredEpochs.add(epoch);
        } else {
            if (nextRevision < revision) return Result.STALE;
            if (nextRevision == revision) return nextPacket.equals(packet) ? Result.DUPLICATE : Result.COLLISION;
        }
        epoch = nextEpoch;
        revision = nextRevision;
        packet = nextPacket;
        return Result.ACCEPTED;
    }
}
