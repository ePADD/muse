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
package edu.stanford.muse.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;

public class BundledMain {

	public static void copyResource(String resource, String filename) throws IOException
	{
		final URL url = BundledMain.class.getClassLoader().getResource(resource);
		if (url == null)
		{
			System.err.println ("Sorry! Unable to locate file on classpath: " + resource);
			throw new RuntimeException();
		}		
		InputStream is = url.openStream();
		Main.copy_stream_to_file(is, filename);
		is.close();
	}
	
	public static void main (String args[]) throws Exception
	{
		String tmp = System.getProperty("java.io.tmpdir");
		tmp += File.separatorChar + "muse";
		new File(tmp + File.separator + "sessions").mkdirs();
		final String[] files = {
								 "sessions/palin.session.v2",
								 "mbox.local__mbox__palin.contents",
								 //"mbox.local__mbox__palin.headers" // not really used/needed, although build.xml currently puts it in the released jar anyway
								};
		for (String file : files) { 
			copyResource(file, tmp + File.separatorChar + file); // exact file name must be preserved for rebase to work correctly
		}

		// add the groups mode arg to any other args
		String newArgs[] = new String[2+args.length];
		newArgs[0] = "--start-page";
		newArgs[1] = "index.jsp?session=palin" + "&cacheDir=" + URLEncoder.encode(tmp) + "&contentsFile=mbox.local__mbox__palin.contents";
		System.arraycopy (args, 0, newArgs, 2, args.length);
		Main.KILL_AFTER_MILLIS = 2L * 3600 * 1000; // kill after 2 hours
		Main.main(newArgs);
	}
}
