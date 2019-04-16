package de.embl.cba.templatematching.match;

import de.embl.cba.templatematching.image.CalibratedRai;
import de.embl.cba.templatematching.Utils;
import de.embl.cba.templatematching.image.DefaultCalibratedRai;
import de.embl.cba.templatematching.imageprocessing.Projection;
import de.embl.cba.transforms.utils.Scalings;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.localextrema.RefinedPeak;
import net.imglib2.algorithm.localextrema.SubpixelLocalization;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.Arrays;

import static de.embl.cba.templatematching.Utils.showIntermediateResult;
import static de.embl.cba.templatematching.Utils.showIntermediateResults;
import static de.embl.cba.transforms.utils.Transforms.getCenter;

public class TemplateMatcherTranslation2D< T extends RealType< T > & NativeType< T > >
{

	public static final int CV_TM_SQDIFF = 0;
	public static final int CORRELATION = 4;
	public static final int NORMALIZED_CORRELATION = 5;

	private final ImageProcessor overview;
	private final double[] overviewPixelSizeNanometer;

	public TemplateMatcherTranslation2D( ImageProcessor overview,
										 double[] overviewPixelSizeNanometer )
	{
		this.overview = overview;
		this.overviewPixelSizeNanometer = overviewPixelSizeNanometer;
	}

	public MatchedTemplate match( CalibratedRai< T > template )
	{
		final CalibratedRai< T > subSampled = subSample( template, getSubSampling() );

		CalibratedRai< T > projected = project( subSampled );

		CalibratedRai< T > downscaled = downscale( projected, getDownScaling() );

		final double[] calibratedPosition = findPositionWithinOverviewImage( downscaled.rai() );

		final MatchedTemplate matched = getMatchedTemplate( template, calibratedPosition );

		return matched;
	}

	private MatchedTemplate getMatchedTemplate(
			CalibratedRai< T > template,
			double[] positionNanometer )
	{
		// set z position to template center
		final double[] center = getCenter( template.rai() );
		positionNanometer[ 2 ] = - center[ 2 ] * template.nanometerCalibration()[ 2 ];

		return new MatchedTemplate( template, positionNanometer);;
	}

	private double[] getCalibratedPosition3D( double[] pixelPosition )
	{
		final double[] calibratedPosition = new double[ 3 ];

		for ( int d = 0; d < pixelPosition.length; d++ )
			calibratedPosition[ d ] = pixelPosition[ d ] * overviewPixelSizeNanometer[ d ];

		return calibratedPosition;
	}


	public CalibratedRai subSample( CalibratedRai input, long[] templateSubSampling )
	{
		final long[] subSampling = getSubSampling();

		final RandomAccessibleInterval< ? extends RealType< ? > > subSampled =
				Views.subsample( input.rai(), subSampling );

		final double[] newCalibration = getNewCalibration( input, Utils.asDoubles( subSampling ) );

		return new DefaultCalibratedRai( subSampled, newCalibration );
	}

	private long[] getSubSampling()
	{
		// determine based on pixel sizes of overview and template
		return new long[]{ subSampling, subSampling, 1 };
	}

	private CalibratedRai project( CalibratedRai input )
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
	private DefaultCalibratedRai< T >
	downscale( CalibratedRai< T > input, double[] scalings )
	{
		Utils.log( "Scaling to overview image resolution..." );

		RandomAccessibleInterval< T > downscaled
				= Scalings.createRescaledArrayImg( input.rai(), scalings );

		showIntermediateResult( downscaled, "downscaled" );

		final double[] newCalibration = getNewCalibration( input, scalings );

		return new DefaultCalibratedRai( downscaled, newCalibration );
	}

	private double[] getNewCalibration( CalibratedRai input, double[] scalings )
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

	private double[] getDownScaling( int numDimensions )
	{
		final double scalingRatio =
				templatePixelSizeNanometer[ 0 ] / overviewPixelSizeNanometer[ 0 ];

		final double[] scaling = new double[ numDimensions ];
		Arrays.fill( scaling, scalingRatio );

		Utils.log( "Template pixel size [nm]: " + templatePixelSizeNanometer[ 0 ] );
		Utils.log( "Overview pixel size [nm]: " + overviewPixelSizeNanometer[ 0 ] );

		Utils.log( "Scaling factor: " +  scalingRatio );

		return scaling;
	}


	private double[] matchToOverview( CalibratedRai< T > template )
	{
		Utils.log( "Finding best match in overview image..." );



		return position;
	}

	private double[] findPositionWithinOverviewImage( RandomAccessibleInterval< T > template )
	{
		final ImageProcessor templateProcessor = Utils.asFloatProcessor( template );

		Utils.log( "X-correlation..." );

		FloatProcessor correlation = TemplateMatchingPlugin.doMatch(
				overview,
				templateProcessor,
				NORMALIZED_CORRELATION );

		if ( showIntermediateResults )
			new ImagePlus( "correlation", correlation ).show();

		Utils.log( "Find maximum in x-correlation..." );
		final int[] position = findMaximumPosition( correlation );

		Utils.log( "Refine maximum to sub-pixel resolution..." );
		final double[] refinedPosition = computeRefinedPosition( correlation, position );

		final double[] calibratedPosition = getCalibratedPosition3D( refinedPosition );

		return calibratedPosition;
	}

	private double[] computeRefinedPosition( FloatProcessor correlation, int[] position )
	{
		final RandomAccessibleInterval< T > correlationRai
				= ImageJFunctions.wrapReal( new ImagePlus( "", correlation ) );

		final int numDimensions = correlationRai.numDimensions();

		final SubpixelLocalization< Point, T > spl =
				new SubpixelLocalization< >( numDimensions );
		spl.setNumThreads( 1 );
		spl.setReturnInvalidPeaks( true );
		spl.setCanMoveOutside( true );
		spl.setAllowMaximaTolerance( true );
		spl.setMaxNumMoves( 10 );

		ArrayList peaks = new ArrayList< Point >(  );
		peaks.add( new Point( position[ 0 ], position[ 1 ] ) );

		final ArrayList< RefinedPeak< Point > > refined = spl.process(
				peaks,
				correlationRai,
				correlationRai );

		final RefinedPeak< Point > refinedPeak = refined.get( 0 );

		final double[] refinedPosition = new double[ numDimensions ];
		for ( int d = 0; d < numDimensions; d++ )
			refinedPosition[ d ] = refinedPeak.getDoublePosition( d );

		return refinedPosition;
	}

	private int[] findMaximumPosition( FloatProcessor processor )
	{
		final int[] maxPos = findMax( processor );
		return maxPos;
	}


	public static int[] findMax( ImageProcessor ip ) {
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
