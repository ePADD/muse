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
package edu.stanford.muse.webapp;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.GroupAssigner;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

/** Manage save/load/delete of groupings */
public class GroupsConfig {
	     public static Log log = LogFactory.getLog(GroupsConfig.class);
	     private static final String GROUPING_SUFFIX = ".groups"; // all session files end with .session	
	     private static final String DEFAULT_CACHE_DIR = System.getProperty("user.home") + File.separator + ".muse" + File.separator + "user";

	     /** loads session with the given name, and puts it into the given http session. returns true if it succeeded */
	     public static boolean load(HttpSession session, String title)
	 	{
	 		boolean success = true;
	 		String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
	 		Archive archive = JSPHelper.getArchive(session);
	 		String filename = "";
	 		if (cacheDir == null)
	 		{
	 			cacheDir = DEFAULT_CACHE_DIR;
	 			session.setAttribute("cacheDir", cacheDir);
	 		}
	 		
	 		filename = cacheDir + File.separatorChar + title + GROUPING_SUFFIX;
	 		if (!new File(filename).exists())
	 			return false;
	 		
 			JSPHelper.log.info("Loading grouping from " + filename);

	 		// keep reading till eof exception
	 		try {
	 			GroupAssigner ga = (GroupAssigner) Util.readObjectFromFile(filename);
	 			archive.setGroupAssigner(ga);
	 		} catch (Exception e) { JSPHelper.log.warn("Warning unable to load groups: " + Util.stackTrace(e)); success = false; }

	 		return success;
	 	}

	 	public static Collection<String> list() throws IOException, ClassNotFoundException
	 	{
	 		return list(null);
	 	}
	 	
	 	/** returns a list of available session names */
	 	public static Collection<String> list(String dir) throws IOException, ClassNotFoundException
	 	{	
	 		if (dir == null)
	 			dir = DEFAULT_CACHE_DIR;
	 		
	 		return Util.filesWithSuffix(dir, GROUPING_SUFFIX);
	 	}
	 	
	 	/** returns status of success */
	 	public static boolean delete(String dir, String session) throws IOException, ClassNotFoundException
	 	{
	 		if (dir == null)
	 			return false;
	 		String filename = dir + File.separatorChar + session + GROUPING_SUFFIX;
	 		File f = new File(filename);
	 		if (!f.exists())
	 			return false; // empty result
	 		return f.delete(); 
	 	}
	 	
	 	/** writes out all serializable objects in the current session to a file */
	 	public static boolean save(HttpSession session, String title) throws IOException
	 	{
	 		String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
	 		if (cacheDir == null)
	 			return false;
	 		String dir = cacheDir;
	 		String filename = dir + File.separatorChar + title + GROUPING_SUFFIX;
	 		Archive archive = JSPHelper.getArchive(session);
 			GroupAssigner ga = archive.groupAssigner;
 			JSPHelper.log.info("Saving grouping to " + filename);
 			boolean success = true; 
 			try { Util.writeObjectToFile(filename, ga); } 
 			catch (Exception e) { JSPHelper.log.warn("Warning unable to save groups: " + Util.stackTrace(e)); success = false; }
 			return success;
	 	}
	 }
