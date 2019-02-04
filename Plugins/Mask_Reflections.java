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
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;
import ij.gui.ProgressBar;
import ij.plugin.filter.ThresholdToSelection;

/** Mask_Reflections.java
  * Masks specular reflections in a cervigram.
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

public class Mask_Reflections implements PlugInFilter{
    private ImagePlus	imp;		// Original image
    private int			width;			// Width of the original image
    private int			height;			// Height of the original image	
    private int			size;			// Total number of pixels
    private float[]		c1, c2, c3;		// Lab values
    private float[]		rf, gf, bf;		// r, g, b values
    private int			dilates;			//Number of dilates to apply to the mask
  
    public int setup(String arg, ImagePlus imp){
        this.imp = imp;
        if (arg.equals("about"))
        {showAbout(); return DONE;}
        return DOES_ALL;
    }
    
     public boolean showDialog(){
		GenericDialog gd = new GenericDialog("Mask reflections");
		
		gd.addNumericField("Expand mask by:", 20, 0);
        
        gd.showDialog();
        if (gd.wasCanceled())
            return false;

        dilates = (int)gd.getNextNumber();
        
        return true;             
    }
  
    public void run(ImageProcessor ip) {
        if (!showDialog())
            return;
            
        int offset, i;
        width = ip.getWidth();
        height = ip.getHeight();
        size = width*height;

		c1 = new float[size];
        c2 = new float[size];
        c3 = new float[size];
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
		IJ.showProgress(0.1f);
		IJ.showStatus("Converting to HSV");
		getHSV();
        
        rf = null;
        gf = null;
        bf = null;
        
        IJ.showProgress(0.4f);
		IJ.showStatus("Thresholding");
        //Create sat-channel image for thresholding
        ImagePlus satChannelImage=NewImage.createFloatImage("sat_"+imp.getTitle(),width,height,1,NewImage.FILL_WHITE);
    	ImageProcessor satChannelImageP = satChannelImage.getProcessor();
        satChannelImageP.setPixels(c2);
		
		c1 = null;
        c2 = null;
        c3 = null;
		
		ImageStatistics imstat = satChannelImageP.getStatistics();
		float satMean = (float)imstat.mean;
		float sd = (float)imstat.stdDev;
		
		IJ.log("SD: "+sd);
		
		ByteProcessor byteImageP = new ByteProcessor(satChannelImageP,true);
		satChannelImage.flush();
		
		//Set the threshold value
		if (sd < 15) sd = 15; 	//Unless the SD is very low... then we'll use 15
		if (sd > 40) sd = 40; 	//Unless the SD is very high... then we'll use 40
		int threshValue = (int)Math.round((satMean*255) - (sd*255));

		if (threshValue < 20) threshValue = 20;
		
		IJ.log("threshValue: "+threshValue);
		
		byteImageP.threshold(threshValue);
		byteImageP.invertLut();
		
		ImagePlus byteImage= new ImagePlus("mask_"+imp.getTitle(),byteImageP);
		
		IJ.showProgress(0.6f);
		IJ.showStatus("Dilating");
		for (i=0;i<dilates;i++) {
			byteImageP.dilate();
			IJ.showProgress(0.6f+(0.05f*i));
		}
		
		IJ.showProgress(0.9f);
		IJ.showStatus("Creating mask");
		byteImageP.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE); 
		ThresholdToSelection tts = new ThresholdToSelection();
		Roi white_roi = tts.convert(byteImageP);
		byteImage.flush();

		imp.setRoi(white_roi, true);
    }
	
    public void getHSV() {                   // HSV_Stack Plugin (HSV colour space is also known as HSB where B means brightness)        
    // http://www.easyrgb.com/math.html         
		for(int q=0; q<size; q++){          // http://www.easyrgb.com/
			float S=0f;
			float var_Min = Math.min(rf[q], gf[q]); //Min. value of RGB
			var_Min = Math.min(var_Min, bf[q]);
			float var_Max = Math.max(rf[q], gf[q]); //Max. value of RGB
			var_Max = Math.max(var_Max, bf[q]);
			float del_Max = var_Max - var_Min;      //Delta RGB value
	
			if ( del_Max == 0 ) S =  0f;
			else S = del_Max / var_Max;

			c2[q] = S;
		}
	}            
	
	public void showAbout() {
		IJ.log("About...");
	}
}

