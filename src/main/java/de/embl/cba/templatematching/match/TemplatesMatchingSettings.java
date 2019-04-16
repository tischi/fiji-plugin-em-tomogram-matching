package de.embl.cba.templatematching.match;

import java.io.File;

public class TemplatesMatchingSettings
{
	public File templatesInputDirectory;
	public File outputDirectory;
	public File overviewImageFile;
	public double overviewAngleDegrees = 11.5;
	public boolean showIntermediateResults = false;
	public boolean confirmScalingViaUI;
	public double matchingPixelSpacingNanometer;
	public String templatesRegExp = ".*_hm.rec";
	public boolean isHierarchicalMatching;
}