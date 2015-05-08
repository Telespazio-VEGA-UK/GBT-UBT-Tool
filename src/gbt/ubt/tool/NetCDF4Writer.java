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

import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import ucar.nc2.jni.netcdf.Nc4Iosp;

import ucar.nc2.Dimension;
import ucar.ma2.*;
import ucar.nc2.Attribute;
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
            Nc4Iosp.setLibraryAndPath(Paths.get("").toAbsolutePath().toString() + "\\netCDF 4.3.3.1\\bin", "netcdf");
            boolean clibraryPresent = Nc4Iosp.isClibraryPresent();
            if (!clibraryPresent) {
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

            // Create dimensions
            Dimension jDim = dataFile.addDimension(null, "j", (maxX - minX));
            Dimension iDim = dataFile.addDimension(null, "i", (maxY - minY));
            List<Dimension> dims = new ArrayList<>();
            dims.add(iDim);
            dims.add(jDim);
            List<Dimension> ims = new ArrayList<>();

            // Define variables
            Variable dataVariableNadLat = dataFile.addVariable(null, "nadir view latitude", DataType.FLOAT, dims);
            dataVariableNadLat.addAttribute(new Attribute("long_name", "nadir view ungridded latitude"));
            dataVariableNadLat.addAttribute(new Attribute("standard_name", "latitude"));
            dataVariableNadLat.addAttribute(new Attribute("units", "degrees_north"));
            dataVariableNadLat.addAttribute(new Attribute("_FillValue", -999999.0f));
            dataVariableNadLat.addAttribute(new Attribute("missing_value", -888888.0f));

            Variable dataVariableNadLon = dataFile.addVariable(null, "nadir view longitude", DataType.FLOAT, dims);
            dataVariableNadLon.addAttribute(new Attribute("long_name", "nadir view ungridded longitude"));
            dataVariableNadLon.addAttribute(new Attribute("standard_name", "longitude"));
            dataVariableNadLon.addAttribute(new Attribute("units", "degrees_east"));
            dataVariableNadLon.addAttribute(new Attribute("_FillValue", -999999.0f));
            dataVariableNadLon.addAttribute(new Attribute("missing_value", -888888.0f));

            Variable dataVariableFwdLat = dataFile.addVariable(null, "forward view latitude", DataType.FLOAT, dims);
            dataVariableFwdLat.addAttribute(new Attribute("long_name", "forward view ungridded latitude"));
            dataVariableFwdLat.addAttribute(new Attribute("standard_name", "latitude"));
            dataVariableFwdLat.addAttribute(new Attribute("units", "degrees_north"));
            dataVariableFwdLat.addAttribute(new Attribute("_FillValue", -999999.0f));

            Variable dataVariableFwdLon = dataFile.addVariable(null, "forward view longitude", DataType.FLOAT, dims);
            dataVariableFwdLon.addAttribute(new Attribute("long_name", "forward view ungridded longitude"));
            dataVariableFwdLon.addAttribute(new Attribute("standard_name", "longitude"));
            dataVariableFwdLon.addAttribute(new Attribute("units", "degrees_east"));
            dataVariableFwdLon.addAttribute(new Attribute("_FillValue", -999999.0f));

            Variable dataVariableNadDSR = dataFile.addVariable(null, "nadir view acquisition time", DataType.DOUBLE, dims);
            dataVariableNadDSR.addAttribute(new Attribute("long_name", "nadir view ungridded acquisition time (mjd2000)"));
            dataVariableNadDSR.addAttribute(new Attribute("standard_name", "time"));
            dataVariableNadDSR.addAttribute(new Attribute("units", "days since 2000-01-01 00:00:00"));
            dataVariableNadDSR.addAttribute(new Attribute("_FillValue", -999999.0));
            dataVariableNadDSR.addAttribute(new Attribute("missing_value", -888888.0));
            dataVariableNadDSR.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));

            Variable dataVariableFwdDSR = dataFile.addVariable(null, "forward view acquisition time", DataType.DOUBLE, dims);
            dataVariableFwdDSR.addAttribute(new Attribute("long_name", "forward view ungridded acquisition time (mjd2000)"));
            dataVariableFwdDSR.addAttribute(new Attribute("standard_name", "time"));
            dataVariableFwdDSR.addAttribute(new Attribute("units", "days since 2000-01-01 00:00:00"));
            dataVariableFwdDSR.addAttribute(new Attribute("_FillValue", -999999.0));
            dataVariableFwdDSR.addAttribute(new Attribute("missing_value", -888888.0));
            dataVariableFwdDSR.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));

            Variable dataVariableNad1200 = dataFile.addVariable(null, "btemp nadir 12000nm", DataType.SHORT, dims);
            dataVariableNad1200.addAttribute(new Attribute("long_name", "Brightness temperature, nadir view (11500-12500 nm)"));
            dataVariableNad1200.addAttribute(new Attribute("standard_name", "toa_brightness_temperature"));
            dataVariableNad1200.addAttribute(new Attribute("units", "K"));
            dataVariableNad1200.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableNad1200.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableNad1200.addAttribute(new Attribute("_FillValue", -999999.0f));
            dataVariableNad1200.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));
            dataVariableNad1200.addAttribute(new Attribute("grid_mapping", "crs"));

            Variable dataVariableFwd1200 = dataFile.addVariable(null, "btemp fward 12000nm", DataType.SHORT, dims);
            dataVariableFwd1200.addAttribute(new Attribute("long_name", "Brightness temperature, forward view (11500-12500 nm)"));
            dataVariableFwd1200.addAttribute(new Attribute("standard_name", "toa_brightness_temperature"));
            dataVariableFwd1200.addAttribute(new Attribute("units", "K"));
            dataVariableFwd1200.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableFwd1200.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableFwd1200.addAttribute(new Attribute("_FillValue", -999999.0f));
            dataVariableFwd1200.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));
            dataVariableFwd1200.addAttribute(new Attribute("grid_mapping", "crs"));

            Variable dataVariableCRS = dataFile.addVariable(null, "crs", DataType.INT, ims);
            dataVariableCRS.addAttribute(new Attribute("grid_mapping_name", "latitude_longitude"));
            dataVariableCRS.addAttribute(new Attribute("longitude_of_prime_meridian", 0.0));
            dataVariableCRS.addAttribute(new Attribute("semi_major_axis", 6378137.0));
            dataVariableCRS.addAttribute(new Attribute("inverse_flattening", 298.257223563));

            // Create file
            dataFile.create();

            // Create data arrays
            ArrayFloat.D2 NadLatOut = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayFloat.D2 NadLonOut = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayFloat.D2 FwdLatOut = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayFloat.D2 FwdLonOut = new ArrayFloat.D2(iDim.getLength(), jDim.getLength());
            ArrayDouble.D2 NadDSROut = new ArrayDouble.D2(iDim.getLength(), jDim.getLength());
            ArrayDouble.D2 FwdDSROut = new ArrayDouble.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Nad1200Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Fwd1200Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());

            Product readProduct = ProductIO.readProduct(parameters.inputFileLocation);
            Band nad1200 = readProduct.getBand("btemp_nadir_1200");
            Band fwd1200 = readProduct.getBand("btemp_fward_1200");

            int i, j;

            for (i = 0; i < iDim.getLength(); i++) {
                for (j = 0; j < jDim.getLength(); j++) {
                    NadLatOut.set(i, j, (float) pixelPositions[i][j][0]);
                    NadLonOut.set(i, j, (float) pixelPositions[i][j][1]);
                    FwdLatOut.set(i, j, (float) pixelPositions[i][j][2]);
                    FwdLonOut.set(i, j, (float) pixelPositions[i][j][3]);
                    NadDSROut.set(i, j, pixelPositions[i][j][4]);
                    FwdDSROut.set(i, j, pixelPositions[i][j][5]);
                    if (pixelPositions[i][j][0] != -999999.0 || pixelPositions[i][j][0] != -888888.0) {
                        Nad1200Out.set(i, j, (short) (nad1200.getSampleFloat(511 - (minX + j), minY + i) * 100));
                    } 
                    if (pixelPositions[i][j][2] != -999999.0) {
                        Fwd1200Out.set(i, j, (short) (fwd1200.getSampleFloat(511 - (minX + j), minY + i) * 100));
                    }
                }
            }
            readProduct.closeIO();
            // Write data to file
            dataFile.write(dataVariableNadLat, NadLatOut);
            dataFile.write(dataVariableNadLon, NadLonOut);
            dataFile.write(dataVariableFwdLat, FwdLatOut);
            dataFile.write(dataVariableFwdLon, FwdLonOut);
            dataFile.write(dataVariableNadDSR, NadDSROut);
            dataFile.write(dataVariableFwdDSR, FwdDSROut);
            dataFile.write(dataVariableNad1200, Nad1200Out);
            dataFile.write(dataVariableFwd1200, Fwd1200Out);

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
