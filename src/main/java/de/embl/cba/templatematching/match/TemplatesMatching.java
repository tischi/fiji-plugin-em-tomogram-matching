package de.embl.cba.templatematching.match;

import de.embl.cba.bdv.utils.io.BdvRaiXYZCTExport;
import de.embl.cba.templatematching.FileUtils;
import de.embl.cba.templatematching.ImageIO;
import de.embl.cba.templatematching.Utils;
import de.embl.cba.templatematching.image.CalibratedRai;
import de.embl.cba.templatematching.image.CalibratedRaiPlus;
import de.embl.cba.templatematching.image.DefaultCalibratedRai;
import de.embl.cba.templatematching.process.Processor;
import ij.ImagePlus;
import ij.gui.*;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;

import static de.embl.cba.templatematching.Utils.asFloatProcessor;

public class TemplatesMatching< T extends RealType< T > & NativeType< T > >
{

	private final TemplatesMatchingSettings settings;
	private ArrayList< File > templateFiles;
	private Overlay matchingOverlay;
	private ImagePlus overviewImagePlus;
	private int templateIndex;
	private ArrayList< MatchedTemplate > matchedTemplates;
	private int addNoiseLevel;
	private CalibratedRaiPlus< T > overviewRaiPlus;

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

		exportOverview();

