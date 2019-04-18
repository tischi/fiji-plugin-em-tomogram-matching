package de.embl.cba.templatematching.browse;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.*;
import bdv.viewer.state.SourceState;
import de.embl.cba.bdv.utils.*;
import de.embl.cba.templatematching.Utils;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import de.embl.cba.templatematching.UiUtils;
import net.imglib2.view.SubsampleIntervalView;
import net.imglib2.view.Views;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static de.embl.cba.bdv.utils.BdvUserInterfaceUtils.addSourcesDisplaySettingsUI;
import static de.embl.cba.bdv.utils.BdvUtils.zoomToSource;
import static de.embl.cba.bdv.utils.BdvViewCaptures.captureView;
import static de.embl.cba.transforms.utils.Transforms.getCenter;

public class MatchedTemplatesBrowserUI< T extends NativeType< T > & RealType< T > > extends JPanel
{
	JFrame frame;
	JComboBox tomogramComboBox;
	private final Bdv bdv;
	private ArrayList< VoxelDimensions > matchedTemplateVoxelDimensions;

	public MatchedTemplatesBrowserUI( Bdv bdv )
	{
		this.bdv = bdv;
		matchedTemplateVoxelDimensions = new ArrayList<>(  );
	}

	public void showUI()
	{
		addSourceZoomPanel( this );
		// zoomToSource( bdv, ( String ) tomogramComboBox.getSelectedItem() );
		addDisplaySettingsUI( this );
		addCaptureViewPanel( this );
		createAndShowUI();
	}

	private void addDisplaySettingsUI( JPanel panel )
	{
		final List< ConverterSetup > converterSetups =
				bdv.getBdvHandle().getSetupAssignments().getConverterSetups();
		final List< SourceState< ? > > sources =
				bdv.getBdvHandle().getViewerPanel().getState().getSources();

		// TODO: how to make this more generic?
		ArrayList< Integer > lowMagTomogramSourceIndices = new ArrayList<>(  );
		ArrayList< Integer > highMagTomogramSourceIndices = new ArrayList<>(  );

		for ( int sourceIndex = 0; sourceIndex < sources.size(); ++sourceIndex )
		{
			final String name = sources.get( sourceIndex ).getSpimSource().getName();
			final int numMipmapLevels = sources.get( sourceIndex ).getSpimSource().getNumMipmapLevels();

			if ( name.contains( "overview" ) )
			{
				Color color = getOverviewColor( name );
				converterSetups.get( sourceIndex ).setColor( Utils.asArgbType( color ) );

				final ArrayList< Integer > indices = new ArrayList<>();
				indices.add( sourceIndex );
				addSourcesDisplaySettingsUI( panel,
						BdvUtils.getName( bdv, sourceIndex ), bdv, indices, color );

				autoContrast( converterSetups, sourceIndex, numMipmapLevels, 0.5 );
			}
			else if ( name.contains( "hm" ) )
			{
				matchedTemplateVoxelDimensions.add( BdvUtils.getVoxelDimensions( bdv, sourceIndex ) );
				highMagTomogramSourceIndices.add( sourceIndex );

				autoContrast( converterSetups, sourceIndex, numMipmapLevels, 0.5 );
			}
			else if ( name.contains( "lm" ) )
			{
				matchedTemplateVoxelDimensions.add( BdvUtils.getVoxelDimensions( bdv, sourceIndex ) );
				lowMagTomogramSourceIndices.add( sourceIndex );

				autoContrast( converterSetups, sourceIndex, numMipmapLevels, 2 );
			}
			else
			{
				// default to highMag (hm)
				matchedTemplateVoxelDimensions.add( BdvUtils.getVoxelDimensions( bdv, sourceIndex ) );
				highMagTomogramSourceIndices.add( sourceIndex );

				autoContrast( converterSetups, sourceIndex, numMipmapLevels, 0 );
			}

		}

		if ( lowMagTomogramSourceIndices.size() >0 )
			addSourcesDisplaySettingsUI( panel,
						"Low Mag Tomograms", bdv, lowMagTomogramSourceIndices, Color.GRAY );

		if ( highMagTomogramSourceIndices.size() > 0)
			addSourcesDisplaySettingsUI( panel,
					"High Mag Tomograms", bdv, highMagTomogramSourceIndices, Color.GRAY );


	}

	private void autoContrast( List< ConverterSetup > converterSetups,
							   int sourceIndex,
							   int numMipmapLevels,
							   double dimingFactor )
	{
		final double[] minMax = getMinMax( sourceIndex, numMipmapLevels - 1 );
		converterSetups.get( sourceIndex ).setDisplayRange(
				minMax[ 0 ],
				minMax[ 1 ] + ( minMax[ 1 ] - minMax[ 0 ] ) * dimingFactor );
	}

	private Color getOverviewColor( String name )
	{
		Color color;

		if ( name.contains( "_ch0" ) )
		{
			color = Color.GRAY;
		}
		else if ( name.contains( "_ch1" ) )
		{
			color = Color.GREEN;
		}
		else if ( name.contains( "_ch2" ) )
		{
			color = Color.MAGENTA;
		}
		else if ( name.contains( "_ch3" ) )
		{
			color = Color.CYAN;
		}
		else
		{
			color = Color.GRAY;
		}
		return color;
	}

	public double[] getMinMax( int sourceIndex, int level ) {

		final List< SourceState< ? > > sources =
				bdv.getBdvHandle().getViewerPanel().getState().getSources();

		final RandomAccessibleInterval< T > rai =
				( RandomAccessibleInterval )
						sources.get( sourceIndex ).getSpimSource().getSource( 0, level );

		final double[] center = getCenter( rai );

		final SubsampleIntervalView< T > subsampleCenterSlice =
				Views.subsample( Views.hyperSlice( rai, 2, (long) center[ 2 ] ), 5, 5 );

		Cursor<T> cursor = Views.iterable( subsampleCenterSlice ).cursor();
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		double value;
		while (cursor.hasNext()) {
			value = cursor.next().getRealDouble();
			if (value < min) min = value;
			if (value > max) max = value;
		}

		return new double[]{ min, max };
	}


	private void addCaptureViewPanel( JPanel panel )
	{
		final JPanel horizontalLayoutPanel = UiUtils.getHorizontalLayoutPanel();

		horizontalLayoutPanel.add( new JLabel( "Resolution [nm]" ) );

		final JTextField resolutionTextField = new JTextField( "" + getMinVoxelSize() );

		horizontalLayoutPanel.add( resolutionTextField );

		final JButton button = new JButton( "Capture current view" );

		button.addActionListener( e -> captureView( bdv, Double.parseDouble( resolutionTextField.getText() ) ) );

		horizontalLayoutPanel.add( button );

		panel.add( horizontalLayoutPanel );
	}

	private double getMinVoxelSize()
	{
		double minVoxelSize = 100;
		for ( VoxelDimensions voxelDimensions : matchedTemplateVoxelDimensions )
		{
			if ( voxelDimensions.dimension( 0 ) < minVoxelSize )
				minVoxelSize = voxelDimensions.dimension( 0 );
		}
		return minVoxelSize;
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
			bdv.getBdvHandle().getViewerPanel().requestRepaint();
			//Utils.updateBdv( bdv,1000 );
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