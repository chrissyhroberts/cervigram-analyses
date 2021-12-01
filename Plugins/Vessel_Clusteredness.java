import ij.*;
import ij.IJ;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.gui.GenericDialog;
import ij.gui.DialogListener;
import ij.measure.Calibration;
import ij.plugin.filter.GaussianBlur;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;
import ij.process.FloodFiller;
import ij.gui.PolygonRoi;
import ij.gui.Wand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ij.plugin.ChannelSplitter;
import ij.gui.ProgressBar;
import ij.plugin.filter.Analyzer;
import ij.measure.ResultsTable;
import ij.plugin.filter.PlugInFilterRunner;
import ij.io.FileSaver;
import ij.io.FileInfo;
import ij.gui.TextRoi;
import ij.gui.ProgressBar;

/** Vessel_Clusteredness.java
  * Analyses clusterdness of blood vessels in cervigrams
  *
  * @author Sigve Holmen
  * @author Holmen Innovative Solutions
  * @author sigve@innovativesolutions.no
  * @version 0.1
 * @license
 * MIT License
 *
 * Copyright (c) 2019 Sigve Holmen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

public class Vessel_Clusteredness implements PlugInFilter {
    private ImagePlus	imp;		// Original image
    private int			width;			// Width of the original image
    private int			height;			// Height of the original image
    public int			n=0,max = 65535; //Number of vessels, max pixel value, foreground value
    private int 		flags = DOES_ALL;
    
    public int setup(String arg, ImagePlus imp){
        this.imp = imp;
        return flags;
    }
  
  		
    public void run(ImageProcessor ip) {
        width = ip.getWidth();
        height = ip.getHeight();

        FileInfo fi = imp.getOriginalFileInfo();
		
		ImageProcessor maskIp = ip.convertToShort(false);
		ImageProcessor probeIp = ip.convertToShort(false);
		
		//Create binary with 1 = max
		for (int w=0;w<width;w++) {
			for (int h=0;h<height;h++) {
				if (ip.get(w,h) > 0) maskIp.set(w,h,max);
			}
		}
		
		//Sectioning
		getSections(maskIp);
		
		double max_dist = Math.sqrt(width*width+height*height);
		
		//Find closest neighbours
		
		
		int[] distClosestNeighbour = new int[n+1];
		
		for (int w=0;w<width;w++) {
			for (int h=0;h<height;h++) {
				int secNr = maskIp.get(w,h);
				if (secNr > 0) {

					//Probe for closest neighbour
					distanceLoop:
					for (int dist = 1; dist < max_dist; dist++) {
						//Check if section has already found closer neighbour
						if (distClosestNeighbour[secNr] > 0 && distClosestNeighbour[secNr] <= dist) break;
						
						for (int x_probe = 0; x_probe <= dist; x_probe++) {
							for (float i = -1; i <= 1; i +=2) {
								float coorX = w+(x_probe*i);
								
								float y_probe = dist - Math.abs(x_probe);
								float coorY = h+y_probe;
								//IJ.log("w = "+w+", h = "+h+", secNr = "+secNr+", dist = "+dist+", coorX = "+coorX+", coorY = "+coorY);
								
								if (inImage(coorX, coorY)) {
							
									int neighbourColor = maskIp.get((int)coorX, (int)coorY);
									if (neighbourColor > 0 && neighbourColor != secNr) {
										distClosestNeighbour[secNr] = dist;
									
										break distanceLoop;
									}
									
									//probeIp.set((int)coorX, (int)coorY, dist);
									
								}
								
								coorY = h-y_probe;
								
								//IJ.log("w = "+w+", h = "+h+", secNr = "+secNr+", dist = "+dist+", coorX = "+coorX+", coorY = "+coorY);
								
								
								if (inImage(coorX, coorY)) {
									int neighbourColor = maskIp.get((int)coorX, (int)coorY);
									if (neighbourColor > 0 && neighbourColor != secNr) {
										distClosestNeighbour[secNr] = dist;
									
										break distanceLoop;
									}
									
									//probeIp.set((int)coorX, (int)coorY, dist);
									
								}
								
							}
						}
					}
				}
			}
        }
		
		//ImagePlus clusterImage = new ImagePlus("Cluster", maskIp);
		//clusterImage.show();
		
		ResultsTable rt = new ResultsTable();
		rt.showRowNumbers(false);

		for(int i=1;i<n;i++) {
			rt.incrementCounter();
//			rt.addValue("Image", fi.fileName);
			rt.addValue("Vessel", (double)i);
			rt.addValue("Distance_closest", (double)distClosestNeighbour[i]);
		}
		rt.show("Results");
		
		
    }
	
	public void getSections(ImageProcessor src) {
		
 		FloodFiller ff = new FloodFiller(src);
    	n = 0;
    	
    	loopSec:
    	for (int y= 0; y < height; y++) {
    		for (int x = 0; x < width; x++) {
    			int pxVal = src.get(x,y);
    			if (pxVal > n) {
    				
    				if (n >= max) break loopSec;
    				
    				src.setValue((double)n);
    				ff.fill8(x,y);
    				n++;
    			}
    		}
    	}
    }
    
    private boolean inImage (float x, float y) {
		return (x >= 0) && (x < width) && (y >= 0) && (y < height);
	}
	
}

