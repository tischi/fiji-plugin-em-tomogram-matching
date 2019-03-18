package de.embl.cba.templatematching.match;

import de.embl.cba.bdv.utils.io.BdvRaiXYZCTExport;
import de.embl.cba.templatematching.CalibratedRAI;
import de.embl.cba.templatematching.FileUtils;
import de.embl.cba.templatematching.ImageIO;
import de.embl.cba.templatematching.Utils;
import de.embl.cba.templatematching.imageprocessing.Projection;
import de.embl.cba.transforms.utils.Scalings;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import net.imglib2.*;
import net.imglib2.Point;
import net.imglib2.algorithm.localextrema.RefinedPeak;
import net.imglib2.algorithm.localextrema.SubpixelLocalization;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;


import static de.embl.cba.templatematching.Utils.asFloatProcessor;
import static de.embl.cba.templatematching.Utils.showIntermediateResult;
import static de.embl.cba.transforms.utils.Transforms.createBoundingIntervalAfterTransformation;
import static de.embl.cba.transforms.utils.Transforms.getCenter;

public class TemplateMatching < T extends RealType< T > & NativeType< T > >
{

	public static final int CV_TM_SQDIFF = 0;
	public static final int CORRELATION = 4;
	public static final int NORMALIZED_CORRELATION = 5;

	private final TemplateMatchingSettings settings;
	private ArrayList< File > templateFiles;
	private RandomAccessibleInterval< T > overview;
	private RandomAccessibleInterval< T > overviewForMatching;
	private Overlay matchingOverlay;
	private ImagePlus overviewForMatchingImagePlus;
	private int subSampling;
	private int templateIndex;
	private double[] templateCalibrationNanometer;
	private ArrayList< MatchedTemplate > matchedTemplates;
	private double[] scaling;
	private double overviewCalibrationNanometer;
	private boolean overviewIs3D;
	private boolean overviewIsMultiChannel;

	public TemplateMatching( TemplateMatchingSettings settings )
	{
		this.settings = settings;
		templateIndex = 0;
		Utils.showIntermediateResults = settings.showIntermediateResults;
	}

	public boolean run()
	{
		openOverview();

		matchTemplates();

		return saveResults();
	}

	public boolean saveResults()
	{
		if ( settings.showIntermediateResults )
			if ( ! confirmSaving() ) return false;

		return saveImagesAsBdvHdf5();
	}

	public boolean confirmSaving()
	{
		final GenericDialog gd =
				new NonBlockingGenericDialog( "Save results" );

		gd.addMessage( "Would you like to " +
				"export the matched templates as multi-resolution images;\n" +
				"for viewing with the " +
				"Template Browsing plugin?" );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		return true;
	}

	private void createTemplateFileList()
	{
		templateFiles = FileUtils.getFileList(
				settings.templatesInputDirectory, settings.templatesRegExp );
	}

	private void matchTemplates()
	{
		matchingOverlay = new Overlay();
		matchedTemplates = new ArrayList< >();

		createTemplateFileList();

		for ( File templateFile : templateFiles )
		{
			matchTemplate( templateFile );
			templateIndex++;
		}
	}

	private void openOverview()
	{
		loadOverview();

		overview = rotate2D( overview );

		overview = Views.zeroMin( overview );

		final long[] overviewSubSampling = getOverviewSubSampling();

		overviewForMatching = Views.subsample( overview, overviewSubSampling );

		createOverviewForMatchingImagePlus(
				asFloatProcessor( overviewForMatching ),
				overviewSubSampling );

		addNoiseToOverview( overviewForMatchingImagePlus );

		if ( settings.showIntermediateResults )
			overviewForMatchingImagePlus.show();

	}

	private void createOverviewForMatchingImagePlus(
			FloatProcessor overviewProcessor,
			long[] overviewSubSampling )
	{
		overviewForMatchingImagePlus = new ImagePlus(
				"Overview for matching", overviewProcessor );

		final Calibration calibration = overviewForMatchingImagePlus.getCalibration();

		calibration.pixelWidth =
				overviewCalibrationNanometer * overviewSubSampling[ 0 ];
		calibration.pixelHeight =
				overviewCalibrationNanometer * overviewSubSampling[ 1 ];
		calibration.setUnit( "nanometer" );
	}

