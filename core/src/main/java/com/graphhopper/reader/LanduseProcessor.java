package com.graphhopper.reader;

import com.graphhopper.geohash.LinearKeyAlgo;
import com.graphhopper.util.Helper;
import com.graphhopper.util.ScanLinePolyFill;
import com.graphhopper.util.SpatialMap;
import com.graphhopper.util.shapes.BBox;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.*;
import gnu.trove.set.hash.TLongHashSet;

import java.util.*;

/**
 * Creats a spatial map of landuse tags, i.e. every spatial grid cell is assigned a landuse key.
 * Gridsize determines fineness of spatial grid in 110000/gridsize degree, such that gridsize
 * roughly resembles fineness in meter. 
 * General usage: (1) collectLanduseNodes (2) collectNodeData (3) initLineFill (4) addPolygon
 *
 * A pixel is filled if both the y coordinate crosses the horizontal midline of that pixel and 
 * the x coordinate the vertical midline
 *  
 * @author jan soe
 */
public class LanduseProcessor implements SpatialMap 
{    
    private ArrayList<String> landuseCases = new ArrayList<String>();
    private TLongHashSet landuseOSMnodeIds = new TLongHashSet();
    private TLongObjectHashMap<TIntArrayList> nodes2coord = new TLongObjectHashMap<TIntArrayList>();
    public TLongByteHashMap landuseMap;
    private int latUnits, lonUnits;
    private BBox analyzedArea= BBox.createInverse(false);
    private LinearKeyAlgo spatialEncoding;
    private int gridsize;
    
    public LanduseProcessor( int gridsizeInMeter )
    {
        gridsize = gridsizeInMeter;
    }
    
    public void setLanduseCases( ArrayList<String> landuseCases )
    {
        this.landuseCases = landuseCases;
    }

    /**
     * collects OSM IDs of all nodes  in landuse ways 
     */
    public void collectLanduseNodes(OSMWay way)
    {
        String usage = way.getTag("landuse");
        if (usage != null) 
        {
            if (landuseCases.contains(usage))
            {
                TLongList nodes = way.getNodes();
                landuseOSMnodeIds.addAll(nodes);
            }
        }    
    }

    /**
     * Assigns each node lat, lon values  
     */
    public void collectNodeData( OSMNode node )
    {
        long nodeId = node.getId();
        if (landuseOSMnodeIds.contains(nodeId))
        {
            double lat = node.getLat();
            double lon = node.getLon();
            addNodeInfo(nodeId, lat, lon);
            // remove to save space, or keep to save computation time?
            landuseOSMnodeIds.remove(nodeId);
        }
    }

    /**
     * Assigns each node lat, lon values
     */
    protected void addNodeInfo( long nodeId, double lat, double lon )
    {
        analyzedArea.update(lat, lon);
        int latInt = Helper.degreeToInt(lat);
        int lonInt = Helper.degreeToInt(lon);
        final TIntArrayList coord = new TIntArrayList(2);
        coord.add(latInt);
        coord.add(lonInt);
        nodes2coord.put(nodeId, coord);
    }

    /**
     * Initializes spatial map, i.e. spatial keying according to gridsize and a key-value map with approximate size
     */
    public void initLineFill()
    {
        latUnits = (int) Math.round((Math.abs(analyzedArea.maxLat - analyzedArea.minLat)*110000/gridsize));
        lonUnits = (int) Math.round((Math.abs(analyzedArea.maxLon - analyzedArea.minLon)*110000/gridsize));
        spatialEncoding = new LinearKeyAlgo(latUnits, lonUnits);
        spatialEncoding.setBounds(analyzedArea);

        System.out.println("MaxSize: " + latUnits*lonUnits);
        System.out.println("Size guess:" + nodes2coord.size()* 250/ gridsize);
        
        // estimate of necessary hashmap size
        landuseMap = new TLongByteHashMap(Math.min(nodes2coord.size() * 250 / gridsize, latUnits * lonUnits));
        System.out.println("Initial size: " + landuseMap.capacity());
    }

    /**
     * Adds filled landuse polygon (OSM way) in spatial map
     */
    public boolean addPolygon( OSMWay way )
    {
        String usage = way.getTag("landuse");
        if (usage != null && landuseCases.contains(usage)) 
        {
            byte usageKey = (byte) landuseCases.indexOf(usage); 
            TLongList nodes = way.getNodes();
            // Skip polygon if it crosses -180,180 boundary, ToDo: take care of this bordercase 
            if (Math.signum(analyzedArea.minLon) != Math.signum(analyzedArea.maxLon))
            {
                return false;
            }
            ScanLinePolyFill polyFill = new ScanLinePolyFill(this);
            polyFill.setValue(usageKey);
            int ix1 = nodes.size()-1;
            for (int ix2=0; ix2<nodes.size(); ix2++)
            {
                TIntArrayList coord1 = nodes2coord.get(nodes.get(ix1));
                TIntArrayList coord2 = nodes2coord.get(nodes.get(ix2));
                double lat1 = Helper.intToDegree(coord1.get(0));
                double lon1 = Helper.intToDegree(coord1.get(1));
                double lat2 = Helper.intToDegree(coord2.get(0));
                double lon2 = Helper.intToDegree(coord2.get(1));
                polyFill.addEdge(lon1, lon2, lat1, lat2);
                ix1 = ix2;
            }
            polyFill.finalizeAllEdgesTable();
            polyFill.doScanlineFill();
        }
        return true;
    }

    /**
     * @return latitude rastering step 
     */
    public double getYStep()
    {
        return spatialEncoding.getLatDelta();
    }

    /**
     * adds param value at spatial keys at latitude lat and from longitude lon1+lonDelta/2 to lon2+lonDelta/2.
     * Addition of
     */
    public void fillLine( double lat, double lon1, double lon2, byte value )
    {
        // fill only if more than half of square is filled
        long spatialKey1 = spatialEncoding.encode(lat, lon1 + spatialEncoding.getLonDelta()/2);
        long spatialKey2 = spatialEncoding.encode(lat, lon2 - spatialEncoding.getLonDelta()/2);

        while (Double.compare(spatialKey1,spatialKey2) <= 0)
        {
            landuseMap.put(spatialKey1, value);
            spatialKey1 ++;
        }
    }
    
    public double discretizeY( double lat )
    {
        return spatialEncoding.roundLat(lat);        
    }
    
    public String getUsage( double lat, double lon )
    {
        byte usage = landuseMap.get(spatialEncoding.encode(lat, lon));
        if (usage != landuseMap.getNoEntryValue())
        {
            return landuseCases.get(usage);
        } else
        {
            return "";
        }
    }

    public void print()
    {
        long key = 0;
        for(int lat = 0; lat < latUnits; lat++)
        {
            for(int lon=0; lon< lonUnits; lon++)
            {
                String symbol = (landuseMap.contains(key))? "x" : "-";
                System.out.print(symbol);
                key++;
            }
            System.out.println(" ");
        }
    }
}
