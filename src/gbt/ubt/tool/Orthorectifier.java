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

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.ode.nonstiff.AdaptiveStepsizeIntegrator;
import org.apache.commons.math3.ode.nonstiff.DormandPrince853Integrator;
import org.apache.commons.math3.util.FastMath;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.ZipJarCrawler;
import org.orekit.errors.OrekitException;
import org.orekit.forces.ForceModel;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.ThirdBodyAttraction;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.frames.Transform;
import org.orekit.models.earth.EarthStandardAtmosphereRefraction;
import org.orekit.orbits.CartesianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.propagation.BoundedPropagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

/**
 *
 * @author ABeaton, Telespazio VEGA UK Ltd 06/07/2015
 *
 * Contact: alasdhair(dot)beaton(at)telespazio(dot)com
 *
 */
public class Orthorectifier {
/* This class leverages the open source Orekit library to propagate the ephemeris of a satellite and then orthorectify the lat 
    */
    static void orthorectify(BoundedPropagator ephemeris, double[] pixelNewPositionsAndTimes, InputParameters params, Band DEM) {
        try {
            /* Get pixel location and local topographic frame */
            Frame earthFrame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
            BodyShape earth = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS, Constants.WGS84_EARTH_FLATTENING, earthFrame);

            /* Get the acquisition times convert from MJD2000 into a calendar date 
             MJD2000 = days since 2000-01-01 00:00:00
             */
            TimeScale utc = TimeScalesFactory.getUTC();
            AbsoluteDate referenceDate = new AbsoluteDate(2000, 1, 1, 0, 0, 0.0, utc);
            
            // if pixel is not valid, do not compute orthorectification
            if (pixelNewPositionsAndTimes[0] != -999999.0 && pixelNewPositionsAndTimes[0] != -888888.0) {
                float terrainHeight = getHeightFromDEM(pixelNewPositionsAndTimes[0], pixelNewPositionsAndTimes[1], params, DEM);
                /* Height has to be within constraints. Note DEM should provide NaN for sea areas (e.g. for 16 bit int 32767)
                if DEM provides 0.0 for sea areas the computation becomes expensive.
                */
                if (terrainHeight > -500.0 && terrainHeight < 9000.0) {
                    double[] azimuthElevation = getSphericalCoordinates(pixelNewPositionsAndTimes[0], pixelNewPositionsAndTimes[1], pixelNewPositionsAndTimes[2], earth, referenceDate, ephemeris);
                    double[] latLonCorr = calculateShift(pixelNewPositionsAndTimes[0], terrainHeight, azimuthElevation);
                   
                    // Apply the corrections to the retrieved UBT lats and longs
                    pixelNewPositionsAndTimes[0] = pixelNewPositionsAndTimes[0] + latLonCorr[0];
                    pixelNewPositionsAndTimes[1] = pixelNewPositionsAndTimes[1] + latLonCorr[1];
                }
            }
            // Repeat calculation for the forward view
            if (pixelNewPositionsAndTimes[3] != -999999.0) {
                float terrainHeight = getHeightFromDEM(pixelNewPositionsAndTimes[3], pixelNewPositionsAndTimes[4], params, DEM);
                if (terrainHeight > -500.0 && terrainHeight < 9000.0) {
                    double[] azimuthElevation = getSphericalCoordinates(pixelNewPositionsAndTimes[3], pixelNewPositionsAndTimes[4], pixelNewPositionsAndTimes[5], earth, referenceDate, ephemeris);
                    double[] latLonCorr = calculateShift(pixelNewPositionsAndTimes[3], terrainHeight, azimuthElevation);
                    pixelNewPositionsAndTimes[3] = pixelNewPositionsAndTimes[3] + latLonCorr[0];
                    pixelNewPositionsAndTimes[4] = pixelNewPositionsAndTimes[4] + latLonCorr[1];
                }
            }
        } catch (OrekitException | IllegalArgumentException | IOException ex) {
            System.out.println("Error in orthorectification");
            System.out.println(ex.getMessage());
            System.exit(1);
        }
    }

    private static double[] getSphericalCoordinates(double latitude, double longitude, double acqTime, BodyShape earth, AbsoluteDate referenceDate, BoundedPropagator ephemeris) throws OrekitException {
        // Using the orekit library to create a point located on the earth representing the pixel location and define the local topographic frame
        GeodeticPoint pixelPoint = new GeodeticPoint(FastMath.toRadians(latitude), FastMath.toRadians(longitude), 0.0);
        TopocentricFrame topoFrame = new TopocentricFrame(earth, pixelPoint, "frame");
        
        // The pixel acquisition time is offset from the epoch (mjd2000) by a number of seconds
        AbsoluteDate pixelTime = referenceDate.shiftedBy(acqTime * 86400.0);

        // Get the location of the satellite at the acquisition time
        TimeStampedPVCoordinates pvCoordinates = ephemeris.getPVCoordinates(pixelTime, topoFrame);
        Vector3D position = pvCoordinates.getPosition();
        
        // Get the corresponding spherical coordinates in the topograhic frame at the pixel location
        double[] value = new double[2];
        value[0] = topoFrame.getAzimuth(position, topoFrame, pixelTime);
        double elevation = topoFrame.getElevation(position, topoFrame, pixelTime);
        
        // Correct for atmospheric refraction (small modification circa 2 metres in forward view...)
        EarthStandardAtmosphereRefraction refractionModel = new EarthStandardAtmosphereRefraction(EarthStandardAtmosphereRefraction.STANDARD_ATM_PRESSURE, EarthStandardAtmosphereRefraction.STANDARD_ATM_TEMPERATURE);
        double refraction = refractionModel.getRefraction(elevation);
        value[1] = elevation + refraction;
        return value; // In radians
    }

    private static float getHeightFromDEM(double latitude, double longitude, InputParameters params, Band DEM) throws IOException {
        float height = 0;
        try {
            // Use beam library to retrieve the height for the given lat lon (Note two step process)
            PixelPos pixelPos = DEM.getGeoCoding().getPixelPos(new GeoPos((float) latitude, (float) longitude), null);
            height = DEM.getSampleFloat((int) Math.floor(pixelPos.x), (int) Math.floor(pixelPos.y));
        } catch (Exception ex) {
            System.out.println("Could not get height from DEM: " + "Lat: " + String.valueOf(latitude) + " Lon: " + String.valueOf(longitude) + " Using 0.0 metres");
        }
        return height;
    }

    private static double[] calculateShift(double latitude, double terrainHeight, double[] azimuthElevation) {
        /* Note this method is taken from the AATSR data processing model for L1B (topographic corrections section 5.16)
        As the ESA CFI target is closed source, we use the azimuth elevation retrieved using the Orekit library with an orbit
        propagated using state vector contained in the L1b product
        */
        double latitudeRadians = latitude * (Math.PI / 180.0);
        double a = Constants.WGS84_EARTH_EQUATORIAL_RADIUS; //semiMajorAxis
        double rf = Constants.WGS84_EARTH_FLATTENING;// reciprocal of flattening
        double b = a * (1 - rf); // semiMinorAxis
        double e1 = Math.pow((1 - (Math.pow(b, 2) / Math.pow(a, 2))), 0.5); //first eccentricity
        double e2sqr = (Math.pow(a, 2) / Math.pow(b, 2) - 1); // secondEccentricitySquared
        double C = 1000.0 * a / Math.pow((1 - Math.pow(e1, 2)), 0.5);
        double N = C / Math.pow((1 + (e2sqr * Math.pow(Math.cos(latitudeRadians), 2))), 0.5);
        double R = N / (1 + (e2sqr * Math.pow(Math.cos(latitudeRadians), 2)));
        double dY = terrainHeight * (1 / Math.tan(azimuthElevation[1])) * Math.cos(azimuthElevation[0]);
        double dX = terrainHeight * (1 / Math.tan(azimuthElevation[1])) * Math.sin(azimuthElevation[0]);
        double[] latLonCorr = new double[2];
        latLonCorr[0] = dY * (180.0 / Math.PI) / R;
        latLonCorr[1] = (dX / Math.cos(latitudeRadians)) * (180.0 / Math.PI) / N;
        return latLonCorr;
    }

    public Orthorectifier() {

    }

    public static BoundedPropagator generateEphemeris(InputParameters params) throws IOException, OrekitException {
        /*
         This function generates the satellite ephemeris for the duration of the acquisition using the orbital
         state vector that is embedded in the product header (MPH). This state vector is normally the restituted
         orbit from the Flight Operations Segment.
         The open source Orekit library (https://www.orekit.org/) is used to perform the numerical propagation.
         The generated ephemeris compares well against the available DORIS Precise Orbit files (maximum 10 metre
         displacement vector magnitude during propagation duration).
         */
        if (params.orthorectify) {
            System.out.println("Orthorectification is selected, performing orbit propagation");

            /* Get the acquisition duration and state vector from the L1b product */
            DataProvidersManager.getInstance().addProvider(new ZipJarCrawler(new File("orekit-data.zip")));
            Product readProduct = ProductIO.readProduct(params.inputFileLocation);
            Date startTime = readProduct.getStartTime().getAsDate();
            Date stopTime = readProduct.getEndTime().getAsDate();
            MetadataElement metadataRoot = readProduct.getMetadataRoot();
            Date vectorTime = metadataRoot.getElementAt(0).getAttributeUTC("STATE_VECTOR_TIME").getAsDate();
            Double xPos = metadataRoot.getElementAt(0).getAttributeDouble("X_POSITION");
            Double yPos = metadataRoot.getElementAt(0).getAttributeDouble("Y_POSITION");
            Double zPos = metadataRoot.getElementAt(0).getAttributeDouble("Z_POSITION");
            Double xVel = metadataRoot.getElementAt(0).getAttributeDouble("X_VELOCITY");
            Double yVel = metadataRoot.getElementAt(0).getAttributeDouble("Y_VELOCITY");
            Double zVel = metadataRoot.getElementAt(0).getAttributeDouble("Z_VELOCITY");

            /* Product state vector in fixed frame and UTC
             Note state vector and product/acquisition start time are not coincident
             */
            Frame fixedFrame = FramesFactory.getITRF(IERSConventions.IERS_2010, false);
            TimeScale utc = TimeScalesFactory.getUTC();
            GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            cal.setTime(vectorTime);
            AbsoluteDate initialDate = new AbsoluteDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DATE), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), utc);
            Vector3D position = new Vector3D(xPos, yPos, zPos);
            Vector3D velocity = new Vector3D(xVel, yVel, zVel);

            /* Orekit can only propate orbits defined in inertial reference frame
             Convert state vector to J2000 (EME2000) frame.
             */
            Frame inertialFrame = FramesFactory.getEME2000();
            Transform frameTransform = fixedFrame.getTransformTo(inertialFrame, initialDate);
            PVCoordinates transformPVCoordinates = frameTransform.transformPVCoordinates(new PVCoordinates(position, velocity));

            /* Set initial spacecraft state */
            double mu = 3.986004415e+14; // Central attraction coefficient
            Orbit initialOrbit = new CartesianOrbit(transformPVCoordinates, inertialFrame, initialDate, mu);
            SpacecraftState initialState = new SpacecraftState(initialOrbit, 7892.0); // Orbital parameters and Envisat dry mass

            /* Set up numerical integrator for propagation */
            double minStep = 1;
            double maxStep = 1000;
            double initialStep = 60;
            OrbitType propagationType = OrbitType.CARTESIAN;
            double[][] tolerance = NumericalPropagator.tolerances(0.001, initialOrbit, propagationType);
            AdaptiveStepsizeIntegrator integrator = new DormandPrince853Integrator(minStep, maxStep, tolerance[0], tolerance[1]);
            integrator.setInitialStepSize(initialStep);
            NumericalPropagator propagator = new NumericalPropagator(integrator);
            propagator.setOrbitType(propagationType);

            /* Add force models to the propagator:
             Consider central body (Earth) gravity pertubation
             Consider third body (Sun & Moon) gravity pertubation
             Other pertubations are not considered (computation speed/accuracy trade-off)
             */
            NormalizedSphericalHarmonicsProvider provider = GravityFieldFactory.getNormalizedProvider(21, 21);
            ForceModel holmesFeatherstone = new HolmesFeatherstoneAttractionModel(FramesFactory.getITRF(IERSConventions.IERS_2010, true), provider);
            propagator.addForceModel(holmesFeatherstone);
            propagator.addForceModel(new ThirdBodyAttraction(CelestialBodyFactory.getSun()));
            propagator.addForceModel(new ThirdBodyAttraction(CelestialBodyFactory.getMoon()));

            /* Run  the propagator to generate the ephemeris */
            propagator.setInitialState(initialState);
            propagator.setEphemerisMode();
            cal.setTime(startTime);
            AbsoluteDate startDate = new AbsoluteDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DATE), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), utc);
            cal.setTime(stopTime);
            AbsoluteDate stopDate = new AbsoluteDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DATE), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), utc);
            SpacecraftState finalState = propagator.propagate(startDate.shiftedBy(-600.0), stopDate.shiftedBy(600.0));
            BoundedPropagator ephemeris = propagator.getGeneratedEphemeris();

            return ephemeris;
        } else {
            return null;
        }
    }

    public static double vincentyFormulae(double lat1, double long1, double lat2, double long2) {
        double latitude1 = deg2rad(lat1);
        double longitude1 = deg2rad(long1);
        double latitude2 = deg2rad(lat2);
        double longitude2 = deg2rad(long2);
        double a = 6378137.0;
        double f = 1.0 / 298.257223563;
        double b = (1.0 - f) * a;
        double u1 = Math.atan((1.0 - f) * Math.tan(latitude1));
        double u2 = Math.atan((1.0 - f) * Math.tan(latitude2));
        double L = longitude2 - longitude1;
        double lambda = L;
        double diff = 1.0;
        double tol = 10.0e-8;
        double cos2Alpha = 0.0;
        double sinOmega = 0.0;
        double cos2OmegaM = 0.0;
        double cosOmega = 0.0;
        double omega = 0.0;
        while (diff > tol) {
            sinOmega = Math.sqrt(Math.pow((Math.cos(u2) * Math.sin(lambda)), 2) + (Math.pow(((Math.cos(u1) * Math.sin(u2)) - (Math.sin(u1) * Math.cos(u2) * Math.cos(lambda))), 2)));
            cosOmega = (Math.sin(u1) * Math.sin(u2)) + (Math.cos(u1) * Math.cos(u2) * Math.cos(lambda));
            omega = Math.atan(sinOmega / cosOmega);
            double sinAlpha = (Math.cos(u1) * Math.cos(u2) * Math.sin(lambda)) / Math.sin(omega);
            cos2Alpha = 1 - (Math.pow(sinAlpha, 2));
            cos2OmegaM = cosOmega - ((2 * Math.sin(u1) * Math.sin(u2)) / cos2Alpha);
            double C = (f / 16.0) * cos2Alpha * (4 + (f * (4 - (3 * cos2Alpha))));
            double lambdaNew = L + ((1 - C) * f * sinAlpha * (omega + (C * sinOmega * (cos2OmegaM + (C * cosOmega * (-1 + (2 * Math.pow(cos2OmegaM, 2))))))));
            diff = Math.abs(lambdaNew - lambda);
            lambda = lambdaNew;
        }
        double uSquared = cos2Alpha * ((Math.pow(a, 2) - Math.pow(b, 2)) / Math.pow(b, 2));
        double A = 1 + ((uSquared / 16384.0) * (4096.0 + (uSquared * (-768.0 + (uSquared * (320.0 - (175.0 * uSquared)))))));
        double B = (uSquared / 1024.0) * (256.0 + (uSquared * (-128.0 + (uSquared * (74.0 - (47.0 * uSquared))))));
        double deltaOmega = B * sinOmega * (cos2OmegaM + (0.25 * B * ((cosOmega * (-1 + (2 * Math.pow(cos2OmegaM, 2)))) - ((1.0 / 6.0) * B * cos2OmegaM * (-3.0 + (4.0 * Math.pow(sinOmega, 2))) * (-3.0 + (4.0 * Math.pow(cos2OmegaM, 2)))))));
        double s = b * A * (omega - deltaOmega);
        return s;
    }

    private static double deg2rad(double value) {
        return value * (Math.PI / 180.0);
    }
}
