package fiji.plugin.trackmate.weka;

import java.io.IOException;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import fiji.plugin.trackmate.TrackMatePlugIn;
import ij.IJ;
import ij.ImageJ;

public class TrackMateWekaTestDrive
{

	public static void main( final String[] args ) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException
	{
		UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
		ImageJ.main( args );
		final String path = "samples/crop-2tp.tif";
		IJ.openImage( path ).show();
		new TrackMatePlugIn().run( null );
	}
}
