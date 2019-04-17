package de.embl.cba.templatematching.ui;

import de.embl.cba.templatematching.browse.MatchedTemplatesBrowser;
import de.embl.cba.templatematching.browse.TemplatesBrowsingSettings;
import de.embl.cba.templatematching.match.TemplatesMatching;
import de.embl.cba.templatematching.match.TemplatesMatchingSettings;
import ij.IJ;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>Registration>Multi Template Matching" )
public class TemplatesMatchingCommand<T extends RealType<T> & NativeType< T > > implements Command
{
	TemplatesMatchingSettings settings = new TemplatesMatchingSettings();

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

	@Parameter ( label = "Hierarchical matching" )
	boolean isHierarchicalMatching = false;

//	@Parameter ( label = "All Images fit into RAM (faster writing)" )
	boolean allTemplatesFitInRAM = false;

	@Parameter ( label = "Run Silent" )
	boolean runSilent = false;

	@Parameter ( label = "Save Results in BigDataViewer Format" )
	boolean saveResultsAsBdv = settings.saveResultsAsBdv;

	public void run()
	{
		setSettings();

		final TemplatesMatching matching = new TemplatesMatching( settings );

		if ( matching.run() )
		{
			TemplatesBrowsingSettings browsingSettings
					= new TemplatesBrowsingSettings();
			browsingSettings.inputDirectory = settings.outputDirectory;

			if ( saveResultsAsBdv )
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
		settings.templatesRegExp = templatesRegExp;
		settings.isHierarchicalMatching = isHierarchicalMatching;
		settings.saveResultsAsBdv = saveResultsAsBdv;
	}


}
