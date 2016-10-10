/*
 Copyright (C) 2012 The Stanford MobiSocial Laboratory

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

// see: http://www.ldotc.com/Web+Page+Thumbnails+in+Java.html
package edu.stanford.muse.webapp;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;
import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.simple.Graphics2DRenderer;
import org.xhtmlrenderer.simple.extend.XhtmlNamespaceHandler;


public class HTMLToImage {

	/** Logger for trace info. */
	private static final Logger log = Logger.getLogger(HTMLToImage.class );

	/**
	 * Convert an HTML page at the specified URL into an image who's data is
	 * written to the provided output stream.
	 *
	 * @param url    URL to the page that is to be imaged.
	 * @param os     An output stream that is to be opened for writing.  Image
	 *               data will be written to the provided stream.  The stream
	 *               will not be closed under any circumstances by this method.
	 * @param width  The desired width of the image that will be created.
	 * @param height The desired height of the image that will be created.
	 *
	 * @returns true if the page at the provided URL was loaded, converted to an
	 *          image, and the image data has been written to the output stream,
	 *          false if an error has ocurred along the way.
	 *
	 * @throws HTMLImagerException if an error has ocurred.
	 */
	public static boolean image( String url,
			OutputStream os,
			int          width,
			int          height )
	{
		if ( log.isDebugEnabled( ) )
			log.debug( "Imaging url '" + url + "'." );

		boolean successful = false;

		try
		{
			HttpClient httpClient = new HttpClient( );
			GetMethod  getMethod  = new GetMethod( url );

			httpClient.executeMethod( getMethod );

			int httpStatus = getMethod.getStatusCode( );

			if ( httpStatus == HttpServletResponse.SC_OK )
			{
				Tidy tidy = new Tidy( );

				tidy.setQuiet( true );
				tidy.setXHTML( true );
				tidy.setHideComments( true );
				tidy.setInputEncoding( "UTF-8" );
				tidy.setOutputEncoding( "UTF-8" );
				tidy.setShowErrors( 0 );
				tidy.setShowWarnings( false );

				Document doc = tidy.parseDOM( getMethod.getResponseBodyAsStream( ),
						null );

				if ( doc != null )
				{
					BufferedImage      buf       = new BufferedImage( width, height,
							BufferedImage.TYPE_INT_RGB );
					Graphics2D         graphics  = (Graphics2D)buf.getGraphics( );
					Graphics2DRenderer renderer  = new Graphics2DRenderer( );
					SharedContext      context   = renderer.getSharedContext( );
					UserAgentCallback  userAgent = new HTMLImagerUserAgent( url );

					context.setUserAgentCallback( userAgent );
					context.setNamespaceHandler( new XhtmlNamespaceHandler( ) );

					renderer.setDocument( doc, url );
					renderer.layout( graphics, new Dimension( width, height ) );
					renderer.render( graphics );
					graphics.dispose( );

					/*
					JPEGEncodeParam param = JPEGCodec.getDefaultJPEGEncodeParam( buf );

					param.setQuality( (float)1.0, false );

					JPEGImageEncoder imageEncoder = JPEGCodec.createJPEGEncoder( os,
							param );

					imageEncoder.encode( buf );
*/
					successful = true;
				}
				else
				{
					if ( log.isDebugEnabled( ) )
						log.debug( "Unable to image URL '" + url +
								"'.  The HTML that was returned could not be tidied." );
				}
			}
			else
			{
				if ( log.isDebugEnabled( ) )
					log.debug( "Unable to image URL '" + url +
							"'.  Server returned status code '" + httpStatus +
					"'." );
			}
		}

		catch ( Exception e )
		{
			throw new RuntimeException( "Unable to image URL '" + url + "'.", e );
		}

		return successful;
	}
}
