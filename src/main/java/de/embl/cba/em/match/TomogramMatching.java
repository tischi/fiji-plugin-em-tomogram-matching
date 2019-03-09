package de.embl.cba.em.match;

import de.embl.cba.em.ImageIO;
import de.embl.cba.em.Utils;
import de.embl.cba.em.bdv.BdvExport;
import de.embl.cba.em.imageprocessing.Projection;
import de.embl.cba.transforms.utils.Scalings;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import net.imagej.ops.OpService;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import static de.embl.cba.em.Utils.asByteProcessor;
import static de.embl.cba.em.Utils.showIntermediateResult;
import static de.embl.cba.transforms.utils.Transforms.createBoundingIntervalAfterTransformation;

public class TomogramMatching < T extends RealType< T > & NativeType< T > >
{

	public static final int Z_DIMENSION = 2;
	public static final int CV_TM_SQDIFF = 0;
	public static final int CORRELATION = 4;
	public static final int NORMALIZED_CORRELATION = 5;

	private final TomogramMatchingSettings settings;
	private ArrayList< File > tomogramFiles;
	private ByteProcessor overviewProcessor;
	private RandomAccessibleInterval< T > overview;
	private RandomAccessibleInterval< T > downscaledTomogram;
	private boolean isFirstTomogram;
	private ImageProcessor overviewProcessorWithNoise;
	private RandomAccessibleInterval< T > overviewForMatching;
	private Overlay matchings;
	private ImagePlus overviewForMatchingImagePlus;
	private int subSamplingOverviewPixelUnits;

	public TomogramMatching( TomogramMatchingSettings settings, OpService opService )
	{
		this.settings = settings;

		isFirstTomogram = true;
		Utils.showIntermediateResults = settings.showIntermediateResults;
	}

	public void run()
	{
		openAndProcessOverviewForMatching();

		createTomogramFileList();

		matchTomograms();

		if ( settings.saveOverview ) saveOverviewAsBdv();
	}

	private void createTomogramFileList()
	{
		File[] files = settings.tomogramInputDirectory.listFiles();

		tomogramFiles = new ArrayList<>();

		for ( File file : files )
		{
			if ( isValid( file ) )
			{
				tomogramFiles.add( file );
			}
		}
	}

	private boolean isValid( File file )
	{
		return true;
	}

	private void matchTomograms()
	{
		matchings = new Overlay();

		for ( File tomogramFile : tomogramFiles )
		{
			matchTomogram( tomogramFile );
		}
	}

	private void openAndProcessOverviewForMatching()
	{
		loadOverview();

		overviewForMatching = Views.subsample( overview, getOverviewSubSampling() );

		overviewForMatching = rotateOverview( overviewForMatching );

		overviewProcessor = asByteProcessor( overviewForMatching );

		overviewForMatchingImagePlus = new ImagePlus(
				"Overview for matching", overviewProcessor );

		//addNoiseToOverview( rotatedOverview );

		if ( settings.showIntermediateResults )
			overviewForMatchingImagePlus.show();

	}

	private long[] getOverviewSubSampling()
	{
		return new long[]{
				subSamplingOverviewPixelUnits,
				subSamplingOverviewPixelUnits };
	}

	private void addNoiseToOverview( ImagePlus rotatedOverview )
	{
		Utils.log( "Adding noise to overview to avoid strange " +
				"edge effects during correlation.." );
		final ImagePlus duplicate = rotatedOverview.duplicate();
		IJ.run( duplicate, "Add Specified Noise...", "standard=10");
		overviewProcessorWithNoise = duplicate.getProcessor();
	}

	private void loadOverview()
	{
		Utils.log( "Opening overview image..." );

		setOverviewCalibration();

		setSubSampling();

		// TODO: perform 8-bit conversion later (only for matching)
		overview = ImageIO.openImageAs8Bit( settings.overviewImage );
	}

	private void setSubSampling()
	{
		subSamplingOverviewPixelUnits = (int)
				Math.floor( settings.matchingPixelSpacingNanometer
						/ settings.overviewCalibrationNanometer );
	}

	private void setOverviewCalibration()
	{
		settings.overviewCalibrationNanometer =
				ImageIO.getNanometerPixelWidth( settings.overviewImage );

		if ( settings.confirmScalingViaUI )
		{
			settings.overviewCalibrationNanometer
					= confirmImageScalingUI(
					settings.overviewCalibrationNanometer,
					"Overview");
		}
	}

