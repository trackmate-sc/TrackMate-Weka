/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2021 - 2022 TrackMate developers.
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
import static fiji.plugin.trackmate.weka.WekaDetectorFactory.KEY_CLASSIFIER_FILEPATH;
import static fiji.plugin.trackmate.weka.WekaDetectorFactory.KEY_CLASS_INDEX;
import static fiji.plugin.trackmate.weka.WekaDetectorFactory.KEY_PROBA_THRESHOLD;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.detection.DetectionUtils;
import fiji.plugin.trackmate.detection.SpotDetectorFactoryBase;
import fiji.plugin.trackmate.features.FeatureFilter;
import fiji.plugin.trackmate.io.IOUtils;
import fiji.plugin.trackmate.util.DetectionPreview;
import fiji.plugin.trackmate.util.TMUtils;
import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class WekaDetectionPreviewer< T extends RealType< T > & NativeType< T > > extends DetectionPreview
{

	private ImagePlus previousImp;

	private int previousFrame = -1;

	private String previousClassifierFilePath;

	private WekaRunner< T > wekaRunner;

	private int previousClassIndex = -1;

	private int previousChannel;

	public WekaDetectionPreviewer(
			final Model model,
			final Settings settings,
			final Supplier< Map< String, Object > > detectionSettingsSupplier,
			final Supplier< Integer > currentFrameSupplier )
	{
		super(
				model,
				settings,
				new WekaDetectorFactory<>(),
				detectionSettingsSupplier,
				currentFrameSupplier,
				null,
				null,
				null );
	}

	@Override
	protected Pair< Model, Double > runPreviewDetection(
			final Settings settings,
			final int frame,
			final SpotDetectorFactoryBase< ? > detectorFactory,
			final Map< String, Object > detectorSettings,
			final String thresholdKey )
	{
		final Logger logger = getLogger();

		/*
		 * Unwrap settings.
		 */

		final Map< String, Object > dsettings = new HashMap<>( detectorSettings );
		@SuppressWarnings( "unchecked" )
		final ImgPlus< T > img = TMUtils.rawWraps( settings.imp );
		final int channel = ( Integer ) dsettings.get( KEY_TARGET_CHANNEL ) - 1;
		final ImgPlus< T > input = TMUtils.hyperSlice( img, channel, frame );
		final int classIndex = ( Integer ) dsettings.get( KEY_CLASS_INDEX );
		final double probaThreshold = ( Double ) dsettings.get( KEY_PROBA_THRESHOLD );
		final boolean simplify = true;

		final boolean is3D = input.dimensionIndex( Axes.Z ) >= 0;
		// First test to make sure we can read the classifier file.
		final Object obj = dsettings.get( KEY_CLASSIFIER_FILEPATH );
		if ( obj == null )
		{
			logger.error( "The path to the Weka classifier file is not set." );
			return null;
		}

		final String classifierFilePath = ( String ) obj;
		final StringBuilder errorHolder = new StringBuilder();
		if ( !IOUtils.canReadFile( classifierFilePath, errorHolder ) )
		{
			logger.error( "Problem with Weka classifier file: " + errorHolder.toString() );
			return null;
		}

		/*
		 * Shall we recompute probabilities?
		 */

		boolean recomputeProba = false;
		if ( settings.imp != previousImp )
			recomputeProba = true;
		previousImp = settings.imp;

		if ( frame != previousFrame )
			recomputeProba = true;
		previousFrame = frame;

		if ( !classifierFilePath.equals( previousClassifierFilePath ) )
			recomputeProba = true;
		previousClassifierFilePath = classifierFilePath;

		if ( classIndex != previousClassIndex )
			recomputeProba = true;
		previousClassIndex = classIndex;

		if ( channel != previousChannel )
			recomputeProba = true;
		previousChannel = channel;

		if ( recomputeProba || wekaRunner == null )
		{
			logger.log( "Recomputing probabilities." );
			wekaRunner = new WekaRunner<>( classifierFilePath, is3D );
			wekaRunner.setNumThreads();

			if ( !wekaRunner.loadClassifier() )
			{
				logger.error( wekaRunner.getErrorMessage() );
				return null;
			}

			final Interval interval = DetectionUtils.squeeze( TMUtils.getInterval( img, settings ) );
			final RandomAccessibleInterval< T > probabilities = wekaRunner.computeProbabilities( input, interval, classIndex );
			if ( probabilities == null )
			{
				logger.error( "Problem computing probabilities: " + wekaRunner.getErrorMessage() );
				return null;
			}
		}

		logger.log( "Creating spots from probabilities." );
		final List< Spot > spots = wekaRunner.getSpotsFromLastProbabilities( probaThreshold, simplify );
		if ( spots == null )
		{
			logger.error( "Problem creating spots: " + wekaRunner.getErrorMessage() );
			return null;
		}

		final Model model = new Model();
		model.getSpots().put( frame, spots );
		model.getSpots().filter( new FeatureFilter( Spot.QUALITY, probaThreshold, true ) );

		return new ValuePair< Model, Double >( model, Double.NaN );
	}

	public List< String > getClassNames( final String classifierFilePath, final Logger logger, final boolean is3D )
	{
		if ( !classifierFilePath.equals( previousClassifierFilePath ) || wekaRunner == null )
		{
			logger.log( "Discovering class names in classifier." );
			wekaRunner = new WekaRunner<>( classifierFilePath, is3D );
			wekaRunner.setNumThreads();

			if ( !wekaRunner.loadClassifier() )
			{
				logger.error( wekaRunner.getErrorMessage() );
				return null;
			}
		}
		previousClassifierFilePath = classifierFilePath;
		final List< String > classNames = wekaRunner.getClassNames();
		logger.log( "Found " + classNames.size() + " classes in classifier." );
		return classNames;
	}
}
