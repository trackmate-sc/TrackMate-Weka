/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2021 - 2023 TrackMate developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package fiji.plugin.trackmate.weka;

import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_TARGET_CHANNEL;
import static fiji.plugin.trackmate.gui.Fonts.BIG_FONT;
import static fiji.plugin.trackmate.gui.Fonts.FONT;
import static fiji.plugin.trackmate.gui.Fonts.SMALL_FONT;
import static fiji.plugin.trackmate.gui.Icons.MAGNIFIER_ICON;
import static fiji.plugin.trackmate.weka.WekaDetectorFactory.KEY_CLASSIFIER_FILEPATH;
import static fiji.plugin.trackmate.weka.WekaDetectorFactory.KEY_CLASS_INDEX;
import static fiji.plugin.trackmate.weka.WekaDetectorFactory.KEY_PROBA_THRESHOLD;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.scijava.prefs.PrefService;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.gui.GuiUtils;
import fiji.plugin.trackmate.gui.components.ConfigurationPanel;
import fiji.plugin.trackmate.util.EverythingDisablerAndReenabler;
import fiji.plugin.trackmate.util.FileChooser;
import fiji.plugin.trackmate.util.FileChooser.DialogType;
import fiji.plugin.trackmate.util.JLabelLogger;
import fiji.plugin.trackmate.util.TMUtils;
import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;

public class WekaDetectorConfigurationPanel extends ConfigurationPanel
{

	private static final long serialVersionUID = 1L;

	private static final NumberFormat THRESHOLD_FORMAT = new DecimalFormat( "#.##" );

	protected static final ImageIcon ICON = new ImageIcon( getResource( "images/TrackMateWeka-logo-100px.png" ) );

	private static final String TITLE = WekaDetectorFactory.NAME;

	private static final FileFilter fileFilter = new FileNameExtensionFilter( "Weka classifier files.", "model" );

	private final JSlider sliderChannel;

	private final JComboBox< String > cmbboxClassId;

	private final JTextField modelFileTextField;

	private final JButton btnBrowse;

	private final JFormattedTextField ftfProbaThreshold;

	protected final PrefService prefService;

	private final WekaDetectionPreviewer< ? > previewer;

	private final boolean is3D;

	/**
	 * Create the panel.
	 */
	public WekaDetectorConfigurationPanel( final Settings settings, final Model model )
	{
		@SuppressWarnings( "rawtypes" )
		final ImgPlus img = TMUtils.rawWraps( settings.imp );
		this.is3D = img.dimensionIndex( Axes.Z ) >= 0;
		this.prefService = TMUtils.getContext().getService( PrefService.class );

		final GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 144, 0, 32 };
		gridBagLayout.rowHeights = new int[] { 0, 84, 0, 27, 0, 0, 0, 0, 37, 23 };
		gridBagLayout.columnWeights = new double[] { 0.0, 1.0, 0.0 };
		gridBagLayout.rowWeights = new double[] { 0., 1., 0., 0., 0., 0., 0., 0., 0., 0. };
		setLayout( gridBagLayout );

		final JLabel lblDetector = new JLabel( TITLE, ICON, JLabel.RIGHT );
		lblDetector.setFont( BIG_FONT );
		lblDetector.setHorizontalAlignment( SwingConstants.CENTER );
		final GridBagConstraints gbcLblDetector = new GridBagConstraints();
		gbcLblDetector.gridwidth = 3;
		gbcLblDetector.insets = new Insets( 5, 5, 5, 0 );
		gbcLblDetector.fill = GridBagConstraints.HORIZONTAL;
		gbcLblDetector.gridx = 0;
		gbcLblDetector.gridy = 0;
		add( lblDetector, gbcLblDetector );

		/*
		 * Help text.
		 */
		final GridBagConstraints gbcLblHelptext = new GridBagConstraints();
		gbcLblHelptext.anchor = GridBagConstraints.NORTH;
		gbcLblHelptext.fill = GridBagConstraints.BOTH;
		gbcLblHelptext.gridwidth = 3;
		gbcLblHelptext.insets = new Insets( 5, 10, 5, 10 );
		gbcLblHelptext.gridx = 0;
		gbcLblHelptext.gridy = 1;
		add( GuiUtils.textInScrollPanel( GuiUtils.infoDisplay( WekaDetectorFactory.INFO_TEXT ) ), gbcLblHelptext );

