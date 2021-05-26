package fiji.plugin.trackmate.weka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.detection.MaskUtils;
import fiji.plugin.trackmate.util.TMUtils;
import ij.IJ;
import ij.ImagePlus;
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
		final RandomAccessibleInterval< T > output = Views.translate( classProba, interval.min( 0 ), interval.min( 1 ) );
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
		final List< Spot > spots = MaskUtils.fromThresholdWithROI(
				proba,
				proba,
				calibration,
				threshold,
				simplify,
				numThreads,
				proba );
		return spots;
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
