package de.embl.cba.templatematching.process;

import de.embl.cba.templatematching.Utils;
import de.embl.cba.templatematching.image.CalibratedRai;
import de.embl.cba.templatematching.image.DefaultCalibratedRai;
import de.embl.cba.templatematching.imageprocessing.Projection;
import de.embl.cba.transforms.utils.Scalings;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import static de.embl.cba.templatematching.Utils.showIntermediateResult;

public abstract class ImageProcessor
{
	public static < T extends RealType< T > & NativeType< T > >
	CalibratedRai< T > subSample( CalibratedRai input, long[] subSampling )
	{
		final RandomAccessibleInterval< ? extends RealType< ? > > subSampled =
				Views.subsample( input.rai(), subSampling );

		final double[] newCalibration =
				getNewCalibration( input, Utils.asDoubles( subSampling ) );

		return new DefaultCalibratedRai( subSampled, newCalibration );
	}

	public static < T extends RealType< T > & NativeType< T > >
	CalibratedRai< T > project( CalibratedRai input )
	{
		Utils.log( "Computing template average projection..." );

		if ( input.rai().numDimensions() == 3 )
		{
			final RandomAccessibleInterval< T > average =
					new Projection( input.rai(), 2 ).average();
			return new DefaultCalibratedRai( average, input.nanometerCalibration() );
		}
		else
		{
			return input;
		}

	}

	/**
	 * Downscales the template to matchToOverview resolution of overview image.
	 * Note that both overview and template may been sub-sampled already.
	 * However, as both have been sub-sampled with the same factor,
	 * the relative downscaling factor here still is correct.
	 * @param input
	 * @return
	 */
	public static < T extends RealType< T > & NativeType< T > >
	CalibratedRai< T > downscale( CalibratedRai< T > input, double[] scalings )
	{
		Utils.log( "Scaling to overview image resolution..." );

		RandomAccessibleInterval< T > downscaled
				= Scalings.createRescaledArrayImg( input.rai(), scalings );

		showIntermediateResult( downscaled, "downscaled" );

		final double[] newCalibration = getNewCalibration( input, scalings );

		return new DefaultCalibratedRai( downscaled, newCalibration );
	}

	public static double[] getNewCalibration( CalibratedRai input, double[] scalings )
	{
		final int n = input.rai().numDimensions();
		final double[] newCalibration = new double[ n ];
		for ( int d = 0; d < n; d++ )
		{
			newCalibration[ d ] = input.nanometerCalibration()[ d ]
					* scalings[ d ];
		}
		return newCalibration;

	}

	public static int[] findMax( ij.process.ImageProcessor ip ) {
		int[] coord = new int[2];
		float max = ip.getPixel(0, 0);
		final int sWh = ip.getHeight();
		final int sWw = ip.getWidth();

		for (int j = 0; j < sWh; j++) {
			for (int i = 0; i < sWw; i++) {
				if (ip.getPixel(i, j) > max) {
					max = ip.getPixel(i, j);
					coord[0] = i;
					coord[1] = j;
				}
			}
		}

		return (coord);
	}
}
