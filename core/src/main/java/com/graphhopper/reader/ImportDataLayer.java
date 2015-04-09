package com.graphhopper.reader;

import com.graphhopper.coll.LongIntMap;
import com.graphhopper.routing.util.EncodingManager;
import gnu.trove.map.hash.TIntByteHashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TLongByteHashMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.set.hash.TLongHashSet;

/**
 * Created by jan on 04.04.15.
 */
public class ImportDataLayer
{

    private LongIntMap osmNodeIdToInternalNodeMap;
    private TIntHashSet trafficNodes;
    private TIntIntHashMap edgeDelaySignature;
    private TLongLongHashMap barrierAccessFlags;
    private TLongHashSet skippedBarrierEdges;


    protected ImportDataLayer( LongIntMap osmNodeIdToInternalNodeMap)
    {

        this.osmNodeIdToInternalNodeMap = osmNodeIdToInternalNodeMap;
        barrierAccessFlags = new TLongLongHashMap(200, .75f, 0, 0);
        trafficNodes = new TIntHashSet(200, 0.75f);
        edgeDelaySignature = new TIntIntHashMap(5);
        skippedBarrierEdges = new TLongHashSet(200, 0.75f);

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
        if (osmNode.hasTag("highway", "traffic_lights"))
        {
            int internalNodeId = osmNodeIdToInternalNodeMap.get(osmNode.getId());
            if (Math.abs(internalNodeId) < 3)
            {
                throw new IllegalStateException("Can't acces node without definitve ID to nodesFlagMap");
            }
            trafficNodes.add(internalNodeId);
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

    public void setTrafficLightSignature( int edgeId, int trafficLightCount )
    {
        edgeDelaySignature.put(edgeId, trafficLightCount);
    }
    
    public int getDelaySignature( int edgeId )
    {
        return edgeDelaySignature.remove(edgeId);
    }
    
    public String getSizeInfo()
    {
        String out = "BarriersFlagMap.size: " + barrierAccessFlags.size() 
                     + ", TrafficNodes.size: " + trafficNodes.size();
        return out;
    }
}