		/*
		 * Channel selector.
		 */

		final JLabel lblSegmentInChannel = new JLabel( "Segment in channel:" );
		lblSegmentInChannel.setFont( SMALL_FONT );
		final GridBagConstraints gbcLblSegmentInChannel = new GridBagConstraints();
		gbcLblSegmentInChannel.anchor = GridBagConstraints.EAST;
		gbcLblSegmentInChannel.insets = new Insets( 5, 5, 5, 5 );
		gbcLblSegmentInChannel.gridx = 0;
		gbcLblSegmentInChannel.gridy = 2;
		add( lblSegmentInChannel, gbcLblSegmentInChannel );

		sliderChannel = new JSlider();
		final GridBagConstraints gbcSliderChannel = new GridBagConstraints();
		gbcSliderChannel.fill = GridBagConstraints.HORIZONTAL;
		gbcSliderChannel.insets = new Insets( 5, 5, 5, 5 );
		gbcSliderChannel.gridx = 1;
		gbcSliderChannel.gridy = 2;
		add( sliderChannel, gbcSliderChannel );

		final JLabel labelChannel = new JLabel( "1" );
		labelChannel.setHorizontalAlignment( SwingConstants.CENTER );
		labelChannel.setFont( SMALL_FONT );
		final GridBagConstraints gbcLabelChannel = new GridBagConstraints();
		gbcLabelChannel.insets = new Insets( 5, 5, 5, 0 );
		gbcLabelChannel.gridx = 2;
		gbcLabelChannel.gridy = 2;
		add( labelChannel, gbcLabelChannel );

		sliderChannel.addChangeListener( l -> labelChannel.setText( "" + sliderChannel.getValue() ) );

		/*
		 * Model file.
		 */

		final JLabel lblCusstomModelFile = new JLabel( "Weka model file:" );
		lblCusstomModelFile.setFont( FONT );
		final GridBagConstraints gbcLblCusstomModelFile = new GridBagConstraints();
		gbcLblCusstomModelFile.anchor = GridBagConstraints.SOUTHWEST;
		gbcLblCusstomModelFile.insets = new Insets( 0, 5, 5, 5 );
		gbcLblCusstomModelFile.gridx = 0;
		gbcLblCusstomModelFile.gridy = 3;
		add( lblCusstomModelFile, gbcLblCusstomModelFile );

		btnBrowse = new JButton( "Browse" );
		btnBrowse.setFont( FONT );
		final GridBagConstraints gbcBtnBrowse = new GridBagConstraints();
		gbcBtnBrowse.insets = new Insets( 5, 0, 5, 0 );
		gbcBtnBrowse.anchor = GridBagConstraints.SOUTHEAST;
		gbcBtnBrowse.gridwidth = 2;
		gbcBtnBrowse.gridx = 1;
		gbcBtnBrowse.gridy = 3;
		add( btnBrowse, gbcBtnBrowse );

		modelFileTextField = new JTextField( "" );
		modelFileTextField.setFont( SMALL_FONT );
		final GridBagConstraints gbcTextField = new GridBagConstraints();
		gbcTextField.gridwidth = 3;
		gbcTextField.insets = new Insets( 0, 5, 5, 5 );
		gbcTextField.fill = GridBagConstraints.BOTH;
		gbcTextField.gridx = 0;
		gbcTextField.gridy = 4;
		add( modelFileTextField, gbcTextField );
		modelFileTextField.setColumns( 10 );

		/*
		 * Class index.
		 */

		final JLabel lblClassId = new JLabel( "Target class:" );
		lblClassId.setFont( SMALL_FONT );
		final GridBagConstraints gbcLblOverlapThreshold = new GridBagConstraints();
		gbcLblOverlapThreshold.anchor = GridBagConstraints.EAST;
		gbcLblOverlapThreshold.insets = new Insets( 5, 5, 5, 5 );
		gbcLblOverlapThreshold.gridx = 0;
		gbcLblOverlapThreshold.gridy = 5;
		add( lblClassId, gbcLblOverlapThreshold );

