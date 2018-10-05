package de.embl.cba.em.bdv;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BoundedValueDouble;

public class BrightnessUpdateListener implements BoundedValueDouble.UpdateListener
{
 	final private ConverterSetup converterSetup;
 	final private BoundedValueDouble min;
	final private BoundedValueDouble max;

	public BrightnessUpdateListener( BoundedValueDouble min,
									 BoundedValueDouble max,
									 ConverterSetup converterSetup )
	{
		this.min = min;
		this.max = max;
		this.converterSetup = converterSetup;

	}

	@Override
	public void update()
	{
		converterSetup.setDisplayRange( min.getCurrentValue(), max.getCurrentValue() );
	}
}
