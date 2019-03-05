import de.embl.cba.em.ui.MatchedTomogramReviewCommand;
import net.imagej.ImageJ;

public class RunTomogramReviewCommand
{

	public static void main(final String... args) throws Exception
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();

		// invoke the plugin
		ij.command().run( MatchedTomogramReviewCommand.class, true );
	}

}