		// For now we simply put a list of dummy class names.
		final Vector< String > dummyClassNames = new Vector<>( Arrays.asList( new String[] {
				"class 1", "class 2", "class 3", "class 4", "class 5", "class 6", "class 7", "class 8", "class 9", "class 10" } ) );

		cmbboxClassId = new JComboBox<>( dummyClassNames );
		cmbboxClassId.setFont( SMALL_FONT );
		final GridBagConstraints gbcSliderClassId = new GridBagConstraints();
		gbcSliderClassId.fill = GridBagConstraints.HORIZONTAL;
		gbcSliderClassId.insets = new Insets( 0, 0, 5, 5 );
		gbcSliderClassId.gridx = 1;
		gbcSliderClassId.gridy = 5;
		add( cmbboxClassId, gbcSliderClassId );

		/*
		 * Proba threshold.
		 */

		final JLabel lblScoreTreshold = new JLabel( "Threshold on probability:" );
		lblScoreTreshold.setFont( SMALL_FONT );
		final GridBagConstraints gbcLblScoreTreshold = new GridBagConstraints();
		gbcLblScoreTreshold.anchor = GridBagConstraints.EAST;
		gbcLblScoreTreshold.insets = new Insets( 5, 5, 5, 5 );
		gbcLblScoreTreshold.gridx = 0;
		gbcLblScoreTreshold.gridy = 6;
		add( lblScoreTreshold, gbcLblScoreTreshold );

		ftfProbaThreshold = new JFormattedTextField( THRESHOLD_FORMAT );
		ftfProbaThreshold.setFont( SMALL_FONT );
		ftfProbaThreshold.setMinimumSize( new Dimension( 60, 20 ) );
		ftfProbaThreshold.setHorizontalAlignment( SwingConstants.CENTER );
		final GridBagConstraints gbcScore = new GridBagConstraints();
		gbcScore.fill = GridBagConstraints.HORIZONTAL;
		gbcScore.insets = new Insets( 5, 5, 5, 5 );
		gbcScore.gridx = 1;
		gbcScore.gridy = 6;
		add( ftfProbaThreshold, gbcScore );

		/*
		 * Refresh class names.
		 */

		final JButton btnClassNames = new JButton( "Refresh class names", MAGNIFIER_ICON );
		btnClassNames.setFont( FONT );
		final GridBagConstraints gbcBtnClassNames = new GridBagConstraints();
		gbcBtnClassNames.gridwidth = 1;
		gbcBtnClassNames.anchor = GridBagConstraints.SOUTHWEST;
		gbcBtnClassNames.insets = new Insets( 5, 5, 5, 5 );
		gbcBtnClassNames.gridx = 0;
		gbcBtnClassNames.gridy = 7;
		add( btnClassNames, gbcBtnClassNames );

		/*
		 * View last proba.
		 */

		final JButton btnLastProba = new JButton( "Last proba map", MAGNIFIER_ICON );
		btnLastProba.setFont( FONT );
		final GridBagConstraints gbcBtnLastProba = new GridBagConstraints();
		gbcBtnLastProba.gridwidth = 2;
		gbcBtnLastProba.anchor = GridBagConstraints.SOUTHEAST;
		gbcBtnLastProba.insets = new Insets( 5, 5, 5, 5 );
		gbcBtnLastProba.gridx = 1;
		gbcBtnLastProba.gridy = 7;
		add( btnLastProba, gbcBtnLastProba );

		/*
		 * Preview.
		 */

		final GridBagConstraints gbcBtnPreview = new GridBagConstraints();
		gbcBtnPreview.gridwidth = 3;
		gbcBtnPreview.fill = GridBagConstraints.BOTH;
		gbcBtnPreview.insets = new Insets( 5, 5, 5, 5 );
		gbcBtnPreview.gridx = 0;
		gbcBtnPreview.gridy = 8;

		previewer = new WekaDetectionPreviewer<>(
				model,
				settings,
				() -> getSettings(),
				() -> ( settings.imp.getFrame() - 1 ) );
		add( previewer.getPanel(), gbcBtnPreview );

		/*
		 * Listeners and specificities.
		 */

		btnClassNames.addActionListener( e -> updateClassNames() );
		btnLastProba.addActionListener( e -> showProbaImg() );

