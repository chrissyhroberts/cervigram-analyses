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

/** Remove_Vessels.java
  * Removes vessels from a cervigram.
 *
 * @author Sigve Holmen
 * @author Holmen Innovative Solutions AS
 * @author sigve@innovativesolutions.no
 * @version 0.1
 * @license
 * MIT License
 *
 * Copyright (c) 2021 Sigve Holmen
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

public class Remove_Vessels implements ExtendedPlugInFilter, DialogListener {
    private ImagePlus	imp;		// Original image
    private int			width;			// Width of the original image
    private int			height;			// Height of the original image
    private int			size;			// Total number of pixels
    private int			noiseLevel;		//Threshold value noise
    private int			minThick;		//Threshold value min
    private int			maxThick;		//Threshold value max
    public int			n,max;				//Number of vessels
    private int nPasses = 1;
    private int flags = DOES_ALL|KEEP_PREVIEW;

    public void setNPasses(int nPasses) {
        this.nPasses = 1;
    }

    public int setup(String arg, ImagePlus imp){
        this.imp = imp;
        return flags;
    }

    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr){
        GenericDialog gd = new GenericDialog(command);

    	gd.addNumericField("Noise level:", 2, 0);
        gd.addNumericField("Min thickness:", 6, 0);
        gd.addNumericField("Max thickness:", 20, 0);
		gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);

        gd.showDialog();



        if (gd.wasCanceled()) return DONE;

        return 1;
    }

  	 public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        noiseLevel = (int)gd.getNextNumber();
        minThick = (int)gd.getNextNumber();
        maxThick = (int)gd.getNextNumber();

        return true;
    }

    public void run(ImageProcessor ip) {

        int offset, i;
        width = ip.getWidth();
        height = ip.getHeight();
        size = width*height;
		max = 65535;

		//Remove below noise threshold
		//and
		//Create masked image
		ImageProcessor maskIp = ip.convertToShort(false);

		for (int w=0;w<width;w++) {
			for (int h=0;h<height;h++) {

				if (ip.get(w,h) <= noiseLevel) {
					ip.set(w,h,0);
					maskIp.set(w,h,0);
				} else maskIp.set(w,h,max);
			}
		}


		getSections(maskIp);
		int[] sectionSize = new int[n+1];
		//ImagePlus maskImp = new ImagePlus("Sections", maskIp);
		//maskImp.show();

		//IJ.log("n = "+n);

		for (int w=0;w<width;w++) {
			for (int h=0;h<height;h++) {
				int secNr = maskIp.get(w,h);
				if (secNr == 0) continue;

				int size = ip.get(w,h);
				if (size > sectionSize[secNr]) sectionSize[secNr] = size;
			}
		}

		for (int w=0;w<width;w++) {
			for (int h=0;h<height;h++) {
				int secNr = maskIp.get(w,h);
				if (secNr == 0) continue;

				if (sectionSize[secNr] > maxThick || sectionSize[secNr] < minThick) ip.set(w,h,0);
			}
		}

    }

	public void getSections(ImageProcessor src) {

 		FloodFiller ff = new FloodFiller(src);
    	n = 0;

    	loopSec:
    	for (int y= 0; y < height; y++) {
    		for (int x = 0; x < width; x++) {
    			if (src.get(x,y) > n) {
    				n++;

    				if (n >= max) {
    					//IJ.log("n: "+n);
    					//IJ.log("max: "+max);
    					break loopSec;
    				}

    				src.setValue((double)n);
    				ff.fill(x,y);

    			}
    		}
    	}
    }

	public void showAbout() {
		IJ.log("About...");
	}
}
