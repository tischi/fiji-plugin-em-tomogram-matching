package de.embl.cba.em.matching;

import bdv.util.*;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public class MatchedTomogramReviewUI < T extends NativeType< T > & RealType< T > > extends JPanel
{
	JFrame frame;
	JComboBox imageSourcesComboBox;

	private final ArrayList< ImageSource > imageSources;
	private final Bdv bdv;


	public MatchedTomogramReviewUI( ArrayList< ImageSource > imageSources, Bdv bdv )
	{
		this.imageSources = imageSources;
		this.bdv = bdv;
	}

	public void showUI()
	{
		addImageSourceSelectionPanel( this );
		addScreenShotButton( this );
		createAndShowUI();
	}

	private JPanel horizontalLayoutPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout(panel, BoxLayout.LINE_AXIS) );
		panel.setBorder( BorderFactory.createEmptyBorder(0, 10, 10, 10) );
		panel.add( Box.createHorizontalGlue() );
		return panel;
	}


	private void addScreenShotButton( JPanel panel )
	{
		final JPanel horizontalLayoutPanel = horizontalLayoutPanel();

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

	private void addImageSourceSelectionPanel( JPanel panel )
	{
		final JPanel horizontalLayoutPanel = horizontalLayoutPanel();

		horizontalLayoutPanel.add( new JLabel( "Image source" ) );

		imageSourcesComboBox = new JComboBox();

		updateImagesSourcesComboBoxItems();

		horizontalLayoutPanel.add( imageSourcesComboBox );

		imageSourcesComboBox.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				zoomToSource( ( String ) imageSourcesComboBox.getSelectedItem() );
				Utils.updateBdv( bdv,1000 );
			}
		} );

		panel.add( horizontalLayoutPanel );
	}

	private void updateImagesSourcesComboBoxItems()
	{
		imageSourcesComboBox.removeAllItems();

		for( ImageSource source : imageSources )
		{
			if ( ! source.getName().contains( Utils.OVERVIEW_NAME ) )
			{
				imageSourcesComboBox.addItem( source.getName() );
			}
		}

		imageSourcesComboBox.updateUI();
	}

	private void zoomToSource( String sourceName )
	{
		for ( ImageSource imageSource : imageSources )
		{
			if ( imageSource.getName().equals( sourceName ) )
			{
				zoomToInterval( imageSource.getInterval(), 0.75 );
			}
		}
	}

	public void zoomToInterval( FinalInterval interval, double zoomFactor )
	{
		final AffineTransform3D affineTransform3D = getImageZoomTransform( interval, zoomFactor );

		bdv.getBdvHandle().getViewerPanel().setCurrentViewerTransform( affineTransform3D );
	}


	public AffineTransform3D getImageZoomTransform( FinalInterval interval, double zoomFactor )
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