package de.embl.cba.em.matching;

import bdv.util.*;
import ij.ImagePlus;
import ij.io.FileSaver;
import net.imagej.DatasetService;
import net.imagej.ops.OpService;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.util.ArrayList;

@Plugin(type = Command.class, menuPath = "Plugins>Registration>EMBL>Compute tomogram matching" )
public class TomogramMatchingCommand<T extends RealType<T> & NativeType< T > > implements Command
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

	TomogramMatchingSettings settings = new TomogramMatchingSettings();

	@Parameter
	public File overviewImage = settings.overviewImage;

	@Parameter ( style = "directory" )
	public File tomogramInputDirectory = settings.tomogramInputDirectory;

	@Parameter
	public double tomogramAngleDegrees = settings.tomogramAngleDegrees;

	@Parameter ( style = "directory" )
	public File outputDirectory = settings.outputDirectory;

	@Parameter
	public boolean showIntermediateResults = settings.showIntermediateResults;

	public void run()
	{
		setSettingsFromUI();

		final TomogramMatching matching = new TomogramMatching( settings, opService );

		matching.run();

		Utils.log( "Done!" );

	}


	public void setSettingsFromUI()
	{
		settings.outputDirectory = outputDirectory;
		settings.overviewImage = overviewImage;
		settings.tomogramInputDirectory = tomogramInputDirectory;
		settings.tomogramAngleDegrees = tomogramAngleDegrees;
		settings.showIntermediateResults = showIntermediateResults;
	}


}