	private double confirmImageScalingUI( double value, final String imageName )
	{
		final GenericDialog gd =
				new GenericDialog( imageName + " scaling" );
		gd.addNumericField(
				imageName + " pixel spacing",
				value, 20, 30, "nm" );
		gd.hideCancelButton();
		gd.showDialog();
		return gd.getNextNumber();
	}

	private RandomAccessibleInterval< T >
	rotateOverview( RandomAccessibleInterval< T > overview )
	{
		if ( overview.numDimensions() == 2 )
		{
			final AffineTransform2D affineTransform2D = new AffineTransform2D();
			affineTransform2D.rotate(
					Math.toRadians( -settings.tomogramAngleDegrees ) );
			return createTransformedView(overview, affineTransform2D );
		}
		else if ( this.overview.numDimensions() == 3 )
		{
			final AffineTransform3D affineTransform3D = new AffineTransform3D();
			affineTransform3D.rotate(
					2, Math.toRadians( -settings.tomogramAngleDegrees ) );
			return createTransformedView( this.overview, affineTransform3D );
		}
		return null;
	}

	public static < T extends NumericType< T > & NativeType< T > >
	RandomAccessibleInterval createTransformedView(
			RandomAccessibleInterval< T > rai, InvertibleRealTransform transform )
	{
		RealRandomAccessible rra =
				Views.interpolate( Views.extendZero( rai ),
						new ClampingNLinearInterpolatorFactory() );

		rra = RealViews.transform( rra, transform );

		final RandomAccessibleOnRealRandomAccessible raster = Views.raster( rra );

		final FinalInterval transformedInterval =
				createBoundingIntervalAfterTransformation( rai, transform );

		final RandomAccessibleInterval< T > transformedIntervalView =
				Views.interval( raster, transformedInterval );

		return transformedIntervalView;
	}

	private void saveOverviewAsBdv()
	{

		Utils.log( "Exporting overview image..." );

		ImagePlus imagePlus = getOverviewAs3dImagePlus();

		double[] calibration = new double[ 3 ];
		calibration[ 0 ] = settings.overviewCalibrationNanometer;
		calibration[ 1 ] = calibration[ 0 ];
		calibration[ 2 ] = 2000;

		double[] offset = new double[ 3 ];

		String path = settings.outputDirectory + File.separator + "overview";
		imagePlus.setTitle( "overview" );
		BdvExport.export(
				imagePlus, path, calibration, "nanometer", offset );

	}

	private ImagePlus getOverviewAs3dImagePlus()
	{
		RandomAccessibleInterval< T > overview3d = getOverviewAs3d();

		return Utils.asImagePlus( overview3d );
	}

	private RandomAccessibleInterval< T > getOverviewAs3d()
	{
		RandomAccessibleInterval< T > overview3d;

		if ( overview.numDimensions() == 2 )
		{
			overview3d = Views.addDimension(
					overview, 0, 0 );
			overview3d = Views.addDimension(
					overview3d, 1, 3 );
		}
		else
		{
			overview3d = Views.addDimension(
					overview, 1, 3 );
		}

		return overview3d;
	}


	private void matchTomogram( File tomogramFile )
	{
		Utils.log( "Matching " + tomogramFile.getName() +" ..." );

		final RandomAccessibleInterval< T > tomogram = openTomogram( tomogramFile );

		final RandomAccessibleInterval< T > subsampledTomogram =
				Views.subsample( tomogram, getTomogramSubSampling() );

		RandomAccessibleInterval< T > projectedTomogram
				= createProjectedTomogram( subsampledTomogram );

		RandomAccessibleInterval< T > downscaledTomogram
				= createDownscaledTomogram( projectedTomogram );

		matchToOverview( downscaledTomogram );
	}

	private long[] getTomogramSubSampling()
	{
		return new long[]{
				settings.matchingPixelSpacingNanometer,
				settings.matchingPixelSpacingNanometer,
				1 };
	}

	private void matchToOverview( RandomAccessibleInterval< T > tomogram )
	{
		Utils.log( "Finding best matchToOverview in overview image..." );

		final int[] bestMatch = computePositionWithinOverviewImage( tomogram );

		showBestMatchOnOverview( tomogram, bestMatch );
	}

	private void showBestMatchOnOverview(
			RandomAccessibleInterval< T > tomogram,
			int[] bestMatch )
	{
		matchings.add( getRoi( tomogram, bestMatch ) );
		overviewForMatchingImagePlus.setOverlay( matchings );
	}

