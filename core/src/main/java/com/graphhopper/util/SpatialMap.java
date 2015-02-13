package com.graphhopper.util;

import com.graphhopper.geohash.KeyAlgo;
import com.graphhopper.geohash.LinearKeyAlgo;
import gnu.trove.map.hash.TLongByteHashMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jan on 17.02.15.
 */
public interface SpatialMap {

    public double getYStep();
    
    public double discretizeY(double y);
    
    public void fillLine(double y, double x1, double x2, byte value);

}
