package de.embl.cba.em.matching;

import bdv.BdvUtils;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.*;
import bdv.viewer.state.SourceState;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import ui.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class MatchedTomogramReviewUI < T extends NativeType< T > & RealType< T > > extends JPanel
{
	JFrame frame;
	JComboBox tomogramComboBox;

	private final ArrayList< ImageSource > imageSources;
	private final Bdv bdv;


	public MatchedTomogramReviewUI( ArrayList< ImageSource > imageSources, Bdv bdv )
	{
		this.imageSources = imageSources;
		this.bdv = bdv;
	}

	public void showUI()
	{
		addSourceZoomPanel( this );
		addOverviewImageUI( this );
		addScreenShotButton( this );
		createAndShowUI();
	}

	private void addOverviewImageUI( JPanel panel )
	{
		final List< ConverterSetup > converterSetups = bdv.getBdvHandle().getSetupAssignments().getConverterSetups();
		final List< SourceState< ? > > sources = bdv.getBdvHandle().getViewerPanel().getState().getSources();

		for ( int sourceIndex = 0; sourceIndex < sources.size(); ++sourceIndex )
		{
			final String name = sources.get( sourceIndex ).getSpimSource().getName();

			if ( name.contains( "overview" ) )
			{
				Color color = Color.GRAY;

				if ( name.contains( "channel 2" ) )
				{
					color = Color.GREEN;
				}

				converterSetups.get( sourceIndex ).setColor( Utils.asArgbType( color ) );
				addSourceDisplaySettingsUI( panel, bdv, sourceIndex, color );
			}

		}

	}


	private static void addSourceDisplaySettingsUI( JPanel panel,
													Bdv bdv,
													int sourceIndex,
													Color color )
	{
		int[] buttonDimensions = new int[]{ 50, 30 };

		JPanel channelPanel = new JPanel();
		channelPanel.setLayout( new BoxLayout( channelPanel, BoxLayout.LINE_AXIS ) );
		channelPanel.setBorder( BorderFactory.createEmptyBorder(0,10,0,10) );
		channelPanel.add( Box.createHorizontalGlue() );
		channelPanel.setOpaque( true );
		channelPanel.setBackground( color );

		JLabel jLabel = new JLabel( bdv.getBdvHandle().getViewerPanel().getState().getSources().get( sourceIndex ).getSpimSource().getName() );
		jLabel.setHorizontalAlignment( SwingConstants.CENTER );

		channelPanel.add( jLabel );
		channelPanel.add( BdvUtils.createColorButton( channelPanel, buttonDimensions, bdv, sourceIndex ) );
		channelPanel.add( BdvUtils.createBrightnessButton( buttonDimensions,  bdv, sourceIndex ) );
		channelPanel.add( BdvUtils.createToggleButton( buttonDimensions,  bdv, sourceIndex ) );

		panel.add( channelPanel );

	}


	private void addScreenShotButton( JPanel panel )
	{
		final JPanel horizontalLayoutPanel = UiUtils.getHorizontalLayoutPanel();

		final JButton button = new JButton( "Take screenshot" );

		button.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				takeScreenShot( bdv );
			}
		} );

		horizontalLayoutPanel.add( button );

		panel.add( horizontalLayoutPanel );
	}

	public void takeScreenShot( Bdv bdv )
	{

		for ( ImageSource imageSource : imageSources )
		{
			final AffineTransform3D affineTransform3D = imageSource.getSpimData().getViewRegistrations().getViewRegistration( 0, 0 ).getModel();
			final RandomAccessibleInterval< T > rai = Utils.getRandomAccessibleInterval( imageSource.getSpimData() );
			final FinalInterval imageInterval = imageSource.getInterval();

			final FinalRealInterval viewerInterval = Utils.getCurrentViewerInterval( bdv );

			final boolean intersecting = Utils.intersecting( imageInterval, viewerInterval );


			if ( intersecting )
			{
				final IntervalView< T > screenshot = Views.interval( rai, Utils.asInterval( viewerInterval ) );
				//ImageJFunctions.show( screenshot );
			}
		}

	}

	private void addSourceZoomPanel( JPanel panel )
	{
		final JPanel horizontalLayoutPanel = UiUtils.getHorizontalLayoutPanel();

		horizontalLayoutPanel.add( new JLabel( "Zoom to" ) );

		tomogramComboBox = new JComboBox();

		updateTomogramComboBoxItems();

		horizontalLayoutPanel.add( tomogramComboBox );

		tomogramComboBox.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				BdvUtils.zoomToSource( bdv, ( String ) tomogramComboBox.getSelectedItem() );
				Utils.updateBdv( bdv,1000 );
			}
		} );

		panel.add( horizontalLayoutPanel );
	}

	private void updateTomogramComboBoxItems()
	{
		tomogramComboBox.removeAllItems();

		for ( SourceState< ? > source : bdv.getBdvHandle().getViewerPanel().getState().getSources() )
		{
			final String name = source.getSpimSource().getName();

			if ( ! name.contains( Utils.OVERVIEW_NAME ) )
			{
				tomogramComboBox.addItem( name );
			}
		}

		tomogramComboBox.updateUI();
	}


	/**
	 * Create the GUI and show it.  For thread safety,
	 * this method should be invoked from the
	 * event-dispatching thread.
	 */
	private void createAndShowUI( )
	{
		//Create and set up the window.
		frame = new JFrame( "Multiposition viewer" );
		frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );

		//Create and set up the content pane.
		setOpaque( true ); //content panes must be opaque
		setLayout( new BoxLayout(this, BoxLayout.Y_AXIS ) );

		frame.setContentPane( this );

		//Display the window.
		frame.pack();
		frame.setVisible( true );
	}

	private void refreshUI()
	{
		this.revalidate();
		this.repaint();
		frame.pack();
	}




}