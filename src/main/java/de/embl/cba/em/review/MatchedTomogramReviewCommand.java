package de.embl.cba.em.review;

import ij.ImagePlus;
import net.imagej.DatasetService;
import net.imagej.ops.OpService;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>Registration>Tomogram browsing" )
public class MatchedTomogramReviewCommand<T extends RealType<T> & NativeType< T > > implements Command
{
	@Parameter
	public UIService uiService;

	@Parameter
	public DatasetService datasetService;

	@Parameter
	public LogService logService;

	@Parameter
	public OpService opService;

	@Parameter
	public StatusService statusService;

	@Parameter( required = false )
	public ImagePlus imagePlus;

	MatchedTomogramReviewSettings settings = new MatchedTomogramReviewSettings();

	@Parameter ( style = "directory" )
	public File inputDirectory = settings.inputDirectory;

	public void run()
	{
		setSettingsFromUI();

		final MatchedTomogramReview review = new MatchedTomogramReview( settings );

		review.run();

	}



	public void setSettingsFromUI()
	{
		settings.inputDirectory = inputDirectory;
	}


}
