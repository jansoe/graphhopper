/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import java.lang.String;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;
import com.sun.org.apache.xpath.internal.operations.*;

import java.util.*;

/**
 * Defines bit layout for cars. (speed, access, ferries, ...)
 * <p>
 * @author Peter Karich
 * @author Nop
 */
public class CarFlagEncoder extends AbstractFlagEncoder
{
    protected final Map<String, Integer> trackTypeSpeedMap = new HashMap<String, Integer>();
    protected final Set<String> badSurfaceSpeedMap = new HashSet<String>();

    //delay objects and associated delay
    protected final Map<String, Integer> delayMap = new HashMap<String, Integer>();

    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    protected final Map<String, Integer> defaultSpeedMap = new HashMap<String, Integer>();

    /**
     * Should be only instantied via EncodingManager
     */
    public CarFlagEncoder()
    {
        this(5, 5, 0);
    }

    public CarFlagEncoder( String propertiesStr )
    {
        this((int) parseLong(propertiesStr, "speedBits", 5),
                parseDouble(propertiesStr, "speedFactor", 5),
                parseBoolean(propertiesStr, "turnCosts", false) ? 3 : 0);
        this.setBlockFords(parseBoolean(propertiesStr, "blockFords", true));
    }

    public CarFlagEncoder( int speedBits, double speedFactor, int maxTurnCosts )
    {
        super(speedBits, speedFactor, maxTurnCosts);
        restrictions.addAll(Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access"));
        restrictedValues.add("private");
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");

        intendedValues.add("yes");
        intendedValues.add("permissive");

        potentialBarriers.add("gate");
        potentialBarriers.add("lift_gate");
        potentialBarriers.add("kissing_gate");
        potentialBarriers.add("swing_gate");

        absoluteBarriers.add("bollard");
        absoluteBarriers.add("stile");
        absoluteBarriers.add("turnstile");
        absoluteBarriers.add("cycle_barrier");
        absoluteBarriers.add("motorcycle_barrier");
        absoluteBarriers.add("block");

        trackTypeSpeedMap.put("grade1", 20); // paved
        trackTypeSpeedMap.put("grade2", 15); // now unpaved - gravel mixed with ...
        trackTypeSpeedMap.put("grade3", 10); // ... hard and soft materials
        trackTypeSpeedMap.put("grade4", 5); // ... some hard or compressed materials
        trackTypeSpeedMap.put("grade5", 5); // ... no hard materials. soil/sand/grass

        badSurfaceSpeedMap.add("cobblestone");
        badSurfaceSpeedMap.add("grass_paver");
        badSurfaceSpeedMap.add("gravel");
        badSurfaceSpeedMap.add("sand");
        badSurfaceSpeedMap.add("paving_stones");
        badSurfaceSpeedMap.add("dirt");
        badSurfaceSpeedMap.add("ground");
        badSurfaceSpeedMap.add("grass");

        maxPossibleSpeed = 100; //[kmH]
        maxPossibleDelay = 30; //[s]
        //accuracy of delay information (usedBits = log2(maxPossilbeDelay/delayResolution))
        delayResolution = 10;
        delayMap.put("traffic_light", 10);
        
        // autobahn
        defaultSpeedMap.put("motorway", 100);
        defaultSpeedMap.put("motorway_link", 70);
        defaultSpeedMap.put("motorroad", 90);
        // bundesstraße
        defaultSpeedMap.put("trunk", 70);
        defaultSpeedMap.put("trunk_link", 65);
        // linking bigger town
        defaultSpeedMap.put("primary", 65);
        defaultSpeedMap.put("primary_link", 60);
        // linking towns + villages
        defaultSpeedMap.put("secondary", 60);
        defaultSpeedMap.put("secondary_link", 50);
        // streets without middle line separation
        defaultSpeedMap.put("tertiary", 50);
        defaultSpeedMap.put("tertiary_link", 40);
        defaultSpeedMap.put("unclassified", 30);
        defaultSpeedMap.put("residential", 30);
        // spielstraße
        defaultSpeedMap.put("living_street", 5);
        defaultSpeedMap.put("service", 20);
        // unknown road
        defaultSpeedMap.put("road", 20);
        // forestry stuff
        defaultSpeedMap.put("track", 15);
    }

    /**
     * Define the place of the speedBits in the edge flags for car.
     */
    @Override
    public int defineWayBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);
        speedEncoder = new EncodedDoubleValue("Speed", shift, speedBits, speedFactor, defaultSpeedMap.get("secondary"), 
                                              maxPossibleSpeed);
        shift += speedEncoder.getBits();
        
        int requiredBits = 32 - Integer.numberOfLeadingZeros((int) Math.ceil((double) maxPossibleDelay/delayResolution));
        delayEncoder = new EncodedDoubleValue("Delay", shift, requiredBits, delayResolution, 0, maxPossibleDelay);
        shift += delayEncoder.getBits();
        
