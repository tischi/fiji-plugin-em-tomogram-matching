package de.embl.cba.templatematching.match;

import java.io.File;

public class TemplateMatchingSettings
{
	public File templatesInputDirectory;
	public File outputDirectory;
	public File overviewImageFile;
	public double overviewCalibrationNanometer = 10.03;
	public double overviewAngleDegrees = 11.5;
	public boolean showIntermediateResults = false;
	public boolean confirmScalingViaUI;
	public double matchingPixelSpacingNanometer;
	public boolean allTemplatesFitInRAM;
}
