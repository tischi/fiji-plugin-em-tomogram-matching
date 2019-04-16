import de.embl.cba.templatematching.ui.MultiTemplateMatchingCommand;
import net.imagej.ImageJ;

public class RunMultiTemplateMatchingCommand
{

	public static void main(final String... args)
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		// invoke the plugin
		ij.command().run( MultiTemplateMatchingCommand.class, true );
	}

}