        return shift;
    }

    protected double getSpeed( OSMWay way )
    {
        String highwayValue = way.getTag("highway");
        Integer speed = defaultSpeedMap.get(highwayValue);
        if (speed == null)
            throw new IllegalStateException(toString() + ", no speed found for: " + highwayValue + ", tags: " + way);

        if (highwayValue.equals("track"))
        {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt))
            {
                Integer tInt = trackTypeSpeedMap.get(tt);
                if (tInt != null)
                    speed = tInt;
            }
        }

        return speed;
    }

    @Override
    public long acceptWay( OSMWay way )
    {
        String highwayValue = way.getTag("highway");
        if (highwayValue == null)
        {
            if (way.hasTag("route", ferries))
            {
                String motorcarTag = way.getTag("motorcar");
                if (motorcarTag == null)
                    motorcarTag = way.getTag("motor_vehicle");

                if (motorcarTag == null && !way.hasTag("foot") && !way.hasTag("bicycle") || "yes".equals(motorcarTag))
                    return acceptBit | ferryBit;
            }
            return 0;
        }

        if ("track".equals(highwayValue))
        {
            String tt = way.getTag("tracktype");
            if (tt != null && !tt.equals("grade1") && !tt.equals("grade2") && !tt.equals("grade3"))
                return 0;
        }

        if (!defaultSpeedMap.containsKey(highwayValue))
            return 0;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return 0;

        // do not drive street cars into fords
        boolean carsAllowed = way.hasTag(restrictions, intendedValues);
        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")) && !carsAllowed)
            return 0;

        // check access restrictions
        if (way.hasTag(restrictions, restrictedValues) && !carsAllowed)
            return 0;

        // do not drive cars over railways (sometimes incorrectly mapped!)
        if (way.hasTag("railway") && !way.hasTag("railway", acceptedRailways))
            return 0;

        return acceptBit;
    }

    @Override
    public long handleRelationTags( OSMRelation relation, long oldRelationFlags )
    {
        return oldRelationFlags;
    }

    @Override
    public long setDouble( long flags, int key, double value )
    {
        if (key == K_DELAY)
        {
            flags = delayEncoder.setDoubleValue(flags, value);
        } else
        {
            throw new UnsupportedOperationException("Unknown key " + key + " for double value.");
        }
        return flags;
    }

    @Override
    public double getDouble( long flags, int key)
    {
        if (key == K_DELAY)
        {
            return delayEncoder.getDoubleValue(flags);
        } else
        {
            throw new UnsupportedOperationException("Unknown key " + key + " for double value.");
        }
    }
    
    @Override
    public long handleWayTags( OSMWay way, long allowed, long relationFlags )
    {
        if (!isAccept(allowed))
            return 0;

        long encoded;
        if (!isFerry(allowed))
        {
            // get assumed speed from highway type
            double speed = getSpeed(way);
            speed = applyMaxSpeed(way, speed, true);

            // limit speed to max 30 km/h if bad surface
            if (speed > 30 && way.hasTag("surface", badSurfaceSpeedMap))
                speed = 30;

            encoded = setSpeed(0, speed);

            boolean isRoundabout = way.hasTag("junction", "roundabout");
            if (isRoundabout)
                encoded = setBool(encoded, K_ROUNDABOUT, true);

            boolean isOneway = way.hasTag("oneway", oneways)
                    || way.hasTag("vehicle:backward")
                    || way.hasTag("vehicle:forward")
                    || way.hasTag("motor_vehicle:backward")
                    || way.hasTag("motor_vehicle:forward");

            if (isOneway || isRoundabout)
            {
                boolean isBackward = way.hasTag("oneway", "-1")
                        || way.hasTag("vehicle:forward", "no")
                        || way.hasTag("motor_vehicle:forward", "no");
                if (isBackward)
                    encoded |= backwardBit;
                else
                    encoded |= forwardBit;
            } else
                encoded |= directionBitMask;

        } else
        {
            encoded = handleFerryTags(way, defaultSpeedMap.get("living_street"), defaultSpeedMap.get("service"), defaultSpeedMap.get("residential"));
            encoded |= directionBitMask;
        }

        return encoded;
    }
    
    @Override
    public void applyWayTags( OSMWay way, EdgeIteratorState edge )
    {
        long flags = edge.getFlags();
        int numTrafficLights = Integer.parseInt(way.getTag("delaySignature"));
        
        // Avoid double delay at big crossings                                                           ___|_|___
        // usually small edges with two traffic lights are just the mittel part of a two lane crossing.  ___|_|___
        // do not count those traffic lights as they are synchronized!                                      | |
        boolean skip = (numTrafficLights>1) && (edge.getDistance()<70);
        if (!skip)
        {
            double delay = calcTrafficLightDelay(numTrafficLights);
            edge.setFlags(setDouble(flags, K_DELAY, delay));
        }
    }

    public double calcTrafficLightDelay(int numTrafficLights)
    {
        if (!delayMap.containsKey("traffic_light"))
        {
            throw new IllegalStateException("No delay value for traffic_lights defined");
        }
        double delay = Math.min(numTrafficLights * delayMap.get("traffic_light"), maxPossibleDelay);
        
        return  delay;
    }
    
    public String getWayInfo( OSMWay way )
    {
        String str = "";
        String highwayValue = way.getTag("highway");
        // for now only motorway links
        if ("motorway_link".equals(highwayValue))
        {
            String destination = way.getTag("destination");
            if (!Helper.isEmpty(destination))
            {
                int counter = 0;
                for (String d : destination.split(";"))
                {
                    if (d.trim().isEmpty())
                        continue;

                    if (counter > 0)
                        str += ", ";

                    str += d.trim();
                    counter++;
                }
            }
        }
        if (str.isEmpty())
            return str;
        // I18N
        if (str.contains(","))
            return "destinations: " + str;
        else
            return "destination: " + str;
    }

    @Override
    public String toString()
    {
        return "car";
    }

    @Override
    public boolean supports( Class<?> feature )
    {
        if (super.supports(feature))
            return true;

        return FastestDelayWeighting.class.isAssignableFrom(feature);
    }
    
}
