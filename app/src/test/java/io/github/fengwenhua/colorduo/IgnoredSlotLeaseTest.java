/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class IgnoredSlotLeaseTest {
    static final class Container { boolean ignored; int hides, restores; }
    private IgnoredSlotLease<Object, Container> lease() {
        return new IgnoredSlotLease<>(new IgnoredSlotLease.Sink<>() {
            public boolean ignored(Container c) { return c.ignored; }
            public void hide(Container c) { c.ignored = true; c.hides++; }
            public void restore(Container c) { c.ignored = false; c.restores++; }
        });
    }
    @Test public void repeatedFramesDoNotChurnLayoutAndLastOwnerRestores() {
        var lease = lease(); var c = new Container(); Object a = new Object(), b = new Object();
        lease.set(a, List.of(c)); lease.set(a, List.of(c)); lease.set(b, List.of(c));
        assertTrue(c.ignored); assertEquals(1,c.hides);
        lease.release(a); assertTrue(c.ignored); assertEquals(0,c.restores);
        lease.release(b); assertFalse(c.ignored); assertEquals(1,c.restores); assertEquals(0,lease.size());
    }
    @Test public void existingNativeExclusionSurvivesRelease() {
        var lease = lease(); var c = new Container(); c.ignored=true; Object owner=new Object();
        lease.set(owner,List.of(c)); lease.release(owner);
        assertTrue(c.ignored); assertEquals(0,c.hides); assertEquals(0,c.restores);
    }
    @Test public void rowRebuildAndMissingReplacementReleaseOnlyOldContainers() {
        var lease=lease(); var old=new Container(); var next=new Container(); Object owner=new Object();
        lease.set(owner,List.of(old)); lease.set(owner,List.of(next));
        assertFalse(old.ignored); assertTrue(next.ignored);
        lease.set(owner,List.of()); assertFalse(next.ignored); assertEquals(0,lease.size());
    }
    @Test public void shutdownRestoresEveryAddedSlotButKeepsPreexistingSlots() {
        var lease=lease(); var a=new Container(); var b=new Container(); b.ignored=true;
        Object owner=new Object(); lease.set(owner,List.of(a,b)); lease.releaseAll();
        assertFalse(a.ignored); assertTrue(b.ignored); assertEquals(0,lease.size());
    }
}
