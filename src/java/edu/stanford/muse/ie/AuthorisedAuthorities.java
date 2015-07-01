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
	public static Map<String, Authority>	cnameToDefiniteID	= null;

	public static Map<String, Authority> getAuthorisedAuthorities(Archive archive) {
		if (cnameToDefiniteID != null)
			return cnameToDefiniteID;
		String filename = archive.baseDir + java.io.File.separator + Config.AUTHORITIES_FILENAME;
		try {
			cnameToDefiniteID = (Map<String, Authority>) Util.readObjectFromFile(filename);
		} catch (Exception e) {
			cnameToDefiniteID = new LinkedHashMap<String, Authority>();
			JSPHelper.log.info("Unable to find existing authorities file:" + filename + " :" + e.getMessage());
		}
		if (cnameToDefiniteID == null)
			cnameToDefiniteID = new LinkedHashMap<String, Authority>();

		if (cnameToDefiniteID != null && cnameToDefiniteID.size() > 0) {
			log.info("Found " + cnameToDefiniteID.size() + " definite fast id mappings.");
			for (String key : cnameToDefiniteID.keySet())
				log.info(key);
		}
		return cnameToDefiniteID;
	}

	public static String exportRecords(Archive archive, String exportType) throws IOException {
		String filename = archive.baseDir + java.io.File.separator + Config.AUTHORITIES_FILENAME;
        if(exportType == null)
            exportType = "csv";

		Map<String, Authority> cnameToDefiniteID = new LinkedHashMap<String, Authority>();
		try {
			cnameToDefiniteID = (Map<String, Authority>) Util.readObjectFromFile(filename);
		} catch (Exception e) {
			cnameToDefiniteID = new LinkedHashMap<String, Authority>();
			JSPHelper.log.info("Unable to find existing authorities file:" + filename + " :" + e.getMessage());
		}
		if (cnameToDefiniteID != null && cnameToDefiniteID.size() > 0) {
			System.out.println("Found " + cnameToDefiniteID.size() + " definite fast id mappings.");
			for (String key : cnameToDefiniteID.keySet())
				System.out.println(key);
		}
		if (exportType.equals("csv")) {
			StringWriter sw = new StringWriter();
			CSVWriter writer = new CSVWriter(sw, ',', '"', '\n');

			List<String> line = new ArrayList<String>();
			line.add("name");
			for (String type : Authority.types)
				line.add(type);
			writer.writeNext(line.toArray(new String[line.size()]));

			for (Authority auth : cnameToDefiniteID.values()) {
				line.clear();
				Map<String, Short> sources = auth.sources;
				Map<Short, String> is = new HashMap<Short, String>();
				if (sources == null) {
					System.err.println("sources:  null for " + auth.name);
					continue;
				}
				for (String s : sources.keySet()) {
					is.put(sources.get(s), s);
					System.err.println("putting: " + sources.get(s) + " for: " + s + ", check: " + is.get(sources.get(s)));
				}

				line.add(EmailUtils.uncanonicaliseName(auth.name));
				for (short i = 0; i < Authority.types.length; i++)
					line.add(is.get(i));
				writer.writeNext(line.toArray(new String[line.size()]));
			}
			writer.close();
			String csv = sw.toString();
			return csv;
		}
		return "Unsupported export type: " + exportType;
	}
}
