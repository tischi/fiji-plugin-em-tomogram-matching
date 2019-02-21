package de.embl.cba.em.review;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.*;
import bdv.viewer.state.SourceState;
import de.embl.cba.bdv.utils.*;
import de.embl.cba.em.Utils;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import de.embl.cba.em.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import static de.embl.cba.bdv.utils.BdvUserInterfaceUtils.addSourcesDisplaySettingsUI;
import static de.embl.cba.bdv.utils.BdvUtils.zoomToSource;
import static de.embl.cba.bdv.utils.BdvViewCaptures.captureView;

public class MatchedTomogramReviewUI < T extends NativeType< T > & RealType< T > > extends JPanel
{
	JFrame frame;
	JComboBox tomogramComboBox;
	private final Bdv bdv;
	private VoxelDimensions tomogramVoxelDimensions;


	public MatchedTomogramReviewUI( Bdv bdv )
	{
		this.bdv = bdv;
	}

	public void showUI()
	{
		addSourceZoomPanel( this );
		addDisplaySettingsUI( this );
		addCaptureViewPanel( this );
		createAndShowUI();
	}

	private void addDisplaySettingsUI( JPanel panel )
	{
		final List< ConverterSetup > converterSetups = bdv.getBdvHandle().getSetupAssignments().getConverterSetups();
		final List< SourceState< ? > > sources = bdv.getBdvHandle().getViewerPanel().getState().getSources();

		ArrayList< Integer > tomogramSourceIndices = new ArrayList<>(  );

		for ( int sourceIndex = 0; sourceIndex < sources.size(); ++sourceIndex )
		{
			final String name = sources.get( sourceIndex ).getSpimSource().getName();

			if ( name.contains( "overview" ) )
			{
				Color color;
				if ( name.contains( "channel 1" ) ) color = Color.GRAY;
				else if ( name.contains( "channel 2" ) ) color = Color.RED;
				else if ( name.contains( "channel 3" ) ) color = Color.GREEN;
				else if ( name.contains( "channel 4" ) ) color = Color.BLUE	;
				else color = Color.MAGENTA;

				converterSetups.get( sourceIndex ).setColor( Utils.asArgbType( color ) );

				final ArrayList< Integer > indices = new ArrayList<>();
				indices.add( sourceIndex );
				addSourcesDisplaySettingsUI( panel, BdvUtils.getName( bdv, sourceIndex ), bdv, indices, color );
			}
			else
			{
				tomogramVoxelDimensions = BdvUtils.getVoxelDimensions( bdv, sourceIndex );
				tomogramSourceIndices.add( sourceIndex );
			}

		}

		// TODO: replace the whole SourceIndices List with Tobias' Group logic?!

		addSourcesDisplaySettingsUI( panel, "Tomograms", bdv, tomogramSourceIndices, Color.GRAY );


	}


	private void addCaptureViewPanel( JPanel panel )
	{
		final JPanel horizontalLayoutPanel = UiUtils.getHorizontalLayoutPanel();

		horizontalLayoutPanel.add( new JLabel( "Resolution [nm]" ) );

		final JTextField resolutionTextField = new JTextField( "" + tomogramVoxelDimensions.dimension( 0 ) );

		horizontalLayoutPanel.add( resolutionTextField );

		final JButton button = new JButton( "Capture current view" );

		button.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				captureView( bdv, Double.parseDouble( resolutionTextField.getText() ) );
			}
		} );


		horizontalLayoutPanel.add( button );

		panel.add( horizontalLayoutPanel );
	}

	private void addSourceZoomPanel( JPanel panel )
	{
		final JPanel horizontalLayoutPanel = UiUtils.getHorizontalLayoutPanel();

		horizontalLayoutPanel.add( new JLabel( "Zoom to" ) );

		tomogramComboBox = new JComboBox();

		updateTomogramComboBoxItems();

		horizontalLayoutPanel.add( tomogramComboBox );

		tomogramComboBox.addActionListener( e -> {
			zoomToSource( bdv, ( String ) tomogramComboBox.getSelectedItem() );
			Utils.updateBdv( bdv,1000 );
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
		frame = new JFrame( "Tomogram viewer" );
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