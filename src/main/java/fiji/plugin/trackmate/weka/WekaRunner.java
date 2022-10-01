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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.detection.MaskUtils;
import fiji.plugin.trackmate.util.TMUtils;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import net.imagej.ImgPlus;
import net.imagej.ops.MetadataUtil;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.MultiThreaded;
import net.imglib2.img.ImgView;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import trainableSegmentation.WekaSegmentation;

public class WekaRunner< T extends RealType< T > & NativeType< T > > implements MultiThreaded
{

	private final String classifierFilePath;

	private final boolean isProcessing3D;

	private String errorMessage;

	private WekaSegmentation segmentation;

	private int numThreads;

	private RandomAccessibleInterval< T > lastOutput;

	private double[] lastCalibration;

	public WekaRunner( final String classifierFilePath, final boolean isProcessing3D )
	{
		this.classifierFilePath = classifierFilePath;
		this.isProcessing3D = isProcessing3D;
		setNumThreads();
	}

	public String getErrorMessage()
	{
		return errorMessage;
	}

	public boolean loadClassifier()
	{
		errorMessage = null;
		IJ.redirectErrorMessages();
		segmentation = new WekaSegmentation( isProcessing3D );
		segmentation.setTrainingImage( NewImage.createByteImage( "DummyImage", 16, 16, 1, NewImage.FILL_BLACK ) );
		final boolean loadingOk = segmentation.loadClassifier( classifierFilePath );
		if ( !loadingOk )
		{
			errorMessage = "Problem loading the classifier for file " + classifierFilePath;
			return false;
		}
		return true;
	}

	public List< String > getClassNames()
	{
		errorMessage = null;
		if ( segmentation == null )
		{
			errorMessage = "The classifier is not loaded.";
			return null;
		}
		final int numOfClasses = segmentation.getNumOfClasses();
		final String[] classLabels = segmentation.getClassLabels();
		final List< String > classNames = new ArrayList<>( numOfClasses );
		for ( int i = 0; i < numOfClasses; i++ )
			classNames.add( classLabels[ i ] );
		return Collections.unmodifiableList( classNames );
	}

	public RandomAccessibleInterval< T > computeProbabilities(
			final ImgPlus< T > input,
			final Interval interval,
			final int classId )
	{
		errorMessage = null;
		if ( segmentation == null )
		{
			errorMessage = "The classifier is not loaded.";
			return null;
		}
		if ( classId >= segmentation.getNumOfClasses() )
		{
			errorMessage = "Requested class #" + ( classId + 1 ) + ", but classifier only knows " + segmentation.getNumOfClasses() + " classes.";
			return null;
		}

		// Properly set the image to process: crop it.
		final RandomAccessibleInterval< T > crop = Views.interval( input, interval );
		final RandomAccessibleInterval< T > zeroMinCrop = Views.zeroMin( crop );
		final ImgPlus< T > cropped = new ImgPlus<>( ImgView.wrap( zeroMinCrop, input.factory() ) );
		MetadataUtil.copyImgPlusMetadata( input, cropped );

		// Run Weka.
		final ImagePlus vimp = ImageJFunctions.wrap( cropped, "Weka-to-segment" );
		final ImagePlus probas = segmentation.applyClassifier( vimp, numThreads, true );

		// Convert to Img and extract desired class.
		@SuppressWarnings( "unchecked" )
		final ImgPlus< T > probaImp = TMUtils.rawWraps( probas );
		final ImgPlus< T > classProba = TMUtils.hyperSlice( probaImp, classId, 0 );


		// Translate back to ROI origin.
		final RandomAccessibleInterval< T > output;
		if ( isProcessing3D )
		{
			/*
			 * In 3D, the output for each class are interleaved in the Z
			 * dimension.... So we need to de-interleave them manually.
			 */
			final RandomAccessibleInterval< T > deinterleaved = deinterleave( classProba, classId, segmentation.getNumOfClasses() );
			output = Views.translate( deinterleaved, interval.min( 0 ), interval.min( 1 ), interval.min( 2 ) );
		}
		else
		{
			output = Views.translate( classProba, interval.min( 0 ), interval.min( 1 ) );
		}
		this.lastOutput = output;
		this.lastCalibration = TMUtils.getSpatialCalibration( input );
		return output;
	}

	public List< Spot > getSpotsFromLastProbabilities( final double threshold, final boolean simplify )
	{
		errorMessage = null;
		if ( segmentation == null )
		{
			errorMessage = "The classifier is not loaded.";
			return null;
		}
		if ( lastOutput == null )
		{
			errorMessage = "Probabilities have not been computed yet.";
			return null;
		}
		return getSpots( lastOutput, lastCalibration, threshold, simplify );
	}

	public List< Spot > getSpots( final RandomAccessibleInterval< T > proba, final double[] calibration, final double threshold, final boolean simplify )
	{
		final List< Spot > spots;
		if ( isProcessing3D )
		{
			spots = MaskUtils.fromThreshold(
					proba,
					proba,
					calibration,
					threshold,
					numThreads,
					proba );
		}
		else
		{
			spots = MaskUtils.fromThresholdWithROI(
					proba,
					proba,
					calibration,
					threshold,
					simplify,
					numThreads,
					proba );
		}
		return spots;
	}

	private RandomAccessibleInterval< T > deinterleave( final RandomAccessibleInterval< T > proba, final long start, final long step )
	{
		final List< RandomAccessibleInterval< T > > slices = new ArrayList<>();
		final long nz = proba.dimension( 2 );
		for ( long z = start; z < nz; z = z + step )
		{
			final RandomAccessibleInterval< T > slice = Views.hyperSlice( proba, 2, z );
			slices.add( slice );
		}
		return Views.stack( slices );
	}

	@Override
	public void setNumThreads()
	{
		this.numThreads = Runtime.getRuntime().availableProcessors();
	}

	@Override
	public void setNumThreads( final int numThreads )
	{
		this.numThreads = numThreads;
	}

	@Override
	public int getNumThreads()
	{
		return numThreads;
	}
}
