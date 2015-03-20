package com.graphhopper.reader;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import junit.framework.TestCase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class LanduseProcessorTest extends TestCase
{

    // Fill 2 square polygons with different tags
    @Test
    public void test2SquareFill()
    {

        OSMWay poly;
        LanduseProcessor landuseProcessor = new LanduseProcessor(1100);
        ArrayList<String> cases = new ArrayList<String>();
        cases.add("residential");
        cases.add("typo");
        cases.add("commercial");
        landuseProcessor.setLanduseCases(cases);

        // First Poly
        landuseProcessor.addNodeInfo(0, 50.00500, 10.00);
        landuseProcessor.addNodeInfo(1, 50.02501, 10.00);
        landuseProcessor.addNodeInfo(2, 50.02501, 10.0151);
        landuseProcessor.addNodeInfo(3, 50.00500, 10.0151);

        // Second Poly
        landuseProcessor.addNodeInfo(4, 50.00500, 10.03);
        landuseProcessor.addNodeInfo(5, 50.03501, 10.03);
        landuseProcessor.addNodeInfo(6, 50.03501, 10.05);
        landuseProcessor.addNodeInfo(7, 50.00500, 10.05);

        // to increase boundary margin
        landuseProcessor.addNodeInfo(8, 49.99, 9.98);
        landuseProcessor.addNodeInfo(9, 50.05, 10.06);

        landuseProcessor.initLineFill();
        poly = new OSMWay(0);
        poly.nodes.add(new long[]{0, 1, 2, 3});
        poly.setTag("landuse", "residential");
        landuseProcessor.addPolygon(poly);

        poly = new OSMWay(1);
        poly.nodes.add(new long[]{4, 5, 6, 7});
        poly.setTag("landuse", "commercial");
        landuseProcessor.addPolygon(poly);

        TLongSet expectedKeys1 = new TLongHashSet(Arrays.asList(10L, 11L, 18L, 19L, 26L, 27L));
        TLongSet expectedKeys2 = new TLongHashSet(Arrays.asList(13L, 14L, 21L, 22L, 29L, 30L, 37L, 38L));

        TLongSet actualKeys = landuseProcessor.landuseMap.keySet();
        byte fillValue1 = (byte) cases.indexOf("residential");
        for (long key : expectedKeys1.toArray())
            assertEquals("wrong spatial map entry", fillValue1, landuseProcessor.landuseMap.get(key));

        byte fillValue2 = (byte) cases.indexOf("commercial");
        for (long key : expectedKeys2.toArray())
            assertEquals("wrong spatial map entry", fillValue2, landuseProcessor.landuseMap.get(key));

        //combine keys of both polygon to check if all necessary keys are included
        expectedKeys1.addAll(expectedKeys2);
        assertTrue("wrong keys in spatial map", expectedKeys1.equals(actualKeys));
    }

    // Fill almost square polygons ot test for correct rounding
    @Test
    public void testSquareFillRound()
    {
        LanduseProcessor landuseProcessor = new LanduseProcessor(1100);
        ArrayList<String> cases = new ArrayList<String>();
        cases.add("residential");
        landuseProcessor.setLanduseCases(cases);

        landuseProcessor.addNodeInfo(0, 50.015000, 10.0649);
        landuseProcessor.addNodeInfo(1, 50.025001, 10.0551);
        landuseProcessor.addNodeInfo(2, 50.025001, 10.0149);
        landuseProcessor.addNodeInfo(3, 50.015000, 10.0051);

        // to increase boundary margin
        landuseProcessor.addNodeInfo(4, 50.00, 10.00);
        landuseProcessor.addNodeInfo(5, 50.05, 10.10);

        landuseProcessor.initLineFill();
        OSMWay poly = new OSMWay(0);
        poly.nodes.add(new long[]{0, 1, 2, 3});
        poly.setTag("landuse", "residential");
        landuseProcessor.addPolygon(poly);

        TLongSet expectedKeys = new TLongHashSet(Arrays.asList(11L, 12L, 13L, 14L, 15L, 21L, 22L, 23L, 24L, 25L));
        TLongSet actualKeys = landuseProcessor.landuseMap.keySet();
        assertEquals("wrong keys in spatial map", expectedKeys, actualKeys);
    }

    // Fill a heart polygon
    @Test
    public void testComplexFill()
    {

        LanduseProcessor landuseProcessor = new LanduseProcessor(550);
        ArrayList<String> cases = new ArrayList<String>();
        cases.add("residential");
        landuseProcessor.setLanduseCases(cases);

        landuseProcessor.addNodeInfo(0, 50.015, 10.01);
        landuseProcessor.addNodeInfo(1, 50.040, 10.035);
        landuseProcessor.addNodeInfo(2, 50.015, 10.06);
        landuseProcessor.addNodeInfo(3, 50.005, 10.05);
        landuseProcessor.addNodeInfo(4, 50.020, 10.035);
        landuseProcessor.addNodeInfo(5, 50.005, 10.02);


        // to increase boundary margin
        landuseProcessor.addNodeInfo(6, 50.0025, 10.00);
        landuseProcessor.addNodeInfo(7, 50.0425, 10.10);

        landuseProcessor.initLineFill();
        OSMWay poly = new OSMWay(0);
        poly.nodes.add(new long[]{0, 1, 2, 3, 4, 5});
        poly.setTag("landuse", "residential");
        landuseProcessor.addPolygon(poly);

        // each longitude row goes from 20i-20(i+1)
        TLongSet expectedKeys = new TLongHashSet(Arrays.asList(23L, 24L, 29L, 30L,
                42L, 43L, 44L, 45L, 48L, 49L, 50L, 51L,
                63L, 64L, 65L, 66L, 67L, 68L, 69L, 70L,
                84L, 85L, 86L, 87L, 88L, 89L,
                105L, 106L, 107L, 108L,
                126L, 127L));
        TLongSet actualKeys = landuseProcessor.landuseMap.keySet();
        assertEquals("wrong keys in spatial map", expectedKeys, actualKeys);
        //landuseProcessor.print();
    }
}