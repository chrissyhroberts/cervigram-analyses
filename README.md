# cervigram-analyses
Algorithms for automated image analyses of cervigrams with detection of ROI, sandy patches etc

This repository contain plugins and macros for ImageJ https://imagej.nih.gov/ij/
The algorithms are meant for automated analyses of photocolposcopic cervigrams for the detection of sandy patches caused by schistosomiasis. However, there are more general applications of many of the algorithms, such as the automated ROI-detection, masking algorithm and colour detection.

_Feel free to contribute to the development of this library. Pull requests are most welcome!_

## Install plugins

- Download ImajeJ from here : [https://imagej.nih.gov/ij/](https://imagej.nih.gov/ij/)
- Open ImajeJ
- Click the `plugins` menu item, then select `install`
- Install each of the plugins from the `Plugins` folder in this repository to the `ImageJ/plugins` folder on your computer
	- Cervigram_ROI.java
	- Convolve_Vessels.java
	- Mask_Reflections.java
	- Mask_white.java
	- Remove_Vessels.java
	- Select_ROI.java
	- Separate_Vessels.java
	- Vessel_Clusteredness.java

If the plugins are properly installed, they should appear as menu items on the `plugins` menu.

## Test plugins

- Open a cervigram photo

### Cervigram region of interest

This plugin appears to identify a region of interest which includes the cervical body and excludes vaginal wall

- Run the `cervigram ROI` plugin
	- Some settings will appear. Need to check what these mean
	- Click `OK` to run the cervigram ROI
	- A yellow line should appear, this delimits the polygon of the ROI
	

## Run in batch mode. 

- To run the analysis, you should use the `Batch colour analysis.ijm` macro
- When it starts, it provides no dialogue, but expects you to choose a folder containing some photos. 
- It runs and spits out a set of new images, plus a data file

- In a test run on file
	- e3dac42b-402d-42ba-83bf-10f0aaca9acd.jpg

- The system creates the following new files
	- e3dac42b-402d-42ba-83bf-10f0aaca9acd.jpg_overlay.jpg
	- e3dac42b-402d-42ba-83bf-10f0aaca9acd.jpg_ROI.jpg
	- results_22.0_9.47.txt
	
	

- Example output 
	
| File                               | PatchArea | ROISize | hueMean | hueMin | hueMax | satMean | satMin | satMax | briMean  | briMin   | briMax | bMean  | bMin   | bMax   | redMean  | redMin   | redMax | greenMean | greenMin | greenMax | blueMean | blueMin | blueMax |
|------------------------------------|-----------|---------|---------|--------|--------|---------|--------|--------|----------|----------|--------|--------|--------|--------|----------|----------|--------|-----------|----------|----------|----------|--------|--------|
| e3dac42b-402d-42ba-83bf-10f0aaca9acd.jpg | 2129650   | 9503604 | 0.74    | 4.64   | 30.54  | 86.725  | 64.765 | 124.245 | 201.9386 | 201.1386 | 255    | 11.7784 | 11.1784 | 43.1735 | 201.9386 | 201.1386 | 255    | 138.9365  | 143.9365 | 199.9365 | 136.0705 | 3      | 187.7705 |


**Scientific publications based on these algorithms:**
- Holmen, S., Kleppa, E., Lillebo, K., Pillay, P., van Lieshout, L., Taylor, M., … Kjetland, E. F. (2015). The First Step Toward Diagnosing Female Genital Schistosomiasis by Computer Image Analysis. American Journal of Tropical Medicine and Hygiene, 93(1), 80–86. http://doi.org/10.4269/ajtmh.15-0071
- Holmen, S., Kjetland, E. F., Taylor, M., Kleppa, E., Lillebø, K., Gundersen, S. G., … Albregtsen, F. (2015). Colourimetric image analysis as a diagnostic tool in female genital schistosomiasis. Medical Engineering & Physics, 37(3), 309–314. http://doi.org/10.1016/j.medengphy.2014.12.007
- Holmen, S., Galappaththi-Arachchige, H. N., Kleppa, E., Pillay, P., Naicker, T., Taylor, M., … Albregtsen, F. (2016). Characteristics of Blood Vessels in Female Genital Schistosomiasis: Paving the Way for Objective Diagnostics at the Point of Care. PLOS Neglected Tropical Diseases, 10(4), e0004628. http://doi.org/10.1371/journal.pntd.0004628
- Galappaththi-Arachchige, H. N., Holmen, S., Koukounari, A., Kleppa, E., Pillay, P., Sebitloane, M., … Kjetland, E. F. (2018). Evaluating diagnostic indicators of urogenital Schistosoma haematobium infection in young women: A cross sectional study in rural South Africa. PLoS ONE, 13(2). http://doi.org/10.1371/journal.pone.0191459

**PhDs based on this work**
- Holmen, S. Computer Image Analysis as a Diagnostic Tool in Female Genital Schistosomiasis. https://www.duo.uio.no/handle/10852/58767