AATSR Pixel Ungridding Tool Version 1.6      09/07/2015

--------------------------------------------------------------------------------
QUICKSTART 
--------------------------------------------------------------------------------
Java tool that ungrids ATSR L1B products and extracts geolocation and pixel 
field of view data 

INPUTS 
-(A)ATSR(-1/2) level 1B product (Envisat Data Product (.N1) format) 
-L1B Characterisation File (ascii & binary format) 
-AATSR FOV Calibration Measurement File (ASCII (.SFV) format) 
-(OPTIONAL) Global Digital Elevation Model for Orthorectification (GeoTIFF (.tif) format)

OUTPUTS 
-Extracted un-gridded geolocation, acquisition time & channel Field of View Map 
 (HDF5 (.h5) format) alternatively including measurement data (netCDF4 CF (.nc)
 format.

USAGE: gbt2ubt <(a)atsr(-1/2)-product> <l1b-characterisation-file> ...
       <fov-measurement-file> <output-file> <rows-per-CPU-thread> ...
       <IFOV-reporting-extent-fraction> <Trim-end-of-product> ... 
       <Pixel Reference> <Topography> <Topo_Relation (km)> <Ortho> <DEM> ...
       OPT<[ix,iy]> OPT<[jx,jy]> 

EXAMPLE: java -jar -d64 -Xmx8g GBT-UBT-Tool.jar "./l1b_sample.n1" ...
         "./CH1_Files/ATS_CH1_AX" "./FOV_measurements/10310845.SFV" ...
         "./output.nc" "2000" "0.4" "TRUE" "Corner" "FALSE" "0.05" "TRUE"...
         "./DEM/global/gt30_global.tif" "[0,0]" "[511,511]" 

Uses the BEAM Java API 4.11, 
available @ (http://www.brockmann-consult.de/cms/web/beam/releases) 

Uses the Java HDF5 Interface (JHI5), 
available @ (http://www.hdfgroup.org/hdf-java-html/) 

Uses the Java NetCDF Interface, 
available @ (http://www.unidata.ucar.edu/software/thredds/current/netcdf-java/documentation.htm)

Uses the Orekit 7.0 space dynamics library (for orthorectification), 
available @ (https://www.orekit.org/download.html)

Distribution includes Windows 64bit HDF5 libraries, pre-built binaries for 
other platforms are available @ 
(http://www.hdfgroup.org/products/java/release/download.html) 

Distribution includes Windows 64bit netCDF4 libraries, source code for 
other platforms are available @ 
(https://www.unidata.ucar.edu/downloads/netcdf/index.jsp)
--------------------------------------------------------------------------------
DESCRIPTION 
--------------------------------------------------------------------------------
This tool parses an (A)ATSR(-1/2_ ATS_TOA_1P product, then for each pixel in the
 Nadir and Forward views: 

1) Computes the original geolocation of the measurement (UBT) using methodology 
taken from a Technical Note by Andrew Birks of Rutherford Appelton Laboratory 
-"Instrument Pixel Coordinates and Measurement Times from AATSR Products" and 
methodology extracted from the AATSR Frequently Asked Questions (FAQ) document 
"Appendix A Interpolations of pixel geolocation in AATSR full resolution 
products". These documents are available @
(https://earth.esa.int/pub/ESA_DOC/ENVISAT/AATSR/Instrument_Pixel_Coordinates_and_Measurement_Time.pdf)
(https://earth.esa.int/instruments/aatsr/faq/AATSR_FAQ_issue1.pdf)

2) Optionally orthorectifies the UBT locations taking into account a Digital
Elevation Model (DEM) (currently the user must specify a global GeoTIFF file).
The pixel to satellite line of sight (LOS) in the local topographic frame is 
reconstructed through numerical propagation of the state vector found in the MPH
of the L1B product. The AATSR L1B data processing model (topographic corrections
section 5.16) is followed to produce geolocation displacements along the Earth 
surface. The Orekit library is used for orbit propagation, refraction and 
geometry calculations.

3) Computes the acquisition time of the pixels using the Andrew Birks TN. This
methodology allows retrieval of the acquisition time on a pixel basis
as opposed to the usual per scan basis. Note that there appears to be a
systematic bias of +0.15 seconds in the acquisition time retrieval. This is
hinted at in the Andrew Birks TN.

4) Computes the extent (size) of AATSR pixel FOVs projected onto a spherical 
Earth surface for both along and across track directions. A threshold parameter 
is used to return the extent of the projection for a given FOV intensity. This
computation facilitates better characterisation of the instrument measurements,
adding a spatial coverage element to the original geolocation. The algorithms to
compute the FOV projection were extracted from IDL code provided by Dave Smith 
(RAL). Please see section FOV Computation Algorithm for more details. 

5) Outputs the results in array datasets within an HDF5 format file or NetCDF4 
CF format file.

HDF5 Output File Structure 

<Output_File> <Acquisition_Times> -Forward_Acquisition_Time Unit: MJD2000 (UTC) 
                                  -Nadir_Acquisition_Time 

              <FOV_Projection>    -Forward_Across_Track Unit: km 
                                  -Forward_Along_Track 
                                  -Nadir_Across_Track 
                                  -Nadir_Along_Track 

              <Geolocation>       -Forward_Latitude Unit: Decimal Degrees 
                                  -Forward_longitude 
                                  -Nadir_Latitude 
                                  -Nadir_Longitude 

NetCDF4 CF Output File Structure (File is self-describing)

<Output_File> <Acquisition_Times> -Forward_Acquisition_Time Unit: MJD2000 (UTC) 
                                  -Nadir_Acquisition_Time 

              -crs: coordinate reference system descriptor

              <Flags>             -Cloud_flags_fward Integer representations
                                  -Cloud_flags_nadir
                                  -Confid_flags_fward
                                  -Confid_flags_nadir
                                  
              <FOV_Projection>    -Forward_Across_Track Unit: km 
                                  -Forward_Along_Track 
                                  -Nadir_Across_Track 
                                  -Nadir_Along_Track 

              <Geolocation>       -Forward_Latitude Unit: Decimal Degrees 
                                  -Forward_longitude 
                                  -Nadir_Latitude 
                                  -Nadir_Longitude

              <Measurements>      -Brightness Temperatures Unit: K 
                                  -TOA Reflectance Unit: %


Fill Values 

Value for no data (e.g. cosmetic pixel, no ADS available) = -999999.0
Value for pixels corresponding to scan number <32 = -888888.0
Value for no data in measurement arrays = -0.02

--------------------------------------------------------------------------------
FOV COMPUTATION ALGORITHM 
--------------------------------------------------------------------------------
The algorithms to compute the Pixel FOV projection are provided by Dave Smith 
(RAL). They are based on a computation methodology outlined by Graeme Mason in 
the thesis "Test and calibration of the along track scanning radiometer, a 
satellite-borne infra-red radiometer designed to measure sea surface 
temperature". 

The provided algorithms have been adapted to extract an FOV extent (measured in 
km, along and across track) from an intensity matrix. 

The computation requires (pre-launch) FOV measurement data which is also 
provided by Dave Smith (RAL). This measurement data is for AATSR. There is only 
limited FOV data availability for ATSR-1/2 and it is not provided. Note that
focus of this tool is targeted at AATSR data only.

Key points to note regarding this computation methodology are: 

1) All pixels are integrated over the full integration time of 75ms. 
2) Algorithms are restricted to AATSR viewing geometry. 
3) Viewing geometry is mapped to a spherical earth model. 

Please also note that work (by RAL) is ongoing to adapt these algorithms to 
SLSTR. 

--------------------------------------------------------------------------------
FILE MANIFEST 
--------------------------------------------------------------------------------
-<Example> Sample command line input and the resulting output data structure
           -command line input (hdf5 output).txt
           -subset_output.h5
           -command line input (netCDF4 CF output).txt
           -subset_output.nc

-<FOV_Measurements> FOV Raw Data Measurements 
                    -10310845.SFV 0.87um channel 
                    -11010836.SFV 0.67um " 
                    -11020836.SFV 0.56um " 
                    -11100831.SFV 1.6um " 
                    -11101916.SFV 11um " 
                    -11110705.SFV 12um " 
                    -11111716.SFV 3.7um " 

-<lib> Various (76) .jar libraries for netCDF4, HDF5, BEAM & Orekit

-<src> Source files for application 
       -Calculator.java Calculates UBT geolocation and projection 
       -Controller.java Main Class that manages parallel processing of product 
       -FOVContour.java Contours the FOV matrix to produce interpolated extents
       -GeolocationInterpolator.java Retrieves Geolocation using AATSR FAQ 
        methodology 
       -HDFWriter.java Writes UBT geolocation, acquisition time & FOV projection
        extent to HDF5 output file 
       -InputParameters.java Parses inputs from auxiliary data files
       -NetCDF4Writer.java Writes output data in CF compliant format. Note also
        includes measurement data and flags
       -Orthorectifier.java Performs orbit propagation and orthorectification
       -PixelCoordinateInterpolator.java Retrieves UBT pixel scan (X&Y) 
        coordinates using TN 
       -ScanAndPixelIndicesExtractor.java Retrieves scan and pixel number

-<CH1_Files> L1b Characterisation Files that contains first pixel numbers
             -ATS_CH1_AX AATSR CH1
             -AT2_CH1_AX ATSR-2 CH1
             -AT1_CH1_AX ATSR-1 CH1

-<netCDF 4.3.3.1> NetCDF C library (Win 64 bit)

-GBT-UBT-Tool.jar Java application 

-jhdf.dll DLL libraries for HDF5 file creation 
-jhdf5.dll

-orekit-data.zip Data bundle for the Orekit library

--------------------------------------------------------------------------------
SYSTEM REQUIREMENTS 
--------------------------------------------------------------------------------
Extensive system testing has not been conducted. Run time for full orbit 
ATS_TOA_1P is ~= 1 min on minimum requirement machine. Tool has been tested on 
Windows 64bit and Ubuntu (Linux) 64bit systems using an appropriate Linux HDF5 
library. Note, use of netCDF4 CF output and orthorectification are
computationally expensive with large RAM requirements (~16 GB). Processing times
are circa 3-4 min per full orbit product.

Minimum requirements: 

-64bit Java Runtime Environment 1.7_25 
-Quad Core CPU 3.2GHz, 6MB 
-8GB RAM 

--------------------------------------------------------------------------------
INSTALLATION 
--------------------------------------------------------------------------------
The application requires a 64bit Java Virtual Machine. Oracle version 1.7.0_25 
is supported. To check what version of Java is installed, run (>java -version ) 
at the Command Prompt. 

Installation steps are as follows:

1) Download and install a Java Virtual Machine. (Oracle is supported) 
2) Unzip the application folder to a directory of choice. 
3) If using a platform other than Windows, grab a suitable pre-built HDF5-C 
binary and netCDF4 C source code and install. See QUICKSTART above for HDF5 
binaries and netCDF4 source code location. 

