/* AATSR GBT-UBT-Tool - Ungrids AATSR L1B products and extracts geolocation data and field of view extent
 * 
 * Copyright (C) 2015 Telespazio VEGA UK Ltd
 * 
 * This file is part of the AATSR GBT-UBT-Tool.
 * 
 * AATSR GBT-UBT-Tool is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * AATSR GBT-UBT-Tool is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with AATSR GBT-UBT-Tool.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package gbt.ubt.tool;

import com.bc.ceres.glevel.MultiLevelImage;
import java.io.IOException;
//import java.io.BufferedInputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.ObjectInputStream;
//import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNodeGroup;
import org.esa.beam.util.logging.BeamLogManager;

/**
 *
 * @author ABeaton, Telespazio VEGA UK Ltd 30/10/2013
 *
 * Contact: alasdhair(dot)beaton(at)telespazio(dot)com
 *
 */
public class Controller {
    /* This class is the main class of the GBT-UBT-Tool and sets up and controls the execution of the entire process.
     * 
     * 
     * Ungrids AATSR L1B products and extracts geolocation data
     * 
     * INPUTS
     * ATSR level 1B product (Envisat Data Product (.N1) format)
     * L1B Characterisation File (binary)
     * FOV Calibration Measurement File (ASCII (.SFV) format)
     * 
     * OUTPUTS
     * Extracted un-gridded geolocation, acquisition time & channel Field of View Map (HDF5 (.h5) format)
     * 
     * Usage: gbt2ubt <aatsr-product> <l1b-characterisation-file> <fov-measurement-file> <output-file> <rows-per-CPU-thread> <IFOV-reporting-extent-fraction> <Trim-end-of-product> <Pixel Reference> <Topography> <Topo-Relation> OPT<[ix,iy]> OPT<[jx,jy]>
     * Example: java -jar GBT-UBT-Tool.jar "./l1b_sample.n1" "./ATS_CH1_AXVIEC20120615_105541_20020301_000000_20200101_000000" "./FOV_measurements/10310845.SFV" "./output.h5" "1000" "0.4" "TRUE" "Corner" "FALSE" "0.05" "[0,0]" "[511,42000]"
     * 
     * Uses the BEAM Java API 4.11, available @ (http://www.brockmann-consult.de/cms/web/beam/releases)
     * Uses the Java HDF5 Interface (JHI5), available @ (http://www.hdfgroup.org/hdf-java-html/)
     */

    /**
     * @param args the command line arguments: 
     * args[0] = (A)ATSR-1/2 product 
     * args[1] = L1B characterisation file 
     * args[2] = FOV calibration measurement file
     * args[3] = output file 
     * args[4] = rows assigned per CPU thread 
     * args[5] = extent of IFOV to report as distance in pixel projection 
     * args[6] = boolean to allow user to trim image rows of product where no ADS available
     * args[7] = to where the pixel coordinates are reference Centre/Corner
     * args[8] = boolean to apply topographic corrections to tie points
     * args[9] = distance (image coordinates) pixel can be from tie-point to have topo correction applied
     * args[11] = optional argument to convert pixel ix, iy
     * args[12]= optional argument used with args[11] to convert array of pixels [ix,iy], [jx,jy]
     */
    private static InputParameters parameters;

    public static void main(String[] args) {
        System.out.println("AATSR Pixel Ungridding Tool Version 1.4b");

        //Check that the input array is the right length
        checkInputs(args);

        // Parse the inputs and set up the activity
        parameters = new InputParameters();
        parameters.parse(args);

        processProduct();

        System.out.println("Processing Complete");
        System.out.println("Output file written to: " + System.getProperty("user.dir") + parameters.outputFileLocation);
        System.exit(0);
    }

    private static void checkInputs(String[] args) {
        if (args.length < 10 || args.length > 12) {
            System.out.println("Check Program Inputs");
            System.out.println("Usage: gbt2ubt <aatsr-product> <l1b-characterisation-file> <fov-measurement-file> <output-file> <rows-per-CPU-thread> <IFOV-reporting-extent-fraction> <Trim-end-of-product> <Pixel Reference> <Topography> <Topo-relation> OPT<[ix,iy]> OPT<[jx,jy]>");
            System.exit(1);
        }
    }

