package de.embl.cba.templatematching.ui;

import de.embl.cba.templatematching.browse.MatchedTemplatesBrowser;
import de.embl.cba.templatematching.browse.MatchedTemplatesBrowsingSettings;
import de.embl.cba.templatematching.match.TemplateMatching;
import de.embl.cba.templatematching.match.TemplateMatchingSettings;
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

	@Parameter ( label = "Templates Directory", style = "directory" )
	public File tomogramInputDirectory = settings.templatesInputDirectory;

	@Parameter ( label = "Templates Regular Expression" )
	public String templatesRegExp = settings.templatesRegExp;

	@Parameter ( label = "Angle between Overview and Templates" )
	public double tomogramAngleDegrees = settings.overviewAngleDegrees;

	@Parameter ( label = "Output Directory", style = "directory" )
	public File outputDirectory = settings.outputDirectory;

	@Parameter ( label = "Pixel Spacing during Matching [nm]" )
	public double pixelSpacingDuringMatching =
			settings.matchingPixelSpacingNanometer;

	@Parameter ( label = "All Images fit into RAM (faster writing)" )
	boolean allTemplatesFitInRAM = false;

	@Parameter ( label = "Run Silent" )
	boolean runSilent = false;

	public void run()
	{
		setSettings();

		final TemplateMatching matching = new TemplateMatching( settings );

		if ( matching.run() )
		{
			MatchedTemplatesBrowsingSettings browsingSettings
					= new MatchedTemplatesBrowsingSettings();
			browsingSettings.inputDirectory = settings.outputDirectory;
			new MatchedTemplatesBrowser( browsingSettings ).run();
		}
		else
		{
			IJ.showMessage( "Template matching finished!" );
		}

	}


	public void setSettings()
	{
		settings.outputDirectory = outputDirectory;
		settings.overviewImageFile = overviewImage;
		settings.templatesInputDirectory = tomogramInputDirectory;
		settings.overviewAngleDegrees = tomogramAngleDegrees;
		settings.showIntermediateResults = ! runSilent;
		settings.confirmScalingViaUI = false;
		settings.matchingPixelSpacingNanometer = pixelSpacingDuringMatching;
		settings.allTemplatesFitInRAM = allTemplatesFitInRAM;
		settings.templatesRegExp = templatesRegExp;
	}


}
