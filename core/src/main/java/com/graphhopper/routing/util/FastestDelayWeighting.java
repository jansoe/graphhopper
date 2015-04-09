package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;

/**
 * Created by jan on 09.04.15.
 */
public class FastestDelayWeighting implements Weighting
{
    /**
     * Converting to seconds is not necessary but makes adding other penalities easier (e.g. turn
     * costs or traffic light costs etc)
     */
    protected final static double SPEED_CONV = 3.6;
    protected final FlagEncoder encoder;
    private final double maxSpeed;

    public FastestDelayWeighting( FlagEncoder encoder )
    {
        this.encoder = encoder;
        maxSpeed = encoder.getMaxSpeed() / SPEED_CONV;
    }

    @Override
    public double getMinWeight( double distance )
    {
        return distance / maxSpeed;
    }

    @Override
    public double calcWeight( EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId )
    {
        double speed = reverse ? encoder.getReverseSpeed(edge.getFlags()) : encoder.getSpeed(edge.getFlags());
        double delay = encoder.getDouble(edge.getFlags(), encoder.K_DELAY);
        if (delay>0)
        {
            System.out.println(delay);
        }
        if (speed == 0)
            return Double.POSITIVE_INFINITY;
        return edge.getDistance() / speed * SPEED_CONV + delay;
    }

    @Override
    public String toString()
    {
        return "FASTESTwtDELAY|" + encoder;
    }
}

