package edu.stanford.muse.ie;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

import edu.stanford.muse.util.EmailUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import au.com.bytecode.opencsv.CSVWriter;

import edu.stanford.muse.Config;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;
import edu.stanford.muse.index.Archive;

/**
 * Data structure that holds assigned authority of a record
 */
public class AuthorisedAuthorities {
	private static Log						log					= LogFactory.getLog(AuthorisedAuthorities.class);
	public static Map<String, Authority>	cnameToDefiniteID	= null; // this should not be static! @vihari

	public static Map<String, Authority> getAuthorisedAuthorities(Archive archive) {

		if (cnameToDefiniteID != null)
			return cnameToDefiniteID;

		// read it from the serialized file if not already loaded up
		String filename = archive.baseDir + java.io.File.separator + Config.AUTHORITIES_FILENAME;
		try {
			cnameToDefiniteID = (Map<String, Authority>) Util.readObjectFromFile(filename);
		} catch (Exception e) {
			JSPHelper.log.info("Unable to find existing authorities file:" + filename + " :" + e.getMessage());
		}

		if (cnameToDefiniteID == null)
			cnameToDefiniteID = new LinkedHashMap<>();

		if (cnameToDefiniteID != null && cnameToDefiniteID.size() > 0) {
			log.info("Found " + cnameToDefiniteID.size() + " definite id mappings.");
			for (String key : cnameToDefiniteID.keySet())
				log.info("key: " + key);
		}
		return cnameToDefiniteID;
	}


}
