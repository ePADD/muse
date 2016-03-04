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
 * Data structure to hold assigned authority of a record
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
			cnameToDefiniteID = new LinkedHashMap<>();
			//JSPHelper.log.info("Unable to find existing authorities file:" + filename + " :" + e.getMessage());
		}
		if (cnameToDefiniteID == null)
			cnameToDefiniteID = new LinkedHashMap<>();

		if (cnameToDefiniteID.size() > 0) {
			log.info("Found " + cnameToDefiniteID.size() + " definite fast id mappings.");
            cnameToDefiniteID.keySet().forEach(log::info);
		}
		return cnameToDefiniteID;
	}

	public static String exportRecords(Archive archive, String exportType) throws IOException {
		String filename = archive.baseDir + java.io.File.separator + Config.AUTHORITIES_FILENAME;
        if(exportType == null)
            exportType = "csv";

		Map<String, Authority> cnameToDefiniteID;
		try {
			cnameToDefiniteID = (Map<String, Authority>) Util.readObjectFromFile(filename);
		} catch (Exception e) {
			cnameToDefiniteID = new LinkedHashMap<>();
			JSPHelper.log.info("Unable to find existing authorities file:" + filename + " :" + e.getMessage());
		}
		if (cnameToDefiniteID != null && cnameToDefiniteID.size() > 0) {
			System.out.println("Found " + cnameToDefiniteID.size() + " definite fast id mappings.");
            cnameToDefiniteID.keySet().forEach(log::info);
		}
		if (exportType.equals("csv")) {
			StringWriter sw = new StringWriter();
			CSVWriter writer = new CSVWriter(sw, ',', '"', '\n');

			List<String> line = new ArrayList<>();
			line.add("name");
            Collections.addAll(line, Authority.types);
			writer.writeNext(line.toArray(new String[line.size()]));

            if (cnameToDefiniteID != null) {
                for (Authority auth : cnameToDefiniteID.values()) {
                    line.clear();
                    Map<String, Short> sources = auth.sources;
                    Map<Short, String> is = new HashMap<>();
                    if (sources == null) {
                        log.warn("sources: null for " + auth.name);
                        continue;
                    }
                    for (String s : sources.keySet()) {
                        is.put(sources.get(s), s);
                        log.warn("Putting: " + sources.get(s) + " for: " + s + ", check: " + is.get(sources.get(s)));
                    }

                    line.add(EmailUtils.uncanonicaliseName(auth.name));
                    for (short i = 0; i < Authority.types.length; i++)
                        line.add(is.get(i));
                    writer.writeNext(line.toArray(new String[line.size()]));
                }
            }
            writer.close();
			return sw.toString();
		}
		return "Unsupported export type: " + exportType;
	}
}
