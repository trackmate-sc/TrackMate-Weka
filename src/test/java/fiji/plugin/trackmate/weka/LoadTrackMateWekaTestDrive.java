package fiji.plugin.trackmate.weka;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import fiji.plugin.trackmate.LoadTrackMatePlugIn;
import ij.ImageJ;

public class LoadTrackMateWekaTestDrive
{

	public static void main( final String[] args ) throws ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException
	{
		UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
		ImageJ.main( args );
		final String path = "samples/crop-2tp.xml";
		new LoadTrackMatePlugIn().run( path );
	}
}
