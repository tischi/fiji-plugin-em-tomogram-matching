package de.embl.cba.em.matching;

import bdv.util.*;
import bdv.util.volatiles.VolatileViews;
import de.embl.cba.em.matching.ImageSource;
import net.imglib2.FinalInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public class MatchedTomogramReviewUI < T extends NativeType< T > & RealType< T > > extends JPanel implements ActionListener
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
		createAndShowUI( );
	}


	private JPanel horizontalLayoutPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout(panel, BoxLayout.LINE_AXIS) );
		panel.setBorder( BorderFactory.createEmptyBorder(0, 10, 10, 10) );
		panel.add( Box.createHorizontalGlue() );
		return panel;
	}

	private void addImageSourceSelectionPanel( JPanel panel )
	{
		final JPanel horizontalLayoutPanel = horizontalLayoutPanel();

		horizontalLayoutPanel.add( new JLabel( "Image source" ) );

		imageSourcesComboBox = new JComboBox();

		updateImagesSourcesComboBoxItems();

		horizontalLayoutPanel.add( imageSourcesComboBox );

		panel.add( horizontalLayoutPanel );
	}

	private void updateImagesSourcesComboBoxItems()
	{
		imageSourcesComboBox.removeAllItems();

		for( ImageSource source : imageSources )
		{
			imageSourcesComboBox.addItem( source.getName() );
		}

		imageSourcesComboBox.updateUI();
	}

	public void updateBdv( long msecs )
	{
		(new Thread(new Runnable(){
			public void run(){
				try
				{
					Thread.sleep( msecs );
				}
				catch ( InterruptedException e )
				{
					e.printStackTrace();
				}

				bdv.getBdvHandle().getViewerPanel().requestRepaint();
			}
		})).start();
	}

	@Override
	public void actionPerformed( ActionEvent e )
	{
		if ( e.getSource() == imageSourcesComboBox )
		{
			zoomToSource( ( String ) imageSourcesComboBox.getSelectedItem() );
			updateBdv( 100 );
		}

	}

	private void zoomToSource( String sourceName )
	{
		for ( ImageSource imageSource : imageSources )
		{
			if ( imageSource.getName().equals( sourceName ) )
			{
				imageSource.getInterval();

			}
		}
	}

	public void zoomToInterval( FinalInterval interval )
	{
		final AffineTransform3D affineTransform3D = getImageZoomTransform( interval );

		bdv.getBdvHandle().getViewerPanel().setCurrentViewerTransform( affineTransform3D );

	}


	public AffineTransform3D getImageZoomTransform( FinalInterval interval )
	{

		final AffineTransform3D affineTransform3D = new AffineTransform3D();

		double[] shiftToImage = new double[ 3 ];

		for( int d = 0; d < 2; ++d )
		{
			shiftToImage[ d ] = - ( interval.min( d ) + interval.dimension( d ) / 2.0 ) ;
		}

		affineTransform3D.translate( shiftToImage );

		int[] bdvWindowDimensions = new int[ 2 ];
		bdvWindowDimensions[ 0 ] = bdv.getBdvHandle().getViewerPanel().getWidth();
		bdvWindowDimensions[ 1 ] = bdv.getBdvHandle().getViewerPanel().getHeight();

		affineTransform3D.scale(  1.05 * bdvWindowDimensions[ 0 ] / interval.dimension( 0 ) );

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