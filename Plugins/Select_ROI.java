import ij.*;
import ij.IJ;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;
import ij.gui.ProgressBar;
import ij.plugin.frame.RoiManager;
import ij.process.FloatProcessor;
import ij.process.ByteProcessor;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.RoiEnlarger;

/** Select_ROI.java
 * Selects region of interest in a cervigram using a simplified algorithm based on saturation alone
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

public class Select_ROI implements PlugInFilter{
    private ImagePlus	imp;
    private int			width, height, n, foreground, background;
  
    public int setup(String arg, ImagePlus imp){
        this.imp = imp;
        return DOES_ALL;
    }
  
  	
    public void run(ImageProcessor ip) {
        foreground = 255;
        background = 0;
        width = ip.getWidth();
        height = ip.getHeight();
		
		getSections(ip);
		
		int[] sections = new int[n];
		
		for (int y= 0; y < height; y++) {
    		for (int x = 0; x < width; x++) {
    			int pxVal = ip.get(x,y);
    			if (pxVal > background && pxVal < foreground) {
    				sections[pxVal]++;
    			}
    		}
		}
		
		int biggest = 0;
		int biggest_size = 0;
		for (int i = 1; i < n; i++) {
			if (sections[i] > biggest_size) {
				biggest = i;
				biggest_size = sections[i];
			}
		}
		
		for (int y= 0; y < height; y++) {
    		for (int x = 0; x < width; x++) {
    			if (ip.get(x,y) != biggest) ip.set(x,y,background);
    			else ip.set(x,y,foreground);
    		}
		}
		
    }
	
	public void getSections(ImageProcessor src) {
		
 		FloodFiller ff = new FloodFiller(src);
    	n = 1;
    	
    	loopSec:
    	for (int y= 0; y < height; y++) {
    		for (int x = 0; x < width; x++) {
    			int pxVal = src.get(x,y);
    			if (pxVal > n) {
    				
    				if (n >= foreground) break loopSec;
    				
    				src.setValue((double)n);
    				ff.fill(x,y);
    				n++;
    			}
    		}
    	}
    }
}

