import de.embl.cba.templatematching.ui.TemplatesBrowserCommand;
import net.imagej.ImageJ;

public class RunMatchedTemplatesBrowserCommand
{

	public static void main(final String... args)
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		// invoke the plugin
		ij.command().run( TemplatesBrowserCommand.class, true );
	}

}