--------------------------------------------------------------------------------
OPERATING INSTRUCTIONS 
--------------------------------------------------------------------------------
Application is launched from command line. 

1) On windows, launch Command Prompt 

2) Change directory to installation folder (e.g. >cd C:/GBT-UBT-Tool) 

3) Run the tool to see the list of required inputs (>java -jar GBT-UBT-Tool.jar)

4) Configure Java Virtual Machine to use required RAM (>java -Xmx5g -jar 
GBT-UBT-Tool.jar) 

4a) If using non-Windows platform, set the Java path to include the local 
installation of the downloaded HDF5 libraries. (> java -Xmx5g 
-Djava.library.path="xxx" -jar GBT-UBT-Tool.jar) where "xxx" is the location of 
the installed HDF5 lib folder. 

5) Choose a number of image rows to assign per thread. This parameter tunes the 
work distributed per CPU core. Suggest initial value of 2000.

6) Choose FOV reporting extent (0 - 1). This parameter alters the size of the 
returned FOV projection depending on the intensity specified. (i.e. 0.1 -> FOV 
projection to 10% intensity) 

7) Choose to trim end of product rows where no ADS (hence no UBT geolocation) is
available. Suggest default is "TRUE" but bear in mind that output file will 
contain less rows. 

8) Choose which FOV measurement data to use for FOV extent projection 
computation. There is a small variation between channels. Extent information
will be output only for the selected channel.

