package de.embl.cba.em.matching;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import net.imagej.ops.OpService;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import static de.embl.cba.em.matching.Utils.asFloatProcessor;
import static de.embl.cba.em.matching.Utils.showIntermediateResult;

public class TomogramMatching < T extends RealType< T > & NativeType< T > >
{

	public static final int Z_DIMENSION = 2;
	public static final int NORMALIZED_CORRELATION = 5;

	private final TomogramMatchingSettings settings;
	private final OpService opService;
	private ArrayList< File > tomogramFiles;
	private FloatProcessor overviewProcessor;
	private RandomAccessibleInterval< T > overview;

	public TomogramMatching( TomogramMatchingSettings settings, OpService opService )
	{
		this.settings = settings;
		this.opService = opService;
		Utils.showIntermediateResults = settings.showIntermediateResults;
	}

	public void run()
	{
		createTomogramFileList();
		computeAndSaveRegisteredTomograms();
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

	private void computeAndSaveRegisteredTomograms()
	{
		openOverview();

//		saveOverviewAsBdv();

		for ( File tomogramFile : tomogramFiles )
		{
			computeAndSaveRegisteredTomogram( tomogramFile );
		}

	}

	private void openOverview()
	{
		overview = Utils.openImageFile( settings.overviewImage );

		// rotate
		final AffineTransform2D affineTransform2D = new AffineTransform2D();
		affineTransform2D.rotate( Math.toRadians( - settings.tomogramAngleDegrees ) );
		overview = Transforms.createTransformedView( overview, affineTransform2D );

		showIntermediateResult( overview );

		overviewProcessor = asFloatProcessor( overview );
	}

	private void saveOverviewAsBdv()
	{
		Utils.log( "Exporting overiew image..." );

		ImagePlus imagePlus = getOverviewAs3dImagePlus();

		double[] calibration = new double[ 3 ];
		calibration[ 0 ] = settings.overviewImageCalibrationNanometer;
		calibration[ 1 ] = calibration[ 0 ];
		calibration[ 2 ] = 2000;

		double[] offset = new double[ 3 ];

		String path = settings.outputDirectory + File.separator + "overview";
		BdvExport.export( imagePlus, path, calibration, "nanometer", offset );

	}

	private ImagePlus getOverviewAs3dImagePlus()
	{
		RandomAccessibleInterval< T > overview3d = getOverviewAs3d();

		return Utils.asImagePlus( overview3d );
	}

	private RandomAccessibleInterval< T > getOverviewAs3d()
	{
		RandomAccessibleInterval< T > overview3d = null;

		if ( overview.numDimensions() == 2 )
		{
			overview3d = Views.addDimension( overview, 0, 0 );
			overview3d = Views.addDimension( overview3d, 1, 3 );
		}
		else
		{
			overview3d = Views.addDimension( overview, 1, 3 );
		}
		return overview3d;
	}



	private void computeAndSaveRegisteredTomogram( File tomogramFile )
	{
		Utils.log( "Matching " + tomogramFile.getName() +"..." );

		final RandomAccessibleInterval< T > tomogram = Utils.openImageFile( tomogramFile );

		// avg projection
		final Projection projection = new Projection( tomogram, Z_DIMENSION );
		final RandomAccessibleInterval< T > projected = projection.average();

		showIntermediateResult( projected );

		// scale
		final double[] scaling = getScaling( projected );
		final RandomAccessibleInterval< T > downscaled = Algorithms.createDownscaledArrayImg( projected, scaling );

		showIntermediateResult( downscaled );

		// crop
//		final FinalInterval cropAfterRotation = getCroppingInterval();
//		final RandomAccessibleInterval cropped = Algorithms.copyAsArrayImg( Views.interval( Views.zeroMin( downscaled ), cropAfterRotation ) );

		//showIntermediateResult( cropped );

		// match
		final double[] position = computePositionWithinOverviewImage( downscaled );

		Utils.log( "Best match found at: " + position[ 0 ] + ", " + position[ 1 ] );

		// save
		saveTomogramAsBdv( tomogram, position, tomogramFile );

	}

	private double[] computePositionWithinOverviewImage( RandomAccessibleInterval cropped )
	{
		FloatProcessor correlation = TemplateMatchingPlugin.doMatch(
				overviewProcessor,
				Utils.asFloatProcessor( cropped ),
				NORMALIZED_CORRELATION,
				true );

		final int[] position = TemplateMatchingPlugin.findMax( correlation, 0 );

		double[] nanometerPosition = new double[ 3 ];
		for ( int i = 0; i < position.length; ++i )
		{
			nanometerPosition[ i ] = position[ i ] * settings.overviewImageCalibrationNanometer;
		}

		return nanometerPosition;
	}

	private void saveTomogramAsBdv( RandomAccessibleInterval< T > tomogram, double[] offset, File tomogramFile )
	{
		Utils.log( "Saving matched " + tomogramFile.getName() + " ..." );

		final IntervalView< T > tomogramWithImpDimensionOrder = Views.permute(
						Views.addDimension( tomogram, 0, 0 ),
						2, 3 );

		BdvExport.export(
				Utils.asImagePlus( tomogramWithImpDimensionOrder ),
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

	private FinalInterval getCroppingInterval()
	{
		// TODO: make this generic
		return new FinalInterval( new long[]{ 42, 42 }, new long[]{ 215 + 42, 215 + 42 } );
	}

	private double[] getScaling( RandomAccessibleInterval< T > projected )
	{
		final double scalingRatio = settings.tomogramCalibrationNanometer / settings.overviewImageCalibrationNanometer;
		final double[] scaling = new double[ projected.numDimensions() ];
		Arrays.fill( scaling, scalingRatio );
		return scaling;
	}

}