    private static void processProduct() {
        // This method sets up the parallelism, calls the calculation tool and collates the results prior to output
        try {
            System.out.println("Calculating Pixel Geolocation, Sample Times & FOV");
            
            // Mask some warnings
            BeamLogManager.removeRootLoggerHandlers();
            System.setProperty("com.sun.media.jai.disableMediaLib", "true");

            // Get the ADS from the product
            Product readProduct = ProductIO.readProduct(parameters.inputFileLocation);
            MetadataElement metadataRoot = readProduct.getMetadataRoot();
            final ProductNodeGroup<MetadataElement> NADIR_VIEW_SCAN_PIX_NUM_ADS_Records = metadataRoot.getElement("NADIR_VIEW_SCAN_PIX_NUM_ADS").getElementGroup();
            final ProductNodeGroup<MetadataElement> FWARD_VIEW_SCAN_PIX_NUM_ADS_Records = metadataRoot.getElement("FWARD_VIEW_SCAN_PIX_NUM_ADS").getElementGroup();
            final ProductNodeGroup<MetadataElement> SCAN_PIXEL_X_AND_Y_ADS_Records = metadataRoot.getElement("SCAN_PIXEL_X_AND_Y_ADS").getElementGroup();
            final ProductNodeGroup<MetadataElement> GEOLOCATION_ADS_Records = metadataRoot.getElement("GEOLOCATION_ADS").getElementGroup();

            // Get the dimensions of the product using one of the bands
            Band band = readProduct.getBand("btemp_nadir_1200");
            MultiLevelImage sourceImage = band.getSourceImage();

            int maxXValue;
            int maxY;
            int minXValue = 0;
            int minYValue = 0;
            
            // If subseting the product, set the min/max dimensions according to input
            if (parameters.subsetFlag == true) {
                maxXValue = parameters.x2;
                maxY = parameters.y2;
                minXValue = parameters.x1;
                minYValue = parameters.y1;
            } else {
                maxXValue = sourceImage.getMaxX();
                maxY = sourceImage.getMaxY();
            }
            final int maxX = maxXValue;
            final int minX = minXValue;
            final int minY = minYValue;

            if (maxX > sourceImage.getMaxX()) {
                System.out.println("Check input X coordinate");
            }

            if (maxY > sourceImage.getMaxY()) {
                System.out.println("Check input Y coordinate");
            }

            if (parameters.trimProductEndWhereNoADS && !parameters.subsetFlag) {
                // If true, trim product for image rows where no ADS is available
                // (ATSR-1/2 have less image rows than ADS cover)
                int a = 32 * SCAN_PIXEL_X_AND_Y_ADS_Records.getNodeCount() + 32;
                int b = 32 * GEOLOCATION_ADS_Records.getNodeCount() + 32;
                int c = 32 * NADIR_VIEW_SCAN_PIX_NUM_ADS_Records.getNodeCount() + 32;
                int d = 32 * FWARD_VIEW_SCAN_PIX_NUM_ADS_Records.getNodeCount() + 32;
                maxY = GeolocationInterpolator.getMinValue(a, b, c, d);
                if (maxY > sourceImage.getMaxY()){
                    maxY = sourceImage.getMaxY();
                }
                System.out.println("Number of image rows covered by ADS: "+maxY+" / "+sourceImage.getMaxY());
            }

            // Get list of GeoLocation ADS scanYCoords (Seems to be very expensive so only compute once)
            final List<Double> scanYCoords = new ArrayList<>();
            for (int k = 0; k < GEOLOCATION_ADS_Records.getNodeCount(); k++) {
                MetadataElement GeoAdsRecord = GEOLOCATION_ADS_Records.get(k);
                scanYCoords.add(GeoAdsRecord.getAttributeDouble("img_scan_y"));
            }

            /* Get the scan number of the first record of the scanPixelADS */
            MetadataElement firstRecord = SCAN_PIXEL_X_AND_Y_ADS_Records.get(0);
            MetadataAttribute instr_scan_num = firstRecord.getAttributeAt(2);
            ProductData data = instr_scan_num.getData();

            final int s0 = data.getElemInt();

            readProduct.closeIO();

            // Get the pixel projection map (along and across track extent) for all 2000 pixels
            // This assumes spherical earth geometry & constant platform altitude
            final List<List<Double>> pixelProjectionMap = new ArrayList<>();
            Calculator.getConstantPixelProjection(parameters, pixelProjectionMap);

            // Get number of available processsors and create threads for each one
            final int availableProcessors = Runtime.getRuntime().availableProcessors();
            System.out.println("Number of available processors: " + availableProcessors);
            ExecutorService threadPool = Executors.newFixedThreadPool(availableProcessors);

            // Assign number of image rows per thread
            final int rowsPerThread = parameters.rowsPerThread;

            // Calculate required number of normal (i.e. full length threads)
            final int numberOfFullThreads = (int) Math.floor((double) (maxY - minY) / (double) rowsPerThread);
            System.out.println("Image broken down into " + (numberOfFullThreads + 1) + " threads");
            // Assign number of image rows for final short thread
            final int rowsInFinalThread = (maxY - minY) - (numberOfFullThreads * (rowsPerThread));

            // Check all image rows assigned
            if (((numberOfFullThreads * rowsPerThread) + rowsInFinalThread) != (maxY - minY)) {
                System.out.println("Failed to calculate number of image rows per thread");
                throw new RuntimeException();
            }
            final InputParameters finalParameters = parameters;
            List<RunnableFuture> tasks = new ArrayList<>();

            // Create task for each thread, use calculator to compute results and return thread storage location as tempResult
            for (int i = 0; i < numberOfFullThreads; i++) {
                final int j = i;
                RunnableFuture task = new FutureTask(new Callable<double[][][]>() {
                    @Override
                    public double[][][] call() {
                        double[][][] tempResult = new double[rowsPerThread][maxX - minX][10];
                        String threadName = "Thread_" + String.valueOf(j);
                        int startingScanNumber = j * rowsPerThread + minY;
                        try {
                            Calculator.unGrid(tempResult, startingScanNumber, rowsPerThread,minX, maxX, s0, NADIR_VIEW_SCAN_PIX_NUM_ADS_Records, FWARD_VIEW_SCAN_PIX_NUM_ADS_Records, SCAN_PIXEL_X_AND_Y_ADS_Records, GEOLOCATION_ADS_Records, scanYCoords, threadName, finalParameters, pixelProjectionMap);
                        } catch (Exception ex) {
                            System.out.println(threadName + " crash");
                            System.out.println(ex.getMessage());
                        }
                        // This commented code is for serialising the results to a temporary file and is currently unused
//                        try {
//                            threadName += ".temp";
//                            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(threadName));
//                            out.writeObject(tempResult);
//                            out.close();
//                        } catch (Exception ex) {
//                            System.out.println(threadName + " crash");
//                            System.out.println(ex.getMessage());
//                        }
                        return tempResult;
                    }
                });
                tasks.add(task);
                try {
                    Future<?> submit = threadPool.submit(task);
                    if (submit.isCancelled()) {
                        System.out.println("Worker thread cancelled prematurely");
                    }
                } catch (Exception ex) {
                    System.out.println("Unable to submit task to thread");
                    throw new RuntimeException();
                }
            }
            // Set up task for final (shorter) thread
            RunnableFuture finalTask = new FutureTask(new Callable<double[][][]>() {
                @Override
                public double[][][] call() {
                    double[][][] tempResult = new double[rowsInFinalThread][maxX - minX][10];
                    String threadName = "Thread_final";
                    int startingScanNumber = rowsPerThread * (numberOfFullThreads) + minY;
                    try {
                        Calculator.unGrid(tempResult, startingScanNumber, rowsInFinalThread, minX, maxX, s0, NADIR_VIEW_SCAN_PIX_NUM_ADS_Records, FWARD_VIEW_SCAN_PIX_NUM_ADS_Records, SCAN_PIXEL_X_AND_Y_ADS_Records, GEOLOCATION_ADS_Records, scanYCoords, threadName, finalParameters, pixelProjectionMap);
                    } catch (Exception ex) {
                        System.out.println(threadName + " crash");
                        System.out.println(ex.getMessage());
                    }
                    // This commented code is for serialising the results to a temporary file and is currently unused
//                    try {
//                        threadName += ".temp";
//                        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(threadName));
//                        out.writeObject(tempResult);
//                        out.close();
//                    } catch (Exception ex) {
//                        System.out.println(threadName + " crash");
//                        System.out.println(ex.getMessage());
//                    }
                    return tempResult;
                }
            });
            Future<?> submit = threadPool.submit(finalTask);
            if (submit.isCancelled()) {
                System.out.println("Final thread cancelled prematurely");
            }

            // Wait for threads to finish computations (max limit 60 mins)
            try {
                threadPool.shutdown();
                threadPool.awaitTermination(60, TimeUnit.MINUTES);
            } catch (InterruptedException ex) {
                System.out.println(ex.getMessage());
            }



            /* Create an array structure for properties of each pixel
             Imagine 2D arrays representing pixels across track by along track, stacked into 3D
             The 2D arrays contain pixel values for:
             0- Nadir View Latitude
             1- Nadir View Longitude
             2- Forward View Latitude
             3- Forward View Longitude
             4- Nadir View Acquisition Times
             5- Forward View Acquisition Times
             6- Nadir View Pixel Field of View (FOV) Across Track
             7- Nadir View Pixel FOV Along Track     
             8- Forward View Pixel FOV Across Track
             9- Forward View Pixel FOV Along Track
             */
            double[][][] pixelPositions = new double[maxY - minY][maxX - minX][10];
            // Get the computation results and place them in the final array for ouput
            if (threadPool.isTerminated()) {
                try {
                    int n = 0;
                    for (RunnableFuture task : tasks) {
                        // This commented code is for returning the serialised results from a temp file and is currently unused
//                        String tempFile = (String) task.get();
//                        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(tempFile)));
//                        double[][][] result = (double[][][]) in.readObject();
                        double[][][] result = (double[][][]) task.get();
                        for (int i = 0; i < result.length; i++) {
                            for (int j = 0; j < result[0].length; j++) {
                                pixelPositions[n + i][j][0] = result[i][j][0];
                                pixelPositions[n + i][j][1] = result[i][j][1];
                                pixelPositions[n + i][j][2] = result[i][j][5];
                                pixelPositions[n + i][j][3] = result[i][j][6];
                                pixelPositions[n + i][j][4] = result[i][j][2];
                                pixelPositions[n + i][j][5] = result[i][j][7];
                                pixelPositions[n + i][j][6] = result[i][j][3];
                                pixelPositions[n + i][j][7] = result[i][j][4];
                                pixelPositions[n + i][j][8] = result[i][j][8];
                                pixelPositions[n + i][j][9] = result[i][j][9];
                            }
                        }
                        n += result.length;
//                        in.close();
                    }
                    // This commented code is for returning the serialised results from a temp file and is currently unused
//                    String tempFile = (String) finalTask.get();
//                    ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(tempFile)));
//                    double[][][] result = (double[][][]) in.readObject();
                    double[][][] result = (double[][][]) finalTask.get();
                    for (int i = 0; i < result.length; i++) {
                        for (int j = 0; j < result[0].length; j++) {
                            pixelPositions[n + i][j][0] = result[i][j][0];
                            pixelPositions[n + i][j][1] = result[i][j][1];
                            pixelPositions[n + i][j][2] = result[i][j][5];
                            pixelPositions[n + i][j][3] = result[i][j][6];
                            pixelPositions[n + i][j][4] = result[i][j][2];
                            pixelPositions[n + i][j][5] = result[i][j][7];
                            pixelPositions[n + i][j][6] = result[i][j][3];
                            pixelPositions[n + i][j][7] = result[i][j][4];
                            pixelPositions[n + i][j][8] = result[i][j][8];
                            pixelPositions[n + i][j][9] = result[i][j][9];
                        }
                    }
//                    in.close();
                } catch (InterruptedException | ExecutionException ex) {
                    System.out.println(ex.getMessage());
                }
            }
        // This commented code is for removing the serialised results from a temp file and is currently unused
//            /* Remove the temporary result files */
//            for (RunnableFuture task : tasks) {
//                File filename = new File((String) task.get());
//                filename.deleteOnExit();
//            }
//            File filename = new File((String) finalTask.get());
//            filename.deleteOnExit();

            if (parameters.outputFileLocation.contains(".h5")){
                HDFWriter.writeDataTofile(parameters, pixelPositions, maxX, maxY, minX, minY);
            } else{
                NetCDF4Writer.writeDataTofile(parameters, pixelPositions, maxX, maxY, minX, minY);
            }
            
        } catch (IOException | RuntimeException ex) {
            System.out.println(ex.getCause());
            System.out.println(ex.fillInStackTrace());
            System.out.println("Error in setup");
            System.exit(1);
        }
    }
}
