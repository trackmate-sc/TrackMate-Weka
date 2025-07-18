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

import java.util.HashMap;
import java.util.Map;

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

@Plugin( type = SpotDetectorFactory.class )
public class WekaDetectorFactory< T extends RealType< T > & NativeType< T > > implements SpotDetectorFactory< T >
{

	/*
	 * CONSTANTS
	 */

	/** The key to the parameter that stores the path to the Weka classifier. */
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

	public static final String DOC_URL = "https://imagej.net/plugins/trackmate/detectors/trackmate-weka";

	/** An html information text. */
	public static final String INFO_TEXT = "<html>"
			+ "This detector relies on the 'Trainable Weka segmentation' plugin to detect objects."
			+ "<p>"
			+ "It works for 2D and 3D images, but returns contours only for 2D images."
			+ "<p>"
			+ "You need to provide the path to a classifier previously trained and saved using the "
			+ "'Trainable Weka segmentation' plugin. It will classically be a '.model' file. "
			+ "<p>"
			+ "If you use this detector for your work, please "
			+ "also cite the Weka IJ paper: <a href=\"https://doi.org/10.1093/bioinformatics/btx180\">Arganda-Carreras, I.; Kaynig, V. & Rueden, C. et al. (2017), "
			+ "'Trainable Weka Segmentation: a machine learning tool for microscopy pixel classification.', "
			+ "Bioinformatics (Oxford Univ Press) 33 (15).</a> "
			+ "</html>";

	/*
	 * METHODS
	 */

	@Override
	public String checkSettings( final Map< String, Object > settings )
	{
		final String error = SpotDetectorFactory.super.checkSettings( settings );
		if ( error != null )
			return error;

		// First test to make sure we can read the classifier file.
		final Object obj = settings.get( KEY_CLASSIFIER_FILEPATH );
		if ( obj == null )
			return "The path to the Weka classifier file is not set.";

		final StringBuilder errorHolder = new StringBuilder();
		if ( !IOUtils.canReadFile( ( String ) obj, errorHolder ) )
			return "Problem with Weka classifier file: " + errorHolder.toString();

		return null;
	}

	@Override
	public SpotDetector< T > getDetector( final ImgPlus< T > img, final Map< String, Object > settings, final Interval interval, final int frame )
	{
		final int channel = ( Integer ) settings.get( KEY_TARGET_CHANNEL ) - 1;
		final ImgPlus< T > input = TMUtils.hyperSlice( img, channel, frame );
		final int classIndex = ( Integer ) settings.get( KEY_CLASS_INDEX );
		final double probaThreshold = ( Double ) settings.get( KEY_PROBA_THRESHOLD );
		final boolean simplify = true;
		final boolean is3D = img.dimensionIndex( Axes.Z ) >= 0;
		final WekaRunner< T > runner = new WekaRunner<>( settings.get( KEY_CLASSIFIER_FILEPATH ).toString(), is3D );
		if ( !runner.loadClassifier() )
		{
			final String errorMessage = runner.getErrorMessage();
			System.err.println( errorMessage );
			return null;
		}

		final WekaDetector< T > detector = new WekaDetector<>(
				runner,
				input,
				interval,
				classIndex,
				probaThreshold,
				simplify );
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
	public boolean has2Dsegmentation()
	{
		return true;
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
		settings.put( KEY_CLASSIFIER_FILEPATH, "" );
		return settings;
	}

	@Override
	public String getInfoText()
	{
		return INFO_TEXT;
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
	public String getUrl()
	{
		return DOC_URL;
	}
}
