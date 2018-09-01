import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import java.io.File;
import java.util.List;

public class BdvShow < T extends RealType< T > & NativeType< T > >
{
	public static < T extends RealType< T > & NativeType< T > > void run() throws SpimDataException
	{
		ImagePlus overviewImp = IJ.openImage( "/Users/tischer/Documents/giulia-mizzon-CLEM--data/template-matching-development/2D_lowMag.tif" );
		final RandomAccessibleInterval< T > overview = ImageJFunctions.wrapReal( overviewImp );

		RandomAccessible< T > extendedOverview = Views.extendBorder( overview );
		final BdvStackSource< T > source = BdvFunctions.show( extendedOverview, overview, "overview" );
		Bdv bdv = source.getBdvHandle();

		String outputDirectory = "/Users/tischer/Documents/giulia-mizzon-CLEM--data/template-matching-development/output";
		String outputFilename = "g22_t27-downScaled";
		String outputPath = outputDirectory + File.pathSeparator + outputFilename;
		final SpimData spimData = new XmlIoSpimData().load( outputPath + ".xml" );

		BdvFunctions.show( spimData,
				BdvOptions.options()
						.addTo( bdv ) );

	}

	public static void main ( String... args ) throws SpimDataException
	{
		BdvShow.run();
	}
}
