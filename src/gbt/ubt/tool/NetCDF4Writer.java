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

import com.bc.ceres.core.ProgressMonitor;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import ucar.nc2.jni.netcdf.Nc4Iosp;

import ucar.nc2.Dimension;
import ucar.ma2.*;
import ucar.nc2.Attribute;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.Variable;

/**
 *
 * @author abeaton
 */
public class NetCDF4Writer {

    public static void writeDataTofile(InputParameters parameters, double[][][] pixelPositions, int maxX, int maxY, int minX, int minY) {
        NetcdfFileWriter dataFile = null;
        try {
            // NetCDF java library does not support direct write to NetCDF4 format. Use netCDF C library for file writing with JNI interface
            Nc4Iosp.setLibraryAndPath(Paths.get("").toAbsolutePath().toString() + "\\netCDF 4.3.3.1\\bin", "netcdf");
            boolean clibraryPresent = Nc4Iosp.isClibraryPresent();
            if (!clibraryPresent) {
                System.out.println("Expecting NetCDF C library at: " + Paths.get("").toAbsolutePath().toString() + "\\netCDF 4.3.3.1\\bin");
                throw new Exception("Could not load NetCDF Library");
            }
            System.out.println("Generating Output File");
            NetcdfFileWriter.Version version = NetcdfFileWriter.Version.netcdf4;
            String outputFile = parameters.outputFileLocation;
            dataFile = NetcdfFileWriter.createNew(version, outputFile, null);

            // Add attributes to file that are compliant with CF conventions
            dataFile.addGroupAttribute(null, new Attribute("Conventions", "CF-1.6"));
            dataFile.addGroupAttribute(null, new Attribute("title", "AATSR GBT-UBT-Tool Output File"));
            dataFile.addGroupAttribute(null, new Attribute("institution", "ESA"));
            dataFile.addGroupAttribute(null, new Attribute("source", parameters.inputFileLocation.substring(parameters.inputFileLocation.lastIndexOf("/"))));
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date = new Date();
            dataFile.addGroupAttribute(null, new Attribute("history", dateFormat.format(date) + " GBT-UBT-Tool" + parameters.toolVersion + " " + parameters.inputString));
            dataFile.addGroupAttribute(null, new Attribute("references", "https://github.com/Telespazio-VEGA-UK/GBT-UBT-Tool"));
            dataFile.addGroupAttribute(null, new Attribute("comment", "none"));

            // Create dimensions of the data arrays
            Dimension jDim = dataFile.addDimension(null, "j", (maxX - minX));
            Dimension iDim = dataFile.addDimension(null, "i", (maxY - minY));
            List<Dimension> dims = new ArrayList<>();
            dims.add(iDim);
            dims.add(jDim);
            List<Dimension> ims = new ArrayList<>();

            // Define groups
            Group root = dataFile.addGroup(null, "");
            Group projection = dataFile.addGroup(root, "FOV Projection");
            Group measurement = dataFile.addGroup(root, "Measurements");
            Group acq_times = dataFile.addGroup(root, "Acquisition Times");
            Group geolocation = dataFile.addGroup(root, "Geolocation");
            Group flags = dataFile.addGroup(root, "Flags");

            // Define variables
            // Note non-standard CF use of differing _FillValue and missing_value
            // Geolocation
            Variable dataVariableNadLat = dataFile.addVariable(geolocation, "nadir view latitude", DataType.FLOAT, dims);
            dataVariableNadLat.addAttribute(new Attribute("long_name", "nadir view ungridded latitude"));
            dataVariableNadLat.addAttribute(new Attribute("standard_name", "latitude"));
            dataVariableNadLat.addAttribute(new Attribute("units", "degrees_north"));
            dataVariableNadLat.addAttribute(new Attribute("_FillValue", -999999.0f));
            dataVariableNadLat.addAttribute(new Attribute("missing_value", -888888.0f));

            Variable dataVariableNadLon = dataFile.addVariable(geolocation, "nadir view longitude", DataType.FLOAT, dims);
            dataVariableNadLon.addAttribute(new Attribute("long_name", "nadir view ungridded longitude"));
            dataVariableNadLon.addAttribute(new Attribute("standard_name", "longitude"));
            dataVariableNadLon.addAttribute(new Attribute("units", "degrees_east"));
            dataVariableNadLon.addAttribute(new Attribute("_FillValue", -999999.0f));
            dataVariableNadLon.addAttribute(new Attribute("missing_value", -888888.0f));

            Variable dataVariableFwdLat = dataFile.addVariable(geolocation, "forward view latitude", DataType.FLOAT, dims);
            dataVariableFwdLat.addAttribute(new Attribute("long_name", "forward view ungridded latitude"));
            dataVariableFwdLat.addAttribute(new Attribute("standard_name", "latitude"));
            dataVariableFwdLat.addAttribute(new Attribute("units", "degrees_north"));
            dataVariableFwdLat.addAttribute(new Attribute("_FillValue", -999999.0f));

            Variable dataVariableFwdLon = dataFile.addVariable(geolocation, "forward view longitude", DataType.FLOAT, dims);
            dataVariableFwdLon.addAttribute(new Attribute("long_name", "forward view ungridded longitude"));
            dataVariableFwdLon.addAttribute(new Attribute("standard_name", "longitude"));
            dataVariableFwdLon.addAttribute(new Attribute("units", "degrees_east"));
            dataVariableFwdLon.addAttribute(new Attribute("_FillValue", -999999.0f));

            // Acquisition times
            Variable dataVariableNadDSR = dataFile.addVariable(acq_times, "nadir view acquisition time", DataType.DOUBLE, dims);
            dataVariableNadDSR.addAttribute(new Attribute("long_name", "nadir view ungridded acquisition time (mjd2000)"));
            dataVariableNadDSR.addAttribute(new Attribute("standard_name", "time"));
            dataVariableNadDSR.addAttribute(new Attribute("units", "days since 2000-01-01 00:00:00"));
            dataVariableNadDSR.addAttribute(new Attribute("_FillValue", -999999.0));
            dataVariableNadDSR.addAttribute(new Attribute("missing_value", -888888.0));
            dataVariableNadDSR.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));

