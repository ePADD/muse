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

import java.net.URI;
import java.net.URISyntaxException;

import org.xhtmlrenderer.swing.NaiveUserAgent;

public class HTMLImagerUserAgent extends NaiveUserAgent
{
	private String base;   

	/**
	 * Check for and resolve a possible relative URI to a full URL.
	 *
	 * @param uri The uri that may need to be resolved.
	 *
	 * @returns If the provided uri is relative returns a full URL to the
	 *          relative resource.  If the provided uri is not relative then
	 *          returns it unmodified.
	 */
	public String resolveURI( String uri )
	{
		String resolved = null;
		String trimmed  = uri.trim( );

		if ( !trimmed.startsWith( "http://" ) )
		{
			StringBuilder buf = new StringBuilder( 1024 );

			buf.append( base );

			if ( !trimmed.startsWith( "/" ) )
			{
				buf.append( "/" );
			}

			buf.append( trimmed );

			resolved = buf.toString( );
		}
		else
		{
			resolved = trimmed;
		}

		return resolved;
	}

	/**
	 * Construct a new HTMLImagerUserAgent.
	 *
	 * @param url The URL of the HTML page that is being retrieved.
	 */
	protected HTMLImagerUserAgent( String url )
	{
		StringBuilder buf = new StringBuilder( );       

		try
		{
			URI uri = new URI( url );               

			buf.append( "http://" );
			buf.append( uri.getHost( ) );

			int port = uri.getPort( );

			if ( port >= 0 )
			{
				buf.append( ":" );
				buf.append( port );
			}
		}

		catch ( URISyntaxException use )
		{
			buf.append( "" );
		}

		this.base = buf.toString( );
	}   
}