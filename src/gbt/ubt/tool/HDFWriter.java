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

import ncsa.hdf.object.*;
import ncsa.hdf.object.h5.*;

/**
 *
 * @author ABeaton, Telespazio VEGA UK Ltd 30/10/2013
 *
 * Contact: alasdhair(dot)beaton(at)telespazio(dot)com
 *
 * 
 */
class HDFWriter {

    static void writeDataTofile(InputParameters parameters, double[][][] pixelPositions, int maxX, int maxY, int minX, int minY) {
        try {

            // Retrieve an instance of the implementing class for the HDF5 format
            FileFormat fileFormat = FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5);

            // If the implementing class wasn't found, it's an error.
            if (fileFormat == null) {
                System.err.println("Cannot find HDF5 FileFormat.");
                return;
            }
            // If the implementing class was found, use it to create a new HDF5 file
            // with a specific file name.  
            //
            // If the specified file already exists, it is truncated.
            // The default HDF5 file creation and access properties are used.
            // 
            H5File outputFile = (H5File) fileFormat.createFile(parameters.outputFileLocation, FileFormat.FILE_CREATE_DELETE);

            // Check for error condition and report.
            if (outputFile == null) {
                System.err.println("Failed to create file: " + parameters.outputFileLocation);
                System.exit(1);
            } else {
                System.out.println("Generating Output File");
                // open the file and retrieve the root group
                outputFile.open();
                Group root = (Group) ((javax.swing.tree.DefaultMutableTreeNode) outputFile.getRootNode()).getUserObject();
                Group geolocation = outputFile.createGroup("Geolocation", root);
                Group acqTime = outputFile.createGroup("Acquisition_Times", root);
                Group FOVProjection = outputFile.createGroup("FOV_Projection", root);

                long[] dimensions = {maxY - minY, maxX - minX};

                Datatype bit32 = outputFile.createDatatype(Datatype.CLASS_FLOAT, 4, Datatype.NATIVE, -1);
                Datatype bit64 = outputFile.createDatatype(Datatype.CLASS_FLOAT, 8, Datatype.NATIVE, -1);

                String[] geolocationDatasets = {"Nadir_Latitude", "Nadir_Longitude", "Forward_Latitude", "Forward_Longitude"};
                String[] projectionDatasets = {"Nadir_Along_Track", "Nadir_Across_Track", "Forward_Along_Track", "Forward_Across_Track"};
                String[] dsrDatasets = {"Nadir_Acquisition_Time", "Forward_Acquisition_Time"};

                for (int i = 0; i < geolocationDatasets.length; i++) {
                    float[] tempOutput = new float[(maxX - minX) * (maxY - minY)];
                    for (int k = minX; k < maxX; k++) {
                        for (int j = minY; j < maxY; j++) {
                            tempOutput[k - minX + ((j - minY) * (maxX - minX))] = (float) pixelPositions[j - minY][k - minX][i];
                        }
                    }
                    Dataset dataset = outputFile.createScalarDS(geolocationDatasets[i], geolocation, bit32, dimensions, null, null, -1000, tempOutput);
                }
                for (int i = 6; i < projectionDatasets.length + 6; i++) {
                    float[] tempOutput = new float[(maxX - minX) * (maxY - minY)];
                    for (int k = minX; k < maxX; k++) {
                        for (int j = minY; j < maxY; j++) {
                            tempOutput[k - minX + ((j - minY) * (maxX - minX))] = (float) pixelPositions[j - minY][k - minX][i];
                        }
                    }
                    Dataset dataset = outputFile.createScalarDS(projectionDatasets[i - 6], FOVProjection, bit32, dimensions, null, null, 0, tempOutput);
                }
                for (int i = 4; i < dsrDatasets.length + 4; i++) {
                    double[] tempOutput = new double[(maxX - minX) * (maxY - minY)];
                    for (int k = minX; k < maxX; k++) {
                        for (int j = minY; j < maxY; j++) {
                            tempOutput[k - minX + ((j - minY) * (maxX - minX))] = pixelPositions[j - minY][k - minX][i];
                        }
                    }
                    Dataset dataset = outputFile.createScalarDS(dsrDatasets[i - 4], acqTime, bit64, dimensions, null, null, 0, tempOutput);
                }
                // Add attributes to file
                long[] attrDims = {1};
                String attrName = "ATX_TOA_1P (GBT) Input File";
                String[] attrValue = {parameters.inputFileLocation.substring(parameters.inputFileLocation.lastIndexOf("/") + 1)};
                int stringLength = attrValue[0].length() + 1;
                if (stringLength < 23){
                    stringLength = 23;
                }
                Datatype attrType = new H5Datatype(Datatype.CLASS_STRING, stringLength, -1, -1);
                Attribute attr = new Attribute(attrName, attrType, attrDims, attrValue);
                outputFile.writeAttribute(root, attr, false);

                attrName = "End of product where no ADS available trimmed";
                attrValue[0] = String.valueOf(parameters.trimProductEndWhereNoADS);
                Attribute attrA = new Attribute(attrName, attrType, attrDims, attrValue);
                outputFile.writeAttribute(root, attrA, false);

                attrName = "Channel used for FOV projection";
                attrValue[0] = String.valueOf(parameters.FOVMeasurementDataBandName);
                Attribute attrB = new Attribute(attrName, attrType, attrDims, attrValue);
                outputFile.writeAttribute(root, attrB, false);

                attrName = "Reporting extent of IFOV (fraction)";
                attrValue[0] = String.valueOf(parameters.pixelIFOVReportingExtent);
                Attribute attrC = new Attribute(attrName, attrType, attrDims, attrValue);
                outputFile.writeAttribute(root, attrC, false);

                attrName = "Fill value for no data";
                attrValue[0] = "-999999.0";
                Attribute attr1 = new Attribute(attrName, attrType, attrDims, attrValue);
                outputFile.writeAttribute(root, attr1, false);

                attrName = "Fill value for scan number < 32";
                attrValue[0] = "-888888.0";
                Attribute attr2 = new Attribute(attrName, attrType, attrDims, attrValue);
                outputFile.writeAttribute(root, attr2, false);

                if (parameters.subsetFlag) {
                    attrName = "Pixels in product";
                    if (parameters.singlePixelFlag) {
                        attrValue[0] = "[" + String.valueOf(parameters.x1) + "," + String.valueOf(parameters.y1) + "]";
                    } else {
                        attrValue[0] = "[" + String.valueOf(parameters.x1) + "," + String.valueOf(parameters.y1) + "] [" + String.valueOf(parameters.x2) + "," + String.valueOf(parameters.y2) + "]";
                    }

                    Attribute attr6 = new Attribute(attrName, attrType, attrDims, attrValue);
                    outputFile.writeAttribute(root, attr6, false);
                }
                attrName = "Acquisition time unit";
                attrValue[0] = "MJD2000 (UTC)";
                Attribute attr3 = new Attribute(attrName, attrType, attrDims, attrValue);
                outputFile.writeAttribute(acqTime, attr3, false);

                attrName = "FOV projection unit";
                attrValue[0] = "km";
                Attribute attr4 = new Attribute(attrName, attrType, attrDims, attrValue);
                outputFile.writeAttribute(FOVProjection, attr4, false);

                attrName = "Geolocation unit";
                attrValue[0] = "Decimal degrees";
                Attribute attr5 = new Attribute(attrName, attrType, attrDims, attrValue);
                outputFile.writeAttribute(geolocation, attr5, false);

                outputFile.close();

                // Check can find created file
                if (outputFile.canRead() == false) {
                    System.err.println("Failed to create file: " + parameters.outputFileLocation);
                    System.exit(1);
                }
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            System.out.println("Error writing output");
            System.exit(1);
        }
    }
}
