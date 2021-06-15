/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2021 The Institut Pasteur.
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
import static fiji.plugin.trackmate.gui.Icons.PREVIEW_ICON;
import static fiji.plugin.trackmate.weka.WekaDetectorFactory.KEY_CLASSIFIER_FILEPATH;
import static fiji.plugin.trackmate.weka.WekaDetectorFactory.KEY_CLASS_INDEX;
import static fiji.plugin.trackmate.weka.WekaDetectorFactory.KEY_PROBA_THRESHOLD;

import java.awt.Dimension;
import java.awt.Font;
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

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.gui.components.ConfigurationPanel;
import fiji.plugin.trackmate.util.EverythingDisablerAndReenabler;
import fiji.plugin.trackmate.util.FileChooser;
import fiji.plugin.trackmate.util.FileChooser.DialogType;
import fiji.plugin.trackmate.util.JLabelLogger;
import fiji.plugin.trackmate.util.TMUtils;
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

	private final Settings settings;

	private final Model model;

	private final WekaDetectionPreviewer< ? > previewer = new WekaDetectionPreviewer<>();

	private final Logger localLogger;

	/**
	 * Create the panel.
	 */
	public WekaDetectorConfigurationPanel( final Settings settings, final Model model )
	{
		this.settings = settings;
		this.model = model;
		this.prefService = TMUtils.getContext().getService( PrefService.class );

		final GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[] { 144, 0, 32 };
		gridBagLayout.rowHeights = new int[] { 0, 0, 84, 0, 27, 0, 0, 0, 0, 37, 23 };
		gridBagLayout.columnWeights = new double[] { 0.0, 1.0, 0.0 };
		gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0 };
		setLayout( gridBagLayout );

		final JLabel lblSettingsForDetector = new JLabel( "Settings for detector:" );
		lblSettingsForDetector.setFont( FONT );
		final GridBagConstraints gbc_lblSettingsForDetector = new GridBagConstraints();
		gbc_lblSettingsForDetector.gridwidth = 3;
		gbc_lblSettingsForDetector.insets = new Insets( 5, 5, 5, 0 );
		gbc_lblSettingsForDetector.fill = GridBagConstraints.HORIZONTAL;
		gbc_lblSettingsForDetector.gridx = 0;
		gbc_lblSettingsForDetector.gridy = 0;
		add( lblSettingsForDetector, gbc_lblSettingsForDetector );

		final JLabel lblDetector = new JLabel( TITLE, ICON, JLabel.RIGHT );
		lblDetector.setFont( BIG_FONT );
		lblDetector.setHorizontalAlignment( SwingConstants.CENTER );
		final GridBagConstraints gbc_lblDetector = new GridBagConstraints();
		gbc_lblDetector.gridwidth = 3;
		gbc_lblDetector.insets = new Insets( 5, 5, 5, 0 );
		gbc_lblDetector.fill = GridBagConstraints.HORIZONTAL;
		gbc_lblDetector.gridx = 0;
		gbc_lblDetector.gridy = 1;
		add( lblDetector, gbc_lblDetector );

		/*
		 * Help text.
		 */
		final JLabel lblHelptext = new JLabel( WekaDetectorFactory.INFO_TEXT
				.replace( "<br>", "" )
				.replace( "<p>", "<p align=\"justify\">" )
				.replace( "<html>", "<html><p align=\"justify\">" ) );
		lblHelptext.setFont( FONT.deriveFont( Font.ITALIC ) );
		final GridBagConstraints gbc_lblHelptext = new GridBagConstraints();
		gbc_lblHelptext.anchor = GridBagConstraints.NORTH;
		gbc_lblHelptext.fill = GridBagConstraints.HORIZONTAL;
		gbc_lblHelptext.gridwidth = 3;
		gbc_lblHelptext.insets = new Insets( 5, 10, 5, 10 );
		gbc_lblHelptext.gridx = 0;
		gbc_lblHelptext.gridy = 2;
		add( lblHelptext, gbc_lblHelptext );

		/*
		 * Channel selector.
		 */

		final JLabel lblSegmentInChannel = new JLabel( "Segment in channel:" );
		lblSegmentInChannel.setFont( SMALL_FONT );
		final GridBagConstraints gbc_lblSegmentInChannel = new GridBagConstraints();
		gbc_lblSegmentInChannel.anchor = GridBagConstraints.EAST;
		gbc_lblSegmentInChannel.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblSegmentInChannel.gridx = 0;
		gbc_lblSegmentInChannel.gridy = 3;
		add( lblSegmentInChannel, gbc_lblSegmentInChannel );

		sliderChannel = new JSlider();
		final GridBagConstraints gbc_sliderChannel = new GridBagConstraints();
		gbc_sliderChannel.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderChannel.insets = new Insets( 5, 5, 5, 5 );
		gbc_sliderChannel.gridx = 1;
		gbc_sliderChannel.gridy = 3;
		add( sliderChannel, gbc_sliderChannel );

		final JLabel labelChannel = new JLabel( "1" );
		labelChannel.setHorizontalAlignment( SwingConstants.CENTER );
		labelChannel.setFont( SMALL_FONT );
		final GridBagConstraints gbc_labelChannel = new GridBagConstraints();
		gbc_labelChannel.insets = new Insets( 5, 5, 5, 0 );
		gbc_labelChannel.gridx = 2;
		gbc_labelChannel.gridy = 3;
		add( labelChannel, gbc_labelChannel );

		sliderChannel.addChangeListener( l -> labelChannel.setText( "" + sliderChannel.getValue() ) );

		/*
		 * Model file.
		 */

		final JLabel lblCusstomModelFile = new JLabel( "Ilastik file:" );
		lblCusstomModelFile.setFont( FONT );
		final GridBagConstraints gbc_lblCusstomModelFile = new GridBagConstraints();
		gbc_lblCusstomModelFile.anchor = GridBagConstraints.SOUTHWEST;
		gbc_lblCusstomModelFile.insets = new Insets( 0, 5, 0, 5 );
		gbc_lblCusstomModelFile.gridx = 0;
		gbc_lblCusstomModelFile.gridy = 4;
		add( lblCusstomModelFile, gbc_lblCusstomModelFile );

		btnBrowse = new JButton( "Browse" );
		btnBrowse.setFont( FONT );
		final GridBagConstraints gbc_btnBrowse = new GridBagConstraints();
		gbc_btnBrowse.insets = new Insets( 5, 0, 0, 5 );
		gbc_btnBrowse.anchor = GridBagConstraints.SOUTHEAST;
		gbc_btnBrowse.gridwidth = 2;
		gbc_btnBrowse.gridx = 1;
		gbc_btnBrowse.gridy = 4;
		add( btnBrowse, gbc_btnBrowse );

		modelFileTextField = new JTextField( "" );
		modelFileTextField.setFont( SMALL_FONT );
		final GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.gridwidth = 3;
		gbc_textField.insets = new Insets( 0, 5, 5, 5 );
		gbc_textField.fill = GridBagConstraints.BOTH;
		gbc_textField.gridx = 0;
		gbc_textField.gridy = 5;
		add( modelFileTextField, gbc_textField );
		modelFileTextField.setColumns( 10 );

		/*
		 * Class index.
		 */

		final JLabel lblClassId = new JLabel( "Target class:" );
		lblClassId.setFont( SMALL_FONT );
		final GridBagConstraints gbc_lblOverlapThreshold = new GridBagConstraints();
		gbc_lblOverlapThreshold.anchor = GridBagConstraints.EAST;
		gbc_lblOverlapThreshold.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblOverlapThreshold.gridx = 0;
		gbc_lblOverlapThreshold.gridy = 6;
		add( lblClassId, gbc_lblOverlapThreshold );

		// For now we simply put a list of dummy class names.
		final Vector< String > dummyClassNames = new Vector<>( Arrays.asList( new String[] {
				"class 1", "class 2", "class 3", "class 4", "class 5", "class 6", "class 7", "class 8", "class 9", "class 10" } ) );

		cmbboxClassId = new JComboBox<>( dummyClassNames );
		cmbboxClassId.setFont( SMALL_FONT );
		final GridBagConstraints gbc_sliderClassId = new GridBagConstraints();
		gbc_sliderClassId.fill = GridBagConstraints.HORIZONTAL;
		gbc_sliderClassId.insets = new Insets( 0, 0, 5, 5 );
		gbc_sliderClassId.gridx = 1;
		gbc_sliderClassId.gridy = 6;
		add( cmbboxClassId, gbc_sliderClassId );

		/*
		 * Proba threshold.
		 */

		final JLabel lblScoreTreshold = new JLabel( "Threshold on probability:" );
		lblScoreTreshold.setFont( SMALL_FONT );
		final GridBagConstraints gbc_lblScoreTreshold = new GridBagConstraints();
		gbc_lblScoreTreshold.anchor = GridBagConstraints.EAST;
		gbc_lblScoreTreshold.insets = new Insets( 5, 5, 5, 5 );
		gbc_lblScoreTreshold.gridx = 0;
		gbc_lblScoreTreshold.gridy = 7;
		add( lblScoreTreshold, gbc_lblScoreTreshold );

		ftfProbaThreshold = new JFormattedTextField( THRESHOLD_FORMAT );
		ftfProbaThreshold.setFont( SMALL_FONT );
		ftfProbaThreshold.setMinimumSize( new Dimension( 60, 20 ) );
		ftfProbaThreshold.setHorizontalAlignment( SwingConstants.CENTER );
		final GridBagConstraints gbc_score = new GridBagConstraints();
		gbc_score.fill = GridBagConstraints.HORIZONTAL;
		gbc_score.insets = new Insets( 5, 5, 5, 5 );
		gbc_score.gridx = 1;
		gbc_score.gridy = 7;
		add( ftfProbaThreshold, gbc_score );

		/*
		 * Logger.
		 */

		final JLabelLogger labelLogger = new JLabelLogger();
		final GridBagConstraints gbc_labelLogger = new GridBagConstraints();
		gbc_labelLogger.gridwidth = 3;
		gbc_labelLogger.gridx = 0;
		gbc_labelLogger.gridy = 10;
		add( labelLogger, gbc_labelLogger );
		this.localLogger = labelLogger.getLogger();

		/*
		 * Preview.
		 */

		final JButton btnPreview = new JButton( "Preview", PREVIEW_ICON );
		btnPreview.setFont( FONT );
		final GridBagConstraints gbc_btnPreview = new GridBagConstraints();
		gbc_btnPreview.gridwidth = 1;
		gbc_btnPreview.anchor = GridBagConstraints.SOUTHEAST;
		gbc_btnPreview.insets = new Insets( 5, 5, 5, 5 );
		gbc_btnPreview.gridx = 2;
		gbc_btnPreview.gridy = 9;
		add( btnPreview, gbc_btnPreview );

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
		gbcBtnClassNames.gridy = 9;
		add( btnClassNames, gbcBtnClassNames );

		/*
		 * Listeners and specificities.
		 */

		btnPreview.addActionListener( e -> preview( settings.imp.getFrame() - 1 ) );
		btnClassNames.addActionListener( e -> updateClassNames() );

		/*
		 * Deal with channels: the slider and channel labels are only visible if
		 * we find more than one channel.
		 */
		if ( null != settings.imp )
		{
			final int n_channels = settings.imp.getNChannels();
			sliderChannel.setMaximum( n_channels );
			sliderChannel.setMinimum( 1 );
			sliderChannel.setValue( settings.imp.getChannel() );

			if ( n_channels <= 1 )
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

	private void preview( final int frame )
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
					settings.detectorSettings = getSettings();
					previewer.preview( settings, frame, model, localLogger );
				}
				catch ( final Exception e )
				{
					e.printStackTrace();
				}
				finally
				{
					enabler.reenable();
				}
			}
		}.start();
	}

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

					@SuppressWarnings( "rawtypes" )
					final ImgPlus img = TMUtils.rawWraps( settings.imp );
					final boolean is3D = img.dimensionIndex( Axes.Z ) >= 0;
					final List< String > classNames = previewer.getClassNames( classifierPath, localLogger, is3D );

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
