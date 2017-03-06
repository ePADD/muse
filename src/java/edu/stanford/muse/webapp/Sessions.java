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
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.*;

/** class to manage sessions. sessions are stored as .session files in the baseDir/sessions (which itself is stored in the session). */
public class Sessions {
    public static Log log = LogFactory.getLog(Sessions.class);
    public static final String SESSION_SUFFIX = ".archive.v2"; // all session files end with .session
	public  static String CACHE_BASE_DIR = null; // overruled (but not overwritten) by session's attribute "cacheDir"
    public static String CACHE_DIR = null; // overruled (but not overwritten) by session's attribute "cacheDir"
    private static String SESSIONS_DIR = null;
    public static String MUSE_DIRNAME = ".muse"; // clients might choose to override this
    
	private static Map< String, SoftReference< Map<String, Object> > > globalSessions = new LinkedHashMap< String, SoftReference< Map<String,Object> > >();
	private static Set<String> loadPendingSet = new LinkedHashSet<String>();
	
	public static Map< String, Map<String, String> > archivesMap = null;

	private static String getVarOrDefault(String prop_name, String default_val)
	{
		String val = System.getProperty(prop_name);
		if (!Util.nullOrEmpty(val))
			return val;
		else
			return default_val;
	}

	static {
		MUSE_DIRNAME = getVarOrDefault("muse.defaultArchivesDir", System.getProperty("user.home") + File.separator + ".muse");
		CACHE_BASE_DIR = getVarOrDefault("muse.dir.cache_base", MUSE_DIRNAME);
		CACHE_DIR      = getVarOrDefault("muse.dir.cache"  , CACHE_BASE_DIR + File.separator + "user"); // warning/todo: this "-D" not universally honored yet, e.g., user_key and fixedCacheDir in MuseEmailFetcher.java
		SESSIONS_DIR   = getVarOrDefault("muse.dir.sessions", CACHE_DIR + File.separator + Archive.SESSIONS_SUBDIR); // warning/todo: this "-D" not universally honored yet, e.g., it is hard-coded again in saveSession() (maybe saveSession should actually use getSessinoDir() rather than basing it on cacheDir)
	}

	public static String getArchivesIndexFilename()
	{
		//return getDefaultSessionDir() + File.separatorChar + "archives.xml";
		return getDefaultRootDir() + File.separatorChar + "archives.xml";
	}

	private static void addXmlTagToMap(Map<String, String> map, Element e, String tag)
	{
		addXmlTagToMap(map, e, tag, false);
	}

	private static void addXmlOptionalTagToMap(Map<String, String> map, Element e, String tag)
	{
		addXmlTagToMap(map, e, tag, true);		
	}

	private static void addXmlTagToMap(Map<String, String> map, Element e, String tag, boolean optional)
	{
		NodeList nl = e.getElementsByTagName(tag);
		if (optional && (nl == null || nl.getLength() == 0)) {
			map.put(tag, null);
			return;
		}
		int len = nl != null ? nl.getLength() : 0;
		assert(nl != null && len == 1);
		Node node = nl.item(0).getFirstChild();
		if (node == null) {
			// blank config => null
			map.put(tag, null);
		} else {
			map.put(tag, node.getNodeValue());
		}
	}

	public static boolean parseArchivesXml(String xmlFile)
	{
		if (!(new File(xmlFile)).exists())
			return false;
			
		if (archivesMap != null) // can't reload (any other) archives.xml
			return true;

		boolean result = true;

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		org.w3c.dom.Document dom;

		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			dom = db.parse(xmlFile);
			Element e_root = dom.getDocumentElement();

			// get a nodelist of <item> elements
			NodeList nl = e_root.getElementsByTagName("item");
			assert(nl != null);

			archivesMap = new LinkedHashMap<String, Map<String,String>>();

			for(int i = 0 ; i < nl.getLength();i++) {
				// get an <item> elemment
				Element e = (Element)nl.item(i);
				Map<String, String> map = new LinkedHashMap<String, String>();

				// extract following tags
				addXmlTagToMap(map, e, "name");
				addXmlTagToMap(map, e, "number");
				addXmlTagToMap(map, e, "description");
				addXmlTagToMap(map, e, "file");
				addXmlOptionalTagToMap(map, e, "lexicon");
				addXmlOptionalTagToMap(map, e, "findingaids");
				addXmlOptionalTagToMap(map, e, "searchworks");

				log.info("Added archive " + map);
				archivesMap.put(map.get("number"), map);
			}
		} catch(Exception e) {
			e.printStackTrace();
			result = false;
		}

