package com.graphhopper.util;

import junit.framework.TestCase;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

public class ScanLinePolyFillTest
{
    public HashSet<List<Double>> map = new HashSet<List<Double>>();

    public SpatialMap simpleMap = new SpatialMap() 
    {
        @Override
        public double getYStep()
        {
            return 1;
        }
        
        @Override
        public double discretizeY(double y) 
        {
            return Math.round(y)+0.5;
        }

        @Override
        public void fillLine(double y, double x1, double x2, byte value)
        {
            map.add(Arrays.asList(new Double[]{y, x1, x2}));
        }

    };
    
    /*
    *  -----
    *  |   |
    *  -----  
    */
    @Test
    public void testScanlineFillSquare()
    {
        map.clear();
        ScanLinePolyFill polyFill = new ScanLinePolyFill(simpleMap);
        polyFill.addEdge(0.7,0.7,2,4);
        polyFill.addEdge(0.7,3.6,4,4);
        polyFill.addEdge(3.6,3.6,4,2);
        polyFill.addEdge(3.6,0.7,2,2);
        polyFill.finalizeAllEdgesTable();
        
        polyFill.doScanlineFill();
        
        List desiredOutput =  new ArrayList();
        desiredOutput.add(Arrays.asList(new Double[]{2.5, 0.7, 3.6}));
        desiredOutput.add(Arrays.asList(new Double[]{3.5, 0.7, 3.6}));

        assertTrue("Segments out of the polygon were filled", map.containsAll(desiredOutput));
        assertEquals("Not all segments of polygon filled", desiredOutput.size(), map.size());
    }

    /*
    *  /\
    *  \/
    */
    @Test
    public void testScanlineFillDiamond()
    {
        map.clear();
        ScanLinePolyFill polyFill = new ScanLinePolyFill(simpleMap);
        polyFill.addEdge(0,2,2,4);
        polyFill.addEdge(2,4,4,2);
        polyFill.addEdge(4,2,2,0);
        polyFill.addEdge(2,0,0,2);
        
        polyFill.finalizeAllEdgesTable();        
        polyFill.doScanlineFill();

        List desiredOutput =  new ArrayList();
        desiredOutput.add(Arrays.asList(new Double[]{0.5, 1.5, 2.5}));
        desiredOutput.add(Arrays.asList(new Double[]{1.5, 0.5, 3.5}));
        desiredOutput.add(Arrays.asList(new Double[]{2.5, 0.5, 3.5}));
        desiredOutput.add(Arrays.asList(new Double[]{3.5, 1.5, 2.5}));

        assertTrue("Segments out of the polygon were filled", map.containsAll(desiredOutput));
        assertEquals("Not all segments of polygon filled", desiredOutput.size(), map.size());
    }

    /*
    *     / \
    *   /     \
    *  \ /\/\  /
    *  \/    \/
    */
    @Test
    public void testScanlineFillPacMan()
    {
        map.clear();
        ScanLinePolyFill polyFill = new ScanLinePolyFill(simpleMap);
        polyFill.addEdge(1,3.5,2,4.5);
        polyFill.addEdge(3.5,6,4.5,2);
        polyFill.addEdge(6,5,2,0);
        polyFill.addEdge(5,4,0,2);
        polyFill.addEdge(4,3.5,2,1);
        polyFill.addEdge(3.5,3,1,2);
        polyFill.addEdge(3,2,2,0);
        polyFill.addEdge(2,1,0,2);

        polyFill.finalizeAllEdgesTable();
        polyFill.doScanlineFill();

        List desiredOutput =  new ArrayList();
        desiredOutput.add(Arrays.asList(new Double[]{0.5, 1.75, 2.25}));
        desiredOutput.add(Arrays.asList(new Double[]{0.5, 4.75, 5.25}));
        desiredOutput.add(Arrays.asList(new Double[]{1.5, 1.25, 2.75}));
        desiredOutput.add(Arrays.asList(new Double[]{1.5, 4.25, 5.75}));
        desiredOutput.add(Arrays.asList(new Double[]{1.5, 3.25, 3.75}));
        desiredOutput.add(Arrays.asList(new Double[]{2.5, 1.5, 5.5}));
        desiredOutput.add(Arrays.asList(new Double[]{3.5, 2.5, 4.5}));

        assertTrue("Segments out of the polygon were filled", map.containsAll(desiredOutput));
        assertEquals("Not all segments of polygon filled", desiredOutput.size(), map.size());
    }

    /*
    *     / \
    *   /    \
    *  \  _   /
    *  \/   \/
    */
    @Test
    public void testScanlineFillPacMan2()
    {
        map.clear();
        ScanLinePolyFill polyFill = new ScanLinePolyFill(simpleMap);
        polyFill.addEdge(1,3.5,2,4.5);
        polyFill.addEdge(3.5,6,4.5,2);
        polyFill.addEdge(6,5,2,0);
        polyFill.addEdge(5,4.25,0,1.5);
        polyFill.addEdge(4.25,3.5,1.5,1.5);
        polyFill.addEdge(3.5,2.75,1.5,1.5);
        polyFill.addEdge(2.75,2,1.5,0);
        polyFill.addEdge(2,1,0,2);

        polyFill.finalizeAllEdgesTable();
        polyFill.doScanlineFill();

        List desiredOutput =  new ArrayList();
        desiredOutput.add(Arrays.asList(new Double[]{0.5, 1.75, 2.25}));
        desiredOutput.add(Arrays.asList(new Double[]{0.5, 4.75, 5.25}));
        desiredOutput.add(Arrays.asList(new Double[]{1.5, 1.25, 5.75}));
        desiredOutput.add(Arrays.asList(new Double[]{2.5, 1.5, 5.5}));
        desiredOutput.add(Arrays.asList(new Double[]{3.5, 2.5, 4.5}));

        assertTrue("Segments out of the polygon were filled", map.containsAll(desiredOutput));
        assertEquals("Not all segments of polygon filled", desiredOutput.size(), map.size());
    }
}