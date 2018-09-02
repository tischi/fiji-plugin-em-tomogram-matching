package de.embl.cba.em.matching;

import bdv.util.BdvSource;
import bdv.util.BdvStackSource;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;

import java.io.File;

public class ImageSource
{
	private final File file;
	private final BdvSource bdvSource;
	private final SpimData spimData;

	public ImageSource( File file, BdvSource bdvSource, SpimData spimData )
	{
		this.file = file;
		this.bdvSource = bdvSource;
		this.spimData = spimData;
	}

	public String getName()
	{
		return file.getName();
	}

	public File getFile()
	{
		return file;
	}

	public FinalInterval getInterval()
	{
		spimData.getViewRegistrations().getViewRegistration( 0,0 ).getModel();
		final ViewSetup viewSetup = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 );
		final Dimensions size = viewSetup.getSize();
		final double[] location = viewSetup.getTile().getLocation();

		RandomAccessibleInterval< ? > image = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 ).getImage( 0 );

		return null;
	}


}