	private Roi getRoi( RandomAccessibleInterval< T > tomogram, int[] bestMatch )
	{
		Roi r = new Roi( bestMatch[0], bestMatch[1],
				tomogram.dimension( 0 ), tomogram.dimension( 1 )) ;
		r.setStrokeColor( Color.green);
		return r;
	}

	private RandomAccessibleInterval< T >
	createProjectedTomogram( RandomAccessibleInterval< T > tomogram )
	{
		Utils.log( "Computing average projection..." );

			if ( tomogram.numDimensions() == 3 )
		{
			final Projection projection = new Projection( tomogram, Z_DIMENSION );
			projectedTomogram = projection.average();
		}
		else
		{
			projectedTomogram = tomogram;
		}

		showIntermediateResult( projectedTomogram,"projected" );

		return projectedTomogram;

	}

	/**
	 * Downscales the tomogram to matchToOverview resolution of overview image.
	 * Note that both overview and tomogram may been subsampled already.
	 * However, as both have been subsampled with the same factor,
	 * the relative downscaling factor here still is correct.
	 *  @param tomogramFile
	 * @param projected
	 */
	private RandomAccessibleInterval< T >
	createDownscaledTomogram( RandomAccessibleInterval< T > projected )
	{
		Utils.log( "Scaling to overview image resolution..." );
		final double[] scaling = getScaling( projected.numDimensions() );
		downscaledTomogram = Scalings.createRescaledArrayImg( projected, scaling );
		showIntermediateResult( downscaledTomogram, "scaled-projection" );
		return downscaledTomogram;
	}

	private RandomAccessibleInterval< T > openTomogram( File tomogramFile )
	{
		settings.tomogramCalibrationNanometer
				= ImageIO.getNanometerPixelWidth( tomogramFile );

		if ( settings.confirmScalingViaUI && isFirstTomogram )
		{
			settings.tomogramCalibrationNanometer =
					confirmImageScalingUI(
						settings.tomogramCalibrationNanometer,
						"Tomogram");
			isFirstTomogram = false;
		}

		return ImageIO.openImageAs8Bit( tomogramFile );
	}

	private int[] computePositionWithinOverviewImage(
			RandomAccessibleInterval< T > tomogram )
	{
		final ByteProcessor tomogramProcessor = Utils.asByteProcessor( tomogram );

		FloatProcessor correlation = TemplateMatchingPlugin.doMatch(
				overviewProcessor,
				tomogramProcessor,
				NORMALIZED_CORRELATION,
				false );

		final ImagePlus correlationImp = new ImagePlus( "correlation", correlation );

		if ( settings.showIntermediateResults )
			correlationImp.show();

		// TODO: why is this only necessary here and not when running within Fiji?
//		Utils.log( "Removing spurious maxima from correlation by minimum filter of radius 2..." );
//		IJ.run( correlationImp, "Minimum...", "radius=2" );

		final int[] position = TemplateMatchingPlugin.findMax(
				correlationImp.getProcessor(), 0 );

		return position;
	}

	private void saveTomogramAsBdv( RandomAccessibleInterval< T > tomogram, double[] offset, File tomogramFile )
	{

		Utils.log( "Saving matched " + tomogramFile.getName() + " ..." );

		final IntervalView< T > tomogramWithImpDimensionOrder = Views.permute(
				Views.addDimension( tomogram, 0, 0 ),
				2, 3 );

		final ImagePlus imagePlus = Utils.asImagePlus( tomogramWithImpDimensionOrder );

		imagePlus.setTitle( tomogramFile.getName().split( "\\." )[ 0 ] );

		BdvExport.export(
				imagePlus,
				settings.outputDirectory + File.separator + tomogramFile.getName(),
				getTomogramCalibration(),
				"nanometer",
				offset );

	}

	private double[] getTomogramCalibration()
	{
		double[] calibration = new double[ 3 ];
		Arrays.fill( calibration, settings.tomogramCalibrationNanometer );
		return calibration;
	}

	private double[] getScaling( int numDimensions )
	{
		final double scalingRatio = settings.tomogramCalibrationNanometer
				/ settings.overviewCalibrationNanometer;
		final double[] scaling = new double[ numDimensions ];
		Arrays.fill( scaling, scalingRatio );
		Utils.log( "Scaling factor: " +  scalingRatio );
		return scaling;
	}

}