9) Choose the pixel coordinate reference point for use in the output file. 
"centre" references the geolocation to the pixel centre point. "corner" 
references the geolocation to the lower left corner, "corner" is the default. 

10) Choose if to apply the topographic corrections to pixels at tie-points. 
Default is "FALSE". Corrections are not applied to non tie-point pixels because 
the AATSR handbook specifies that corrections in the Geolocation ADS should not 
be interpolated. 

11) Choose the topographic homogeneity value in km. Pixels within this distance 
(instrument measurement coordinates) of a tie-point will have the topographic 
correction applied if corrections are enabled. 

12) OPTIONALLY, select a subset of the product to convert. This is achieved 
using the OPT<[ix,iy]> and OPT<[jx,jy]> parameters. Use of OPT<[ix,iy]> alone 
will extract a single pixel, where ix is across track pixel number (0->511) and 
ix is along track pixel number (0->~42000). Use of both OPT<[ix,iy]> and 
OPT<[jx,jy]> will extract a rectangular region of pixels, [ix,iy] represents 
the pixel at the upper left corner of the rectangle, [jx,jy] represents the 
lower right corner. 

13) Choose HDF5 or netCDF4 CF output through appending either .h5 or .nc to 
output filename.

14) Choose whether to orthorectify the product. If true provide a path to a DEM 
which has sufficient coverage (i.e. global) and in GeoTIFF format (.tif). If 
false provide a dummy parameter (e.g. " ").

