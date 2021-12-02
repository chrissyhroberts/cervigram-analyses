//This batch macro was made for ImageJ v1.53k and requires the following custom plugins:
//- Cervigram ROI
//- Mask reflections
dir1 = getDirectory("Choose Source Directory ");
list = getFileList(dir1);
dir2 = getDirectory("Choose Destination Directory ");

setBatchMode(true);

erode = "1";
dilate ="20";
outliers = "7";

getDateAndTime(year, month, dayOfWeek, dayOfMonth, hour, minute, second, msec);

filepath = dir2 +"results_"+dayOfMonth+"."+month+"_"+hour+"."+minute+".txt";

outputfile = File.open(filepath);
print(outputfile, "File	PatchArea	ROISize	hueMean	hueMin	hueMax	satMean	satMin	satMax	briMean	briMin	briMax	bMean	bMin	bMax	redMean	redMin	redMax	greenMean	greenMin	greenMax	blueMean	blueMin	blueMax");
File.close(outputfile);

run("Set Measurements...", "area redirect=None decimal=3");


for (img=0; img<list.length; img++) {
	roiManager("reset");

	//Mask reflections
	open(dir1+list[img]);
	run("Set Scale...", "distance=0 known=0 pixel=1 unit=pixel");
	run("Mask Reflections", "expand=0");
	run("Make Inverse");

	//Only enlarge if selection
	if (selectionType() > -1) {

		run("Enlarge...", "enlarge=20 pixel");
		run("Make Inverse");

		roiManager("Add");
	}
	//Select ROI
	run("Select None");

	//Select ROI
	run("Cervigram ROI", "gauss=35 lower=0.55 percent=0.20 number=50 binsize=50 failsafe,=0.10");
	roiManager("Add");

	//Intersection of white and ROI
	roiManager("AND");
	roiManager("reset")
	roiManager("Add");

	run("Remove Overlay");


	roiManager("Select", 0);
	run("Measure");
	selectWindow("Results");

	//Measure ROI
	ROISize = getResult("Area", 0);
	run("Clear Results");

	//Save visual ROI
	selectWindow(list[img]);
	run("Flatten");
	saveAs("jpeg", dir2 + list[img] + "_ROI.jpg");
	close();

	//Profiling
	open(dir1+list[img]);
	run("Set Scale...", "distance=0 known=0 pixel=1 unit=pixel");
	run("Colors...", "foreground=white background=black selection=yellow");
	run("Options...", "iterations=1 black count=1");

	run("Duplicate...", "title=profiling_Lab");
	run("Color Transformer", "colour=Lab");
	run("Convert Stack to Images");

	selectWindow("L");
	close();
	selectWindow("a");
	close();

	//Mean b
	//the lesions should be in the upper scale of b (blue-->yellow)
	selectWindow("b");
	roiManager("Select", 0);
	getStatistics(tmp, b_mean, b_min, b_max);

	selectWindow(list[img]);
	run("Select None");
	run("Duplicate...", "title=profiling_HSV");
	run("HSB Stack");
	run("Convert Stack to Images");

	//Mean Hue
	selectWindow("Hue");
	roiManager("Select", 0);

	getHistogram(0, counts, 256);
	mean_hue_sin = 0;
	mean_hue_cos = 0;
	pixels_hue = 0;
	ratio = 256/(2*PI);
	for (i=0;i<counts.length;i++) {
		mean_hue_sin = mean_hue_sin + counts[i]*sin(i/ratio);
		mean_hue_cos = mean_hue_cos + counts[i]*cos(i/ratio);
		pixels_hue = pixels_hue + counts[i];
	}
	hue_mean = atan2(mean_hue_sin/pixels_hue, mean_hue_cos/pixels_hue)*ratio;
	if (hue_mean < 0) hue_mean = 255 + hue_mean;

	//Mean sat
	selectWindow("Saturation");
	roiManager("Select", 0);
	getStatistics(tmp, sat_mean);

	//Mean value / brightness
	selectWindow("Brightness");
	roiManager("Select", 0);
	getStatistics(tmp, bri_mean);


	selectWindow(list[img]);
	run("Select None");
	run("Duplicate...", "title=profiling_RGB");
	run("RGB Stack");
	run("Convert Stack to Images");

	//Mean red
	selectWindow("Red");
	roiManager("Select", 0);
	getStatistics(tmp, red_mean);

	//Mean green
	selectWindow("Green");
	roiManager("Select", 0);
	getStatistics(tmp, green_mean);

	//Mean blue
	selectWindow("Blue");
	roiManager("Select", 0);
	getStatistics(tmp, blue_mean, blue_min);

	//Red window
	//The lesions needs to contain some green (red+green = yellow)
	//Mean 34
	//95% CI = 27 ; 41
	//99% CI = 25 ; 43
	//STD 17.4
	filter_red = "pass";
	red_min = red_mean + 34 - (2*17.4);
	if (red_min > 255) red_min = 255;
	red_max = red_mean + 34 + (2*17.4);
	if (red_max > 255) red_max = 255;

	//Green window
	//The lesions needs to contain some green (red+green = yellow)
	//Mean 29
	//95% CI = 23.3 ; 35.4
	//STD 16
	filter_green = "pass";
	green_min = green_mean + 29 - (1.5*16);
	if (green_min > 255) green_min = 255;
	green_max = green_mean + 29 + (2*16);
	if (green_max > 255) green_max = 255;

	//Blue window
	//The lesion shouldn't contain too much blue
	//Mean 17.7
	//95% CI = 11.2 ; 24.2
	//STD 17
	filter_blue = "pass";
	blue_min = blue_min;
	blue_max = blue_mean + 17.7 + (2*17);
	if (blue_max > 255) blue_max = 255;

	//Hue window
	//Mean 7.4
	//95% CI = 5.3 ; 9.5
	//STD 5.6

	filter_hue = "pass";
	hue_min = hue_mean + 7.4 - (0.625*5.6);
	hue_max = hue_mean + 7.4 + (4*5.6);

	if (hue_min > 255 && hue_max > 255) {
		hue_min = hue_min - 255;
		hue_max = hue_max - 255;
		filter_hue = "pass";
	} else if (hue_min < 255 && hue_max > 255) {
		hue_max = hue_min;
		hue_min = hue_mean + (7.4 + (4*5.6)) - 255;
		filter_hue = "stop";
	} else if (hue_min < 255 && hue_max < 255) {
		filter_hue = "pass";
	}


	//Sat window
	//SD 14.87
	//mean 7.78
	filter_sat = "pass";
	sat_min = sat_mean + 7.78 - (2*14.87);
	if (sat_min < 0) sat_min = 0;
	sat_max = sat_mean + 7.78 + (2*14.87);
	if (sat_max > 255) sat_max = 255;

	//Value window / Brightness window
	//SD 17.4
	//Mean 34
	//95% CI 27.4 ; 40.6
	filter_bri = "pass";
	bri_min = bri_mean + 34 - (2*17.4);
	if (bri_min > 255) bri_min = 255;
	bri_max = bri_mean + 34 + (2*17.4);
	if (bri_max > 255) bri_max = 255;

	//b window
	//Mean 7.3
	//SD 7.9
	//95% CI 4.3 - 10.3
	filter_b = "pass";
	b_min = b_mean + 7.3 - (1*7.9);
	if (b_min > b_max) b_min = b_max;
	//b_max = b_mean + 7.3 + (5*7.9);
	b_max = b_max;
	//if (b_max > 255) b_max = 255;


	//Apply thresholds

	//Hue window
	selectWindow("Hue");
	setThreshold(hue_min, hue_max);
	run("Make Binary", "thresholded remaining");
	if (filter_hue=="stop")  run("Invert");
	saveAs("Gif", dir2+list[img] + "_Hue.gif");

	//Sat window
	selectWindow("Saturation");
	setThreshold(sat_min, sat_max);
	run("Make Binary", "thresholded remaining");
	if (filter_sat=="stop")  run("Invert");
	saveAs("Gif", dir2+list[img] + "_Sat.gif");

	//Value / brightness window
	selectWindow("Brightness");
	setThreshold(bri_min, bri_max);
	run("Make Binary", "thresholded remaining");
	if (filter_bri=="stop")  run("Invert");
	saveAs("Gif", dir2+list[img] + "_Value.gif");


	//b window
	selectWindow("b");
	setThreshold(b_min, b_max);
	run("Make Binary", "thresholded remaining");
	if (filter_b=="stop")  run("Invert");
	saveAs("Gif", dir2+list[img] + "_b.gif");


	//Red window
	selectWindow("Red");
	setThreshold(red_min, red_max);
	run("Make Binary", "thresholded remaining");
	if (filter_red=="stop")  run("Invert");
	saveAs("Gif", dir2+list[img] + "_Red.gif");

	//green window
	selectWindow("Green");
	setThreshold(green_min, green_max);
	run("Make Binary", "thresholded remaining");
	if (filter_green=="stop")  run("Invert");
	saveAs("Gif", dir2+list[img] + "_Green.gif");

	//blue window
	selectWindow("Blue");
	setThreshold(blue_min, blue_max);
	run("Make Binary", "thresholded remaining");
	if (filter_blue=="stop")  run("Invert");
	saveAs("Gif", dir2+list[img] + "_Blue.gif");


	//Intersection
	imageCalculator("AND create", "Hue","b");
	selectWindow("b");
	close();
	imageCalculator("AND create", "Result of Hue", "Brightness");
	selectWindow( "Brightness");
	close();
	imageCalculator("AND create", "Result of Result of Hue", "Red");
	selectWindow( "Red");
	close();
	imageCalculator("AND create", "Result of Result of Result of Hue", "Green");
	selectWindow( "Green");
	close();
	imageCalculator("AND create", "Result of Result of Result of Result of Hue", "Blue");
	selectWindow( "Blue");
	close();
	imageCalculator("AND create", "Result of Result of Result of Result of Result of Hue", "Saturation");

	//Remove outside ROI
	roiManager("Select", 0);
	run("Make Inverse");
	run("Clear", "slice");
	run("Select None");
	saveAs("Gif", dir2+list[img] + "_intersection.gif");

	selectWindow("Hue");
	close();
	selectWindow("Saturation");
	close();

	selectWindow("Result of Result of Result of Result of Result of Result of Hue");

	run("Invert LUT");
	run("Colors...", "foreground=black background=white selection=yellow");

	//run("Options...", "iterations=1 white count=1");
	//run("Dilate");

	run("Options...", "iterations="+erode+" white count=1");
	run("Erode");

	run("Remove Outliers...", "radius="+outliers+" threshold=100 which=Dark");

	run("Options...", "iterations="+dilate+" white count=1");
	run("Dilate");


	run("Create Selection");
	run("Clear Results");
	 if (selectionType() == -1)
	{
		close();
		open(dir1+list[img]);
		run("Set Scale...", "distance=0 known=0 pixel=1 unit=pixel");
		saveAs("jpeg", dir2 + list[img] + "_overlay.jpg");
		close();
		File.append(list[img]+"	0	"+ROISize+"	"+hue_mean+"	"+hue_min+"	"+hue_max+"	"+sat_mean+"	"+sat_min+"	"+sat_max+"	"+bri_mean+"	"+bri_min+"	"+bri_max+"	"+b_mean+"	"+b_min+"	"+b_max+"	"+red_mean+"	"+red_min+"	"+red_max+"	"+green_mean+"	"+green_min+"	"+green_max+"	"+blue_mean+"	"+blue_min+"	"+blue_max, filepath);
	}
	else
	{

		run("Measure");
		run("Add Selection...", "stroke=blue width=2 fill=none new");

		run("To ROI Manager");
		close();
		roiManager("Show All");

		open(dir1+list[img]);
		run("Set Scale...", "distance=0 known=0 pixel=1 unit=pixel");
		run("From ROI Manager");
		run("Flatten");
		saveAs("jpeg", dir2 + list[img] + "_overlay.jpg");

		run("Close All");


		//saveAs("Gif", dir2 + list[img] + "_binary_after.gif");

		selectWindow("Results");

		//Beregn total areal
		areal = getResult("Area", 0);
		run("Clear Results");

		//skriv opp filnavn og verdier
		File.append(list[img]+"	"+areal+"	"+ROISize+"	"+hue_mean+"	"+hue_min+"	"+hue_max+"	"+sat_mean+"	"+sat_min+"	"+sat_max+"	"+bri_mean+"	"+bri_min+"	"+bri_max+"	"+b_mean+"	"+b_min+"	"+b_max+"	"+red_mean+"	"+red_min+"	"+red_max+"	"+green_mean+"	"+green_min+"	"+green_max+"	"+blue_mean+"	"+blue_min+"	"+blue_max, filepath);

		call("java.lang.System.gc");
	}
}

beep();
