package de.embl.cba.em.matching;

import bdv.util.*;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import java.io.File;
import java.util.ArrayList;

public class MatchedTomogramReview < T extends RealType< T > & NativeType< T > >
{
	public static final ARGBType OVERVIEW_EM_COLOR = new ARGBType( ARGBType.rgba( 125, 125, 125, 255 ) );
	private final MatchedTomogramReviewSettings settings;
	private ArrayList< File > inputFiles;
	private Bdv bdv;
	private ArrayList< ImageSource > imageSources;
	private double displayRangeFactorMin = 0.9;
	private double displayRangeFactorMax = 1 + ( 1 - displayRangeFactorMin );


	public MatchedTomogramReview( MatchedTomogramReviewSettings settings )
	{
		this.settings = settings;
		this.imageSources = new ArrayList<>( );
	}

	public void run()
	{
		fetchImageSources();
		showImageSources();
		showUI();
	}

	private void showUI()
	{
		final MatchedTomogramReviewUI ui = new MatchedTomogramReviewUI( imageSources, bdv );
		ui.showUI();
	}

	private void showImageSources()
	{
		for ( File file : inputFiles )
		{
			showWithBdv( file );
		}
	}

	private void showWithBdv( File file )
	{
		final SpimData spimData = openSpimData( file );

		setNames( spimData, file.getName() );

		final BdvStackSource< ? > bdvStackSource = BdvFunctions.show(
				spimData,
				BdvOptions.options()
						.addTo( bdv )
						.preferredSize( 800, 800 )
						.transformEventHandlerFactory( new BehaviourTransformEventHandler3DWithoutRotation.BehaviourTransformEventHandler3DFactory() )
				).get( 0 );

		setDisplayRange( bdvStackSource );

		setColor( file, bdvStackSource );

		bdv = bdvStackSource.getBdvHandle();

		imageSources.add( new ImageSource( file, bdvStackSource, spimData ) );

		Utils.updateBdv( bdv,1000 );

	}

	private void setNames( SpimData spimData, String name )
	{
		int n = spimData.getSequenceDescription().getViewSetupsOrdered().size();

		for ( int i = 0; i < n; ++i )
		{
			// TODO: does not work, maybe because SpimData is inherently disk resident?
			// spimData.getSequenceDescription().getViewSetupsOrdered().get( i ).getChannel().setName( name + "-channel" + i );
		}

	}

	private void setColor( File file, BdvStackSource< ? > bdvStackSource )
	{
		if ( file.getName().contains( "overview" ) )
		{
			bdvStackSource.setColor( OVERVIEW_EM_COLOR );
		}
	}

	private void setDisplayRange( BdvStackSource< ? > bdvStackSource )
	{
		final int numMipmapLevels = bdvStackSource.getSources().get( 0 ).getSpimSource().getNumMipmapLevels();

		final RandomAccessibleInterval< T > lowResSource = (RandomAccessibleInterval) bdvStackSource.getSources().get( 0 ).getSpimSource().getSource( 0, numMipmapLevels - 1  );

		final Cursor< T > cursor = Views.iterable( lowResSource ).cursor();

		double min = Double.MAX_VALUE;
		double max = - Double.MAX_VALUE;
		double value;

		while ( cursor.hasNext() )
		{
			value = cursor.next().getRealDouble();
			if ( value < min ) min = value;
			if ( value > max ) max = value;
		}

		min *= displayRangeFactorMin;
		max *= displayRangeFactorMax;

		bdvStackSource.setDisplayRange( min, max );
	}

	private SpimData openSpimData( File file )
	{
		try
		{
			final SpimData spimData;
			spimData = new XmlIoSpimData().load( file.getAbsolutePath() );
			return spimData;
		}
		catch ( SpimDataException e )
		{
			e.printStackTrace();
			return null;
		}
	}

	private void fetchImageSources()
	{
		File[] files = settings.inputDirectory.listFiles();

		inputFiles = new ArrayList<>();

		for ( File file : files )
		{
			if ( isValid( file ) )
			{
				inputFiles.add( file );
			}
		}
	}

	private boolean isValid( File file )
	{
		if ( file.getName().endsWith( ".xml" ) )
		{
			return true;
		}
		else
		{
			return false;
		}
	}
}
