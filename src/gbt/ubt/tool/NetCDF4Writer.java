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
            Variable dataVariableNad1200 = dataFile.addVariable(measurement, "btemp nadir 12000nm", DataType.SHORT, dims);
            dataVariableNad1200.addAttribute(new Attribute("long_name", "Brightness temperature, nadir view (11500-12500 nm)"));
            dataVariableNad1200.addAttribute(new Attribute("standard_name", "toa_brightness_temperature"));
            dataVariableNad1200.addAttribute(new Attribute("units", "K"));
            dataVariableNad1200.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableNad1200.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableNad1200.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableNad1200.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));
            dataVariableNad1200.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableNad1200.addAttribute(new Attribute("ancillary_variables", "confid_flags_nadir cloud_flags_nadir"));

            Variable dataVariableFwd1200 = dataFile.addVariable(measurement, "btemp fward 12000nm", DataType.SHORT, dims);
            dataVariableFwd1200.addAttribute(new Attribute("long_name", "Brightness temperature, forward view (11500-12500 nm)"));
            dataVariableFwd1200.addAttribute(new Attribute("standard_name", "toa_brightness_temperature"));
            dataVariableFwd1200.addAttribute(new Attribute("units", "K"));
            dataVariableFwd1200.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableFwd1200.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableFwd1200.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableFwd1200.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));
            dataVariableFwd1200.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableFwd1200.addAttribute(new Attribute("ancillary_variables", "confid_flags_fward cloud_flags_fward"));

            Variable dataVariableNad1100 = dataFile.addVariable(measurement, "btemp nadir 11000nm", DataType.SHORT, dims);
            dataVariableNad1100.addAttribute(new Attribute("long_name", "Brightness temperature, nadir view (10400-11300 nm)"));
            dataVariableNad1100.addAttribute(new Attribute("standard_name", "toa_brightness_temperature"));
            dataVariableNad1100.addAttribute(new Attribute("units", "K"));
            dataVariableNad1100.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableNad1100.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableNad1100.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableNad1100.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));
            dataVariableNad1100.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableNad1100.addAttribute(new Attribute("ancillary_variables", "confid_flags_nadir cloud_flags_nadir"));

            Variable dataVariableFwd1100 = dataFile.addVariable(measurement, "btemp fward 11000nm", DataType.SHORT, dims);
            dataVariableFwd1100.addAttribute(new Attribute("long_name", "Brightness temperature, forward view (10400-11300 nm)"));
            dataVariableFwd1100.addAttribute(new Attribute("standard_name", "toa_brightness_temperature"));
            dataVariableFwd1100.addAttribute(new Attribute("units", "K"));
            dataVariableFwd1100.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableFwd1100.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableFwd1100.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableFwd1100.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));
            dataVariableFwd1100.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableFwd1100.addAttribute(new Attribute("ancillary_variables", "confid_flags_fward cloud_flags_fward"));

            Variable dataVariableNad370 = dataFile.addVariable(measurement, "btemp nadir 3700nm", DataType.SHORT, dims);
            dataVariableNad370.addAttribute(new Attribute("long_name", "Brightness temperature, nadir view (3505-3895 nm)"));
            dataVariableNad370.addAttribute(new Attribute("standard_name", "toa_brightness_temperature"));
            dataVariableNad370.addAttribute(new Attribute("units", "K"));
            dataVariableNad370.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableNad370.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableNad370.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableNad370.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));
            dataVariableNad370.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableNad370.addAttribute(new Attribute("ancillary_variables", "confid_flags_nadir cloud_flags_nadir"));

            Variable dataVariableFwd370 = dataFile.addVariable(measurement, "btemp fward 3700nm", DataType.SHORT, dims);
            dataVariableFwd370.addAttribute(new Attribute("long_name", "Brightness temperature, forward view (3505-3895 nm)"));
            dataVariableFwd370.addAttribute(new Attribute("standard_name", "toa_brightness_temperature"));
            dataVariableFwd370.addAttribute(new Attribute("units", "K"));
            dataVariableFwd370.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableFwd370.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableFwd370.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableFwd370.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));
            dataVariableFwd370.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableFwd370.addAttribute(new Attribute("ancillary_variables", "confid_flags_fward cloud_flags_fward"));

            Variable dataVariableNad160 = dataFile.addVariable(measurement, "reflec nadir 1600nm", DataType.SHORT, dims);
            dataVariableNad160.addAttribute(new Attribute("long_name", "Reflectance, nadir view (1580-1640 nm)"));
            dataVariableNad160.addAttribute(new Attribute("standard_name", "toa_reflectance"));
            dataVariableNad160.addAttribute(new Attribute("units", "Percent"));
            dataVariableNad160.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableNad160.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableNad160.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableNad160.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));
            dataVariableNad160.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableNad160.addAttribute(new Attribute("ancillary_variables", "confid_flags_nadir cloud_flags_nadir"));

            Variable dataVariableFwd160 = dataFile.addVariable(measurement, "reflec fward 1600nm", DataType.SHORT, dims);
            dataVariableFwd160.addAttribute(new Attribute("long_name", "Reflectance, nadir view (1580-1640 nm)"));
            dataVariableFwd160.addAttribute(new Attribute("standard_name", "toa_reflectance"));
            dataVariableFwd160.addAttribute(new Attribute("units", "Percent"));
            dataVariableFwd160.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableFwd160.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableFwd160.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableFwd160.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));
            dataVariableFwd160.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableFwd160.addAttribute(new Attribute("ancillary_variables", "confid_flags_fward cloud_flags_fward"));

            Variable dataVariableNad87 = dataFile.addVariable(measurement, "reflec nadir 870nm", DataType.SHORT, dims);
            dataVariableNad87.addAttribute(new Attribute("long_name", "Reflectance, nadir view (855-875 nm)"));
            dataVariableNad87.addAttribute(new Attribute("standard_name", "toa_reflectance"));
            dataVariableNad87.addAttribute(new Attribute("units", "Percent"));
            dataVariableNad87.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableNad87.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableNad87.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableNad87.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));
            dataVariableNad87.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableNad87.addAttribute(new Attribute("ancillary_variables", "confid_flags_nadir cloud_flags_nadir"));

            Variable dataVariableFwd87 = dataFile.addVariable(measurement, "reflec fward 870nm", DataType.SHORT, dims);
            dataVariableFwd87.addAttribute(new Attribute("long_name", "Reflectance, nadir view (855-875 nm)"));
            dataVariableFwd87.addAttribute(new Attribute("standard_name", "toa_reflectance"));
            dataVariableFwd87.addAttribute(new Attribute("units", "Percent"));
            dataVariableFwd87.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableFwd87.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableFwd87.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableFwd87.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));
            dataVariableFwd87.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableFwd87.addAttribute(new Attribute("ancillary_variables", "confid_flags_fward cloud_flags_fward"));

            Variable dataVariableNad67 = dataFile.addVariable(measurement, "reflec nadir 670nm", DataType.SHORT, dims);
            dataVariableNad67.addAttribute(new Attribute("long_name", "Reflectance, nadir view (649-669 nm)"));
            dataVariableNad67.addAttribute(new Attribute("standard_name", "toa_reflectance"));
            dataVariableNad67.addAttribute(new Attribute("units", "Percent"));
            dataVariableNad67.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableNad67.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableNad67.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableNad67.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));
            dataVariableNad67.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableNad67.addAttribute(new Attribute("ancillary_variables", "confid_flags_nadir cloud_flags_nadir"));

            Variable dataVariableFwd67 = dataFile.addVariable(measurement, "reflec fward 670nm", DataType.SHORT, dims);
            dataVariableFwd67.addAttribute(new Attribute("long_name", "Reflectance, nadir view (649-669 nm)"));
            dataVariableFwd67.addAttribute(new Attribute("standard_name", "toa_reflectance"));
            dataVariableFwd67.addAttribute(new Attribute("units", "Percent"));
            dataVariableFwd67.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableFwd67.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableFwd67.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableFwd67.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));
            dataVariableFwd67.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableFwd67.addAttribute(new Attribute("ancillary_variables", "confid_flags_fward cloud_flags_fward"));

            Variable dataVariableNad55 = dataFile.addVariable(measurement, "reflec nadir 550nm", DataType.SHORT, dims);
            dataVariableNad55.addAttribute(new Attribute("long_name", "Reflectance, nadir view (545-565 nm)"));
            dataVariableNad55.addAttribute(new Attribute("standard_name", "toa_reflectance"));
            dataVariableNad55.addAttribute(new Attribute("units", "Percent"));
            dataVariableNad55.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableNad55.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableNad55.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableNad55.addAttribute(new Attribute("coordinates", "nadir_view_longitude nadir_view_latitude"));
            dataVariableNad55.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableNad55.addAttribute(new Attribute("ancillary_variables", "confid_flags_nadir cloud_flags_nadir"));

            Variable dataVariableFwd55 = dataFile.addVariable(measurement, "reflec fward 550nm", DataType.SHORT, dims);
            dataVariableFwd55.addAttribute(new Attribute("long_name", "Reflectance, nadir view (545-565 nm)"));
            dataVariableFwd55.addAttribute(new Attribute("standard_name", "toa_reflectance"));
            dataVariableFwd55.addAttribute(new Attribute("units", "Percent"));
            dataVariableFwd55.addAttribute(new Attribute("scale_factor", 0.01f));
            dataVariableFwd55.addAttribute(new Attribute("add_offset ", 0.0f));
            dataVariableFwd55.addAttribute(new Attribute("_FillValue", (short) -2));
            dataVariableFwd55.addAttribute(new Attribute("coordinates", "forward_view_longitude forward_view_latitude"));
            dataVariableFwd55.addAttribute(new Attribute("grid_mapping", "crs"));
            dataVariableFwd55.addAttribute(new Attribute("ancillary_variables", "confid_flags_fward cloud_flags_fward"));

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

            // Create data arrays
            // TODO streamline this process in future update.
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
            ArrayShort.D2 Nad1200Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Fwd1200Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Nad1100Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Fwd1100Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Nad370Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Fwd370Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Nad160Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Fwd160Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Nad87Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Fwd87Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Nad67Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Fwd67Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Nad55Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 Fwd55Out = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 NadConfidOut = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 FwdConfidOut = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 NadCloudOut = new ArrayShort.D2(iDim.getLength(), jDim.getLength());
            ArrayShort.D2 FwdCloudOut = new ArrayShort.D2(iDim.getLength(), jDim.getLength());

            Product readProduct = ProductIO.readProduct(parameters.inputFileLocation);
            Band nad1200 = readProduct.getBand("btemp_nadir_1200");
            Band fwd1200 = readProduct.getBand("btemp_fward_1200");
            Band nad1100 = readProduct.getBand("btemp_nadir_1100");
            Band fwd1100 = readProduct.getBand("btemp_fward_1100");
            Band nad370 = readProduct.getBand("btemp_nadir_0370");
            Band fwd370 = readProduct.getBand("btemp_fward_0370");
            Band nad160 = readProduct.getBand("reflec_nadir_1600");
            Band fwd160 = readProduct.getBand("reflec_fward_1600");

            int i, j;

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
                    if (pixelPositions[i][j][0] != -999999.0 || pixelPositions[i][j][0] != -888888.0) {
                        float value = nad1200.getSampleFloat(511 - (minX + j), minY + i);
                        if (value < 0) {
                            Nad1200Out.set(i, j, (short) -2);
                        } else {
                            Nad1200Out.set(i, j, (short) (value * 100));
                        }
                        value = nad1100.getSampleFloat(511 - (minX + j), minY + i);
                        if (value < 0) {
                            Nad1100Out.set(i, j, (short) -2);
                        } else {
                            Nad1100Out.set(i, j, (short) (value * 100));
                        }
                        value = nad370.getSampleFloat(511 - (minX + j), minY + i);
                        if (value < 0) {
                            Nad370Out.set(i, j, (short) -2);
                        } else {
                            Nad370Out.set(i, j, (short) (value * 100));
                        }
                        value = nad160.getSampleFloat(511 - (minX + j), minY + i);
                        if (value < 0) {
                            Nad160Out.set(i, j, (short) -2);
                        } else {
                            Nad160Out.set(i, j, (short) (value * 100));
                        }
                    } else {
                        Nad1200Out.set(i, j, (short) -2);
                        Nad1100Out.set(i, j, (short) -2);
                        Nad370Out.set(i, j, (short) -2);
                        Nad160Out.set(i, j, (short) -2);
                    }
                    if (pixelPositions[i][j][2] != -999999.0) {
                        float fwdValue = fwd1200.getSampleFloat(511 - (minX + j), minY + i);
                        if (fwdValue < 0) {
                            Fwd1200Out.set(i, j, (short) -2);
                        } else {
                            Fwd1200Out.set(i, j, (short) (fwdValue * 100));
                        }
                        fwdValue = fwd1100.getSampleFloat(511 - (minX + j), minY + i);
                        if (fwdValue < 0) {
                            Fwd1100Out.set(i, j, (short) -2);
                        } else {
                            Fwd1100Out.set(i, j, (short) (fwdValue * 100));
                        }
                        fwdValue = fwd370.getSampleFloat(511 - (minX + j), minY + i);
                        if (fwdValue < 0) {
                            Fwd370Out.set(i, j, (short) -2);
                        } else {
                            Fwd370Out.set(i, j, (short) (fwdValue * 100));
                        }
                        fwdValue = fwd160.getSampleFloat(511 - (minX + j), minY + i);
                        if (fwdValue < 0) {
                            Fwd160Out.set(i, j, (short) -2);
                        } else {
                            Fwd160Out.set(i, j, (short) (fwdValue * 100));
                        }
                    } else {
                        Fwd1200Out.set(i, j, (short) -2);
                        Fwd1100Out.set(i, j, (short) -2);
                        Fwd370Out.set(i, j, (short) -2);
                        Fwd160Out.set(i, j, (short) -2);
                    }
                }
            }

            // Problem with BEAM reader so break product sampling into two steps...
            nad1200.dispose();
            fwd1200.dispose();
            nad1100.dispose();
            fwd1100.dispose();
            nad370.dispose();
            fwd370.dispose();
            nad160.dispose();
            fwd160.dispose();
            readProduct.closeIO();
            readProduct = ProductIO.readProduct(parameters.inputFileLocation);
            Band nad87 = readProduct.getBand("reflec_nadir_0870");
            Band fwd87 = readProduct.getBand("reflec_fward_0870");
            Band nad67 = readProduct.getBand("reflec_nadir_0670");
            Band fwd67 = readProduct.getBand("reflec_fward_0670");
            Band nad55 = readProduct.getBand("reflec_nadir_0550");
            Band fwd55 = readProduct.getBand("reflec_fward_0550");
            Band nadConfid = readProduct.getBand("confid_flags_nadir");
            Band nadCloud = readProduct.getBand("cloud_flags_nadir");
            Band fwdConfid = readProduct.getBand("confid_flags_fward");
            Band fwdCloud = readProduct.getBand("cloud_flags_fward");

            for (i = 0; i < iDim.getLength(); i++) {
                for (j = 0; j < jDim.getLength(); j++) {
                    NadConfidOut.set(i, j, (short) nadConfid.getSampleInt(511 - (minX + j), minY + i));
                    FwdConfidOut.set(i, j, (short) fwdConfid.getSampleInt(511 - (minX + j), minY + i));
                    NadCloudOut.set(i, j, (short) nadCloud.getSampleInt(511 - (minX + j), minY + i));
                    FwdCloudOut.set(i, j, (short) fwdCloud.getSampleInt(511 - (minX + j), minY + i));
                    if (pixelPositions[i][j][0] != -999999.0 || pixelPositions[i][j][0] != -888888.0) {
                        float value = nad87.getSampleFloat(511 - (minX + j), minY + i);
                        if (value < 0) {
                            Nad87Out.set(i, j, (short) -2);
                        } else {
                            Nad87Out.set(i, j, (short) (value * 100));
                        }
                        value = nad67.getSampleFloat(511 - (minX + j), minY + i);
                        if (value < 0) {
                            Nad67Out.set(i, j, (short) -2);
                        } else {
                            Nad67Out.set(i, j, (short) (value * 100));
                        }
                        value = nad55.getSampleFloat(511 - (minX + j), minY + i);
                        if (value < 0) {
                            Nad55Out.set(i, j, (short) -2);
                        } else {
                            Nad55Out.set(i, j, (short) (value * 100));
                        }
                    } else {
                        Nad87Out.set(i, j, (short) -2);
                        Nad67Out.set(i, j, (short) -2);
                        Nad55Out.set(i, j, (short) -2);
                    }
                    if (pixelPositions[i][j][2] != -999999.0) {
                        float fwdValue = fwd87.getSampleFloat(511 - (minX + j), minY + i);
                        if (fwdValue < 0) {
                            Fwd87Out.set(i, j, (short) -2);
                        } else {
                            Fwd87Out.set(i, j, (short) (fwdValue * 100));
                        }
                        fwdValue = fwd67.getSampleFloat(511 - (minX + j), minY + i);
                        if (fwdValue < 0) {
                            Fwd67Out.set(i, j, (short) -2);
                        } else {
                            Fwd67Out.set(i, j, (short) (fwdValue * 100));
                        }
                        fwdValue = fwd55.getSampleFloat(511 - (minX + j), minY + i);
                        if (fwdValue < 0) {
                            Fwd55Out.set(i, j, (short) -2);
                        } else {
                            Fwd55Out.set(i, j, (short) (fwdValue * 100));
                        }
                    } else {
                        Fwd87Out.set(i, j, (short) -2);
                        Fwd67Out.set(i, j, (short) -2);
                        Fwd55Out.set(i, j, (short) -2);
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
            dataFile.write(dataVariableNadFOVAcross, NadLatFOVAcross);
            dataFile.write(dataVariableNadFOVAlong, NadLatFOVAlong);
            dataFile.write(dataVariableFwdFOVAcross, FwdLatFOVAcross);
            dataFile.write(dataVariableFwdFOVAlong, FwdLatFOVAlong);
            dataFile.write(dataVariableNad1200, Nad1200Out);
            dataFile.write(dataVariableFwd1200, Fwd1200Out);
            dataFile.write(dataVariableNad1100, Nad1100Out);
            dataFile.write(dataVariableFwd1100, Fwd1100Out);
            dataFile.write(dataVariableNad370, Nad370Out);
            dataFile.write(dataVariableFwd370, Fwd370Out);
            dataFile.write(dataVariableNad160, Nad160Out);
            dataFile.write(dataVariableFwd160, Fwd160Out);
            dataFile.write(dataVariableNad87, Nad87Out);
            dataFile.write(dataVariableFwd87, Fwd87Out);
            dataFile.write(dataVariableNad67, Nad67Out);
            dataFile.write(dataVariableFwd67, Fwd67Out);
            dataFile.write(dataVariableNad55, Nad55Out);
            dataFile.write(dataVariableFwd55, Fwd55Out);
            dataFile.write(dataVariableNadConfid, NadConfidOut);
            dataFile.write(dataVariableFwdConfid, FwdConfidOut);
            dataFile.write(dataVariableNadCloud, NadCloudOut);
            dataFile.write(dataVariableFwdCloud, FwdCloudOut);

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
