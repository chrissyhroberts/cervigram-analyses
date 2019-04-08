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

/** Separate_Vessels.java
 * Separates individual vessels from a binary skeleton
 *
 * @author Sigve Holmen
 * @author Holmen Innovative Solutions AS
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

public class Separate_Vessels implements PlugInFilter {
    private ImagePlus	imp;		// Original image
    private int			width;			// Width of the original image
    private int			height;			// Height of the original image	
    private int			size;			// Total number of pixels
    private int			noiseLevel;		//Threshold value noise
    private int			minThick;		//Threshold value min
    private int			maxThick;		//Threshold value max
    public int			n,max;				//Number of vessels
    public int			foreground, background; //Colour values
    private int 		flags = DOES_ALL;
    private String		origFileName,savePath;
    
   
    
    public int setup(String arg, ImagePlus imp){
        this.imp = imp;
        return flags;
    }
  
    public boolean showDialog(){
        GenericDialog gd = new GenericDialog("Separate vessels");
    	FileInfo fi = imp.getOriginalFileInfo();
    	
    	if (fi != null) {
	    	origFileName = fi.fileName;
    		gd.addStringField("Save output files to:", fi.directory, 100);
      	} else {
      		origFileName = imp.getTitle();
      		
      		gd.addStringField("Save output files to:", IJ.getDirectory("user.home"), 100);
		}	
        gd.addNumericField("Foreground pixel value:", 0, 0);
        gd.addNumericField("Background pixel value:", 255, 0);
        
        gd.showDialog();
        if (gd.wasCanceled()) return false;
		
		savePath = gd.getNextString();
		foreground = (int)gd.getNextNumber();
		background = (int)gd.getNextNumber();
        return true;
    }
  		
    public void run(ImageProcessor ip) {
		if (!showDialog()) return;
            
        int offset;
        width = ip.getWidth();
        height = ip.getHeight();
        size = width*height;
		max = 65535;
		
		ImageProcessor maskIp = ip.convertToShort(false);
		
		//Create binary with 1 = max
		for (int w=0;w<width;w++) {
			for (int h=0;h<height;h++) {
				if (ip.get(w,h) == foreground) maskIp.set(w,h,max);
				else if (ip.get(w,h) == background) maskIp.set(w,h,0);
			}
		}
		
		getSections(maskIp);
		
		//ImagePlus maskImp = new ImagePlus("sections", maskIp);
		//maskImp.show();
		
		//Determine max and min values of coordinates (bounds)
		int[][] sectionSize = new int[n][4];
		
		//Set min values to max
		for (int i=1; i< n; i++) {
			sectionSize[i][0] = width;
			sectionSize[i][2] = height;
		}
		
		for (int w=0;w<width;w++) {
			for (int h=0;h<height;h++) {
				int secNr = maskIp.get(w,h);
				if (secNr == 0) continue;
				
				//Min x
				if (w < sectionSize[secNr][0] || w == 0) sectionSize[secNr][0] = w;
				//Max x
				if (w > sectionSize[secNr][1]) sectionSize[secNr][1] = w;
				
				//Min y
				if (h < sectionSize[secNr][2] || h == 0) sectionSize[secNr][2] = h;
				
				//Max y
				if (sectionSize[secNr][3] < h) sectionSize[secNr][3] = h;
			}
		}
		
		//Transfer vessels to new images
		ArrayList<ImageProcessor> vesselImages = new ArrayList<ImageProcessor>();
		vesselImages.add(ip.createProcessor(0,0));
		
		for (int i = 1; i < n; i++) {
			
			vesselImages.add(ip.createProcessor(
				sectionSize[i][1] - sectionSize[i][0] + 1,
				sectionSize[i][3] - sectionSize[i][2] + 1
				)
			);
			
			vesselImages.get(i).invertLut();
			
			//IJ.log(i+" created width: "+(sectionSize[i][1]  - sectionSize[i][0])+", height: "+(sectionSize[i][3] - sectionSize[i][2]));

		}
		//IJ.log("n = "+n+", vesselImages size: "+vesselImages.size());
		
		for (int w=0;w<width;w++) {
			for (int h=0;h<height;h++) {
				int secNr = maskIp.get(w,h);
				if (secNr == 0) continue;
				
				maskIp.set(w,h,0);
				//IJ.log(secNr+" transferring x: "+(w- sectionSize[secNr][0])+", y: "+(h - sectionSize[secNr][2]));
				//IJ.log("width: "+vesselImages.get(secNr).getWidth()+", height: "+vesselImages.get(secNr).getHeight());
				vesselImages.get(secNr).set(w-sectionSize[secNr][0],h-sectionSize[secNr][2],255);
			}
		}
		
		
		for (int i = 1; i < n; i++) {
			ImagePlus newImp = new ImagePlus(origFileName+"_vessel_"+i, vesselImages.get(i));
			FileSaver newImpSaver = new FileSaver(newImp);
			//IJ.log(savePath+origFileName+"_vessel_"+i+".png");
			newImpSaver.saveAsPng(savePath+origFileName+"_vessel_"+i+".png");
			//newImp.show();
		}
		
    }
	
	public void getSections(ImageProcessor src) {
		
 		FloodFiller ff = new FloodFiller(src);
    	n = 1;
    	
    	loopSec:
    	for (int y= 0; y < height; y++) {
    		for (int x = 0; x < width; x++) {
    			if (src.get(x,y) > n) {
    				
    				if (n >= max) break loopSec;
    				
    				src.setValue((double)n);
    				ff.fill8(x,y);
    				n++;
    			}
    		}
    	}
    }
	
	public void showAbout() {
		IJ.log("About...");
	}
}

