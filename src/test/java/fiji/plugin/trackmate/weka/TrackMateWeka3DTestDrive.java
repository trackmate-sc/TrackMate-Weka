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

import fiji.plugin.trackmate.TrackMatePlugIn;
import ij.IJ;
import ij.ImageJ;

public class TrackMateWeka3DTestDrive
{

	public static void main( final String[] args )
	{
		ImageJ.main( args );
		final String path = "samples/merged.tif";
//		final String path = "samples/mesh/CElegansMask3D.tif";
		IJ.openImage( path ).show();
		new TrackMatePlugIn().run( null );
	}
}
