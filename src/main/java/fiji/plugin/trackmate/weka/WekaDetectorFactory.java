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

import static fiji.plugin.trackmate.detection.DetectorKeys.DEFAULT_TARGET_CHANNEL;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_TARGET_CHANNEL;
import static fiji.plugin.trackmate.detection.ThresholdDetectorFactory.KEY_SMOOTHING_SCALE;
import static fiji.plugin.trackmate.io.IOUtils.readDoubleAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readIntegerAttribute;
import static fiji.plugin.trackmate.io.IOUtils.readStringAttribute;
import static fiji.plugin.trackmate.io.IOUtils.writeAttribute;
import static fiji.plugin.trackmate.io.IOUtils.writeTargetChannel;
import static fiji.plugin.trackmate.util.TMUtils.checkMapKeys;
import static fiji.plugin.trackmate.util.TMUtils.checkParameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;

import org.jdom2.Element;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.detection.SpotDetector;
import fiji.plugin.trackmate.detection.SpotDetectorFactory;
import fiji.plugin.trackmate.gui.components.ConfigurationPanel;
import fiji.plugin.trackmate.io.IOUtils;
import fiji.plugin.trackmate.util.TMUtils;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.Interval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

@Plugin( type = SpotDetectorFactory.class, priority = Priority.LOW - 4.1 )
public class WekaDetectorFactory< T extends RealType< T > & NativeType< T > > implements SpotDetectorFactory< T >
{

	/*
	 * CONSTANTS
	 */
	/**
	 * The key to the parameter that stores the path to the Weka classifier.
	 */
	public static final String KEY_CLASSIFIER_FILEPATH = "CLASSIFIER_FILEPATH";

	/**
	 * The key to the parameter that stores the probability threshold. Values
	 * are {@link Double}s from 0 to 1.
	 */
	public static final String KEY_PROBA_THRESHOLD = "PROBA_THRESHOLD";

	public static final Double DEFAULT_PROBA_THRESHOLD = Double.valueOf( 0.5 );

	/**
	 * The key to the parameter that stores the index of the class to use to
	 * create objects. Values are positive integers.
	 */
	public static final String KEY_CLASS_INDEX = "CLASS_INDEX";

	public static final Integer DEFAULT_CLASS_INDEX = Integer.valueOf( 0 );

	/** A string key identifying this factory. */
	public static final String DETECTOR_KEY = "WEKA_DETECTOR";

	/** The pretty name of the target detector. */
	public static final String NAME = "Weka detector";

	/** An html information text. */
	public static final String INFO_TEXT = "<html>"
			+ "This detector relies on the 'Trainable Weka segmentation' plugin to detect objects."
			+ "<p>"
			+ "It works for 2D and 3D images, returns contours for 2D images and meshes"
			+ "for 3D images."
			+ "<p>"
			+ "You need to provide the path to a classifier previously trained and saved using the "
			+ "'Trainable Weka segmentation' plugin. It will classically be a '.model' file. "
			+ "<p>"
			+ "If you use this detector for your work, please "
			+ "also cite the Weka IJ paper: <a href=\"https://doi.org/10.1093/bioinformatics/btx180\">Arganda-Carreras, I.; Kaynig, V. & Rueden, C. et al. (2017), "
			+ "'Trainable Weka Segmentation: a machine learning tool for microscopy pixel classification.', "
			+ "Bioinformatics (Oxford Univ Press) 33 (15).</a> "
			+ "<p>"
			+ "Documentation for this module "
			+ "<a href=\"https://imagej.net/plugins/trackmate/trackmate-weka\">on the ImageJ Wiki</a>."
			+ "<p>"
			+ "</html>";

	/*
	 * FIELDS
	 */

	/** The image to operate on. Multiple frames, single channel. */
	protected ImgPlus< T > img;

	protected Map< String, Object > settings;

	protected String errorMessage;

	protected WekaRunner< T > runner;

	/*
	 * METHODS
	 */

	@Override
	public SpotDetector< T > getDetector( final Interval interval, final int frame )
	{
		final int channel = ( Integer ) settings.get( KEY_TARGET_CHANNEL ) - 1;
		final ImgPlus< T > input = TMUtils.hyperSlice( img, channel, frame );
		final int classIndex = ( Integer ) settings.get( KEY_CLASS_INDEX );
		final double probaThreshold = ( Double ) settings.get( KEY_PROBA_THRESHOLD );
		final boolean simplify = true;
		final Object smoothingObj = settings.get( KEY_SMOOTHING_SCALE );
		final double smoothingScale = smoothingObj == null
				? -1.
				: ( ( Number ) smoothingObj ).doubleValue();
		final WekaDetector< T > detector = new WekaDetector<>(
				runner,
				input,
				interval,
				classIndex,
				probaThreshold,
				simplify,
				smoothingScale );
		return detector;
	}

	@Override
	public boolean forbidMultithreading()
	{
		/*
		 * We want to run one frame after another, giving all resources to one
		 * frame at a time.
		 */
		return true;
	}