	private long[] getOverviewSubSampling()
	{
		if ( overview.numDimensions() == 2 )
		{
			return new long[]{ subSampling, subSampling };
		}
		else if ( overview.numDimensions() == 3)
		{
			return new long[]{ subSampling, subSampling, 1 };
		}
		else
		{
			return null;
		}
	}

	/**
	 * Sometimes there are areas of uniform pixel intensities in the images.
	 * This causes issues with the x-correlation normalisation.
	 * Adding some noise seems to solve this issue, while
	 * not harming the x-correlation.
	 *
	 * @param overview
	 */
	private void addNoiseToOverview( ImagePlus overview )
	{
		Utils.log( "Adding noise to overview..." );
		//IJ.run( overview, "Add Specified Noise...", "standard=10");
		IJ.run( overview, "Add Noise", "");
	}

	private void loadOverview()
	{
		Utils.log( "Loading overview image..." );

		final CalibratedRAI< T > calibratedRAI =
				ImageIO.withBFopenRAI( settings.overviewImageFile );

		// TODO: wrap into "PhysicalRai"
		overviewCalibrationNanometer = calibratedRAI.nanometerCalibration[ 0 ];
		overviewIs3D = calibratedRAI.is3D;
		overviewIsMultiChannel = calibratedRAI.isMultiChannel;

		if ( settings.confirmScalingViaUI )
		{
			overviewCalibrationNanometer
					= confirmImageScalingUI(
					overviewCalibrationNanometer,
					"Overview");
		}

		setSubSampling();

		overview = calibratedRAI.rai;
	}

