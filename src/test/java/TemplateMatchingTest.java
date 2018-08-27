import de.embl.cba.em.matching.Algorithms;
import de.embl.cba.em.matching.Projection;
import de.embl.cba.em.matching.Transforms;
import ij.*;
import ij.measure.Calibration;
import ij.process.*;
import ij.gui.*;

import java.awt.*;

import ij.plugin.*;
import ij.plugin.filter.MaximumFinder;
import ij.measure.ResultsTable;
import net.imagej.ImageJ;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import scala.Array;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;

public class TemplateMatchingTest < T extends RealType< T > & NativeType< T > >
{

	public static final int Z_DIMENSION = 2;

	public void run()
	{
		final net.imagej.ImageJ ij = new ImageJ();
		ij.ui().showUI();


		ImagePlus overview = IJ.openImage( "/Users/tischer/Documents/giulia-mizzon-CLEM--data/template-matching-development/2D_lowMag.tif" );

		ArrayList< ImagePlus > templates = new ArrayList<>(  );
		templates.add( IJ.openImage( "/Users/tischer/Documents/giulia-mizzon-CLEM--data/template-matching-development/g22_t27-downScaled.tif" ) );
//		templates.add( IJ.openImage( "/Users/tischer/Documents/giulia-mizzon-CLEM--data/template-matching-development/g22_t29-downScaled.tif" ) );


		final double templateRotationAngle = 11.5 / 180.0 * Math.PI;
		final double overviewScale = 10.03;
		final double templatesScale = 6.275; // 1.255
		final double scalingRatio = templatesScale / overviewScale;


		ArrayList< int[] > offsets = new ArrayList<>(  );

		for ( ImagePlus template : templates )
		{

			template.show();
			final RandomAccessibleInterval< T > rai = ImageJFunctions.wrapReal( template );

			// avg projection
			final Projection projection = new Projection( rai, Z_DIMENSION );
			final RandomAccessibleInterval< T > projected = projection.average();
			ImageJFunctions.show( projected );

			// scale
			final double[] scalings = new double[ projected.numDimensions() ];
			Arrays.fill( scalings, scalingRatio );
			final RandomAccessibleInterval< T > downscaled = Algorithms.createDownscaledArrayImg( projected, scalings );

			ImageJFunctions.show( downscaled );

			// rotate
			final AffineTransform2D affineTransform2D = new AffineTransform2D();
			affineTransform2D.rotate( templateRotationAngle );
			final RandomAccessibleInterval rotated = Transforms.createTransformedView( downscaled, affineTransform2D );

			ImageJFunctions.show( rotated );

			// crop // TODO: make this generic
			final FinalInterval cropAfterRotation = new FinalInterval( new long[]{ 42, 42 }, new long[]{ 215 + 42, 215 + 42 } );
			final RandomAccessibleInterval cropped = Algorithms.copyAsArrayImg(
					Views.interval( Views.zeroMin( rotated ), cropAfterRotation ) );

			ImageJFunctions.show( cropped );

//			template.setRoi(915,916,218,214);
//			IJ.run( template, "Crop", "");
//
//			template.show();
//
//			FloatProcessor rFp = doMatch( overview, template, 5, true );
//
//			offsets.add( findMax(rFp, 0) );

		}

//		overview.show();
//
//		Overlay overlay = new Overlay(  );
//		for ( int[] offset : offsets )
//		{
//			overlay.add( new Roi( offset[0], offset[1], templates.get( 0 ).getWidth(), templates.get( 0 ).getHeight() ));
//		}
//
//		overview.setOverlay( overlay );
	}

	public static void main( String... args )
	{
		final TemplateMatchingTest templateMatchingTest = new TemplateMatchingTest();
		templateMatchingTest.run();

	}
}
