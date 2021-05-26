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

import java.util.ArrayList;
import java.util.List;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.detection.DetectionUtils;
import fiji.plugin.trackmate.detection.SpotDetector;
import fiji.plugin.trackmate.util.TMUtils;
import net.imagej.ImgPlus;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class WekaDetector< T extends RealType< T > & NativeType< T > > implements SpotDetector< T >
{

	private final static String BASE_ERROR_MESSAGE = "WekaDetector: ";

	protected final Interval interval;

	protected List< Spot > spots = new ArrayList<>();

	protected String baseErrorMessage;

	protected String errorMessage;

	protected long processingTime;

	private final WekaRunner< T > runner;

	private final ImgPlus< T > img;

	private final int classIndex;

	private final double probaThreshold;

	private final boolean simplify;

	public WekaDetector(
			final WekaRunner< T > runner,
			final ImgPlus< T > img,
			final Interval interval,
			final int classIndex,
			final double probaThreshold,
			final boolean simplify )
	{
		this.runner = runner;
		this.img = img;
		this.interval = DetectionUtils.squeeze( interval );
		this.classIndex = classIndex;
		this.probaThreshold = probaThreshold;
		this.simplify = simplify;
		this.baseErrorMessage = BASE_ERROR_MESSAGE;
	}

	@Override
	public boolean checkInput()
	{
		if ( null == img )
		{
			errorMessage = baseErrorMessage + "Image is null.";
			return false;
		}
		return true;
	}

	@Override
	public boolean process()
	{
		final long start = System.currentTimeMillis();

		final RandomAccessibleInterval< T > probabilities = runner.computeProbabilities( img, interval, classIndex );
		if ( probabilities == null )
		{
			errorMessage = BASE_ERROR_MESSAGE + "Problem computing probabilities: " + runner.getErrorMessage();
			return false;
		}

		spots = runner.getSpots( probabilities, TMUtils.getSpatialCalibration( img ), probaThreshold, simplify );
		if ( spots == null )
		{
			System.err.println( "Problem creating spots: " + runner.getErrorMessage() );
			return false;
		}

		final long end = System.currentTimeMillis();
		this.processingTime = end - start;
		return true;
	}

	@Override
	public List< Spot > getResult()
	{
		return spots;
	}

	@Override
	public String getErrorMessage()
	{
		return errorMessage;
	}

	@Override
	public long getProcessingTime()
	{
		return processingTime;
	}
}
