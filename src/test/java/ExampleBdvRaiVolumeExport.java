import de.embl.cba.templatematching.bdv.BdvRaiVolumeExport;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;

public class ExampleBdvRaiVolumeExport
{
	public static void main( String[] args )
	{
		final RandomAccessibleInterval< UnsignedByteType > rai
				= ArrayImgs.unsignedBytes( 100, 100, 100 );

		final BdvRaiVolumeExport export = new BdvRaiVolumeExport();

		export.export( rai,
				"/Users/tischer/Desktop/hello.xml",
				new double[]{1,1,1},
				"pixel",
				new double[]{0,0,0}
				);

	}
}
