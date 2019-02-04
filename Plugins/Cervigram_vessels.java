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
import ij.process.ByteProcessor;
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

/** Cervigram_vessels.java
  * Detects vessels on a cervigram.
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

public class Cervigram_vessels implements PlugInFilter{
    private ImagePlus	imp;		// Original image
    private int			width;			// Width of the original image
    private int			height;			// Height of the original image	
    private int			size;			// Total number of pixels
    private float[]		c1, c2, c3;		// Lab values
    private float[]		rf, gf, bf;		// r, g, b values
    private int			method;			// Method of detection
    private double		threshVal;		//Threshold value
    private double		threshValGrowth;	//Threshold for growth
    private double		threshValPercent;	//Absolute lower threshold for inclusion of pixels
    private double		Gauss;			//Gauss smoothing
    private double		GaussX;			//Gauss smoothing X-axis
    private double		GaussY;			//Gauss smoothing Y-axis
    private double		circularity;			//Circularity of mask expansion in percent
	private double		failsafe;
	private int			nmbAngles;		//Number of rays
	private int			binSize;		//Binsize of pixels for detecting cervix border
	private boolean		showAC;			//Show mapping
  
    public int setup(String arg, ImagePlus imp){
        this.imp = imp;
        if (arg.equals("about"))
        {showAbout(); return DONE;}
        return DOES_ALL;
    }
  
    public boolean showDialog(){
        GenericDialog gd = new GenericDialog("ROI detection method");
    
        gd.addNumericField("Gauss smoothing:", 35f, 0);
        gd.addNumericField("Lower threshold of a-channel in percent of maximum:", 0.65f, 2);
        gd.addNumericField("Percent drop in brightness to trigger boundary detection:", 0.2f, 2);
        gd.addNumericField("Number of rays for growth:", 50, 0);
        gd.addNumericField("Binsize of pixels for cervix border detection:", 50, 0);
		gd.addNumericField("Failsafe, abort if result area is less than:", 0.1f, 2);
		gd.addCheckbox("Show a-channel and centrality mapping", false);
		
        gd.showDialog();
        if (gd.wasCanceled())
            return false;

        GaussX = gd.getNextNumber();
        GaussY = GaussX;
        threshValPercent = gd.getNextNumber();
        threshValGrowth = gd.getNextNumber();
      	nmbAngles = (int)gd.getNextNumber();
        binSize = (int)gd.getNextNumber();
        failsafe = gd.getNextNumber();
        showAC = gd.getNextBoolean();
        
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
		IJ.showStatus("Converting to Lab");
		getLab();
        
        rf = null;
        gf = null;
        bf = null;
        
        //Create a-channel image for smoothing
        ImagePlus aChannelImageSmooth=NewImage.createFloatImage("a-channel_smooth"+imp.getTitle(),width,height,1,NewImage.FILL_BLACK);
    	ImageProcessor aChannelImageSmoothP = aChannelImageSmooth.getProcessor();
        aChannelImageSmoothP.setPixels(c2);

		c1 = null;
        c2 = null;
        c3 = null;
		
 		//Blur a channel
        GaussianBlur gb = new GaussianBlur();
      	gb.blurGaussian(aChannelImageSmoothP, GaussX, GaussY, 0.02f);
      	
 		IJ.showProgress(0.2f);
 		IJ.showStatus("Creating new array of alpha to centrality");
 		//Create new array of alpha to centrality
 		//a-channel		
		ImageStatistics imstat = aChannelImageSmoothP.getStatistics();
		float aMin = (float)imstat.min;
		float aMax;
		if (aMin < 0) aMax = (float)imstat.max + Math.abs(aMin);
		else aMax = (float)imstat.max;
		
		float cMax = (float)(Math.sqrt(Math.pow((width/2), 2) + Math.pow((height/2), 2)));
	
		int w;
		int h;
		double oldCloseDistZero = (float)(Math.sqrt(Math.pow((width), 2) + Math.pow((height), 2)));
		double oldFarDistZero = 0f;
		double distZero;
		int[] closestPixel = new int[2];
		int[] farthestPixel = new int[2];
		
		//Map pixels between old and new dimension
		int[][][] mappedPixels = new int[width][height][2];
		
		ImagePlus imageAc=NewImage.createShortImage("Centrality_a-channel_"+imp.getTitle(),width,height,1,NewImage.FILL_BLACK);
        ImageProcessor imageAcP = imageAc.getProcessor();
		int oldValue;
		float avalue, centrality;
		
		for (w=0; w<width; w++) {
			for (h=0;h<height;h++) {
				
				
				if (aMin < 0) avalue = height-1-(((aChannelImageSmoothP.getf(w,h) + Math.abs(aMin))/aMax)*(height-1));
				else avalue = height-1-((aChannelImageSmoothP.getf(w,h)/aMax)*(height-1));
				
				centrality = (float)((Math.sqrt(Math.pow((w - width/2), 2) + Math.pow((h - height/2), 2)))/cMax)*(width-1);
				
				//IJ.log((int)Math.floor(centrality)+"	"+(int)avalue);
				if (inImage((int)Math.round(centrality),(int)Math.floor(avalue))) {
					oldValue = imageAcP.get((int)Math.round(centrality),(int)Math.floor(avalue));
					imageAcP.set((int)Math.round(centrality),(int)Math.floor(avalue),oldValue+1);
					mappedPixels[w][h][0] = (int)Math.round(centrality);
					mappedPixels[w][h][1] = (int)Math.round(avalue);
				} else IJ.log("Out of bounds width:	"+centrality+"	height:	"+avalue);
				
				//Detect the pixel closest to upper left corner (0,height)
				distZero = Math.sqrt(Math.pow(centrality, 2) + Math.pow(avalue, 2));
				if (distZero < oldCloseDistZero) {
					closestPixel[0] = (int)Math.round(centrality);
					closestPixel[1] = (int)Math.round(avalue);
					oldCloseDistZero = distZero;
				}
				
				if (distZero > oldFarDistZero) {
					farthestPixel[0] = (int)Math.round(centrality);
					farthestPixel[1] = (int)Math.round(avalue);
					oldFarDistZero = distZero;
				}
				
			}
		}
		
		
		IJ.showProgress(0.3f);
		IJ.showStatus("K-means clustering");
		
		ImagePlus imageGroups=NewImage.createByteImage("Grouped_"+imp.getTitle(),width,height,1,NewImage.FILL_BLACK);
        ImageProcessor imageGroupsP = imageGroups.getProcessor();
		
		double distClosest,distFarthest;
		float accClosestW,accClosestH,accFarthestW,accFarthestH;
		int nClosest, nFarthest;
		int[][] classifiedPixels = new int[width][height];
		int pixelWeight,s;
		
		//Classify pixels in two groups based on distance from closest and farthest pixels
		for (s=0; s<20;s++) {
			nClosest = 0;
			nFarthest = 0;
			accClosestW = 0f;
			accClosestH = 0f;
			accFarthestW = 0f;
			accFarthestH = 0f;
			for (w=0; w<width; w++) {
				for (h=0;h<height;h++) {
					pixelWeight = imageAcP.get(w,h);
					if (pixelWeight > 0) {
						//Calculate the distance from each of the centroids
						distClosest = Math.sqrt(Math.pow(w-closestPixel[0], 2) + Math.pow(h-closestPixel[1], 2));
						distFarthest = Math.sqrt(Math.pow(w-farthestPixel[0], 2) + Math.pow(h-farthestPixel[1], 2));
						
						//Classify in correct cluster
						if (distClosest <= distFarthest) {
							classifiedPixels[w][h] = 1;
							imageGroupsP.set(w,h,255);
						} else {
							imageGroupsP.set(w,h,127);
						}
						
						//Adjust for pixel weight
						for(i=0;i<pixelWeight;i++) {
							if (distClosest <= distFarthest) {
								accClosestW += w;
								accClosestH += h;
								nClosest++;
							} else {
								accFarthestW += w;
								accFarthestH += h;
								nFarthest++;
							}
						}
					} else classifiedPixels[w][h] = 0;
				}
			}
			
			//Check if the clustering is converging
			if ((int)(accClosestW/nClosest) - closestPixel[0] <= 2 & (int)(accClosestH/nClosest)- closestPixel[1] <= 2) break;
			
			//Set new centroids
			closestPixel[0] = (int)(accClosestW/nClosest);
			closestPixel[1] = (int)(accClosestH/nClosest);
			farthestPixel[0] = (int)(accFarthestW/nClosest);
			farthestPixel[1] = (int)(accFarthestH/nClosest);

		}
		
		//Clean up
		closestPixel = null;
		farthestPixel = null;
		
		IJ.showProgress(0.4f);
		IJ.showStatus("Transferring back to original space");
		ImagePlus imageMapped=NewImage.createByteImage("Mapped_"+imp.getTitle(),width,height,1,NewImage.FILL_BLACK);
        ImageProcessor imageMappedP = imageMapped.getProcessor();
		
		i = 0;
		//Transfer back to original space
		for (w=0; w<width; w++) {
			for (h=0;h<height;h++) {
				
				if (height-mappedPixels[w][h][1] > height*threshValPercent && classifiedPixels[mappedPixels[w][h][0]][mappedPixels[w][h][1]] == 1) {
					
					imageMappedP.set(w,h,255);
				}
			}
		}
		
		//Clean up
		mappedPixels = null;
		classifiedPixels = null;
		
		IJ.showProgress(0.5f);
		IJ.showStatus("Filling cluster holes");
		//Fill holes
		//Adapted from Binary fill developed by Gabriel Landini, G.Landini at bham.ac.uk
		int background = 0;
		int foreground = 255;
        FloodFiller ff = new FloodFiller(imageMappedP);
        imageMappedP.setColor(127);
        for (int y=0; y<height; y++) {
            if (imageMappedP.getPixel(0,y)==background) ff.fill(0, y);
            if (imageMappedP.getPixel(width-1,y)==background) ff.fill(width-1, y);
        }
        for (int x=0; x<width; x++){
            if (imageMappedP.getPixel(x,0)==background) ff.fill(x, 0);
            if (imageMappedP.getPixel(x,height-1)==background) ff.fill(x, height-1);
        }
        byte[] pixels = (byte[])imageMappedP.getPixels();
        int n = width*height;
        for (i=0; i<n; i++) {
        if (pixels[i]==127)
            pixels[i] = (byte)background;
        else
            pixels[i] = (byte)foreground;
        }
        
		IJ.showProgress(0.6f);
		IJ.showStatus("Sectioning of clusters");
		//Keep only largest section
		//Identify sections
		getSections(imageMappedP);

		//Count size of sections
		int currentPixel;
		int[] sections = new int[255];
		for (w=0;w<width;w++) {
			for (h=0;h<height;h++) {
				currentPixel = imageMappedP.get(w,h);
				if (currentPixel > 0 && currentPixel < 255) sections[currentPixel]++;
			}
		}
		int largestSection = 1;
		for (i=2;i<255;i++) {
			if (sections[i] > sections[largestSection]) largestSection = i;
		}
		//IJ.log("largestSection: "+largestSection);
		sections = null;
		
		//Delete other sections and find mid-point of the largest section
		float maskSumX = 0;
		float maskSumY = 0;
		for (w=0;w<width;w++) {
			for (h=0;h<height;h++) {
				currentPixel = imageMappedP.get(w,h);
				if (currentPixel > 0 && currentPixel != largestSection) imageMappedP.set(w,h,0);
				else if (currentPixel == largestSection) {
					imageMappedP.set(w,h,255);
					i++;
					maskSumX += w;
					maskSumY += h;
				}
			}
		}
		
		float maskMeanX = maskSumX/i;
		float maskMeanY = maskSumY/i;
		IJ.showProgress(0.7f);
		IJ.showStatus("Detecting ROI boundaries");
		
		//Propagate rays to until pixels with value-difference below the growth-threshold
		double y;
		int x;
		double curAngle;
		
		float lastFiveSum;
		//int nmbAngles = 100;
		int coorX,coorY,j,k,l,pixelNumber;
		
		int[] exhausted = new int[nmbAngles];
		float[] lastFiveMean = new float[nmbAngles];
		float[] oldMean = new float[nmbAngles];
		double angles= 2*(Math.PI)/nmbAngles;
		int endPoints[][] = new int[2][nmbAngles];
		float[] sortingArray = new float[nmbAngles];
		int diagonal = (int)Math.round(Math.sqrt((Math.pow(width,2))+(Math.pow(height,2))));
		float[][] rayPixels = new float[nmbAngles][diagonal];
		ArrayList<Ray> rayPoints = new ArrayList<Ray>(nmbAngles);
	//	int binSize = 15;
		
		//Create new image of a-channel for border detection
        //ImagePlus aChannelImage=NewImage.createFloatImage("a-channel"+imp.getTitle(),width,height,1,NewImage.FILL_BLACK);
    	//ImageProcessor aChannelImageP = aChannelImage.getProcessor();
        //aChannelImageP.setPixels(c2_2);
		
		//Create new image of greyscale from RGB
		ImagePlus greyImage=NewImage.createByteImage("grey_"+imp.getTitle(),width,height,1,NewImage.FILL_BLACK);
        greyImage.setProcessor(ip.convertToByte(false));
 		ImageProcessor greyImageP = greyImage.getProcessor();
		
		//Create rays
		for (i=0;i<nmbAngles;i++) {
			curAngle = angles*i;
			rayPoints.add(new Ray(i,curAngle,maskMeanX,maskMeanY));
		}
		
		//Sort the rays
		Collections.sort(rayPoints);
		
		//Get pixel values for all rays and determine end points
		for (i=0;i<rayPoints.size();i++) {
			l = 0;
			Ray currentRay = rayPoints.get(i);
			
			//Go through a number of pixels corresponding to the diagonal (theoretical maximum of pixels in a ray)
			for (j=0;j<diagonal;j++) {
				
				coorX = currentRay.getPixelX(j);
				coorY = currentRay.getPixelY(j);
				
				//Set the end point if at the border of the image and still not exhausted
				if ((coorX == width-1 || coorX == 0 || coorY == height-1 || coorY == 0) && exhausted[i] != 1) {
					
					exhausted[i] = 1;
					//imageMappedP.set(coorX, coorY, 255); //Endpoint

					currentRay.endPointX = coorX;
					currentRay.endPointY = coorY;
					
					break;
					
				} else if (inImage(coorX, coorY))	{
					//Skip to next pixel if the current pixel is in the mask OR if the status is exhausted and the pixel is not in the mask
					if ((imageMappedP.getf(coorX,coorY) > 0 & exhausted[i] != 1) || (exhausted[i] == 1 & imageMappedP.getf(coorX,coorY) == 0)) continue;
					else if (imageMappedP.getf(coorX,coorY) > 0 & exhausted[i] == 1) {
						//If the mask is present in the ray again, reset exhausted and mean
							exhausted[i] = 0; 
							oldMean[i] = 0;
							l = 0;
							continue;
					} else {
						l++;
						rayPixels[i][j] = greyImageP.getf(coorX,coorY);
						
						if (l==binSize) {
							lastFiveSum = 0;
							pixelNumber = 0;
							
							for(k=j;k>j-binSize;k--) {
								if (rayPixels[i][k] != 0) {
									lastFiveSum += rayPixels[i][k];
									pixelNumber++;
								}
							}
							
							lastFiveMean[i] = lastFiveSum/pixelNumber;
					//		if (i == 49) IJ.log(lastFiveMean[i]+"	"+oldMean[i]);
							
							if (oldMean[i]/lastFiveMean[i] > (1+threshValGrowth)) {
								exhausted[i] = 1;
								//imageMappedP.set(coorX, coorY, 255); //Endpoint

								currentRay.endPointX = currentRay.getPixelX(j-binSize);
								currentRay.endPointY = currentRay.getPixelY(j-binSize);;
							}
							oldMean[i] = lastFiveMean[i];
							l = 0;
						}
						imageMappedP.set(coorX, coorY, 100); //Rays
					}
				} else break;
			}
		}
		//Clean up
		lastFiveMean = null;
		oldMean = null;
		sortingArray = null;
		rayPixels = null;
		exhausted = null;
		
		IJ.showProgress(0.8f);
		IJ.showStatus("Smoothing ROI");
					
		//Find mean ray length
		double sumRaylengths = 0f;
		
		for(i=0;i<rayPoints.size();i++) {
			Ray currentRay = rayPoints.get(i);
			sumRaylengths += currentRay.getLength();
		}
		
		double rayMean = sumRaylengths/nmbAngles;
		double rayLength;
		int[][] endPointsFinalArray = new int[2][nmbAngles];
		j = 0;
		double fiveRayBinSum;
		double fiveRayBinMean;
		int ep = 0;
		//Normalize rays to smooth the polygon boundary
		for(i=0;i<rayPoints.size();i++) {
			fiveRayBinSum = 0;
			for (j=-2;j<=2;j++) {
				k = i+j;
				if (k < 0) k = rayPoints.size() + j;
				else if (k >= rayPoints.size()) k = j-1;
				
				Ray currentRay = rayPoints.get(k);
				fiveRayBinSum += currentRay.getLength();
			}
			
			Ray currentRay = rayPoints.get(i);
			fiveRayBinMean = fiveRayBinSum / 5f;
			if (currentRay.getLength() > fiveRayBinMean) {
				currentRay.setLength((int)(Math.round(fiveRayBinMean)));
			}
			
		//	IJ.log(i+"	"+currentRay.Angle);
			
			if (inImage(currentRay.endPointX,currentRay.endPointY)) {
				endPointsFinalArray[0][ep] = currentRay.endPointX;
				endPointsFinalArray[1][ep] = currentRay.endPointY;
				imageMappedP.set(currentRay.endPointX, currentRay.endPointY, 255);
				ep++;
			}
		}
		
		IJ.showProgress(0.9f);
		IJ.showStatus("Creating the ROI polygon");
		//Create the ROI polygon
		
		Roi polyRoi = new PolygonRoi(endPointsFinalArray[0], endPointsFinalArray[1], ep, Roi.FREEROI);
		imp.setRoi(polyRoi);
		
		endPointsFinalArray = null;
		
		IJ.showStatus("Evaluating area of polygon");
		
		Analyzer imageAnalyser = new Analyzer(imp);
		imageAnalyser.measure();
		ResultsTable analysisResults = imageAnalyser.getResultsTable();
		double polyArea = analysisResults.getValueAsDouble(0,0);

		if (((float)polyArea / (float)(width*height)) < failsafe) {
			imp.setRoi(0,0,width,height);
			IJ.showStatus("Failure to detect ROI. Resorting to failsafe");
		} else IJ.showStatus("Success");
		
		IJ.showProgress(1f);
		

	if (showAC) {
		imageAc.show();
		IJ.resetMinAndMax();
	}
	//	imageMapped.show();
		//imageGroups.show();
	//	aChannelImageSmooth.show();

	//	aChannelImage.show();
		
		//RedChannelImage.show();
		
     	

    }
	
	private final boolean inImage (int x, int y) {
		return (x >= 0) && (x < width) && (y >= 0) && (y < height);
	}
	
	public class Ray implements Comparable<Ray>{
		public int rayNumber;
		public double xValue;
		public double yValue;
		public double Angle;
		public int endPointX;
		public int endPointY;
		public int Direction;
		
		public Ray(int raynumber, double angle, double coorX, double coorY) {
			rayNumber = raynumber;
			Angle = angle;
			xValue = coorX;
			yValue = coorY;
			if (Angle > 90 && Angle < 270) Direction = -1;
			else if (Angle == 90 || Angle == 270) Direction = 0;
			else Direction = 1;
		}
		
		public int compareTo(Ray compFloat) {
			if (Angle > compFloat.yValue) return 1;
			else if (Angle == compFloat.yValue) return 0;
			else return -1;
		}
		
		public int getY(int x) {
			return (int)Math.round(x*((Math.sin(Angle))/(Math.cos(Angle))) + yValue);
		}
		
		public int getX(int y) {
			return (int)Math.round(y*((Math.cos(Angle))/(Math.sin(Angle))) + xValue);
		}
		
		public double getLength() {
			return Math.sqrt(Math.pow(xValue-endPointX, 2)+Math.pow(yValue-endPointY,2));
		}
		
		public void setLength(int newLength) {
			endPointX = getPixelX(newLength);
			endPointY = getPixelY(newLength);
		}
		
		public int getPixelX(int i) {
			return (int)(Math.round(i*(Math.cos(Angle))) + xValue);
		}
		public int getPixelY(int i) {
			return (int)(Math.round(i*(Math.sin(Angle))) + yValue);
		}
	}
	
    public void getLab(){
        // http://www.easyrgb.com/math.html          AND
        // @INBOOK{RonnierLuoM98colour:chapterbook,
        //  chapter = {Colour science},
        //  pages = {27-65},
        //  title = {The Colour Image Processing Handbook},
        //  publisher = {Springer},
        //  year = {1998},
        //  editor = {R. E.N. Horne},
        //  author = {Ronnier Luo},
        //}        
        for(int q=0; q<size; q++){
            float l, a, b;
            //rf[q] = (rf[q] > 0.04045f)?(new Double(Math.exp(Math.log((rf[q]+0.055f)/1.055f)*2.4f))).floatValue():rf[q]/12.92f;
            rf[q] = (rf[q] > 0.04045f)?(float)(Math.exp(Math.log((rf[q]+0.055f)/1.055f)*2.4f)):rf[q]/12.92f;
            
            //gf[q] = (gf[q] > 0.04045f)?(new Double(Math.exp(Math.log((gf[q]+0.055f)/1.055f)*2.4f))).floatValue():gf[q]/12.92f;
            gf[q] = (gf[q] > 0.04045f)?(float)(Math.exp(Math.log((gf[q]+0.055f)/1.055f)*2.4f)):gf[q]/12.92f;
            
            //bf[q] = (bf[q] > 0.04045f)?(new Double(Math.exp(Math.log((bf[q]+0.055f)/1.055f)*2.4f))).floatValue():bf[q]/12.92f;
			bf[q] = (bf[q] > 0.04045f)?(float)(Math.exp(Math.log((bf[q]+0.055f)/1.055f)*2.4f)):bf[q]/12.92f;

            rf[q] = rf[q] * 100f;
            gf[q] = gf[q] * 100f;
            bf[q] = bf[q] * 100f;

            float X = 0.4124f * rf[q] + 0.3576f * gf[q] + 0.1805f * bf[q];
            float Y = 0.2126f * rf[q] + 0.7152f * gf[q] + 0.0722f * bf[q];
            float Z = 0.0193f * rf[q] + 0.1192f * gf[q] + 0.9505f * bf[q];
            
            float fX, fY, fZ;
            //float La, aa, bb;            
            
            // XYZ to Lab
            if ( X > 0.008856f )
                //fX = (new Double(Math.exp(Math.log(X)/3f))).floatValue();
                fX =  (float)Math.exp(Math.log(X)/3f); 
            else fX = ((7.787f * X) + (16f/116f)); 

            if ( Y > 0.008856f )
           // fY = (new Double(Math.exp(Math.log(Y)/3f))).floatValue(); 
           	fY =  (float)Math.exp(Math.log(Y)/3f); 
            else fY = ((7.787f * Y) + (16f/116f));

            if ( Z > 0.008856f )
          //  fZ =  (new Double(Math.exp(Math.log(Z)/3f))).floatValue(); 
            fZ =  (float)Math.exp(Math.log(Z)/3f); 
            else fZ = ((7.787f * Z) + (16f/116f)); 

            //l = ( 116f * fY ) - 16f;
            a = 500f * ( fX - fY );
            //b = 200f * ( fY - fZ );
            
            //c1[q] = l;
            c2[q] = a;
            //c3[q] = b;

        }            
    }          
	
	public void getSections(ImageProcessor src) {
 		FloodFiller ff = new FloodFiller(src);
    	int n = 1;
    	int y,x;
    	yLoop:
    	for (y= 0; y< height; y++) {
    		for (x = 0; x< height; x++) {
    			if (src.get(x,y) == 255) {
    				src.setValue((float)n);
    				ff.fill(x,y);
    				n++;
    				if (n>254) break yLoop;
    			}
    		}
    	}
    }
	
	public void showAbout() {
		IJ.log("About...");
	}
}

