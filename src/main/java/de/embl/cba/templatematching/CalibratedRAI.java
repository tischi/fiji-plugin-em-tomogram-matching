package de.embl.cba.templatematching;

import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;

public class CalibratedRAI< T extends RealType< T > >
{
	public RandomAccessibleInterval< T > rai;
	public double[] nanometerCalibration;
	public boolean is3D = false;
	public boolean isMultiChannel = false;

	public CalibratedRAI( ImagePlus imp )
	{
		rai = ImageJFunctions.wrapReal( imp );

		final String unit = imp.getCalibration().getUnit();

		nanometerCalibration = new double[ 3 ];

		nanometerCalibration[ 0 ] = Utils.asNanometers(
				imp.getCalibration().pixelWidth, unit );
		nanometerCalibration[ 1 ] = Utils.asNanometers(
				imp.getCalibration().pixelHeight, unit );
		nanometerCalibration[ 2 ] = Utils.asNanometers(
				imp.getCalibration().pixelDepth, unit );

		if ( imp.getNChannels() > 1 ) isMultiChannel = true;
		if ( imp.getNSlices() > 1 ) is3D = true;

	}
}
