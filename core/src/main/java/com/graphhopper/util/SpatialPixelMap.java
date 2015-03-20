package com.graphhopper.util;

/**
 * Interface describes rasterized representation of a spatial area
 *
 * @author jansoe
 */
public interface SpatialPixelMap
{

    /**
     * @return size of the y-raster
     */
    public double getYStep();

    /**
     * rounds y value to the nearest y-pixel value
     */
    public double discretizeY( double y );

    /**
     * writes value to spatial map at height y and from x1 to x2
     */
    public void fillLine( double y, double x1, double x2, byte value );

}
