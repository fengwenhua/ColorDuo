/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import java.io.File;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrioTextControlsTest {
    @After public void restoreFont() { TrioRenderer.setFont(""); }

    @Test public void increasingWeightDoesNotShrinkDigitsAtTheSlotCeiling() {
        for (int zone : new int[]{0, 1, 2}) {
            TrioSettings s=RingOpeningTest.settings(Prefs.PART_VALUE,zone);s.valueSize=56;
            float previous=-1;
            for(int weight:new int[]{100,400,700,900}) {
                s.valueWeight=weight;
                float size=TrioRenderer.fittedRingTextSize(TrioAppearance.of(s),zone,Prefs.PART_VALUE,"100",56);
                if(previous>=0)assertEquals("weight must change strokes, not compensate by shrinking the em",previous,size,.05f);
                previous=size;
            }
        }
    }

    @Test public void endSlotSlidersStopBeforeTheAutomaticFitPlateau() {
        for (int z : new int[]{0, 2}) for (int weight : new int[]{100, 700, 900}) {
            TrioSettings s = RingOpeningTest.settings(Prefs.PART_VALUE, z);
            s.valueWeight = weight;
            TrioAppearance a = TrioAppearance.of(s);
            int maximum = TrioTextControls.maxValueSize(a);
            assertTrue(maximum < Prefs.MAX_RING_VALUE_SIZE);
            for (int size = Prefs.MIN_VALUE_SIZE; size <= maximum; size++)
                for (int level = 0; level <= 100; level++)
                    assertEquals("allowed setting must not be cancelled by fitting", size,
                            TrioRenderer.fittedRingTextSize(a,z,Prefs.PART_VALUE,String.valueOf(level),size),.05f);
            boolean binds = false;
            for (int level = 0; level <= 100; level++)
                binds |= TrioRenderer.fittedRingTextSize(a,z,Prefs.PART_VALUE,String.valueOf(level),maximum+1) < maximum+.95f;
            assertTrue("the ceiling must be the last usable setting", binds);
        }
    }

    @Test public void movingDigitsRecalculatesTheSlotsOwnRangeWithoutRewritingSavedSize() {
        TrioSettings s=RingOpeningTest.settings(Prefs.PART_VALUE,0);s.valueSize=56;
        int top=TrioTextControls.maxValueSize(TrioAppearance.of(s));
        s.ringZones=Prefs.decodeLayout("|3||");
        int centre=TrioTextControls.maxValueSize(TrioAppearance.of(s));
        assertTrue(centre>=Prefs.MIN_VALUE_SIZE);assertTrue(top>30);assertEquals(56,s.valueSize);
        s.ringZones=Prefs.decodeLayout("3|||");
        assertEquals(top,TrioTextControls.maxValueSize(TrioAppearance.of(s)));
    }

    @Test public void identicalFontOutlinesShareOneWeightStepAndResultsAreDefensive() {
        TrioAppearance a=TrioAppearance.of(RingOpeningTest.settings(Prefs.PART_VALUE,0));
        int[] choices=TrioTextControls.valueWeights(a);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(128);Path path=new Path();
        float[] previous=null;boolean previousBold=false;
        for(int weight:choices){
            paint.setTypeface(TrioRenderer.typefaceFor(weight));
            boolean bold=TrioRenderer.fakeBoldFor(weight);paint.setFakeBoldText(bold);
            path.reset();paint.getTextPath("0123456789",0,10,0,0,path);
            float[] current=path.approximate(.05f);
            assertTrue(previous==null||bold!=previousBold||!Arrays.equals(previous,current));
            previous=current;previousBold=bold;
        }
        // Desktop face has only regular and bold. Native weights are checked on N5.
        assertEquals(2,choices.length);
        int first=choices[0];choices[0]=-1;
        assertEquals(first,TrioTextControls.valueWeights(a)[0]);
    }

    @Test public void repeatedUiReadsReuseTheWeightProbeUntilTheFontChanges() throws Exception {
        TrioSettings s=RingOpeningTest.settings(Prefs.PART_VALUE,0);
        int[] original=TrioTextControls.valueWeights(TrioAppearance.of(s));
        int requests=Typeface.weightRequests;
        TrioTextControls.valueWeights(TrioAppearance.of(s));
        assertEquals(requests,Typeface.weightRequests);
        File file=File.createTempFile("control-font", ".ttf");
        try {
            s.fontPath=file.getAbsolutePath();
            int[] custom=TrioTextControls.valueWeights(TrioAppearance.of(s));
            assertEquals(requests+9,Typeface.weightRequests);
            assertTrue("synthetic bold must remain a distinct choice",custom.length>=2);
            assertEquals(600,custom[custom.length-1]);
            s.fontPath="";
            assertArrayEquals(original,TrioTextControls.valueWeights(TrioAppearance.of(s)));
            assertEquals(requests+18,Typeface.weightRequests);
        }finally{file.delete();}
    }
}
