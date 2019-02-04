dir1 = getDirectory("Choose Source Directory ");
list = getFileList(dir1);
dir2 = getDirectory("Choose Destination Directory ");

setBatchMode(true);

erode = 4;
dilate = 20;

getDateAndTime(year, month, dayOfWeek, dayOfMonth, hour, minute, second, msec);

filepath = dir2 +"resultater_Kolposkopi_"+dayOfMonth+"."+month+"_"+hour+"."+minute+".txt";

skrivefil = File.open(filepath);
print(skrivefil, "File	satMean	satMin	satMax	greenMean	greenMin	greenMax	blueMean	blueMin	blueMax");
File.close(skrivefil);

run("Set Measurements...", "area redirect=None decimal=3");

run("Colors...", "foreground=black background=white selection=yellow");

for (bilde=0; bilde<list.length; bilde++) {
	open(dir1+list[bilde]);
		
	run("Color Transformer", "colour=Lab");
	run("Convert Stack to Images");
	selectWindow("L");
	close();
	selectWindow("b");
	close();
	
	selectWindow("a");
	run("8-bit");
	saveAs("jpeg", dir2 + list[bilde] + "_a.jpg");
	run("Gaussian Blur...", "sigma=35");

	setAutoThreshold("Mean");
//	setAutoThreshold("Triangle");
	run("Convert to Mask");
	run("Invert LUT");
	
//	run("Options...", "iterations="+erode+" black count=1");
//	run("Erode");
		
//	run("Options...", "iterations="+dilate+" black count=1");
//	run("Dilate");

//	run("Fill Holes");

//Measure total area for later
	run("Select All");
	run("Measure");
	totalSize = getResult("Area", nResults-1);
	
	roiManager("reset");
	
	run("Clear Results");
	run("Analyze Particles...", "size=0-Infinity circularity=0.00-1.00 show=Nothing display clear include add");
	close();
	
	largestSize = 0;
	for(region = 0; region < nResults; region++) {
		
		currentSize = getResult('Area', region);
		//print(oldSize, currentSize);
		if (currentSize >= largestSize) {
			largest = region;
			largestSize = currentSize;
		}
		//print(largest);
		
	}
	
	
	
	if (largestSize >= totalSize*0.30) {
	//Success
		selectWindow(list[bilde]);
		roiManager("Select", largest);
		run("Convex Hull");
		run("Add Selection...", "stroke=blue width=5 fill=none new");
		run("Flatten");
		
		
		//run("Make Inverse");
		//run("Clear", "slice");
	}
	
	saveAs("jpeg", dir2 + list[bilde] + "_roi.jpg");
	close();
}

beep();