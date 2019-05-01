package headless;

import de.embl.cba.templatematching.command.TemplatesMatchingCommand;

import java.io.File;

public class RunTemplatesMatching
{
	public static void main( String[] args )
	{
		final TemplatesMatchingCommand command = new TemplatesMatchingCommand();
		command.overviewImage = new File("/Users/tischer/Documents/fiji-plugin-em-tomogram-matching/src/test/resources/input-data-00/RotatedOverview.tif");
		command.inputDirectory = new File("/Users/tischer/Documents/fiji-plugin-em-tomogram-matching/src/test/resources/input-data-00");
		command.outputDirectory = new File("/Users/tischer/Documents/fiji-plugin-em-tomogram-matching/src/test/resources/matched-data-00");

		command.templatesRegExp = ".*m.tif";
		command.pixelSpacingDuringMatching = 0;
		command.tomogramAngleDegrees = 0.0;
		command.saveResultsAsBdv = true;
		command.isHierarchicalMatching = true;
		command.runSilent = true;
		command.run();
	}

}