	@Override
	public boolean setTarget( final ImgPlus< T > img, final Map< String, Object > settings )
	{
		// First test to make sure we can read the classifier file.
		final Object obj = settings.get( KEY_CLASSIFIER_FILEPATH );
		if ( obj == null )
		{
			errorMessage = "The path to the Weka classifier file is not set.";
			return false;
		}

		final StringBuilder errorHolder = new StringBuilder();
		if ( !IOUtils.canReadFile( ( String ) obj, errorHolder ) )
		{
			errorMessage = "Problem with Weka classifier file: " + errorHolder.toString();
			return false;
		}

		final String classifierFilePath = ( String ) obj;
		final boolean is3D = img.dimensionIndex( Axes.Z ) >= 0;
		this.runner = new WekaRunner<>( classifierFilePath, is3D );
		if ( !runner.loadClassifier() )
		{
			errorMessage = runner.getErrorMessage();
			return false;
		}
		this.img = img;
		this.settings = settings;
		return checkSettings( settings );
	}

	@Override
	public String getErrorMessage()
	{
		return errorMessage;
	}

	@Override
	public boolean marshall( final Map< String, Object > settings, final Element element )
	{
		final StringBuilder errorHolder = new StringBuilder();
		boolean ok = writeTargetChannel( settings, element, errorHolder );
		ok = ok && writeAttribute( settings, element, KEY_CLASSIFIER_FILEPATH, String.class, errorHolder );
		ok = ok && writeAttribute( settings, element, KEY_CLASS_INDEX, Integer.class, errorHolder );
		ok = ok && writeAttribute( settings, element, KEY_PROBA_THRESHOLD, Double.class, errorHolder );

		if ( !ok )
			errorMessage = errorHolder.toString();

		return ok;
	}

	@Override
	public boolean unmarshall( final Element element, final Map< String, Object > settings )
	{
		settings.clear();
		final StringBuilder errorHolder = new StringBuilder();
		boolean ok = true;
		ok = ok && readIntegerAttribute( element, settings, KEY_TARGET_CHANNEL, errorHolder );
		ok = ok && readStringAttribute( element, settings, KEY_CLASSIFIER_FILEPATH, errorHolder );
		ok = ok && readIntegerAttribute( element, settings, KEY_CLASS_INDEX, errorHolder );
		ok = ok && readDoubleAttribute( element, settings, KEY_PROBA_THRESHOLD, errorHolder );

		if ( !ok )
		{
			errorMessage = errorHolder.toString();
			return false;
		}
		return checkSettings( settings );
	}

	@Override
	public ConfigurationPanel getDetectorConfigurationPanel( final Settings settings, final Model model )
	{
		return new WekaDetectorConfigurationPanel( settings, model );
	}

	@Override
	public Map< String, Object > getDefaultSettings()
	{
		final Map< String, Object > settings = new HashMap<>();
		settings.put( KEY_TARGET_CHANNEL, DEFAULT_TARGET_CHANNEL );
		settings.put( KEY_CLASS_INDEX, DEFAULT_CLASS_INDEX );
		settings.put( KEY_PROBA_THRESHOLD, DEFAULT_PROBA_THRESHOLD );
		settings.put( KEY_CLASSIFIER_FILEPATH, null );
		return settings;
	}

	@Override
	public boolean checkSettings( final Map< String, Object > settings )
	{
		boolean ok = true;
		final StringBuilder errorHolder = new StringBuilder();
		ok = ok & checkParameter( settings, KEY_TARGET_CHANNEL, Integer.class, errorHolder );
		ok = ok & checkParameter( settings, KEY_CLASS_INDEX, Integer.class, errorHolder );
		ok = ok & checkParameter( settings, KEY_PROBA_THRESHOLD, Double.class, errorHolder );
		ok = ok & checkParameter( settings, KEY_CLASSIFIER_FILEPATH, String.class, errorHolder );
		final List< String > mandatoryKeys = new ArrayList<>();
		mandatoryKeys.add( KEY_TARGET_CHANNEL );
		mandatoryKeys.add( KEY_CLASS_INDEX );
		mandatoryKeys.add( KEY_PROBA_THRESHOLD );
		mandatoryKeys.add( KEY_CLASSIFIER_FILEPATH );
		ok = ok & checkMapKeys( settings, mandatoryKeys, null, errorHolder );
		if ( !ok )
			errorMessage = errorHolder.toString();

		// Extra test to make sure we can read the classifier file.
		if ( ok )
		{
			final Object obj = settings.get( KEY_CLASSIFIER_FILEPATH );
			if ( obj == null )
			{
				errorMessage = "The path to the Weka classifier file is not set.";
				return false;
			}

			if ( !IOUtils.canReadFile( ( String ) obj, errorHolder ) )
			{
				errorMessage = "Problem with Weka classifier file: " + errorHolder.toString();
				return false;
			}
		}

		return ok;
	}

	@Override
	public String getInfoText()
	{
		return INFO_TEXT;
	}

	@Override
	public ImageIcon getIcon()
	{
		return null;
	}

	@Override
	public String getKey()
	{
		return DETECTOR_KEY;
	}

	@Override
	public String getName()
	{
		return NAME;
	}

	@Override
	public boolean has2Dsegmentation()
	{
		return true;
	}

	@Override
	public WekaDetectorFactory< T > copy()
	{
		return new WekaDetectorFactory<>();
	}
}
