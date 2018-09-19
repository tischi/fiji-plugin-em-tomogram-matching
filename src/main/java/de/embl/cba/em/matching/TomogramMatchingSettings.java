package de.embl.cba.em.matching;

import java.io.File;

public class TomogramMatchingSettings
{
	public File tomogramInputDirectory;
	public File outputDirectory;
	public File overviewImage;
	public double overviewImageCalibrationNanometer = 10.03;
	public double tomogramCalibrationNanometer = 6.275;
	public double tomogramAngleDegrees = 11.5;
	public boolean showIntermediateResults = false;
	public boolean saveResults = true;
}
