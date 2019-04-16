package de.embl.cba.templatematching.match;

import de.embl.cba.bdv.utils.io.BdvRaiXYZCTExport;
import de.embl.cba.templatematching.image.CalibratedRaiPlus;
import de.embl.cba.templatematching.FileUtils;
import de.embl.cba.templatematching.ImageIO;
import de.embl.cba.templatematching.Utils;
import ij.ImagePlus;
import ij.gui.*;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import net.imglib2.*;
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


import static de.embl.cba.templatematching.Utils.asFloatProcessor;
import static de.embl.cba.transforms.utils.Transforms.*;

public class TemplatesMatching< T extends RealType< T > & NativeType< T > >
{

	private final TemplatesMatchingSettings settings;
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
	private int addNoiseLevel;
	private double[] overviewProcessorPixelSizeNanometer = new double[ 2 ];

	public TemplatesMatching( TemplatesMatchingSettings settings )
	{
		this.settings = settings;
		templateIndex = 0;
		addNoiseLevel = 5; // TODO: 5 is very random...

		Utils.showIntermediateResults = settings.showIntermediateResults;
	}

	public boolean run()
	{
		createTemplateFileList();

		openOverview();

		matchTemplates();

		return saveResults();
	}

	// TODO: refactor into separate class!
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

		for ( File templateFile : templateFiles )
		{
			if ( settings.isHierarchicalMatching &&  templateFile.getName().contains( "hm" ) )
				continue; // as this will be later matched in the hierarchy

			final CalibratedRaiPlus< T > template = openTemplate( templateFile );

			final TemplateMatcherTranslation2D templateMatcherTranslation2D
					= new TemplateMatcherTranslation2D(
							overviewForMatchingImagePlus.getProcessor(),
							overviewProcessorPixelSizeNanometer );

			templateMatcherTranslation2D.match( template );

			if ( settings.showIntermediateResults )
				showBestMatchOnOverview( template, position );


			// open next template in hierarchy and then match against the one before.
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
				asFloatProcessor( overviewForMatching, addNoiseLevel ),
				overviewSubSampling );

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

		overviewProcessorPixelSizeNanometer[ 0 ] = calibration.pixelWidth;
		overviewProcessorPixelSizeNanometer[ 1 ] = calibration.pixelHeight;


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


	private void loadOverview()
	{
		Utils.log( "Loading overview image..." );

		final CalibratedRaiPlus< T > calibratedRaiPlus =
				ImageIO.withBFopenRAI( settings.overviewImageFile );

		// TODO: wrap into "PhysicalRai"
		overviewCalibrationNanometer = calibratedRaiPlus.nanometerCalibration[ 0 ];
		overviewIs3D = calibratedRaiPlus.is3D;
		overviewIsMultiChannel = calibratedRaiPlus.isMultiChannel;

		if ( settings.confirmScalingViaUI )
		{
			overviewCalibrationNanometer
					= confirmImageScalingUI(
					overviewCalibrationNanometer,
					"Overview");
		}

		setSubSampling();

		overview = calibratedRaiPlus.rai;
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
		exportOverview();
		exportTemplates();
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
					template.pixelSizesNanometer,
					template.calibrationUnit,
					template.matchedPositionNanometer );
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

	private CalibratedRaiPlus< T > openTemplate( File templateFile )
	{
		final CalibratedRaiPlus< T > calibratedRaiPlus = ImageIO.withBFopenRAI( templateFile );

		if ( settings.confirmScalingViaUI && templateIndex == 0 )
		{
			calibratedRaiPlus.nanometerCalibration[ 0 ] =
					confirmImageScalingUI(
							templateCalibrationNanometer[ 0 ],
						"Template");
		}

		return calibratedRaiPlus;
	}



}
