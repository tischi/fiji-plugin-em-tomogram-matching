package de.embl.cba.em.ui;

import de.embl.cba.em.match.TemplateMatching;
import de.embl.cba.em.match.TemplateMatchingSettings;
import ij.IJ;
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

@Plugin(type = Command.class, menuPath = "Plugins>Registration>Multi Template Matching" )
public class MultiTemplateMatchingCommand<T extends RealType<T> & NativeType< T > > implements Command
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

	TemplateMatchingSettings settings = new TemplateMatchingSettings();

	@Parameter ( label = "Overview Image" )
	public File overviewImage = settings.overviewImageFile;

	@Parameter ( label = "Tomograms Directory", style = "directory" )
	public File tomogramInputDirectory = settings.templatesInputDirectory;

	@Parameter ( label = "Angle between Overview and Tomograms" )
	public double tomogramAngleDegrees = settings.overviewAngleDegrees;

	@Parameter ( label = "Output Directory", style = "directory" )
	public File outputDirectory = settings.outputDirectory;

	@Parameter ( label = "Matching Resolution [nm]" )
	public double pixelSpacingDuringMatching =
			settings.matchingPixelSpacingNanometer;

	@Parameter ( label = "Confirm Results before Saving" )
	boolean confirmResultsBeforeSaving = true;

	public void run()
	{
		setSettings();

		final TemplateMatching matching = new TemplateMatching( settings );

		matching.run();

		IJ.showMessage( "Tomogram matching finished!" );
	}


	public void setSettings()
	{
		settings.outputDirectory = outputDirectory;
		settings.overviewImageFile = overviewImage;
		settings.templatesInputDirectory = tomogramInputDirectory;
		settings.overviewAngleDegrees = tomogramAngleDegrees;
		settings.showIntermediateResults = confirmResultsBeforeSaving;
		settings.confirmScalingViaUI = false;
		settings.matchingPixelSpacingNanometer = pixelSpacingDuringMatching;
	}


}
