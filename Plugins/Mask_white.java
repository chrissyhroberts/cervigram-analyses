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

/** Mask_white.java 
  * Masks specular reflections by filtering on low saturation values
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

public class Mask_white implements PlugInFilter{
    private ImagePlus	imp;			// Original image
    private int			width;			// Width of the original image
    private int			height;			// Height of the original image	
    private int			size;			// Total number of pixels
    private float[]		rf, gf, bf;		// r, g, b values
    private float[]		c2;				// Sat-values
	private double		SDs;			//Number standard deviations
    private double		upper,lower;	//Failsafe values
    private double		meanSat,SDsat, enlargePx;	//Mean sat value and SD
  
    public int setup(String arg, ImagePlus imp){
        this.imp = imp;
        return DOES_ALL;
    }
  
    public boolean showDialog(){
        GenericDialog gd = new GenericDialog("Masking parameters");
    
        gd.addNumericField("Number of standard deviations below the mean saturation value to be masked:", 1.96, 2);
        gd.addNumericField("Lower limit (failsafe):", 15, 0);
        gd.addNumericField("Upper limit (failsafe):", 75, 0);
        gd.addNumericField("Expand the mask by pixels:", 50, 0);
		
        gd.showDialog();
        if (gd.wasCanceled())
            return false;

        SDs = gd.getNextNumber();
        lower = gd.getNextNumber() / 255;
      	upper = gd.getNextNumber() / 255;
        enlargePx = gd.getNextNumber();
        
        return true;             
    }
  	
    public void run(ImageProcessor ip) {
        if (!showDialog())
            return;
        int offset, i;
        width = ip.getWidth();
        height = ip.getHeight();
        size = width*height;
		
		rf = new float[size];
        gf = new float[size];
        bf = new float[size];

        IJ.showProgress(0f);
		IJ.showStatus("Calculating RGB values");     
        for (int row = 0; row < height; row++){
            offset = row*width;
            for (int col = 0; col < width; col++) {
                i = offset + col;
                int c = ip.get(col, row);
                rf[i] = ((c&0xff0000)>>16)/255f;    //R 0..1
                gf[i] = ((c&0x00ff00)>>8)/255f;     //G 0..1
                bf[i] =  (c&0x0000ff)/255f;         //B 0..1                    
            }
        }
		
        IJ.showProgress(0.3f);
		IJ.showStatus("Calculating Sat values");     
		
		meanSat = 0;
		c2 = new float[size];
       	getSat();
        
        //Clean up
        rf = null;
        gf = null;
        bf = null;
        
        //Calculate SD
        SDsat = 0;
        for (int q = 0; q < size; q++) {
        	SDsat += Math.pow(c2[q] - meanSat, 2);
        }
        SDsat = Math.sqrt(SDsat / size);
        
        double threshold = meanSat - SDs*SDsat;
       	//Check failsafe
       	if (threshold < lower) {
       		threshold = lower;
       		IJ.log(imp.getTitle()+" - lower saturation threshold reached. Resorting to failsafe = "+lower);
       	} else if (threshold > upper) {
       		threshold = upper;
       		IJ.log(imp.getTitle()+" - upper saturation threshold reached. Resorting to failsafe = "+upper);
        }
         //Create empty imageProcessor
        ByteProcessor newIp = new ByteProcessor(width, height);
        newIp.invertLut();
        
        //Mask below threshold
        for (int row = 0; row < height; row++){
            offset = row*width;
            for (int col = 0; col < width; col++) {
                i = offset + col;
                if (c2[i] < threshold)  newIp.set(col, row, 255);
            }
        }
        
        //Expanding mask
        IJ.showProgress(0.5f);
        float progress = 0.5f;
		IJ.showStatus("Calculating Sat values"); 
        /*
        for (i = 0; i < dilates; i++) {
        	newIp.dilate();
        	IJ.showProgress(progress += 0.04f);
        }
        */
        
         //Create imageProcessor of sat-values
        //FloatProcessor satP = new FloatProcessor(width, height, c2);
        
       	//ImagePlus satImage = new ImagePlus("Saturation", newIp);
        //satImage.show();
        
        //Create the ROI polygon
		IJ.showProgress(0.9f);
		IJ.showStatus("Creating the ROI polygon");
		
		newIp.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE); 
		ThresholdToSelection tts = new ThresholdToSelection();
		Roi white_roi = tts.convert(newIp);
        if (white_roi != null) {

            RoiEnlarger re = new RoiEnlarger();
            //	re.enlarge(white_roi, enlargePx);

            imp.setRoi(re.enlarge(white_roi, enlargePx), true);
        }
    }
	
	public void getSat() {                   		// HSV_Stack Plugin (HSV colour space is also known as HSB where B means brightness)        
    												// http://www.easyrgb.com/math.html         
		for(int q=0; q<size; q++) {          		// http://www.easyrgb.com/
			float S=0;
			float var_Min = Math.min(rf[q], gf[q]); //Min. value of RGB
            var_Min = Math.min(var_Min, bf[q]);   
            float var_Max = Math.max(rf[q], gf[q]); //Max. value of RGB
            var_Max = Math.max(var_Max, bf[q]);
            float del_Max = var_Max - var_Min;      //Delta RGB value

			if ( del_Max == 0 ) S =  0f;
			else S = del_Max / var_Max;   

			c2[q] = S;
			meanSat += S;
		}
		meanSat = meanSat / size;
	}
}

