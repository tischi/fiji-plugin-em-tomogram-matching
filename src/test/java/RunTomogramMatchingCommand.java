import de.embl.cba.em.ui.TomogramMatchingCommand;
import net.imagej.ImageJ;

public class RunTomogramMatchingCommand
{

	public static void main(final String... args) throws Exception
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		// invoke the plugin
		ij.command().run( TomogramMatchingCommand.class, true );
	}

}
