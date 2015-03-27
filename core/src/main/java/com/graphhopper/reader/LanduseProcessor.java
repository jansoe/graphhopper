package com.graphhopper.reader;

import com.graphhopper.geohash.LinearKeyAlgo;
import com.graphhopper.util.Helper;
import com.graphhopper.util.ScanLinePolyFill;
import com.graphhopper.util.SpatialPixelMap;
import com.graphhopper.util.shapes.BBox;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TLongByteHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;

import java.util.ArrayList;

/**
 * Creats a spatial map of landuse tags, i.e. every spatial grid cell is assigned a landuse key.
 * Gridsize determines fineness of spatial grid in 110000/gridsize degree, such that gridsize
 * roughly resembles fineness in meter.
 * General usage: 
 * (1) collectLanduseNodes -> create a list of nodes necessary to create landuse borders
 * (2) collectNodeData -> collect lat, long values for nodes 
 * (3) initMapFill -> init DataStructures
 * (4) addPolygon -> add Landuse Polygons
 * (5) finishMapFill -> free memory 
 * (6) getUsage -> query LanduseMap
 * <p/>
 * A pixel is filled if both the y coordinate crosses the horizontal midline of that pixel and
 * the x coordinate the vertical midline
 *
 * @author jan soe
 */
public class LanduseProcessor implements SpatialPixelMap
{
    private ArrayList<String> landuseCases = new ArrayList<String>();
    private TLongHashSet landuseOSMnodeIds = new TLongHashSet();
    private TLongObjectHashMap<TIntArrayList> nodes2coord = new TLongObjectHashMap<TIntArrayList>();
    protected TLongByteHashMap landuseMap = new TLongByteHashMap(5, 0.75f);
    private int latUnits, lonUnits;
    private BBox analyzedArea = BBox.createInverse(false);
    private LinearKeyAlgo spatialEncoding;
    private int gridsize;
    private boolean finishedInit = false;

    public LanduseProcessor( int gridsizeInMeter )
    {
        gridsize = gridsizeInMeter;
    }

    public void setLanduseCases( ArrayList<String> landuseCases )
    {
        if (finishedInit)
        {
            throw new IllegalStateException("Can't modify finished landuse map");
        }
        this.landuseCases = landuseCases;
    }

    /**
     * collects OSM IDs of all nodes  in landuse ways
     */
    public void collectLanduseNodes( OSMWay way )
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
    
    public int getMaxTiles()
    {
        return latUnits*lonUnits;
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
    public void initMapFill()
    {
        latUnits = (int) Math.round((Math.abs(analyzedArea.maxLat - analyzedArea.minLat) * 110000 / gridsize));
        lonUnits = (int) Math.round((Math.abs(analyzedArea.maxLon - analyzedArea.minLon) * 110000 / gridsize));
        spatialEncoding = new LinearKeyAlgo(latUnits, lonUnits);
        spatialEncoding.setBounds(analyzedArea);

        // estimate of necessary hashmap size
        landuseMap.ensureCapacity(Math.min((int) (nodes2coord.size() * 250L / gridsize), latUnits * lonUnits));
        finishedInit = true;
    }

    public void finishMapFill()
    {
        nodes2coord = null;
        landuseMap.trimToSize();
    }
    
    /**
     * Adds filled landuse polygon (OSM way) in spatial map
     */
    public boolean addPolygon( OSMWay way )
    {
        String usage = way.getTag("landuse");

        // there are extremly rare cases of simultanious landuse/highway tags, exclude them as no nodeInfo was saved
        if (usage != null && landuseCases.contains(usage) && !way.hasTag("highway"))
        {
            byte usageKey = (byte) landuseCases.indexOf(usage);
            TLongList nodes = way.getNodes();

            ScanLinePolyFill polyFill = new ScanLinePolyFill(this);
            polyFill.setValue(usageKey);
            int ix1 = nodes.size() - 1;
            for (int ix2 = 0; ix2 < nodes.size(); ix2++)
            {

                TIntArrayList coord1 = nodes2coord.get(nodes.get(ix1));
                TIntArrayList coord2 = nodes2coord.get(nodes.get(ix2));
                double lat1 = Helper.intToDegree(coord1.get(0));
                double lon1 = Helper.intToDegree(coord1.get(1));
                double lat2 = Helper.intToDegree(coord2.get(0));
                double lon2 = Helper.intToDegree(coord2.get(1));
                polyFill.addEdge(lon1, lon2, lat1, lat2);
                //do not process polygons which cross the -180,180 jump (ToDo: handle proberly)
                if (Math.signum(lon1) != Math.signum(lon2) && Math.abs(lon1) > 170)
                {
                    return false;
                }
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
     * adds param value at spatial keys for latitude lat and from longitude lon1+lonDelta/2 to lon2+lonDelta/2.
     */
    public void fillLine( double lat, double lon1, double lon2, byte value )
    {
        // fill only if more than half of square is filled
        long spatialKey1 = spatialEncoding.encode(lat, lon1 + spatialEncoding.getLonDelta() / 2);
        long spatialKey2 = spatialEncoding.encode(lat, lon2 - spatialEncoding.getLonDelta() / 2);

        while (Double.compare(spatialKey1, spatialKey2) <= 0)
        {
            landuseMap.put(spatialKey1, value);
            spatialKey1++;
        }
    }
    
    public double discretizeY( double lat )
    {
        return spatialEncoding.roundLat(lat);
    }

    public String getUsage( double lat, double lon )
    {
        if (!finishedInit)
        {
            throw new IllegalStateException("Initialization has to be finished before querying landuse map");
        }
        byte usage = landuseMap.get(spatialEncoding.encode(lat, lon));
        if (usage != landuseMap.getNoEntryValue())
        {
            return landuseCases.get(usage);
        } else
        {
            return "";
        }
    }

    /**
     * visualizes landuse map for debugging purpose. only convinient for small maps
     */
    public void print()
    {
        long key = 0;
        for (int lat = 0; lat < latUnits; lat++)
        {
            for (int lon = 0; lon < lonUnits; lon++)
            {
                String symbol = (landuseMap.contains(key)) ? "x" : "-";
                System.out.print(symbol);
                key++;
            }
            System.out.println(" ");
        }
    }
}
