//Works with version 1.52
// This macro will analyse the following parameters in colour cervigrams:
// - number of vessels identified (n)
// - vessel clusteredness as mean distance between vessels and the SD
// - the result of a regression model representing "clusteredness"
// - number of template matches for circular features
// This macro will analyse the following parameters in colour cervigrams:
// - number of vessels identified (n)
// - vessel clusteredness as mean distance between vessels and the SD
// - the result of a regression model representing "clusteredness"
// - number of template matches for circular features

// Requires the java-plugins:
// - Vessel_Clusteredness.java
// - Mask_white.java
// - Remove_vessels.java
// - Select_ROI.java

Dialog.create("Convolution template");
Dialog.addMessage("Please select a convolution template, then input and output folders");
Dialog.show();
setBatchMode(true);
open(); // E.g. circle_120_thin_4096.png
rename("convolution");
run("Colors...", "foreground=black background=white selection=yellow");
run("Canvas Size...", "width=4096 height=4096 position=Center");

dir1 = getDirectory("Choose Source Directory ");
list = getFileList(dir1);
dir2 = getDirectory("Choose Destination Directory ");

run("Input/Output...", "jpeg=90");

//Colour analysis
erode = 0;
dilate = 5;
outliers = 0;
noiseValue = 2;
minValue = 6;	//Defines the minimum thickness required to qualify as a vessel
maxValue = 60;	//Defines the thickest allowable value for a vessel

//Image size
standard_width = 3096;

//Output intermediate images?
output_img = false;

getDateAndTime(year, month, dayOfWeek, dayOfMonth, hour, minute, second, msec);

filepath = dir2 +"results_"+year+"."+(month+1)+"."+dayOfMonth+"_"+hour+"."+minute+".txt";

skrivefil = File.open(filepath);
print(skrivefil, "File	mean_Clusteredness	sd	n	regr	templateMatches");
File.close(skrivefil);

//Create output dir for vessels and main images
exec("mkdir", dir2 + "output_images");
dir2_images = dir2 + "output_images/";
exec("mkdir", dir2 + "output_vessels");
dir2_images = dir2 + "output_vessels/";
exec("mkdir", dir2 + "clusteredness");
dir2_cluster = dir2 + "clusteredness/";


