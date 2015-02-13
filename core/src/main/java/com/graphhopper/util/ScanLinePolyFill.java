package com.graphhopper.util;

import com.graphhopper.reader.LanduseProcessor;

import java.util.*;

/**
 * Created by jan on 16.02.15.
 */
public class ScanLinePolyFill
{
    
    private LinkedList<ScanlineEdge> allEdgesTable = new LinkedList<ScanlineEdge>();
    private TreeSet<ScanlineEdge> activeEdges = new TreeSet<ScanlineEdge>(new Comparator<ScanlineEdge>() 
    {
        @Override
        public int compare(ScanlineEdge scanlineEdge1, ScanlineEdge scanlineEdge2)
        {
            int comparison = Double.compare(scanlineEdge1.xAtscanlineY, scanlineEdge2.xAtscanlineY);
            if (comparison != 0)
            {
                return comparison;
            } else
            {
                return Double.compare(scanlineEdge1.slopeInverse, scanlineEdge2.slopeInverse);
            }
        }
    });
    private boolean allEdgesTableCompleted = false;
    private double globalYmin = Double.MAX_VALUE;
    private SpatialMap spatialMap;
    private byte value = 0;
    
    public ScanLinePolyFill(SpatialMap spatialMap)
    {
        this.spatialMap = spatialMap;
    }
    
    public void setValue(byte value)
    {
        this.value = value;        
    }
    
    public void addEdge( double x1, double x2, double y1, double y2 )
    {
         // ToDo detect crossings of -180,-180 lon and 90,90 lat
        double minY, maxY, xAtminY, slopeInverse;
        double deltaX = x2-x1;
        double deltaY = y2-y1;
        
        int comparisionY = Double.compare(y1, y2);
        if  (comparisionY != 0)
        {
            if (comparisionY<0)
            {
                minY = y1;
                xAtminY = x1;
                maxY = y2;
                slopeInverse = deltaX / deltaY;
            } else  
            {
                minY = y2;
                xAtminY = x2;
                maxY = y1;
                slopeInverse = deltaX / deltaY;
            }
            ScanlineEdge newEdge = new ScanlineEdge(minY, maxY, xAtminY, slopeInverse);
            allEdgesTable.add(newEdge);
            if (Double.compare(minY, globalYmin)<0)
            {
                globalYmin = minY;
            }
        } else
        {
            //skip edge
        }       
    }
    
    public void finalizeAllEdgesTable()
    {
        Collections.sort(allEdgesTable, new Comparator<ScanlineEdge>() 
        {
            @Override
            public int compare(ScanlineEdge scanlineEdge1, ScanlineEdge scanlineEdge2) 
            {
                return Double.compare(scanlineEdge1.minY, scanlineEdge2.minY);
            }
        });
        allEdgesTableCompleted = true;        
    }
    
    public void doScanlineFill()
    {
        ScanlineEdge nextEdge;
        double scanlineY = spatialMap.discretizeY(globalYmin);
        nextEdge = allEdgesTable.pollFirst();
        while (nextEdge != null || !activeEdges.isEmpty())
        {
            //collect active edges
            if (nextEdge != null && Double.compare(nextEdge.minY, scanlineY) <= 0)
            {
                nextEdge.updateX2currentY(scanlineY);
                activeEdges.add(nextEdge);
                nextEdge = allEdgesTable.pollFirst();
            }
            // scan line
            else
            {
                singleLineFill(activeEdges, scanlineY);
                scanlineY += spatialMap.getYStep();
                // update remaining edges
                Iterator<ScanlineEdge> edgeIter = activeEdges.iterator();
                while (edgeIter.hasNext())
                {
                    ScanlineEdge edge = edgeIter.next();
                    if (Double.compare(edge.maxY, scanlineY) <= 0) //edge not active
                    {
                        edgeIter.remove();
                    } else
                    {
                        // adjust x values
                        edge.updateX2currentY(scanlineY);
                    }
                }
            }
        }
    }

    private void singleLineFill( SortedSet<ScanlineEdge> edges, double currentY )
    {
        boolean draw = false;
        double prevY = -Double.MAX_VALUE, prevX = -Double.MAX_VALUE;

        for (ScanlineEdge edge : edges )
        {
            if (edge.xAtscanlineY > prevX)
            {
                if (draw)
                {
                    spatialMap.fillLine(currentY, prevX, edge.xAtscanlineY, value);
                }
                draw = !draw;
                prevX = edge.xAtscanlineY;
            } else //both edges are at same X
            {
                if (((prevY-currentY)*(edge.maxY-currentY))>0)
                {
                    draw = !draw;
                }
            }
            
        }
    }
    
    private class ScanlineEdge {
        private double minY, maxY, x0, xAtscanlineY, slopeInverse;

        private ScanlineEdge( double minY, double maxY, double xAtminY, double slopeInverse )
        {
            this.minY = minY;
            this.maxY = maxY;
            this.x0 = xAtminY;
            this.slopeInverse = slopeInverse;
        }

        private void updateX2currentY(double currentY)
        {
            double deltaY = currentY - minY;
            xAtscanlineY = x0 + slopeInverse*deltaY;
        }
    }

}