		return result;
	}

	public static Map<String, String> getArchiveInfoMap(String archiveNumber)
	{
		if (archivesMap == null) {
			parseArchivesXml(getArchivesIndexFilename());
		}
		if (archivesMap == null) {
			return new LinkedHashMap<String, String>();
		}
		return archivesMap.get(archiveNumber);
	}

	public static String getDefaultSessionDir()
    {
    	return SESSIONS_DIR;
    }

	public static String getDefaultCacheDir()
	{
		return CACHE_DIR;
	}

	public static String getDefaultRootDir()
	{
		return CACHE_BASE_DIR;
	}

	/** WARNING: this will SET the session dir to default (which has led to bugs). */
	/*
	public static String getSessionDir(HttpSession session, String userKey)
    {
		if (!Util.nullOrEmpty(userKey))
			return getDefaultBaseDir() + File.separatorChar + userKey + File.separatorChar + Archive.SESSIONS_SUBDIR;
    	// should probably decouple sessionDir and cacheDir?
    	if (JSPHelper.getSessionAttribute(session, "cacheDir") == null) {
    		session.setAttribute("cacheDir", CACHE_DIR);
    		session.setAttribute("userKey", "user");
    	}
    	return (String) JSPHelper.getSessionAttribute(session, "cacheDir") + File.separatorChar + Archive.SESSIONS_SUBDIR;
    }
	*/

    private static String getSessionFilename(String baseDir, String title)
    {
    	if (Util.nullOrEmpty(baseDir)) return null;
    	return baseDir + File.separatorChar + Archive.SESSIONS_SUBDIR + File.separatorChar + title + SESSION_SUFFIX;
    }

	/** gets the cache (base) dir for the current session */
    /*
	private static String getBaseDir(HttpSession session)
	{
		// TODO: SECURITY NOTE FOR PUBLIC MODE:
		// All session attributes are user inputs and should be assumed malicious and need to be validated before used.
		// For example, don't use cacheDir/ARCHIVE_NAME/userKey directly to construct the location of the files to be read.
		// But should maintain a map that maps from archiveId/userKey to the proper location. If not exist in the map, gives error.
		if (ModeConfig.isMultiUser()) {
			// disallow manual cacheDir change by user via browser in multi-user mode
			String userKey = JSPHelper.getUserKey(session);
			if (Util.nullOrEmpty(userKey))
				return null;
			else
				return Sessions.getDefaultRootDir() + File.separatorChar + userKey;
		} else {
			String baseDir = (String) session . getAttribute("cacheDir"); // intentional use of session . getSessionAttribute()
			if (Util.nullOrEmpty(baseDir))
				return Sessions.getDefaultCacheDir(); // TODO: alternative to this is to have launcher's Main set the initial cacheDir by making request to index.jsp?cacheDir=...
			else
				return baseDir;
		}
	}
	*/

	/** returns status of success */
	public static boolean deleteSession(String dir, String session) throws IOException, ClassNotFoundException
	{
		if (dir == null)
			return false;
		String sessionsFile = getSessionFilename(dir, session);
		File f = new File(sessionsFile);
		if (!f.exists())
			return false; // empty result
		return f.delete(); 
	}

	/** gets the shared/global/server-side session map that correspond to the specified session file.
	 *  if the session map has never been loaded or its loaded soft reference has been released, will (re)load the file.
	 * @throws IOException 
	 * @throws LockObtainFailedException 
	 * @throws CorruptIndexException 
	 */
	private static Map<String, Object> getGlobalSession(String session_filename, String baseDir) throws IOException
	{
		Map<String, Object> map = null;
		synchronized (globalSessions) {
			try {
				map = globalSessions.get(session_filename).get();
			} catch (NullPointerException e) {
				// globalSessions.get(session_filename) is null
				log.warn("Archive " + session_filename + " may be inappropriately loaded/cleaned up previously");
				// be lenient and create a place holder for it.
				globalSessions.put(session_filename, new SoftReference<Map<String,Object>>(null));
			}
		}

		if (map == null) {
			// GCed, reload and it will be temporarily pinned by normal ref
			synchronized (loadPendingSet) {
				if (loadPendingSet.contains(session_filename)) {
					try {
						do {
							log.info("Waiting for pending load of " + session_filename);
							loadPendingSet.wait();
						} while (loadPendingSet.contains(session_filename));
					} catch (InterruptedException e) {
						log.info("Exception: " + e + " : " + Util.stackTrace());
					}
					log.info("Pending load of " + session_filename + " finished. Using its result without duplicated loading.");
					return getGlobalSession(session_filename, baseDir); // retry
				} else {
					loadPendingSet.add(session_filename);
				}
			}

			boolean succeeded = false;
			try {
				map = SimpleSessions.loadSessionAsMap(session_filename, baseDir, true /* readOnly */); // not done in synchronized(globalSessions) for performance

				synchronized (globalSessions) {
					Map<String, Object> map_global = globalSessions.get(session_filename).get(); // should not throw NullPointerException again
					if (map_global == null) {
						log.info("Loaded/reloaded " + session_filename + " into global session");
						globalSessions.put(session_filename, new SoftReference<Map<String,Object>>(map));
					} else {
						// may be concurrently put in by other user loading the same archive
						log.info("Redundant load discarded");
						map = map_global;
					}
				}

				succeeded = true;
			} finally {
				// release lock even if there is exception
				synchronized (loadPendingSet) {
					loadPendingSet.remove(session_filename);
					loadPendingSet.notifyAll();
				}
				if (!succeeded)
					log.warn("Failed to load " + session_filename);
			}
		}
	
		return map;
	}

	// todo: this should detach the shared session from the per-user session rather than blindly/selectively remove all attributes
	private static void removeAllAttributes(HttpSession session)
	{
		java.util.Enumeration keys = session.getAttributeNames();
	    while (keys.hasMoreElements())
	    {
	    	String key = (String) keys.nextElement();
	    	if ("cacheDir".equals(key) || "userKey".equals(key) || "museEmailFetcher".equals(key))
	    		// do not remove "cacheDir"/"userKey" which may be user-provided.
	    		// do not remove "museEmailFetcher" which is required for fetching.
	    		// TODO: this is getting ugly. maybe we should do the opposite = removing only certain attributes.
	    		continue;
			session.removeAttribute(key);
	    }
	}

	/**
	 * return the public archive specified by archiveId. the archive is shared by all clients.
	 * if the archive has never been loaded or has been garbage collected (due to weak ref),
	 * will load the "default" file from <default_root_dir>/<archive_file>/ where archive_file
	 * is determined by archiveId according to <default_root_dir>/archives.xml.
	 * TODO: following session attributes will also be set in the given session (for compatibility
	 * with the code being shared with Desktop mode in newBrowse.jsp, etc.):
	 *   "archive", "emailDocs", "lexicon".
	 * @param session
	 * @param archiveId
	 * @return
	 * @throws CorruptIndexException
	 * @throws LockObtainFailedException
	 * @throws IOException
	 */
	public static Archive loadSharedArchiveAndPrepareSession(HttpSession session, String archiveId) throws IOException
    {
    	Util.ASSERT(ModeConfig.isMultiUser());
		log.info ("Loading shared session: " + archiveId);
    	Archive result = null;

    	if (Util.nullOrEmpty(archiveId)) return result;

    	String userKey = getArchiveInfoMap(archiveId).get("file"); // this is equivalent to userKey
    	if (Util.nullOrEmpty(userKey)) return result;

    	// reload the archive on-demand if necessary
    	String title = "default";

		String baseDir = getDefaultRootDir() + File.separatorChar + userKey;
		String filename = getSessionFilename(baseDir, title);

		if (!new File(filename).exists()) return result;

		synchronized (globalSessions) {
			if (!globalSessions.containsKey(filename)) {
				// has never been loaded = need a fresh load.
				log.info ("Archive has not been loaded, needs a fresh load: " + archiveId);
				globalSessions.put(filename, new SoftReference<Map<String,Object>>(null));
				removeAllAttributes(session);
			} else
				log.info ("Good, archive has already been loaded: " + archiveId);
		}

		Map<String, Object> loaded_map = null;
		try {
			log.info ("Loading global session from basedir: " + baseDir + " filename: " + filename);
			loaded_map = getGlobalSession(filename, baseDir); // perform the actual load
		} catch (Exception e) {
			Util.print_exception(e, log);
		}

		if (!Util.nullOrEmpty(loaded_map)) {
			result = (Archive) loaded_map.get("archive");
			if (result != null && session != null) {
				session.setAttribute("archive", result);
				session.setAttribute("emailDocs", result.getAllDocs());
				session.setAttribute("lexicon", result.getLexicon("general"));
			}
		}

		return result;
    }

    /*
     * Expecting parameters "title", "trimArchive", "forPublicMode" in the request.
     * Write out a fresh archive at <user.home>/muse.<title>.
     */
    public static Pair<Boolean,String> exportArchive(HttpServletRequest request) throws Exception
    {
    	HttpSession session = request.getSession();

    	Archive archive = JSPHelper.getArchive(session);
    	String title = request.getParameter("title");
    	String base_dir = System.getProperty("user.home") + java.io.File.separator + "muse." + title;
    	String session_name = "default";

    	boolean succeeded = false;
    	String message = "";

    	Collection<EmailDocument> emailDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
    	if (emailDocs != null)
    	{
    		boolean trimArchive = request.getParameter("trimArchive") != null;
    		boolean forPublicMode = request.getParameter("forPublicMode") != null;
    		/* UNDESIRABLE as this will permanently change the index.
    		if (trimArchive) {
    			archive.trimArchive(emailDocs); // fine if its null, nothing will be done (shouldn't happen)
    		}
    		*/
    		String dir = archive.export(trimArchive ? emailDocs : archive.getAllDocs(), forPublicMode ? true : false, base_dir, session_name);
    		// TODO: may choose to invalidate session here since the state of archive has already changed
    		succeeded = dir != null;
    		if (!succeeded)
    			message = "Archive export failed";
    		else
    			message = "Archive successfully exported to " + dir;
    	} else {
    		message = "No messages in this session";
    	}

    	return new Pair<Boolean,String>(succeeded, message);
    }
}
