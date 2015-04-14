package com.graphhopper.reader;

import com.graphhopper.coll.LongIntMap;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIteratorState;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TLongByteHashMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.set.hash.TLongHashSet;

import java.util.HashMap;

/**
 * Created by jan on 04.04.15.
 */
public class ImportDataLayer
{

    private LongIntMap osmNodeIdToInternalNodeMap;
    private TIntHashSet trafficNodes;
    private TIntIntHashMap edgeTrafficLightCount;
    private TLongLongHashMap barrierAccessFlags;
    private TLongHashSet skippedBarrierEdges;
    private TLongByteHashMap junctionType;
    private TLongByteHashMap junctionWayCount;
    private TIntIntHashMap edgeJunctionSignature;
    private HashMap<String, Byte> roadHierachy = new HashMap<String, Byte>(20);
  

    protected ImportDataLayer( LongIntMap osmNodeIdToInternalNodeMap)
    {
        this.osmNodeIdToInternalNodeMap = osmNodeIdToInternalNodeMap;
        barrierAccessFlags = new TLongLongHashMap(200, .75f, 0, 0);
        trafficNodes = new TIntHashSet(200, 0.75f);
        junctionType = new TLongByteHashMap(200, 0.75f);
        junctionWayCount = new TLongByteHashMap(200, 0.75f);
        edgeTrafficLightCount = new TIntIntHashMap(5);
        edgeJunctionSignature = new TIntIntHashMap(5);
        skippedBarrierEdges = new TLongHashSet(200, 0.75f);

        roadHierachy.put("motorway", (byte) 5);
        roadHierachy.put("motorway_link",(byte) 5);
        roadHierachy.put("motorroad",(byte) 5);
        roadHierachy.put("trunk",(byte) 5);
        roadHierachy.put("trunk_link",(byte) 5);
        roadHierachy.put("primary",(byte) 4);
        roadHierachy.put("primary_link",(byte) 4);
        roadHierachy.put("secondary", (byte) 3);
        roadHierachy.put("secondary_link",(byte) 3);
        roadHierachy.put("tertiary",(byte) 2);
        roadHierachy.put("tertiary_link",(byte) 2);
        roadHierachy.put("unclassified",(byte) 1);
        roadHierachy.put("residential",(byte) 1);
        roadHierachy.put("service", (byte) 1);
        roadHierachy.put("living_street",(byte) 1);
        roadHierachy.put("road",(byte) 1);
        roadHierachy.put("track",(byte) 1);
    }


    public void markBarrierNode( OSMNode osmNode, EncodingManager encodingManager )
    {
        long nodeFlags = encodingManager.handleNodeTags(osmNode);
        if (nodeFlags != 0)
        {
            barrierAccessFlags.put(osmNode.getId(), nodeFlags);
        }
    }

    public void markDelayNode( OSMNode osmNode, EncodingManager encodingManager )
    {
        if (osmNode.hasTag("highway", "traffic_signals"))
        {
            int internalNodeId = osmNodeIdToInternalNodeMap.get(osmNode.getId());
            if (Math.abs(internalNodeId) < 3)
            {
                throw new IllegalStateException("Can't acces node without definitve ID to nodesFlagMap");
            }
            trafficNodes.add(internalNodeId);
        }
    }

    public void markJunctionNode( OSMWay way, long osmNodeId )
    {
        String highwayTag = way.getTag("highway");
        byte highwayType = roadHierachy.containsKey(highwayTag)? roadHierachy.get(highwayTag) : (byte) 0;
        byte currentJunctionType = junctionType.containsKey(osmNodeId)? junctionType.get(osmNodeId) : (byte) 0;
        if (highwayType > currentJunctionType)
        {
            junctionType.put(osmNodeId, highwayType);
            junctionWayCount.adjustOrPutValue(osmNodeId, (byte) (junctionWayCount.get(osmNodeId) + 1), (byte) 1);
        }        
    }
    
    public boolean hasTrafficLight( long osmNodeId )
    {
        int internalNodeId = osmNodeIdToInternalNodeMap.get(osmNodeId);
        if(Math.abs(internalNodeId)<3)
        {
            throw new IllegalStateException("Can't acces node without definitve ID to nodesFlagMap");
        }
        return trafficNodes.contains(internalNodeId);
    }

    public long getNodeBarrierFlags( long osmNodeId, EncodingManager encodingManager )
    {
        if (!skippedBarrierEdges.contains(osmNodeId))
        {
            skippedBarrierEdges.add(osmNodeId);
            return 0;
        } else
        {
            return barrierAccessFlags.get(osmNodeId);
        }
    }

    public void markEdgeTrafficLight( int edgeId, int trafficLightCount )
    {
        edgeTrafficLightCount.put(edgeId, trafficLightCount);
    }

    public void markEdgeJunction( int edgeId, long osmNodeId1, long osmNodeId2)
    {
        byte junction1 = (junctionWayCount.get(osmNodeId1)>2)? junctionType.get(osmNodeId1) : 0;
        byte junction2 = (junctionWayCount.get(osmNodeId2)>2)? junctionType.get(osmNodeId2) : 0;
        int junctionSignature = (junction1) & (junction2 << 8);
        edgeJunctionSignature.put(edgeId, junctionSignature);
    }
    
    public OSMWay tagWaySegment(OSMWay way, EdgeIteratorState edge)
    {
        Integer delaySignature = getDelaySignature(edge);
        way.setTag("delaySignature", delaySignature.toString());
        int junctionSignature = getJunctionSignature(edge);
        Byte junction1 = (byte) junctionSignature;
        Byte junction2 = (byte) (junctionSignature >> 8);
        String highwayTag = way.getTag("highway");
        Byte highwayType = roadHierachy.containsKey(highwayTag)? roadHierachy.get(highwayTag) : (byte) 0;
        way.setTag("edge", highwayType.toString());
        way.setTag("junction1", junction1.toString());
        way.setTag("junction2", junction2.toString());
        return way;
    }


    public int getDelaySignature( EdgeIteratorState edge )
    {
        return edgeTrafficLightCount.remove(edge.getEdge());
    }

    public int getJunctionSignature( EdgeIteratorState edge )
    {
        return edgeJunctionSignature.remove(edge.getEdge());
    }


    public String getSizeInfo()
    {
        String out = "BarriersFlagMap.size: " + barrierAccessFlags.size() 
                     + ", TrafficNodes.size: " + trafficNodes.size()
                     + ", JunctionTypes.size: " + junctionType.size();
        return out;
    }

}