for (img=0; img<list.length; img++) {
	run("Set Measurements...", "area redirect=None decimal=3");
	run("Clear Results");
	roiManager("reset");
	
	open(dir1+list[img]);
	theWidth = getWidth();
	theHeight = getHeight();
	if (theWidth > standard_width) {
		theHeight = theHeight * (standard_width / theWidth);
		theWidth = standard_width;
		
		run("Scale...", "x=- y=- width="+theWidth+" height="+theHeight+" interpolation=Bilinear average create title=resized");
		
		selectWindow(list[img]);
		close();
		
		selectWindow("resized");
		rename(list[img]);
	}

	run("Duplicate...", "title=sr");
	
	
	selectWindow("sr");
	
	//Mask specular reflections
	run("Mask white", "number=1.96 lower=15 upper=50 enlargePx=50");
	setBackgroundColor(0, 0, 0);
	if (selectionType() > -1) {
		run("Clear", "slice");
		run("Make Inverse");
		roiManager("add");
		run("Select None");
	}
	close();
	
	selectWindow(list[img]);

//HSV-colour space
	run("Duplicate...", "title=HSV");
	run("HSB Stack");
	run("Convert Stack to Images");
	
	selectWindow("Hue");
	close();
	selectWindow("Brightness");
	close();

//Normalize Sat channel	
	selectWindow("Saturation");
	run("Duplicate...", "title=Saturation-1");
	run("Gaussian Blur...", "sigma=40");
	run("Invert");
	
	imageCalculator("Add create 32-bit", "Saturation", "Saturation-1");
	if (output_img) { saveAs("PNG", dir2_images+list[img] + "_saturation.png"); }
	rename("S");
	
	selectWindow("Saturation");
	close();
	selectWindow("Saturation-1");
	close();
	

//RGB colour space
	selectWindow(list[img]);
	run("Duplicate...", "title=RGB");
	run("RGB Stack");
	run("Convert Stack to Images");

	

//ROI using the red and green and blue channels
//Blue (remove ectopy)
	selectWindow("Blue");
	run("Gaussian Blur...", "sigma=20");
	getStatistics(tmp, blue_mean, blue_min, blue_max, blue_sd);
	blueLower = blue_mean - 0.5 * blue_sd;
	blueUpper = blue_max;
	setThreshold(blueLower, blueUpper);
	run("Convert to Mask");
	run("Create Selection");
	roiManager("add");
	close();

//Red
	selectWindow("Red");
	run("Gaussian Blur...", "sigma=15");
	getStatistics(tmp, red_mean, red_min, red_max, red_sd);
	redLower = red_mean;
	redUpper = red_max;
	setThreshold(redLower, redUpper);
	run("Convert to Mask");
	run("Create Selection");
	roiManager("add");
	close();

//Green
	selectWindow("Green");
	run("Duplicate...", "title=Green-1tmp");

	run("Gaussian Blur...", "sigma=40");
	run("Duplicate...", "title=Green-1"); //Save for normalization

	selectWindow("Green-1tmp");
	getStatistics(tmp, green_mean, green_min, green_max, green_sd);
	greenLower = green_min;
	greenUpper = green_mean + 1.96*green_sd;
	setThreshold(greenLower, greenUpper);
	run("Convert to Mask");
	run("Create Selection");
	roiManager("add");
	close();

//Normalize Green channel
	selectWindow("Green");
	run("Invert");
	imageCalculator("Add create 32-bit", "Green", "Green-1");
	if (output_img) { saveAs("PNG", dir2_images+list[img] + "_green.png"); }
	rename("G");
	
	selectWindow("Green");
	close();
	selectWindow("Green-1");
	close();


//Multiply Sat by inverted Green
	imageCalculator("Multiply create 32-bit", "S", "G");
	if (output_img) { saveAs("PNG", dir2_images+list[img] + "_multiply"); }
	rename("S_multiply_G");
	
	selectWindow("S");
	close();
	selectWindow("G");
	close();
		
	//Refine ROI
	newImage("ROI", "8-bit", theWidth, theHeight, 1);
	run("Invert");
	roiManager("Deselect");
	roiManager("AND");
	run("Invert");
	run("Select None");

	run("Select ROI");


	run("Invert");
	run("Create Selection");
	roiManager("reset");
	roiManager("Add");
	close();
	
	selectWindow("S_multiply_G");
	roiManager("select", 0);
	
	getStatistics(tmp, div_mean, div_min, div_max, div_sd);
			
	run("Make Inverse");
	run("Clear", "slice");
	run("Make Inverse");
	run("Select None");
	
	rename("threshold");
	run("Duplicate...", "title=topographic");
	
	run("Min...", "value="+div_mean);
	run("Invert");
	run("Colors...", "foreground=black background=white selection=yellow");
		
	run("Canvas Size...", "width=4096 height=4096 position=Center");
	
	//Convolve
	run("FD Math...", "image1=topographic operation=Convolve image2=convolution result=convoluted do");
	run("8-bit");
	run("Invert");
	run("Canvas Size...", "width="+theWidth+" height="+theHeight+" position=Center");
	if (output_img) { saveAs("PNG", dir2_images + list[img] + "_convoluted"); }

	run("32-bit");
	
	setThreshold(0, 230); //remove background
	run("NaN Background");
	
	getStatistics(tmp, conv_mean, conv_min, conv_max, conv_sd);
	
	setThreshold(0, conv_mean - 1.96*conv_sd);
	run("Convert to Mask");
	
	roiManager("select", 0);

	roiManager("reset");
	run("Analyze Particles...", "circularity=0.60-1.00 display clear add");
	
	
	for(i = nResults-1; i >= 0; i--) {
		roiManager("select", i);
		if (getResult("Area", i) > 2000 || getResult("Area", i) < 125) roiManager("Delete");

	}
	
	templateMatches = roiManager("count");
	
	if (roiManager("count") > 1) {
	
		roiManager("Deselect");
		roiManager("OR");
		roiManager("reset");
		roiManager("Add");
	}
	close();
	
	selectWindow("topographic");
	run("Canvas Size...", "width="+theWidth+" height="+theHeight+" position=Center");
	if (output_img) { saveAs("PNG", dir2_images+list[img] + "_topo"); }
	close();
	
	selectWindow("threshold");
	//Set threshold to 50-percentile
	setThreshold(div_mean+div_sd, div_max);
	run("Convert to Mask");
	if (output_img) { saveAs("PNG", dir2_images+list[img] + "_threshold"); }

	run("Remove Outliers...", "radius=5 threshold=50 which=Dark");
	for(i = 0; i < dilate; i++) {
		run("Dilate");
	}
	run("Distance Map");
	if (output_img) { saveAs("PNG", dir2_images+list[img] + "_distmap"); }
	
	run("Remove Vessels", "noise="+noiseValue+" min="+minValue+" max="+maxValue);
	if (output_img) {  saveAs("PNG", dir2_images+list[img] + "_distmap_filtered"); }
	
	setThreshold(noiseValue, 255);
	run("Convert to Mask");
	
	//Measure area
	run("Create Selection");
	run("Measure");
	totalArea = getResult("Area", 0);
	run("Clear Results");
	run("Select None");
	

	//Skeletonize
	run("Skeletonize");
	if (output_img) { saveAs("PNG", dir2_images + list[img] + "_skeleton"); }
	rename("skeleton");
	
	//Output all vessels
	// run("Separate Vessels", "savePath=["+dir2_images+"]");
	
	//Overlay template matches
	selectWindow("skeleton");
	if (roiManager("count") > 0) {
		roiManager("Set Fill Color", "red");
		roiManager("Show All without labels");
		run("From ROI Manager");
	}
	run("Flatten");
	if (output_img) { saveAs("PNG", dir2_images + list[img] + "_tmpl_skeleton"); }
	close();
	
	//Create overlay on original
	selectWindow("skeleton");
	run("Create Selection");
	roiManager("Add");
	run("Select None");
	roiManager("Select", roiManager("count")-1);
	roiManager("Set Fill Color", "green");
	
	run("Clear Results");
	run("Vessel Clusteredness");
	selectWindow("Results");
	saveAs("text", dir2_cluster + list[img] + "_clusteredness.txt");
	nVessels = nResults;
	run("Summarize");
	
	mean = getResult("Distance_closest", nResults-4);
	sd = getResult("Distance_closest", nResults-3);
	close();
	 	
	//regression value of clusteredness
	regr = 7.280281712806443 - mean*0.12814619796758592 + sd*0.057684658005654525 - nVessels*mean*0.0001147832678137158;
	
	//skriv opp filnavn og verdier
	File.append(list[img]+"	"+mean+"	"+sd+"	"+nVessels+"	"+regr+"	"+templateMatches, filepath);

	selectWindow(list[img]);
	
	if (getWidth() > standard_width) {
		new_height = getHeight() * (standard_width / getWidth());
		
		run("Scale...", "x=- y=- width="+standard_width+" height="+new_height+" interpolation=Bilinear average create title="+list[img]+"_resized");
	}
	
	roiManager("Show All without labels");
	run("From ROI Manager");
	run("Flatten");

	saveAs("jpeg", dir2_images +list[img] + "_skeleton_overlay");
	close();
	
	selectWindow(list[img]);
	close();
		
	run("Clear Results");
	roiManager("reset");
}
run("Close All");

beep();
