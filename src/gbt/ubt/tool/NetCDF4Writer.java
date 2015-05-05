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
import java.util.ArrayList;
import java.util.List;
import ucar.nc2.jni.netcdf.Nc4Iosp;

import ucar.nc2.Dimension;
import ucar.ma2.*;
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

            // Create dimensions
            Dimension iDim = dataFile.addDimension(null, "i", (maxX - minX));
            Dimension jDim = dataFile.addDimension(null, "j", (maxY - minY));
            List<Dimension> dims = new ArrayList<>();
            dims.add(iDim);
            dims.add(jDim);

            // Define variables
            Variable dataVariableLat = dataFile.addVariable(null, "latitude", DataType.DOUBLE, dims);
            Variable dataVariableLon = dataFile.addVariable(null, "longitude", DataType.DOUBLE, dims);

            // Create file
            dataFile.create();

            // Create data arrays
            ArrayDouble.D2 latOut = new ArrayDouble.D2(iDim.getLength(), jDim.getLength());
            ArrayDouble.D2 lonOut = new ArrayDouble.D2(iDim.getLength(), jDim.getLength());
            
            int i,j;
            
            for (i=0; i<iDim.getLength(); i++){
                for (j=0; j< jDim.getLength(); j++){
                    latOut.set(i, j, pixelPositions[i - minY][j - minX][0]);
                    lonOut.set(i, j, pixelPositions[i - minY][j - minX][1]);
                }
            }
            
            // Write data to file
             dataFile.write(dataVariableLat, latOut);
             dataFile.write(dataVariableLon, lonOut);
            
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