		/*
		 * Deal with channels: the slider and channel labels are only visible if
		 * we find more than one channel.
		 */
		if ( null != settings.imp )
		{
			final int nChannels = settings.imp.getNChannels();
			sliderChannel.setMaximum( nChannels );
			sliderChannel.setMinimum( 1 );
			sliderChannel.setValue( settings.imp.getChannel() );

			if ( nChannels <= 1 )
			{
				labelChannel.setVisible( false );
				lblSegmentInChannel.setVisible( false );
				sliderChannel.setVisible( false );
			}
			else
			{
				labelChannel.setVisible( true );
				lblSegmentInChannel.setVisible( true );
				sliderChannel.setVisible( true );
			}
		}

		btnBrowse.addActionListener( l -> browse() );
		final PropertyChangeListener l = e -> prefService.put(
				WekaDetectorConfigurationPanel.class, KEY_CLASSIFIER_FILEPATH, modelFileTextField.getText() );
		modelFileTextField.addPropertyChangeListener( "value", l );
	}

	@Override
	public Map< String, Object > getSettings()
	{
		final HashMap< String, Object > settings = new HashMap<>( 4 );

		final int targetChannel = sliderChannel.getValue();
		settings.put( KEY_TARGET_CHANNEL, targetChannel );

		settings.put( KEY_CLASSIFIER_FILEPATH, modelFileTextField.getText() );

		final int classID = cmbboxClassId.getSelectedIndex();
		settings.put( KEY_CLASS_INDEX, classID );

		final double probaThreshold = ( ( Number ) ftfProbaThreshold.getValue() ).doubleValue();
		settings.put( KEY_PROBA_THRESHOLD, probaThreshold );
		return settings;
	}

	@Override
	public void setSettings( final Map< String, Object > settings )
	{
		String filePath = ( String ) settings.get( KEY_CLASSIFIER_FILEPATH );
		if ( filePath == null || filePath.isEmpty() )
			filePath = prefService.get( WekaDetectorConfigurationPanel.class, KEY_CLASSIFIER_FILEPATH );
		modelFileTextField.setText( filePath );
		cmbboxClassId.setSelectedIndex( ( Integer ) settings.get( KEY_CLASS_INDEX ) );
		sliderChannel.setValue( ( Integer ) settings.get( KEY_TARGET_CHANNEL ) );
		ftfProbaThreshold.setValue( settings.get( KEY_PROBA_THRESHOLD ) );
	}

	@Override
	public void clean()
	{}

	protected void browse()
	{
		btnBrowse.setEnabled( false );
		try
		{
			final File file = FileChooser.chooseFile( this, modelFileTextField.getText(), fileFilter, "Select an Weka model file", DialogType.LOAD );
			if ( file != null )
			{
				modelFileTextField.setText( file.getAbsolutePath() );
				prefService.put( WekaDetectorConfigurationPanel.class, KEY_CLASSIFIER_FILEPATH, file.getAbsolutePath() );
				updateClassNames();
			}
		}
		finally
		{
			btnBrowse.setEnabled( true );
		}
	}

	private void showProbaImg()
	{
		final ImagePlus proba = previewer.getLastProbabilityImage();
		proba.show();
	}

	private void updateClassNames()
	{
		new Thread( "TrackMate preview detection thread" )
		{
			@Override
			public void run()
			{
				final EverythingDisablerAndReenabler enabler = new EverythingDisablerAndReenabler(
						SwingUtilities.getWindowAncestor( WekaDetectorConfigurationPanel.this ), new Class[] { JLabel.class, JLabelLogger.class } );
				enabler.disable();
				try
				{
					// Get class names from classifier file.
					final String classifierPath = modelFileTextField.getText();
					if ( classifierPath == null || classifierPath.isEmpty() )
						return;

					final List< String > classNames = previewer.getClassNames(
							classifierPath,
							previewer.getLogger(),
							is3D );

					// Update GUI.
					cmbboxClassId.setModel( new DefaultComboBoxModel<>( new Vector<>( classNames ) ) );
				}
				finally
				{
					enabler.reenable();
				}
			}
		}.start();
	}

	protected static URL getResource( final String name )
	{
		return WekaDetectorFactory.class.getClassLoader().getResource( name );
	}
}
