package bdv;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.Bdv;
import bdv.viewer.Interpolation;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.state.SourceState;
import de.embl.cba.em.matching.Utils;
import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import static de.embl.cba.em.matching.Transforms.createBoundingIntervalAfterTransformation;

public class BdvUtils
{
	public static int getSourceId( Bdv bdv, String sourceName )
	{
		final List< SourceState< ? > > sources = bdv.getBdvHandle().getViewerPanel().getState().getSources();

		int sourceId = -1;
		for ( int i = 0;  i < sources.size(); ++i )
		{
			if ( sources.get(i ).getSpimSource().getName().equals( sourceName ) )
			{
				sourceId = i;
			}
		}
		return sourceId;
	}

	public static void zoomToSource( Bdv bdv, String sourceName )
	{
		zoomToSource( bdv, getSourceId( bdv, sourceName ) );
	}

	public static void zoomToSource( Bdv bdv, int sourceId )
	{
		final FinalInterval interval = getInterval( bdv, sourceId );

		zoomToInterval( bdv, interval, 1.0 );
	}

	public static VoxelDimensions getVoxelDimensions( Bdv bdv, int sourceId )
	{
		return bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceId ).getSpimSource().getVoxelDimensions();
	}

	public static FinalInterval getInterval( Bdv bdv, int sourceId )
	{
		final AffineTransform3D sourceTransform = getSourceTransform( bdv, sourceId );
		final RandomAccessibleInterval< ? > rai = getRandomAccessibleInterval( bdv, sourceId );
		return createBoundingIntervalAfterTransformation( rai, sourceTransform );
	}

	public static AffineTransform3D getSourceTransform( Bdv bdv, int sourceId )
	{
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceId ).getSpimSource().getSourceTransform( 0, 0 , sourceTransform );
		return sourceTransform;
	}

	public static RandomAccessibleInterval< ? > getRandomAccessibleInterval( Bdv bdv, int sourceId )
	{
		return bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceId ).getSpimSource().getSource( 0, 0 );
	}

	public static RealRandomAccessible< ? > getRealRandomAccessible( Bdv bdv, int sourceId )
	{
		return bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceId ).getSpimSource().getInterpolatedSource( 0, 0, Interpolation.NLINEAR );
	}

	public static void zoomToInterval( Bdv bdv, FinalInterval interval, double zoomFactor )
	{
		final AffineTransform3D affineTransform3D = getImageZoomTransform( bdv, interval, zoomFactor );

		bdv.getBdvHandle().getViewerPanel().setCurrentViewerTransform( affineTransform3D );
	}

	public static AffineTransform3D getImageZoomTransform( Bdv bdv, FinalInterval interval, double zoomFactor )
	{

		final AffineTransform3D affineTransform3D = new AffineTransform3D();

		double[] shiftToImage = new double[ 3 ];

		for( int d = 0; d < 3; ++d )
		{
			shiftToImage[ d ] = - ( interval.min( d ) + interval.dimension( d ) / 2.0 ) ;
		}

		affineTransform3D.translate( shiftToImage );

		int[] bdvWindowDimensions = new int[ 2 ];
		bdvWindowDimensions[ 0 ] = bdv.getBdvHandle().getViewerPanel().getWidth();
		bdvWindowDimensions[ 1 ] = bdv.getBdvHandle().getViewerPanel().getHeight();

		affineTransform3D.scale(  zoomFactor * bdvWindowDimensions[ 0 ] / interval.dimension( 0 ) );

		double[] shiftToBdvWindowCenter = new double[ 3 ];

		for( int d = 0; d < 2; ++d )
		{
			shiftToBdvWindowCenter[ d ] += bdvWindowDimensions[ d ] / 2.0;
		}

		affineTransform3D.translate( shiftToBdvWindowCenter );

		return affineTransform3D;
	}

	public static JButton createColorButton( JPanel panel,
											 int[] buttonDimensions,
											 Bdv bdv,
											 int sourceIndex )
	{
		JButton colorButton = new JButton( "C" );
		colorButton.setPreferredSize( new Dimension( buttonDimensions[0], buttonDimensions[1] ) );

		colorButton.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				Color color = JColorChooser.showDialog( null, "", null );
				bdv.getBdvHandle().getSetupAssignments().getConverterSetups().get( sourceIndex ).setColor( Utils.asArgbType( color ) );
				panel.setBackground( color );
			}
		} );



		return colorButton;
	}

	public static JButton createBrightnessButton( int[] buttonDimensions,
												  Bdv bdv,
												  int sourceIndex )
	{
		JButton button = new JButton( "B" );
		button.setPreferredSize( new Dimension( buttonDimensions[0], buttonDimensions[1] ) );

		button.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				final ConverterSetup converterSetup = bdv.getBdvHandle().getSetupAssignments().getConverterSetups().get( sourceIndex );

				GenericDialog gd = new GenericDialog( "LUT max value" );
				gd.addNumericField( "LUT max value: ", converterSetup.getDisplayRangeMax(), 0 );
				gd.showDialog();

				if ( gd.wasCanceled() ) return;


				converterSetup.setDisplayRange( converterSetup.getDisplayRangeMin(), ( int ) gd.getNextNumber() );
			}
		} );

		return button;
	}

	public static JButton createToggleButton( int[] buttonDimensions,
											  Bdv bdv,
											  int sourceIndex )
	{
		JButton button = new JButton( "T" );
		button.setPreferredSize( new Dimension( buttonDimensions[0], buttonDimensions[1] ) );

		button.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				final VisibilityAndGrouping visibilityAndGrouping = bdv.getBdvHandle().getViewerPanel().getVisibilityAndGrouping();
				visibilityAndGrouping.setSourceActive( sourceIndex , ! visibilityAndGrouping.isSourceActive( sourceIndex ) );
			}
		} );

		return button;
	}
}
