package de.embl.cba.em.bdv;

import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.SliderPanelDouble;
import bdv.util.Bdv;
import bdv.util.BoundedValueDouble;
import bdv.viewer.Interpolation;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.state.SourceState;
import de.embl.cba.em.Utils;
import de.embl.cba.em.imageprocessing.Transforms;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.Duplicator;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.*;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import static de.embl.cba.em.imageprocessing.Transforms.createBoundingIntervalAfterTransformation;

public class BdvUtils
{
	public static int getSourceId( Bdv bdv, String sourceName )
	{
		final List< SourceState< ? > > sources = bdv.getBdvHandle().getViewerPanel().getState().getSources();

		int sourceId = -1;
		for ( int i = 0; i < sources.size(); ++i )
		{
			if ( sources.get( i ).getSpimSource().getName().equals( sourceName ) )
			{
				sourceId = i;
			}
		}
		return sourceId;
	}

	public static String getName( Bdv bdv, int sourceId )
	{
		return bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceId ).getSpimSource().getName();
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
		bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceId ).getSpimSource().getSourceTransform( 0, 0, sourceTransform );
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

		for ( int d = 0; d < 3; ++d )
		{
			shiftToImage[ d ] = -( interval.min( d ) + interval.dimension( d ) / 2.0 );
		}

		affineTransform3D.translate( shiftToImage );

		int[] bdvWindowDimensions = new int[ 2 ];
		bdvWindowDimensions[ 0 ] = bdv.getBdvHandle().getViewerPanel().getWidth();
		bdvWindowDimensions[ 1 ] = bdv.getBdvHandle().getViewerPanel().getHeight();

		affineTransform3D.scale( zoomFactor * bdvWindowDimensions[ 0 ] / interval.dimension( 0 ) );

		double[] shiftToBdvWindowCenter = new double[ 3 ];

		for ( int d = 0; d < 2; ++d )
		{
			shiftToBdvWindowCenter[ d ] += bdvWindowDimensions[ d ] / 2.0;
		}

		affineTransform3D.translate( shiftToBdvWindowCenter );

		return affineTransform3D;
	}

	public static JButton createColorButton( JPanel panel,
											 int[] buttonDimensions,
											 Bdv bdv,
											 ArrayList< Integer > sourceIndices )
	{
		JButton colorButton = new JButton( "C" );
		colorButton.setPreferredSize( new Dimension( buttonDimensions[ 0 ], buttonDimensions[ 1 ] ) );

		colorButton.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				Color color = JColorChooser.showDialog( null, "", null );

				for ( int i : sourceIndices )
				{
					bdv.getBdvHandle().getSetupAssignments().getConverterSetups().get( i ).setColor( Utils.asArgbType( color ) );
				}

				panel.setBackground( color );
			}
		} );


		return colorButton;
	}

	public static JButton createBrightnessButton( int[] buttonDimensions,
												  String name, Bdv bdv,
												  ArrayList< Integer > sourceIndices )
	{
		JButton button = new JButton( "B" );
		button.setPreferredSize( new Dimension( buttonDimensions[ 0 ], buttonDimensions[ 1 ] ) );

		button.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				final ArrayList< ConverterSetup > converterSetups = new ArrayList<>();

				for ( int i : sourceIndices )
				{
					converterSetups.add( bdv.getBdvHandle().getSetupAssignments().getConverterSetups().get( i ) );
				}

				showBrightnessDialog( name, converterSetups );
			}
		} );

		return button;
	}


	public static void showBrightnessDialog( String name, ArrayList< ConverterSetup > converterSetups )
	{

		JFrame frame = new JFrame( name );

		frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );

		final BoundedValueDouble min = new BoundedValueDouble( 0, 65535, ( int ) converterSetups.get(0).getDisplayRangeMin() );
		final BoundedValueDouble max = new BoundedValueDouble( 0, 65535, ( int ) converterSetups.get(0).getDisplayRangeMax() );

		JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout( panel, BoxLayout.PAGE_AXIS ) );
		final SliderPanelDouble minSlider = new SliderPanelDouble( "Min", min, 1 );
		final SliderPanelDouble maxSlider = new SliderPanelDouble( "Max", max, 1 );

		final BrightnessUpdateListener brightnessUpdateListener =
				new BrightnessUpdateListener( min, max, converterSetups );

		min.setUpdateListener( brightnessUpdateListener );
		max.setUpdateListener( brightnessUpdateListener );

		panel.add( minSlider );
		panel.add( maxSlider );

		frame.setContentPane( panel );

		//Display the window.
		frame.setBounds( MouseInfo.getPointerInfo().getLocation().x,
				MouseInfo.getPointerInfo().getLocation().y,
				120, 10);
		frame.pack();
		frame.setVisible( true );

	}

	public static void showGenericBrightnessDialog( ConverterSetup converterSetup )
	{
		GenericDialog gd = new GenericDialog( "LUT max value" );
		gd.addNumericField( "LUT max value: ", converterSetup.getDisplayRangeMax(), 0 );
		gd.showDialog();
		converterSetup.setDisplayRange( converterSetup.getDisplayRangeMin(), ( int ) gd.getNextNumber() );
	}

	public static JButton createToggleButton( int[] buttonDimensions,
											  Bdv bdv,
											  ArrayList< Integer > sourceIndices )
	{
		JButton button = new JButton( "T" );
		button.setPreferredSize( new Dimension( buttonDimensions[ 0 ], buttonDimensions[ 1 ] ) );

		button.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				final VisibilityAndGrouping visibilityAndGrouping = bdv.getBdvHandle().getViewerPanel().getVisibilityAndGrouping();
				for ( int i : sourceIndices )
				{
					visibilityAndGrouping.setSourceActive( i, !visibilityAndGrouping.isSourceActive( i ) );
				}
			}
		} );

		return button;
	}

	public static < T extends RealType< T > & NativeType< T > > void captureScreenshot( Bdv bdv )
	{

	}

	public static < T extends RealType< T > & NativeType< T > > void captureCurrentView( Bdv bdv, double resolution )
	{

		double[] scalingFactors = new double[ 3 ];
		scalingFactors[ 0 ] = 1 / resolution;
		scalingFactors[ 1 ] = 1 / resolution;
		scalingFactors[ 2 ] = 1.0;

		int n = bdv.getBdvHandle().getViewerPanel().getState().getSources().size();

		final ArrayList< RandomAccessibleInterval< T > > randomAccessibleIntervals = new ArrayList<>();

		for ( int sourceIndex = 0; sourceIndex < n; ++sourceIndex )
		{
			final FinalInterval interval = getInterval( bdv, sourceIndex );

			final FinalRealInterval viewerRealInterval = Utils.getCurrentViewerInterval( bdv );

			final boolean intersecting = Utils.intersecting( interval, viewerRealInterval );

			if ( intersecting )
			{
				RealRandomAccessible< T > rra = ( RealRandomAccessible ) getRealRandomAccessible( bdv, sourceIndex );
				final AffineTransform3D sourceTransform = getSourceTransform( bdv, sourceIndex );
				rra = RealViews.transform( rra, sourceTransform );

				Scale scale = new Scale( scalingFactors );
				RealRandomAccessible< T > rescaledRRA = RealViews.transform( rra, scale );
				final RandomAccessible< T > rastered = Views.raster( rescaledRRA );

				final FinalInterval scaledInterval = Transforms.scaleIntervalInXY( Utils.asInterval( viewerRealInterval ), scale );

				final RandomAccessibleInterval< T > cropped = Views.interval( rastered, scaledInterval );

				randomAccessibleIntervals.add( cropped  );
			}

		}

		showAsIJ1MultiColorImage( bdv, resolution, randomAccessibleIntervals );

	}

	public static < T extends RealType< T > & NativeType< T > > void showAsIJ1MultiColorImage( Bdv bdv, double resolution, ArrayList< RandomAccessibleInterval< T > > randomAccessibleIntervals )
	{
		final ImagePlus imp = ImageJFunctions.wrap( Views.stack( randomAccessibleIntervals ), "capture" );
		final ImagePlus dup = new Duplicator().run( imp ); // otherwise it is virtual and cannot be modified
		IJ.run( dup, "Subtract...", "value=32768 slice");
		VoxelDimensions voxelDimensions = getVoxelDimensions( bdv, 0 );
		IJ.run( dup, "Properties...", "channels="+randomAccessibleIntervals.size()+" slices=1 frames=1 unit="+voxelDimensions.unit()+" pixel_width="+resolution+" pixel_height="+resolution+" voxel_depth=1.0");
		final CompositeImage compositeImage = new CompositeImage( dup );
		for ( int channel = 1; channel <= compositeImage.getNChannels(); ++channel )
		{
			compositeImage.setC( channel );
			switch ( channel )
			{
				case 1: // tomogram
					compositeImage.setChannelLut( compositeImage.createLutFromColor( Color.GRAY ) );
					compositeImage.setDisplayRange( 0, 1000 ); // TODO: get from bdv
					break;
				case 2: compositeImage.setChannelLut( compositeImage.createLutFromColor( Color.GRAY ) ); break;
				case 3: compositeImage.setChannelLut( compositeImage.createLutFromColor( Color.RED ) ); break;
				case 4: compositeImage.setChannelLut( compositeImage.createLutFromColor( Color.GREEN ) ); break;
				default: compositeImage.setChannelLut( compositeImage.createLutFromColor( Color.BLUE ) ); break;
			}
		}
		compositeImage.show();
		compositeImage.setTitle( "capture" );
		IJ.run(compositeImage, "Make Composite", "");
	}
}
