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
 * Created by jan soe on 11.02.15.
 */
public class LanduseProcessor implements SpatialMap 
{
    
    private ArrayList<String> landuseCases = new ArrayList<String>();
    private TLongHashSet landuseOSMnodeIds = new TLongHashSet();
    private TLongObjectHashMap<TIntArrayList> nodes2coord = new TLongObjectHashMap<TIntArrayList>();
    public TLongByteHashMap landuseMap;
    private int latUnits, lonUnits;
    private BBox analyzedArea= BBox.initInverse(false);    
    private LinearKeyAlgo spatialEncoding;
    private int gridsize;
    
    public LanduseProcessor(int gridsizeInMeter)
    {
        gridsize = gridsizeInMeter;
    }
    
    public void setLanduseCases(ArrayList<String> landuseCases)
    {
        this.landuseCases = landuseCases;
    }
    
    public void collectImportantNodes(OSMWay way) 
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
    
    public void collectNodeData(OSMNode node)
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
    
    protected void addNodeInfo(long nodeId, double lat, double lon)
    {
        analyzedArea.update(lat, lon);
        int latInt = Helper.degreeToInt(lat);
        int lonInt = Helper.degreeToInt(lon);
        final TIntArrayList coord = new TIntArrayList(2);
        coord.add(latInt);
        coord.add(lonInt);
        nodes2coord.put(nodeId, coord);
    }
    
    public void initEncoding()
    {
        latUnits = (int) (Math.abs(analyzedArea.maxLat - analyzedArea.minLat)*110000/gridsize);
        lonUnits = (int) (Math.abs(analyzedArea.maxLon - analyzedArea.minLon)*110000/gridsize);
        System.out.println("MaxSize: " + latUnits*lonUnits);
        spatialEncoding = new LinearKeyAlgo(latUnits, lonUnits);
        spatialEncoding.setBounds(analyzedArea);
        // manual estimate of necessary size
        System.out.println(nodes2coord.size()* 250/ gridsize);
        landuseMap = new TLongByteHashMap(nodes2coord.size()* 250/ gridsize, 0.75f);
        System.out.println("Initial size: " + landuseMap.capacity());
    }
    
    public void addPolygon(OSMWay way) 
    {
        String usage = way.getTag("landuse");
        if (usage != null && landuseCases.contains(usage)) 
        {
            byte usageKey = (byte) landuseCases.indexOf(usage); 
            TLongList nodes = way.getNodes();
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
    }
    
    public double getYStep()
    {
        return spatialEncoding.getLatDelta();
    }
    
    public void fillLine(double lat, double lon1, double lon2, byte value)
    {
        //ToDo: Round x1, x2
        long spatialKey1 = spatialEncoding.encode(lat, lon1);
        long spatialKey2 = spatialEncoding.encode(lat, lon2);

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
    
    public int getLength()
    {
        return landuseOSMnodeIds.size();
    }
}