		return true;
	}

	// TODO: refactor into separate class!
	public boolean saveResults()
	{
		if ( settings.showIntermediateResults )
			if ( !confirmSaving() ) return false;

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
		matchedTemplates = new ArrayList<>();

		for ( File templateFile : templateFiles )
		{
			if ( settings.isHierarchicalMatching && templateFile.getName().contains( "hm" ) )
				continue; // as this will be later matched in the hierarchy

			final CalibratedRaiPlus< T > template = openImage( templateFile );

			final TemplateMatcherTranslation2D templateMatcherTranslation2D
					= new TemplateMatcherTranslation2D( overviewImagePlus );

			final MatchedTemplate matchedTemplate = templateMatcherTranslation2D.match( template );

			matchedTemplate.file = templateFile;

			if ( settings.showIntermediateResults )
				showBestMatchOnOverview( matchedTemplate );

			// TODO: maybe let the user confirm?!
			exportTemplate( matchedTemplate );

			// open next template in hierarchy and then match against the one before.
		}
	}

	private void openOverview()
	{
		loadOverview();

		CalibratedRai rotated =
				Processor.rotate2D( overviewRaiPlus, settings.overviewAngleDegrees );

		rotated = new DefaultCalibratedRai<>(
				Views.zeroMin( rotated.rai() ), rotated.nanometerCalibration() );

		final long[] overviewSubSampling = getOverviewSubSamplingXY();

		Utils.log( "Sub-sampling overview image by: " + overviewSubSampling[ 0 ] );
		final CalibratedRai< T > subSampled =
				Processor.subSample( rotated, overviewSubSampling );

		// TODO: one should also downsample to get to the actually requested calibration pixel size!

		createOverviewForMatching(
				asFloatProcessor( subSampled.rai(), addNoiseLevel ),
				overviewSubSampling );

		if ( settings.showIntermediateResults )
			overviewImagePlus.show();

	}

	private void createOverviewForMatching(
			FloatProcessor overviewProcessor,
			long[] overviewSubSampling )
	{
		overviewImagePlus = new ImagePlus(
				"Overview for matching", overviewProcessor );

		final Calibration calibration = overviewImagePlus.getCalibration();
		calibration.pixelWidth =
				overviewRaiPlus.nanometerCalibration()[ 0 ] * overviewSubSampling[ 0 ];
		calibration.pixelHeight =
				overviewRaiPlus.nanometerCalibration()[ 1 ] * overviewSubSampling[ 1 ];
		calibration.setUnit( "nanometer" );

	}

	private long[] getOverviewSubSamplingXY()
	{
		final long[] subSampling = new long[ 2 ];

		for ( int d = 0; d < 2; d++ )
			subSampling[ d ] = (long) (
					settings.matchingPixelSpacingNanometer
							/ overviewRaiPlus.nanometerCalibration()[ d ] );

		if ( overviewRaiPlus.rai().numDimensions() == 2 )
			return new long[]{ subSampling[ 0 ], subSampling[ 1 ] };
		else if ( overviewRaiPlus.rai().numDimensions() == 3 )
			return new long[]{ subSampling[ 0 ], subSampling[ 1 ], 1 };
		else
			return null;
	}


	private void loadOverview()
	{
		Utils.log( "Loading overview image..." );

		overviewRaiPlus = ImageIO.withBFopenRAI( settings.overviewImageFile );
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

	private boolean saveImagesAsBdvHdf5()
	{
		Utils.log( "# Saving results" );
		exportOverview();
		exportTemplates();
		return true;
	}

	private void exportTemplates()
	{
		for ( MatchedTemplate< T > template : matchedTemplates )
		{
			exportTemplate( template );
		}
	}

	private void exportTemplate( MatchedTemplate< T > template )
	{
		RandomAccessibleInterval< T > rai = template.calibratedRai.rai();

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
				template.calibratedRai.nanometerCalibration(),
				"nanometer",
				template.matchedPositionNanometer );
	}

	private String getOutputPath( String name )
	{
		return settings.outputDirectory + File.separator + name;
	}

	private void exportOverview()
	{
		Utils.log( "Exporting overview image..." );

		double[] calibration = new double[ 3 ];
		calibration[ 0 ] = overviewRaiPlus.nanometerCalibration()[ 0 ];
		calibration[ 1 ] = overviewRaiPlus.nanometerCalibration()[ 1 ];
		calibration[ 2 ] = 2000;

		double[] translation = new double[ 3 ];

		String path = getOutputPath( "overview" );

		RandomAccessibleInterval< T > overview = overviewRaiPlus.rai();

		if ( ! overviewRaiPlus.is3D ) // add z-dimension
			overview = Views.addDimension( overview, 0, 0 );

		if ( overviewRaiPlus.isMultiChannel ) // swap z and channel dimension
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
			MatchedTemplate matchedTemplate )
	{
		final double[] position = matchedTemplate.matchedPositionNanometer;
		final double[] size = matchedTemplate.getImageSizeNanometer();

		final int[] pixelPosition = new int[ 2 ];
		pixelPosition[ 0 ] = ( int ) ( position[ 0 ] / overviewImagePlus.getCalibration().pixelWidth );
		pixelPosition[ 1 ] = ( int ) ( position[ 0 ] / overviewImagePlus.getCalibration().pixelHeight );

		final int[] templateSizePixel = new int[ 2 ];
		templateSizePixel[ 0 ] = ( int ) ( size[ 0 ] / overviewImagePlus.getCalibration().pixelWidth );
		templateSizePixel[ 1 ] = ( int ) ( size[ 0 ] / overviewImagePlus.getCalibration().pixelHeight );

		matchingOverlay.add( getRectangleRoi( pixelPosition, templateSizePixel ) );
		matchingOverlay.add( getTextRoi( templateSizePixel ) );

		overviewImagePlus.setOverlay( matchingOverlay );
	}

	private Roi getRectangleRoi( int[] position, int[] size )
	{
		Roi r = new Roi( position[ 0 ], position[ 1 ], size[ 0 ], size[ 1 ] );
		r.setStrokeColor( Color.green );
		return r;
	}

	private Roi getTextRoi( int[] bestMatch )
	{
		Roi r = new TextRoi(
				bestMatch[ 0 ] + 5, bestMatch[ 1 ] + 5,
				"" + templateIndex );
		r.setStrokeColor( Color.magenta );
		return r;
	}

	private CalibratedRaiPlus< T > openImage( File file )
	{
		return  (CalibratedRaiPlus) ImageIO.withBFopenRAI( file );
	}


}
