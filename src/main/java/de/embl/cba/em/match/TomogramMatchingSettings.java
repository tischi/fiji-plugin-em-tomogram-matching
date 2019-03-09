package de.embl.cba.em.match;

import java.io.File;

public class TomogramMatchingSettings
{
	public File tomogramInputDirectory;
	public File outputDirectory;
	public File overviewImage;
	public double overviewCalibrationNanometer = 10.03;
	public double tomogramCalibrationNanometer = 6.275;
	public double tomogramAngleDegrees = 11.5;
	public boolean showIntermediateResults = false;
	public boolean saveResults = true;
	public boolean saveOverview = true;
	public int fillingValue = 24674;
	public boolean confirmScalingViaUI;
	public int subSamplingDuringMatching;
}
