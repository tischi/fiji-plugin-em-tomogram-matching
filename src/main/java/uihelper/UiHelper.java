package uihelper;

import bdv.tools.brightness.ConverterSetup;
import de.embl.cba.em.matching.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class UiHelper
{
	public static JButton getColorButton( int[] buttonDimensions, ConverterSetup setup )
	{
		JButton colorButton = new JButton( "C" );
		colorButton.setPreferredSize( new Dimension( buttonDimensions[0], buttonDimensions[1] ) );

		colorButton.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				Color color = JColorChooser.showDialog( null, "", null );
				setup.setColor( Utils.asArgbType( color ) );
			}
		} );

		return colorButton;
	}

	public static JPanel getHorizontalLayoutPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout(panel, BoxLayout.LINE_AXIS) );
		panel.setBorder( BorderFactory.createEmptyBorder(0, 10, 10, 10) );
		panel.add( Box.createHorizontalGlue() );
		return panel;
	}
}