15) Run application by putting file locations and input parameters as Strings ""
after Java command e.g. (where ... represents continuation of same line but 
should not be typed) >java -d64 -Xmx5g -jar GBT-UBT-Tool.jar ... 
"./ATS_TOA_1PRUPA20040801_202807_000013052029_00085_12664_0910.N1" ... 
"./CH1_Files/ATS_CH1_AX" "./FOV_measurements/10310845.SFV" ...
"./ubt_output_12664.nc" "2000" "0.3" "TRUE" "Centre" "FALSE" "0.05" "TRUE"... 
"./global_DEM.tif" "[30,100]" "[120,300]"

--------------------------------------------------------------------------------
AUTHORS 
--------------------------------------------------------------------------------
Alasdhair Beaton 
Satellite Systems & Applications Business Unit 
Telespazio VEGA UK Ltd 

alasdhair(dot)beaton(at)telespazio(dot)com 

--------------------------------------------------------------------------------
COPYRIGHT & LICENSING 
--------------------------------------------------------------------------------
Copyright (C) 2015 Telespazio VEGA UK Ltd

The AATSR GBT-UBT-Tool is licensed under the terms of the GNU General Public 
License (GPL3), which implies that it is completely free, including the source 
code.

The AATSR GBT-UBT-Tool software may be used for any personal, commercial or 
educational purpose, including installation on as many different computers as 
wanted. The AATSR GBT-UBT-Tool software and its components may also be copied, 
distributed, modified, and resold as long as this happens under the terms of the
GPL3 as published by the Free Software Foundation.

A copy of the GPL3 license is provided in the distribution folder as 
"LICENSE.txt". If this license is missing, see <http://www.gnu.org/licenses/>.

A copy of the Copyright Notice and Statement for NCSA Hierarchical Data Format 
(HDF) 
Software Library and Utilities is provided in the distribution folder as 
"LICENSE_HDF.txt". If this license is missing, see 
<http://www.hdfgroup.org/ftp/HDF5/current/src/unpacked/COPYING>.

A copy of the Copyright Notice and Statement for UCAR/UNIDATA network Common 
Data Form (netCDF)
Software Library and Utilities is provided in the distribution folder as 
"LICENSE_netCDF.txt". If this license is missing, see 
<http://www.unidata.ucar.edu/software/netcdf/copyright.html>.

A copy of the Copyright Notice and Statement for Orekit Low Level Space Dynamics
Software Library and Utilities is provided in the distribution folder as 
"LICENSE_Orekit.txt". If this license is missing, see 
<https://www.orekit.org/license.html>
--------------------------------------------------------------------------------
VERSION CHANGELOG 
--------------------------------------------------------------------------------
1.0a 17/03/2014 First Alpha Release to ALTS Team (Internal Distribution) 
1.1a 08/04/2014 Introduced thread serialisation for low RAM environments 
1.2a 14/05/2014 Command line region subsetting 
1.3a 14/05/2014 User option for coordinate reference point (corner/centre) 
1.4a 22/09/2014 User option to apply topographic corrections to tie-points
1.4b 30/01/2015 First Beta Release to community (Limited Distribution)
1.5  26/06/2015 Public release incorporating beta test feedback
1.51 02/07/2015 Reduced memory overhead during netcdf4 writing
1.52 03/07/2015 Increased speed during netcdf4 writing
1.6  09/07/2015 User option for orthorectification

--------------------------------------------------------------------------------
ACKNOWLEDGEMENTS 
--------------------------------------------------------------------------------
The author would like to thank Stefano Casadio (SERCO) for input to the tool 
testing programme. 

Also acknowledged for support during the tool development are Andrew Birks 
(RAL), Gary Corlett (Leicester University) & Dave Smith (RAL). 

--------------------------------------------------------------------------------