            Variable dataVariableFwdDSR = dataFile.addVariable(acq_times, "forward view acquisition time", DataType.DOUBLE, dims);
            dataVariableFwdDSR.addAttribute(new Attribute("long_name", "forward view ungridded acquisition time (mjd2000)"));
            dataVariableFwdDSR.addAttribute(new Attribute("standard_name", "time"));
            dataVariableFwdDSR.addAttribute(new Attribute("units", "days since 2000-01-01 00:00:00"));
            dataVariableFwdDSR.addAttribute(new Attribute("_FillValue", -999999.0));
            dataVariableFwdDSR.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));

            // Projections
            Variable dataVariableNadFOVAcross = dataFile.addVariable(projection, "nadir view across track FOV projection", DataType.FLOAT, dims);
            dataVariableNadFOVAcross.addAttribute(new Attribute("long_name", "nadir view pixel field of view projection across track"));
            dataVariableNadFOVAcross.addAttribute(new Attribute("standard_name", "projection_x_coordinate"));
            dataVariableNadFOVAcross.addAttribute(new Attribute("units", "km"));
            dataVariableNadFOVAcross.addAttribute(new Attribute("_FillValue", -999999.0f));
            dataVariableNadFOVAcross.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));

            Variable dataVariableNadFOVAlong = dataFile.addVariable(projection, "nadir view along track FOV projection", DataType.FLOAT, dims);
            dataVariableNadFOVAlong.addAttribute(new Attribute("long_name", "nadir view pixel field of view projection along track"));
            dataVariableNadFOVAlong.addAttribute(new Attribute("standard_name", "projection_y_coordinate"));
            dataVariableNadFOVAlong.addAttribute(new Attribute("units", "km"));
            dataVariableNadFOVAlong.addAttribute(new Attribute("_FillValue", -999999.0f));
            dataVariableNadFOVAlong.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));

            Variable dataVariableFwdFOVAcross = dataFile.addVariable(projection, "forward view across track FOV projection", DataType.FLOAT, dims);
            dataVariableFwdFOVAcross.addAttribute(new Attribute("long_name", "forward view pixel field of view projection across track"));
            dataVariableFwdFOVAcross.addAttribute(new Attribute("standard_name", "projection_x_coordinate"));
            dataVariableFwdFOVAcross.addAttribute(new Attribute("units", "km"));
            dataVariableFwdFOVAcross.addAttribute(new Attribute("_FillValue", -999999.0f));
            dataVariableFwdFOVAcross.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));

            Variable dataVariableFwdFOVAlong = dataFile.addVariable(projection, "forward view along track FOV projection", DataType.FLOAT, dims);
            dataVariableFwdFOVAlong.addAttribute(new Attribute("long_name", "forward view pixel field of view projection along track"));
            dataVariableFwdFOVAlong.addAttribute(new Attribute("standard_name", "projection_y_coordinate"));
            dataVariableFwdFOVAlong.addAttribute(new Attribute("units", "km"));
            dataVariableFwdFOVAlong.addAttribute(new Attribute("_FillValue", -999999.0f));
            dataVariableFwdFOVAlong.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));

            // Measurement
            // Note link between measurement data to flag variables
            List<String> variableNames = new ArrayList<>(Arrays.asList("btemp_nadir_1200", "btemp_fward_1200", "btemp_nadir_1100", "btemp_fward_1100", "btemp_nadir_0370", "btemp_fward_0370", "reflec_nadir_1600", "reflec_fward_1600", "reflec_nadir_0870", "reflec_fward_0870", "reflec_nadir_0670", "reflec_fward_0670", "reflec_nadir_0550", "reflec_fward_0550"));
            List<String> longNames = new ArrayList<>(Arrays.asList("Brightness temperature, nadir view (11500-12500 nm)", "Brightness temperature, forward view (11500-12500 nm)", "Brightness temperature, nadir view (10400-11300 nm)", "Brightness temperature, forward view (10400-11300 nm)", "Brightness temperature, nadir view (3505-3895 nm)", "Brightness temperature, forward view (3505-3895 nm)"));
            longNames.addAll(Arrays.asList("Reflectance, nadir view (1580-1640 nm)", "Reflectance, forward view (1580-1640 nm)", "Reflectance, nadir view (855-875 nm)", "Reflectance, forward view (855-875 nm)", "Reflectance, nadir view (649-669 nm)", "Reflectance, forward view (649-669 nm)", "Reflectance, nadir view (545-565 nm)", "Reflectance, forward view (545-565 nm)"));

            for (String variable : variableNames) {
                Variable dataVariable = dataFile.addVariable(measurement, variable, DataType.SHORT, dims);
                dataVariable.addAttribute(new Attribute("long_name", longNames.get(variableNames.indexOf(variable))));
                if (variable.contains("btemp")) {
                    dataVariable.addAttribute(new Attribute("standard_name", "toa_brightness_temperature"));
                    dataVariable.addAttribute(new Attribute("units", "K"));
                } else {
                    dataVariable.addAttribute(new Attribute("standard_name", "toa_reflectance"));
                    dataVariable.addAttribute(new Attribute("units", "Percent"));
                }
                if (variable.contains("nadir")) {
                    dataVariable.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));
                    dataVariable.addAttribute(new Attribute("ancillary_variables", "confid_flags_nadir cloud_flags_nadir"));
                } else {
                    dataVariable.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));
                    dataVariable.addAttribute(new Attribute("ancillary_variables", "confid_flags_fward cloud_flags_fward"));
                }
                dataVariable.addAttribute(new Attribute("scale_factor", 0.01f));
                dataVariable.addAttribute(new Attribute("add_offset ", 0.0f));
                dataVariable.addAttribute(new Attribute("_FillValue", (short) -2));
                dataVariable.addAttribute(new Attribute("grid_mapping", "crs"));
                dataVariable.addAttribute(new Attribute("valid_min", (short) 0));
            }

            // Flag data
            // Flag data is packed as integer, future todo will enumerate flags for variables.
            Variable dataVariableNadConfid = dataFile.addVariable(flags, "confid flags nadir", DataType.SHORT, dims);
            dataVariableNadConfid.addAttribute(new Attribute("long_name", "Confidence flags, nadir view"));
            dataVariableNadConfid.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));
            dataVariableNadConfid.addAttribute(new Attribute("grid_mapping", "crs"));

            Variable dataVariableFwdConfid = dataFile.addVariable(flags, "confid flags fward", DataType.SHORT, dims);
            dataVariableFwdConfid.addAttribute(new Attribute("long_name", "Confidence flags, forward view"));
            dataVariableFwdConfid.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));
            dataVariableFwdConfid.addAttribute(new Attribute("grid_mapping", "crs"));

            Variable dataVariableNadCloud = dataFile.addVariable(flags, "cloud flags nadir", DataType.SHORT, dims);
            dataVariableNadCloud.addAttribute(new Attribute("long_name", "Cloud flags, nadir view"));
            dataVariableNadCloud.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));
            dataVariableNadCloud.addAttribute(new Attribute("grid_mapping", "crs"));

            Variable dataVariableFwdCloud = dataFile.addVariable(flags, "cloud flags fward", DataType.SHORT, dims);
            dataVariableFwdCloud.addAttribute(new Attribute("long_name", "Cloud flags, forward view"));
            dataVariableFwdCloud.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));
            dataVariableFwdCloud.addAttribute(new Attribute("grid_mapping", "crs"));

            // CRS
            Variable dataVariableCRS = dataFile.addVariable(null, "crs", DataType.INT, ims);
            dataVariableCRS.addAttribute(new Attribute("long_name", "coordinate reference system"));
            dataVariableCRS.addAttribute(new Attribute("grid_mapping_name", "latitude_longitude"));
            dataVariableCRS.addAttribute(new Attribute("longitude_of_prime_meridian", 0.0));
            dataVariableCRS.addAttribute(new Attribute("semi_major_axis", 6378137.0));
            dataVariableCRS.addAttribute(new Attribute("inverse_flattening", 298.257223563));

            // Create file
            dataFile.create();

            // Create data arrays (excluding measurement)
            ArrayFloat.D2 NadLatOut = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayFloat.D2 NadLonOut = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayFloat.D2 FwdLatOut = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayFloat.D2 FwdLonOut = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayDouble.D2 NadDSROut = new ArrayDouble.D2(iDim.getLength(), jDim.getLength());
            ArrayDouble.D2 FwdDSROut = new ArrayDouble.D2(iDim.getLength(), jDim.getLength());
            ArrayFloat.D2 NadLatFOVAcross = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayFloat.D2 NadLatFOVAlong = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayFloat.D2 FwdLatFOVAcross = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayFloat.D2 FwdLatFOVAlong = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 NadConfidOut = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 FwdConfidOut = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 NadCloudOut = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 FwdCloudOut = new ArrayShort.D2(iDim.getLength(), jDim.getLength());

            // Write extracted data (write measurement data later to reduce memory overhead)
            int i, j, k;

            Product readProduct = ProductIO.readProduct(parameters.inputFileLocation);
            Band nadConfid = readProduct.getBand("confid_flags_nadir");
            Band nadCloud = readProduct.getBand("cloud_flags_nadir");
            Band fwdConfid = readProduct.getBand("confid_flags_fward");
            Band fwdCloud = readProduct.getBand("cloud_flags_fward");

            int jDimLength = jDim.getLength();
            int iDimLength = iDim.getLength();
            ProductData nadConfidValues = ProductData.createInstance(new short[jDim.getLength() * iDim.getLength()]);
            nadConfid.readRasterData(minX, minY, jDimLength, iDimLength, nadConfidValues, ProgressMonitor.NULL);
            ProductData nadCloudValues = ProductData.createInstance(new short[jDim.getLength() * iDim.getLength()]);
            nadCloud.readRasterData(minX, minY, jDimLength, iDimLength, nadCloudValues, ProgressMonitor.NULL);
            ProductData fwdConfidValues = ProductData.createInstance(new short[jDim.getLength() * iDim.getLength()]);
            fwdConfid.readRasterData(minX, minY, jDimLength, iDimLength, fwdConfidValues, ProgressMonitor.NULL);
            ProductData fwdCloudValues = ProductData.createInstance(new short[jDim.getLength() * iDim.getLength()]);
            fwdCloud.readRasterData(minX, minY, jDimLength, iDimLength, fwdCloudValues, ProgressMonitor.NULL);
            
            for (i = 0; i < iDim.getLength(); i++) {
                for (j = 0; j < jDim.getLength(); j++) {
                    NadLatOut.set(i, j, (float) pixelPositions[i][j][0]);
                    NadLonOut.set(i, j, (float) pixelPositions[i][j][1]);
                    FwdLatOut.set(i, j, (float) pixelPositions[i][j][2]);
                    FwdLonOut.set(i, j, (float) pixelPositions[i][j][3]);
                    NadDSROut.set(i, j, pixelPositions[i][j][4]);
                    FwdDSROut.set(i, j, pixelPositions[i][j][5]);
                    NadLatFOVAlong.set(i, j, (float) pixelPositions[i][j][6]);
                    NadLatFOVAcross.set(i, j, (float) pixelPositions[i][j][7]);
                    FwdLatFOVAlong.set(i, j, (float) pixelPositions[i][j][8]);
                    FwdLatFOVAcross.set(i, j, (float) pixelPositions[i][j][9]);
                    NadConfidOut.set(i, j, (short)  nadConfidValues.getElemIntAt(((jDimLength-1)-j) + (i*jDimLength)));
                    FwdConfidOut.set(i, j, (short) nadCloudValues.getElemIntAt(((jDimLength-1)-j) + (i*jDimLength)));
                    NadCloudOut.set(i, j, (short) fwdConfidValues.getElemIntAt(((jDimLength-1)-j) + (i*jDimLength)));
                    FwdCloudOut.set(i, j, (short) fwdCloudValues.getElemIntAt(((jDimLength-1)-j) + (i*jDimLength)));
                }
            }
            dataFile.write(dataVariableNadLat, NadLatOut);
            dataFile.write(dataVariableNadLon, NadLonOut);
            dataFile.write(dataVariableFwdLat, FwdLatOut);
            dataFile.write(dataVariableFwdLon, FwdLonOut);
            dataFile.write(dataVariableNadDSR, NadDSROut);
            dataFile.write(dataVariableFwdDSR, FwdDSROut);
            dataFile.write(dataVariableNadFOVAcross, NadLatFOVAcross);
            dataFile.write(dataVariableNadFOVAlong, NadLatFOVAlong);
            dataFile.write(dataVariableFwdFOVAcross, FwdLatFOVAcross);
            dataFile.write(dataVariableFwdFOVAlong, FwdLatFOVAlong);
            dataFile.write(dataVariableNadConfid, NadConfidOut);
            dataFile.write(dataVariableFwdConfid, FwdConfidOut);
            dataFile.write(dataVariableNadCloud, NadCloudOut);
            dataFile.write(dataVariableFwdCloud, FwdCloudOut);

            Double[][][] arrayFlags = new Double[pixelPositions.length + 1][pixelPositions[0].length + 1][2];
            for (i = 0; i < iDim.getLength(); i++) {
                for (j = 0; j < jDim.getLength(); j++) {
                    arrayFlags[i][j][0] = pixelPositions[i][j][0];
                    arrayFlags[i][j][1] = pixelPositions[i][j][2];
                }
            }

            ArrayShort.D2 variableOut = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            System.out.println("Copying Measurement Data");
            for (k = 0; k < variableNames.size(); k++) {
                Band band = readProduct.getBand(variableNames.get(k));
                int viewFlag = 0;
                if (variableNames.contains("fward")) {
                    viewFlag = 1;
                }
                ProductData values = ProductData.createInstance(new short[jDim.getLength() * iDim.getLength()]);
                band.readRasterData(minX, minY, jDimLength, iDimLength, values, ProgressMonitor.NULL);
                for (i = 0; i < iDim.getLength(); i++) {
                    for (j = 0; j < jDim.getLength(); j++) {
                        if (arrayFlags[i][j][viewFlag] != -999999.0 || arrayFlags[i][j][viewFlag] != -888888.0) {
                            short value = (short) values.getElemIntAt(((jDimLength-1)-j) + (i*jDimLength));
                            if (value < 0) {
                                variableOut.set(i, j, (short) -2);
                            } else {
                                variableOut.set(i, j, value);
                            }
                        } else {
                            variableOut.set(i, j, (short) -2);
                        }
                    }
                }
                // Write data to file
                dataFile.write(dataFile.findVariable("/Measurements/" + variableNames.get(k)), variableOut);
            }
            readProduct.closeIO();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (null != dataFile) {
                try {
                    dataFile.close();
                } catch (Exception ioe) {
                    ioe.printStackTrace();
                }
            }
        }
    }
}
