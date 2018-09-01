import bdv.util.BdvFunctions;
import bdv.util.BdvStackSource;
import de.embl.cba.em.matching.ExportBdvHdf5;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;

import java.io.File;
import java.util.List;

public class BdvExport
{
	public static void main ( String... args ) throws SpimDataException
	{
		final ImagePlus imp = IJ.openImage( "/Users/tischer/Documents/giulia-mizzon-CLEM--data/template-matching-development/g22_t27-downScaled.tif" );

		String outputDirectory = "/Users/tischer/Documents/giulia-mizzon-CLEM--data/template-matching-development/output";
		String outputFilename = "g22_t27-downScaled";
		String outputPath = outputDirectory + File.pathSeparator + outputFilename;

		ExportBdvHdf5.export( imp,
				outputDirectory + File.pathSeparator + outputFilename,
				new double[]{1,1,1},
				"micrometer",
				new double[]{20,0,0} );

		final SpimData spimData = new XmlIoSpimData().load( outputPath + ".xml" );

		final List< BdvStackSource< ? > > bdvStackSources = BdvFunctions.show( spimData );

	}
}
