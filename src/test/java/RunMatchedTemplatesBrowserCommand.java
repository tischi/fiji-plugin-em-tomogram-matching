import de.embl.cba.em.ui.MatchedTemplateBrowserCommand;
import net.imagej.ImageJ;

public class RunMatchedTemplatesBrowserCommand
{

	public static void main(final String... args) throws Exception
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		// invoke the plugin
		ij.command().run( MatchedTemplateBrowserCommand.class, true );
	}

}