	private void setSubSampling()
	{
		subSampling = (int)
				Math.ceil( settings.matchingPixelSpacingNanometer
						/ overviewCalibrationNanometer );

		Utils.log( "SubSampling: " + subSampling );
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
	rotate2D( RandomAccessibleInterval< T > rai )
	{
		if ( rai.numDimensions() == 2 )
		{
			final AffineTransform2D affineTransform2D = new AffineTransform2D();
			affineTransform2D.rotate(
					Math.toRadians( -settings.overviewAngleDegrees ) );
			return createTransformedView( rai, affineTransform2D );
		}
		else if ( this.overview.numDimensions() == 3 )
		{
			final AffineTransform3D affineTransform3D = new AffineTransform3D();
			affineTransform3D.rotate(
					2, Math.toRadians( -settings.overviewAngleDegrees ) );
			return createTransformedView( rai, affineTransform3D );
		}
		return null;
	}

	public static < T extends NumericType< T > & NativeType< T > >
	RandomAccessibleInterval createTransformedView(
			RandomAccessibleInterval< T > rai, InvertibleRealTransform transform )
	{
		RealRandomAccessible rra =
				Views.interpolate( Views.extendZero( rai ),
						new NearestNeighborInterpolatorFactory<>() );

		rra = RealViews.transform( rra, transform );

		final RandomAccessibleOnRealRandomAccessible< T > raster = Views.raster( rra );

		final FinalInterval transformedInterval =
				createBoundingIntervalAfterTransformation( rai, transform );

		final RandomAccessibleInterval< T > transformedIntervalView =
				Views.interval( raster, transformedInterval );

		return transformedIntervalView;
	}

	private boolean saveImagesAsBdvHdf5()
	{
		Utils.log( "# Saving results" );
		exportTemplates();
		exportOverview();
		return true;
	}

	private void exportTemplates()
	{
		for ( MatchedTemplate template : matchedTemplates )
		{
			RandomAccessibleInterval< T > rai = getRandomAccessibleInterval( template );

			String path = getOutputPath( template.file.getName() );
			Utils.log( "Exporting " + template.file.getName() );

			if ( rai.numDimensions() == 2 ) // add z-dimension
				rai = Views.addDimension( rai, 0, 0 );

			// add channel dimension
			rai = Views.addDimension( rai, 0, 0 );

			// add time dimension
			rai = Views.addDimension( rai, 0, 0 );


			new BdvRaiXYZCTExport< T >().export(
					rai,
					template.file.getName(),
					path,
					template.calibration,
					template.calibrationUnit,
					template.calibratedPosition );
		}
	}

	private String getOutputPath( String name )
	{
		return settings.outputDirectory + File.separator + name;
	}

	private RandomAccessibleInterval< T > getRandomAccessibleInterval( MatchedTemplate template )
	{
		RandomAccessibleInterval< T > rai;
		if ( template.rai == null )
		{
			rai = openTemplate( template.file );
		}
		else
		{
			rai = template.rai;
		}
		return rai;
	}

	private void exportOverview()
	{
		Utils.log( "Exporting overview image..." );

		double[] calibration = new double[ 3 ];
		calibration[ 0 ] = overviewCalibrationNanometer;
		calibration[ 1 ] = overviewCalibrationNanometer;
		calibration[ 2 ] = 2000;

		double[] translation = new double[ 3 ];
		translation[ 2 ] = - 0.5 * calibration[ 2 ]; // center around 0

		String path = getOutputPath( "overview" );

		if ( ! overviewIs3D ) // add z-dimension
			overview = Views.addDimension( overview, 0, 0 );

		if ( overviewIsMultiChannel ) // swap z and channel dimension
			overview = Views.permute( overview, 2, 3 );

		// add time dimension
		overview = Views.addDimension( overview, 0, 0 );

		new BdvRaiXYZCTExport< T >().export(
				Views.zeroMin( overview ),
				"overview",
				path,
				calibration,
				"nanometer",
				translation );
	}

	private void matchTemplate( File templateFile )
	{
		final RandomAccessibleInterval< T > template = openTemplate( templateFile );

		final RandomAccessibleInterval< T > subSampledTemplate =
				Views.subsample( template, getTemplateSubSampling() );

		RandomAccessibleInterval< T > projectedTemplate
				= createProjectedTemplate( subSampledTemplate );

		RandomAccessibleInterval< T > downscaledTemplate
				= createDownscaledTemplate( projectedTemplate );

		final double[] positionInSubSampledOverview = matchToOverview( downscaledTemplate );

		final MatchedTemplate matchedTemplate =
				getMatchedTemplate( templateFile, template, positionInSubSampledOverview );

		matchedTemplates.add( matchedTemplate );
	}

	private MatchedTemplate getMatchedTemplate(
			File templateFile,
			RandomAccessibleInterval< T > template,
			double[] positionInSubSampledOverview )
	{
		final double[] position = getCalibratedPosition( positionInSubSampledOverview );

		// set z position to template center
		final double[] center = getCenter( template );
		position[ 2 ] = - center[ 2 ];

		final MatchedTemplate matchedTemplate = new MatchedTemplate();

		if ( settings.allTemplatesFitInRAM )
			matchedTemplate.rai = template;
		else
		{
			template = null;
			System.gc();
		}

		matchedTemplate.file = templateFile;
		matchedTemplate.calibratedPosition = position;
		matchedTemplate.calibration = templateCalibrationNanometer;
		matchedTemplate.calibrationUnit = "nanometer";

		return matchedTemplate;
	}

	private double[] getCalibratedPosition( double[] pixelPositionInSubSampledOverview )
	{
		final double[] calibratedPosition = new double[ 3 ];

		for ( int d = 0; d < pixelPositionInSubSampledOverview.length; d++ )
		{
			calibratedPosition[ d ] = pixelPositionInSubSampledOverview[ d ]
					* subSampling * overviewCalibrationNanometer;
		}

		return calibratedPosition;
	}

	class MatchedTemplate
	{
		RandomAccessibleInterval< T > rai;
		File file;
		double[] calibratedPosition;
		double[] calibration;
		String calibrationUnit;
	}

	private long[] getTemplateSubSampling()
	{
		return new long[]{ subSampling, subSampling, 1 };
	}

	private double[] matchToOverview( RandomAccessibleInterval< T > template )
	{
		Utils.log( "Finding best match in overview image..." );

		final double[] position = computePositionWithinOverviewImage( template );

		if ( settings.showIntermediateResults )
			showBestMatchOnOverview( template, position );

		return position;
	}

	private void showBestMatchOnOverview(
			RandomAccessibleInterval< T > template,
			double[] position )
	{
		Utils.log( "Show match in overview image..." );

		final int[] intPosition = new int[ position.length ];
		for ( int d = 0; d < position.length; d++ )
			intPosition[ d ] = (int) position[ d ];

		matchingOverlay.add( getRectangleRoi( template, intPosition ) );
		matchingOverlay.add( getTextRoi( intPosition ) );
		overviewForMatchingImagePlus.setOverlay( matchingOverlay );
	}

	private Roi getRectangleRoi(
			RandomAccessibleInterval< T > template,
			int[] bestMatch )
	{
		Roi r = new Roi( bestMatch[0], bestMatch[1],
				template.dimension( 0 ), template.dimension( 1 )) ;
		r.setStrokeColor( Color.green);
		return r;
	}

	private Roi getTextRoi( int[] bestMatch )
	{
		Roi r = new TextRoi(
				bestMatch[0] + 5, bestMatch[1] + 5,
				"" + templateIndex ) ;
		r.setStrokeColor( Color.magenta);
		return r;
	}

	private RandomAccessibleInterval< T >
	createProjectedTemplate( RandomAccessibleInterval< T > template )
	{
		Utils.log( "Computing template average projection..." );

		if ( template.numDimensions() == 3 )
			return new Projection( template, 2 ).average();
		else
			return template;
	}

	/**
	 * Downscales the template to matchToOverview resolution of overview image.
	 * Note that both overview and template may been subsampled already.
	 * However, as both have been subsampled with the same factor,
	 * the relative downscaling factor here still is correct.
	 * @param projected
	 */
	private RandomAccessibleInterval< T >
	createDownscaledTemplate( RandomAccessibleInterval< T > projected )
	{
		Utils.log( "Scaling to overview image resolution..." );

		scaling = getScaling( projected.numDimensions() );

		RandomAccessibleInterval< T > downscaledTemplate
				= Scalings.createRescaledArrayImg( projected, scaling );

		showIntermediateResult( downscaledTemplate,
				"" + templateIndex + " template" );

		return downscaledTemplate;
	}

	private RandomAccessibleInterval< T > openTemplate( File templateFile )
	{
		final CalibratedRAI< T > calibratedRAI = ImageIO.withBFopenRAI( templateFile );

		templateCalibrationNanometer = calibratedRAI.nanometerCalibration;

		if ( settings.confirmScalingViaUI && templateIndex == 0 )
		{
			templateCalibrationNanometer[ 0 ] =
					confirmImageScalingUI(
							templateCalibrationNanometer[ 0 ],
						"Template");
		}

		return calibratedRAI.rai;
	}

	private double[] computePositionWithinOverviewImage(
			RandomAccessibleInterval< T > template )
	{
		final ImageProcessor templateProcessor = Utils.asFloatProcessor( template );

		Utils.log( "X-correlation..." );

		FloatProcessor correlation = TemplateMatchingPlugin.doMatch(
				overviewForMatchingImagePlus.getProcessor(),
				templateProcessor,
				NORMALIZED_CORRELATION );

		if ( settings.showIntermediateResults )
			new ImagePlus( ""+ templateIndex +" correlation",
					correlation ).show();

		Utils.log( "Find maximum in x-correlation..." );

		final int[] position = findMaximumPosition( correlation );

		Utils.log( "Refine maximum to sub-pixel resolution..." );

		final double[] refinedPosition = computeRefinedPosition( correlation, position );

		return refinedPosition;
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

	private double[] getScaling( int numDimensions )
	{
		final double scalingRatio =
				templateCalibrationNanometer[ 0 ] / overviewCalibrationNanometer;
		final double[] scaling = new double[ numDimensions ];
		Arrays.fill( scaling, scalingRatio );

		Utils.log( "Template pixel size [nm]: " + templateCalibrationNanometer[ 0 ] );
		Utils.log( "Overview pixel size [nm]: " + overviewCalibrationNanometer );

		Utils.log( "Scaling factor: " +  scalingRatio );
		return scaling;
	}

}
