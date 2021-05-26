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

import java.util.List;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings.TrackMateObject;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettingsIO;
import fiji.plugin.trackmate.util.TMUtils;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class WekaTestDrive
{

	public static < T extends RealType< T > & NativeType< T > > void main( final String[] args )
	{
		final String classifierPath = "/Users/tinevez/Projects/CCharendoff/Data/jyclassifier.model";
		final String targetImagePath = "/Users/tinevez/Projects/CCharendoff/Data/crop-2tp.tif";

		ImageJ.main( args );
		final ImagePlus imp = IJ.openImage( targetImagePath );
		imp.show();

		@SuppressWarnings( "unchecked" )
		final ImgPlus< T > allChannels = TMUtils.rawWraps( imp );
		final long targetChannel = 0;
		final long targetFrame = 0;
		final ImgPlus< T > input = TMUtils.hyperSlice( allChannels, targetChannel, targetFrame );

		final Interval interval = input;
		// Intervals.createMinSize( 464, 82, 325, 233 );
		final int classId = 0;
		final double probaThreshold = 0.5;
		final int numThreads = Runtime.getRuntime().availableProcessors();
		final boolean simplify = true;

		/*
		 * Execute segmentation.
		 */

		final long start0 = System.currentTimeMillis();

		final boolean is3D = input.dimensionIndex( Axes.Z ) >= 0;
		final WekaRunner< T > wekaRunner = new WekaRunner<>( classifierPath, is3D );
		wekaRunner.setNumThreads( numThreads );

		if ( !wekaRunner.loadClassifier() )
		{
			System.err.println( wekaRunner.getErrorMessage() );
			return;
		}

		final List< String > classNames = wekaRunner.getClassNames();
		System.out.println( String.format( "Found %d classes in classifier:", classNames.size() ) );
		classNames.forEach( s -> System.out.println( " - " + s ) );

		final RandomAccessibleInterval< T > probabilities = wekaRunner.computeProbabilities( input, interval, classId );
		if ( probabilities == null )
		{
			System.err.println( "Problem computing probabilities: " + wekaRunner.getErrorMessage() );
			return;
		}

		final List< Spot > spots0 = wekaRunner.getSpotsFromLastProbabilities( probaThreshold, simplify );
		if ( spots0 == null )
		{
			System.err.println( "Problem creating spots: " + wekaRunner.getErrorMessage() );
			return;
		}

		final long end0 = System.currentTimeMillis();
		System.out.println( String.format( "First run took %.2f seconds to run.", ( end0 - start0 ) / 1000. ) );

		/*
		 * Display results.
		 */

		final SpotCollection spots = new SpotCollection();
		spots.put( 0, spots0 );
		spots.setVisible( true );
		System.out.println( spots.toString() );

		final Model model = new Model();
		model.setSpots( spots, false );

		final DisplaySettings ds = DisplaySettingsIO.readUserDefault();
		ds.setSpotColorBy( TrackMateObject.SPOTS, Spot.QUALITY );
		ds.setSpotMinMax( 0., 1. );
		final HyperStackDisplayer displayer = new HyperStackDisplayer( model, new SelectionModel( model ), imp, ds );
		displayer.render();

		/*
		 * Redo analysis with another threshold.
		 */

		final long start1 = System.currentTimeMillis();

		final double probaThreshold2 = 0.8;
		final List< Spot > spots1 = wekaRunner.getSpotsFromLastProbabilities( probaThreshold2, simplify );

		final long end1 = System.currentTimeMillis();
		System.out.println( String.format( "Second run took %.2f seconds to run.", ( end1 - start1 ) / 1000. ) );

		spots.put( 1, spots1 );
		spots.setVisible( true );
		System.out.println( spots.toString() );

		displayer.refresh();
	}
}
