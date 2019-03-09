package de.embl.cba.em.match;

import de.embl.cba.em.ImageIO;
import de.embl.cba.em.Utils;
import de.embl.cba.em.bdv.BdvExport;
import de.embl.cba.em.imageprocessing.Projection;
import de.embl.cba.transforms.utils.Scalings;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
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
	private final OpService opService;
	private ArrayList< File > tomogramFiles;
	private ByteProcessor overviewProcessor;
	private RandomAccessibleInterval< T > overview;
	private final int fillingValue;
	private RandomAccessibleInterval< T > downscaledTomogram;
	private RandomAccessibleInterval< T > projectedTomogram;
	private double[] bestMatch;
	private boolean isFirstTomogram;
	private ImageProcessor overviewProcessorWithNoise;
	private RandomAccessibleInterval< T > overviewForMatching;

	public TomogramMatching( TomogramMatchingSettings settings, OpService opService )
	{
		this.settings = settings;
		this.opService = opService;
		this.fillingValue = settings.fillingValue;

		isFirstTomogram = true;

		Utils.showIntermediateResults = settings.showIntermediateResults;
	}

	public void run()
	{
		openAndProcessOverviewForMatching();

		createTomogramFileList();

		registerAndSaveTomograms();

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

	private void registerAndSaveTomograms()
	{
		for ( File tomogramFile : tomogramFiles )
		{
			registerAndSaveTomogram( tomogramFile );
		}
	}

	private void openAndProcessOverviewForMatching()
	{
		loadOverview();

		overviewForMatching = Views.subsample( overview, getOverviewSubSampling() );

		overviewForMatching = rotateOverview( overviewForMatching );

		overviewProcessor = asByteProcessor( overviewForMatching );

		final ImagePlus overviewForMatching = new ImagePlus(
				"Overview for matching", overviewProcessor );

		//addNoiseToOverview( rotatedOverview );

		if ( settings.showIntermediateResults )
			overviewForMatching.show();

	}

	private long[] getOverviewSubSampling()
	{
		return new long[]{
				settings.subSamplingDuringMatching,
				settings.subSamplingDuringMatching };
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

		// TODO: perform 8-bit conversion later (only for matching)
		overview = ImageIO.openImageAs8Bit( settings.overviewImage );
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


	private void registerAndSaveTomogram( File tomogramFile )
	{
		Utils.log( "Matching " + tomogramFile.getName() +" ..." );

		final RandomAccessibleInterval< T > tomogram = openTomogram( tomogramFile );

		final RandomAccessibleInterval< T > subsample =
				Views.subsample( tomogram, getTomogramSubSampling() );

		createProjectedTomogram( tomogramFile, subsample );

		createDownscaledTomogram( tomogramFile, projectedTomogram );

		findBestMatchingPosition( downscaledTomogram );

		if ( settings.saveResults )
			saveTomogramAsBdv( tomogram, bestMatch, tomogramFile );
	}

	private long[] getTomogramSubSampling()
	{
		return new long[]{
				settings.subSamplingDuringMatching,
				settings.subSamplingDuringMatching,
				1 };
	}

	private void findBestMatchingPosition( RandomAccessibleInterval< T > tomogram )
	{
		Utils.log( "Finding best match in overview image..." );
		bestMatch = computePositionWithinOverviewImage( tomogram );

		Utils.log( "Best match found at (upper left corner) [nm]: "
				+ bestMatch[ 0 ] + ", " + bestMatch[ 1 ] );
		Utils.log( "Best match found at (upper left corner) [pixels]: " +
				(int) ( bestMatch[ 0 ] / settings.overviewCalibrationNanometer ) + ", " +
				(int) ( bestMatch[ 1 ] / settings.overviewCalibrationNanometer ) );
	}

	private void createProjectedTomogram(
			File tomogramFile, RandomAccessibleInterval< T > tomogram )
	{
		// avg projection
		// TODO: to speed this up one could subsample before
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

		showIntermediateResult( projectedTomogram,
				"projection-" + tomogramFile.getName() );
	}

	private void createDownscaledTomogram(
			File tomogramFile,
			RandomAccessibleInterval< T > projected )
	{
		Utils.log( "Scaling to overview image resolution..." );
		final double[] scaling = getScaling( projected.numDimensions() );
		downscaledTomogram = Scalings.createRescaledArrayImg( projected, scaling );
		showIntermediateResult( downscaledTomogram,
				"scaled-projection-" + tomogramFile.getName() );
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

	private double[] computePositionWithinOverviewImage(
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

		double[] nanometerPosition = new double[ 3 ];
		for ( int i = 0; i < position.length; ++i )
		{
			nanometerPosition[ i ] = position[ i ] * settings.overviewCalibrationNanometer;
		}

		return nanometerPosition;
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
