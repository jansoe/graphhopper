package com.graphhopper.reader;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import junit.framework.TestCase;
import org.junit.Test;

public class LanduseProcessorTest extends TestCase {
    
    @Test
    public void testSquareFill()
    {
        LanduseProcessor landuseProcessor = new LanduseProcessor(500);
        landuseProcessor.addNodeInfo(0, 50.00, 10.00);
        landuseProcessor.addNodeInfo(1, 50.035, 10.00);
        landuseProcessor.addNodeInfo(2, 50.035, 10.02);
        landuseProcessor.addNodeInfo(3, 50.00, 10.02);

        // to increase boundary margin
        landuseProcessor.addNodeInfo(4, 49.99,  9.93);
        landuseProcessor.addNodeInfo(5, 50.04,  10.03);

        landuseProcessor.initEncoding();
        OSMWay poly = new OSMWay(0);
        poly.nodes.add(new long[]{0,1,2,3});
        poly.setTag("landuse", "residential");
        landuseProcessor.addPolygon(poly);
        
        landuseProcessor.print();

    }
    


}