package de.embl.cba.templatematching.match;

import de.embl.cba.templatematching.CalibratedRAI;
import de.embl.cba.templatematching.FileUtils;
import de.embl.cba.templatematching.ImageIO;
import de.embl.cba.templatematching.Utils;
import de.embl.cba.bdv.utils.io.BdvRaiVolumeExport;
import de.embl.cba.templatematching.imageprocessing.Projection;
import de.embl.cba.transforms.utils.Scalings;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.*;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
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

import static de.embl.cba.templatematching.Utils.asFloatProcessor;
import static de.embl.cba.templatematching.Utils.showIntermediateResult;
import static de.embl.cba.transforms.utils.Transforms.createBoundingIntervalAfterTransformation;

public class TemplateMatching < T extends RealType< T > & NativeType< T > >
{

	public static final int Z_DIMENSION = 2;
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

	public TemplateMatching( TemplateMatchingSettings settings )
	{
		this.settings = settings;
		templateIndex = 0;
		Utils.showIntermediateResults = settings.showIntermediateResults;
	}

	public void run()
	{
		openAndProcessOverview();

		matchTemplates();

		processResults();
	}

	public void processResults()
	{
		if ( settings.showIntermediateResults )
			if ( ! confirmSaving() ) return;

		saveImagesAsBdvHdf5();
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
		templateFiles = FileUtils.getFileList( settings.templatesInputDirectory, settings.templatesRegExp );
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

	private void openAndProcessOverview()
	{
		loadOverview();

		overviewForMatching = Views.subsample( overview, getOverviewSubSampling() );

		overviewForMatching = rotateOverview( overviewForMatching );

		FloatProcessor overviewProcessor = asFloatProcessor( overviewForMatching );

		overviewForMatchingImagePlus = new ImagePlus(
				"Overview for matching", overviewProcessor );

		addNoiseToOverview( overviewForMatchingImagePlus );

		if ( settings.showIntermediateResults )
			overviewForMatchingImagePlus.show();

	}

	private long[] getOverviewSubSampling()
	{
		return new long[]{
				subSampling,
				subSampling };
	}

	private void addNoiseToOverview( ImagePlus overview )
	{
		Utils.log( "Adding noise to overview..." );
		//IJ.run( overview, "Add Specified Noise...", "standard=10");
		IJ.run( overview, "Add Noise", "");
	}

	private void loadOverview()
	{
		Utils.log( "Opening overview image..." );

		final CalibratedRAI< T > calibratedRAI =
				ImageIO.withBFopenRAI( settings.overviewImageFile );

		settings.overviewCalibrationNanometer = calibratedRAI.nanometerCalibration[ 0 ];

		if ( settings.confirmScalingViaUI )
		{
			settings.overviewCalibrationNanometer
					= confirmImageScalingUI(
					settings.overviewCalibrationNanometer,
					"Overview");
		}

		setSubSampling();

		overview = calibratedRAI.rai;
	}

	private void setSubSampling()
	{
		subSampling = (int)
				Math.ceil( settings.matchingPixelSpacingNanometer
						/ settings.overviewCalibrationNanometer );

		Utils.log( "Subsampling: " + subSampling );
	}

	private void setOverviewCalibration()
	{
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
					Math.toRadians( -settings.overviewAngleDegrees ) );
			return createTransformedView(overview, affineTransform2D );
		}
		else if ( this.overview.numDimensions() == 3 )
		{
			final AffineTransform3D affineTransform3D = new AffineTransform3D();
			affineTransform3D.rotate(
					2, Math.toRadians( -settings.overviewAngleDegrees ) );
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

	private void saveImagesAsBdvHdf5()
	{
		exportOverview();
		exportTemplates();
	}

	private void exportTemplates()
	{
		for ( MatchedTemplate template : matchedTemplates )
		{
			RandomAccessibleInterval< T > rai = getRandomAccessibleInterval( template );

			String path = getOutputPath( template.file.getName() );

			Utils.log( "Exporting " + template.file.getName() );
			new BdvRaiVolumeExport().export(
					rai,
					path,
					template.calibration,
					template.calibrationUnit,
					template.calibratedPositionInOverview );
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
		calibration[ 0 ] = settings.overviewCalibrationNanometer;
		calibration[ 1 ] = settings.overviewCalibrationNanometer;
		calibration[ 2 ] = 2000;

		double[] offset = new double[ 3 ];

		String path = getOutputPath( "overview" );

		new BdvRaiVolumeExport().export(
				overview, path, calibration, "nanometer", offset );
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


	private void matchTemplate( File templateFile )
	{
		final RandomAccessibleInterval< T > template = openTemplate( templateFile );

		final RandomAccessibleInterval< T > subSampledTemplate =
				Views.subsample( template, getTemplateSubSampling() );

		RandomAccessibleInterval< T > projectedTemplate
				= createProjectedTemplate( subSampledTemplate );

		RandomAccessibleInterval< T > downscaledTemplate
				= createDownscaledTemplate( projectedTemplate );

		final int[] bestMatch = matchToOverview( downscaledTemplate );

		final MatchedTemplate matchedTemplate =
				getMatchedTemplate( templateFile, template, bestMatch );

		matchedTemplates.add( matchedTemplate );
	}

	private MatchedTemplate getMatchedTemplate(
			File templateFile,
			RandomAccessibleInterval< T > template,
			int[] bestMatch )
	{
		final double[] position = getCalibratedPositionInOverview( bestMatch );

		final MatchedTemplate matchedTemplate = new MatchedTemplate();

		if ( settings.allTemplatesFitInRAM )
			matchedTemplate.rai = template;

		matchedTemplate.file = templateFile;
		matchedTemplate.calibratedPositionInOverview = position;
		matchedTemplate.calibration = templateCalibrationNanometer;
		matchedTemplate.calibrationUnit = "nanometer";

		return matchedTemplate;
	}

	private double[] getCalibratedPositionInOverview( int[] bestMatch )
	{
		final double[] position = new double[ 3 ];

		for ( int d = 0; d < bestMatch.length; d++ )
		{
			position[ d ] = bestMatch[ d ] * scaling[ d ] * subSampling;
		}
		return position;
	}

	class MatchedTemplate
	{
		RandomAccessibleInterval< T > rai;
		File file;
		double[] calibratedPositionInOverview;
		double[] calibration;
		String calibrationUnit;
	}

	private long[] getTemplateSubSampling()
	{
		return new long[]{ subSampling, subSampling, 1 };
	}

	private int[] matchToOverview( RandomAccessibleInterval< T > template )
	{
		Utils.log( "Finding best match in overview image..." );

		final int[] bestMatch = computePositionWithinOverviewImage( template );

		showBestMatchOnOverview( template, bestMatch );

		return bestMatch;
	}

	private void showBestMatchOnOverview(
			RandomAccessibleInterval< T > template,
			int[] bestMatch )
	{
		if ( settings.showIntermediateResults )
		{
			matchingOverlay.add( getRectangleRoi( template, bestMatch ) );
			matchingOverlay.add( getTextRoi( bestMatch ) );
			overviewForMatchingImagePlus.setOverlay( matchingOverlay );
		}
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
			return new Projection( template, Z_DIMENSION ).average();
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

	private int[] computePositionWithinOverviewImage(
			RandomAccessibleInterval< T > template )
	{
		final ImageProcessor templateProcessor = Utils.asFloatProcessor( template );

		FloatProcessor correlation = TemplateMatchingPlugin.doMatch(
				overviewForMatchingImagePlus.getProcessor(),
				templateProcessor,
				NORMALIZED_CORRELATION );

		if ( settings.showIntermediateResults )
			new ImagePlus( ""+ templateIndex +" correlation",
					correlation ).show();

		final int[] position = findMaximumPosition( correlation );

		return position;
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
				templateCalibrationNanometer[ 0 ] / settings.overviewCalibrationNanometer;
		final double[] scaling = new double[ numDimensions ];
		Arrays.fill( scaling, scalingRatio );

		Utils.log( "Template pixel size [nm]: " + templateCalibrationNanometer[ 0 ] );
		Utils.log( "Overview pixel size [nm]: " + settings.overviewCalibrationNanometer );

		Utils.log( "Scaling factor: " +  scalingRatio );
		return scaling;
	}

}
