import net.imagej.ImageJ;

public class TomogramMatchingCommand
{

	public static void main(final String... args) throws Exception
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		// invoke the plugin
		ij.command().run( de.embl.cba.em.matching.TomogramMatchingCommand.class, true );
	}

}
