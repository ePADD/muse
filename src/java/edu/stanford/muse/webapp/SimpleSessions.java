package edu.stanford.muse.webapp;

import edu.stanford.muse.email.MuseEmailFetcher;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Archive.ProcessingMetadata;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.Lexicon;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SimpleSessions {
	public static Log	log	= LogFactory.getLog(Sessions.class);

	/**
	 * loads session from the given filename, and returns the map of loaded
	 * attributes.
	 * if readOnly is false, caller MUST make sure to call packIndex.
	 * baseDir is Indexer's baseDir (path before "indexes/")
	 * 
	 * @throws IOException
	 * @throws LockObtainFailedException
	 * @throws CorruptIndexException
	 */
	public static Map<String, Object> loadSessionAsMap(String filename, String baseDir, boolean readOnly) throws CorruptIndexException, LockObtainFailedException, IOException
	{
		log.info("Loading session from file " + filename + " size: " + Util.commatize(new File(filename).length() / 1024) + " KB");

		ObjectInputStream ois = null;

		// keep reading till eof exception
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		try {
			ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(filename)));

			while (true)
			{
				String key = (String) ois.readObject();
				log.info("loading key: " + key);
				try {
					Object value = ois.readObject();
					result.put(key, value);
				} catch (InvalidClassException ice)
				{
					log.error("Bad version for value of key " + key + ": " + ice + "\nContinuing but this key is not set...");
				} catch (ClassNotFoundException cnfe)
				{
					log.error("Class not found for value of key " + key + ": " + cnfe + "\nContinuing but this key is not set...");
				}
			}
		} catch (EOFException eof) {
			log.info("end of session file reached");
		} catch (Exception e) {
			log.warn("Warning unable to load session: " + Util.stackTrace(e));
			result.clear();
		}

		if (ois != null)
			try {
				ois.close();
			} catch (Exception e) {
			}

		// need to set up sentiments explicitly -- now no need since lexicon is part of the session
		log.info("Memory status: " + Util.getMemoryStats());

		Archive archive = (Archive) result.get("archive");

		// no groups in public mode
		if (archive != null)
		{
            System.err.println("dir: "+archive.indexer);
			// most of this code should probably move inside Archive, maybe a function called "postDeserialized()"
			archive.postDeserialized(baseDir, readOnly);
			result.put("emailDocs", archive.getAllDocs());
		}

		return result;
	}

	/** saves the archive in the current session to the cachedir */
	public static boolean saveArchive(HttpSession session) throws FileNotFoundException, IOException
	{
		Archive archive = (Archive) session.getAttribute("archive");
		// String baseDir = (String) session.getAttribute("cacheDir");
		return saveArchive(archive.baseDir, "default", archive);
	}

	/**
	 * reads name.processing.metadata from the given basedir. should be used
	 * when quick archive metadata is needed without loading the actual archive
	 */
	public static ProcessingMetadata readProcessingMetadata(String baseDir, String name)
	{
		String processingFilename = baseDir + File.separatorChar + name + Sessions.PROCESSING_METADATA_SUFFIX;
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(processingFilename)));
			ProcessingMetadata metadata = (ProcessingMetadata) ois.readObject();
			return metadata;
		} catch (Exception e) {
			return null;
		} finally {
			try {
				if (ois != null)
					ois.close();
			} catch (Exception e1) {
			}
		}
	}

	/** saves the archive in the current session to the cachedir. note: no blobs saved. */
	public static boolean saveArchive(String baseDir, String name, Archive archive) throws FileNotFoundException, IOException
	{
		String dir = baseDir + File.separatorChar + Archive.SESSIONS_SUBDIR;
		new File(dir).mkdirs(); // just to be safe
		String filename = dir + File.separatorChar + name + Sessions.SESSION_SUFFIX;
		log.info("Saving archive to (session) file " + filename);

		if (archive.processingMetadata == null)
			archive.processingMetadata = new ProcessingMetadata();

		archive.processingMetadata.timestamp = new Date().getTime();
		archive.processingMetadata.tz = TimeZone.getDefault();
		archive.processingMetadata.nDocs = archive.getAllDocs().size();
		archive.processingMetadata.nBlobs = archive.blobStore.uniqueBlobs();

		archive.close();

		ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(filename)));
		try {
			oos.writeObject("archive");
			oos.writeObject(archive);
		} catch (Exception e1) {
			log.warn("Failed to write archive: " + e1);
		} finally {
			oos.close();
		}

		// now write out the metadata
		String processingFilename = dir + File.separatorChar + name + Sessions.PROCESSING_METADATA_SUFFIX;
		oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(processingFilename)));
		try {
			oos.writeObject(archive.processingMetadata);
		} catch (Exception e1) {
			log.warn("Failed to write archive's metadata: " + e1);
			oos.close();
		} finally {
			oos.close();
		}

		archive.openForRead();

		return true;
	}

	/** loads an archive from the given directory. return false if it exists */
	public static Archive readArchiveIfPresent(String baseDir) throws CorruptIndexException, LockObtainFailedException, IOException
	{
		String archiveFile = baseDir + File.separator + Archive.SESSIONS_SUBDIR + File.separator + "default" + Sessions.SESSION_SUFFIX;
		if (!new File(archiveFile).exists()) {
			return null;
		}

		Map<String, Object> map = loadSessionAsMap(archiveFile, baseDir, /*
																		 * read
																		 * only
																		 */true);
		// read the session map, but only use archive
		Archive a = (Archive) map.get("archive");
		// could do more health checks on archive here
		a.setBaseDir(baseDir);
		return a;
	}

	/**
	 * reads from default dir (usually ~/.muse/user) and sets up cachedir,
	 * archive vars.
	 */
	public static Archive prepareAndLoadDefaultArchive(HttpServletRequest request) throws CorruptIndexException, LockObtainFailedException, IOException
	{
		HttpSession session = request.getSession();

		// allow cacheDir parameter to override default location
		String dir = request.getParameter("cacheDir");
		if (Util.nullOrEmpty(dir))
			dir = Sessions.CACHE_DIR;

		Archive archive = SimpleSessions.readArchiveIfPresent(dir);
		if (archive != null)
		{
			JSPHelper.log.info("Good, archive read from " + dir);

			// always set these three together
			session.setAttribute("userKey", "user");
			session.setAttribute("cacheDir", dir);
			session.setAttribute("archive", archive);

			// is this really needed?
			Archive.prepareBaseDir(dir); // prepare default lexicon files etc.
			Lexicon lex = archive.getLexicon("general");
			if (lex != null)
				session.setAttribute("lexicon", lex); // set up default general lexicon, so something is in the session as default lexicon (so facets can show it)
		}
		return archive;
	}

	public static void prepareAndLoadArchive(MuseEmailFetcher m, HttpServletRequest request) throws CorruptIndexException, LockObtainFailedException, IOException
	{
		HttpSession session = request.getSession();

		// here's where we create a fresh archive
		String userKey = "user";
		if (ModeConfig.isServerMode())
		{
			// use existing key, or if not available, ask the fetcher which has the login email addresses for a key
			userKey = (String) session.getAttribute("userKey");
			if (Util.nullOrEmpty(userKey))
				userKey = m.getEffectiveUserKey();
			Util.ASSERT(!Util.nullOrEmpty(userKey)); // disaster if we got here without a valid user key
		}

		String archiveDir = Sessions.CACHE_BASE_DIR + File.separator + userKey;
		Archive archive = SimpleSessions.readArchiveIfPresent(archiveDir);

		if (archive != null) {
			JSPHelper.log.info("Good, existing archive found");
		} else {
			JSPHelper.log.info("Creating a new archive in " + archiveDir);
			archive = JSPHelper.preparedArchive(request, archiveDir, new ArrayList<String>());
		}
		// always set these three together
		session.setAttribute("userKey", userKey);
		session.setAttribute("cacheDir", archiveDir);
		session.setAttribute("archive", archive);

		Lexicon lex = archive.getLexicon("general");
		if (lex != null)
			session.setAttribute("lexicon", lex); // set up default general lexicon, so something is in the session as default lexicon (so facets can show it)
	}

	public static void main(String args[]) throws CorruptIndexException, LockObtainFailedException, IOException
	{
		// just use as <basedir> <string to find>
		Archive a = readArchiveIfPresent(args[0]);
		for (Document d : a.getAllDocs())
		{
			String c = a.getContents(d, false);
			if (c.indexOf(args[1]) >= 0)
			{
				System.out.println("\n______________________________" + d + "\n\n" + c + "\n___________________________\n\n\n");
			}
		}

	}

}
