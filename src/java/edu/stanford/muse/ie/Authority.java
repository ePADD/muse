package edu.stanford.muse.ie;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// This is a generic authority class to represent an entity found in a database.
public class Authority implements Serializable {
    private static final long	serialVersionUID	= 1L;
	public String				name;
	//all the links to external database
	//dbId -> dbType
	public Map<String, Short>	sources;
    public static short			FAST				= 0, DBPEDIA = 1, VIAF = 2, LOC_SUBJECT = 3, LOC_NAME = 4, FREEBASE = 5, GEO_NAMES = 6;
    public static  String[]		types				= new String[] { "fast", "dbpedia", "viaf", "locSubject", "locName", "freebase", "geonames" };
    public static Log log						= LogFactory.getLog(Authority.class);
    public static String        sep = ":::";


    /**
	 * @TODO Add export to csv function
	 */
	public Authority(String name, String[] ids, String[] dbTypes) {
        //geo names -> http://sws.geonames.org/
        this.name = name;
		sources = new HashMap<String, Short>();
		if (ids == null || dbTypes == null) {
			log.warn("Improper params to add authority! Either of ids or dbTypes field supplied is null! Returning");
			return;
		} else if (ids.length != dbTypes.length) {
			log.warn("Improper params to add authority! The length of ids and types are not the same.");
			return;
		}
		for (int i = 0; i < ids.length; i++) {
			short typeIdx = -1, k = 0;
			for (String type : types) {
				if (type.equals(dbTypes[i])) {
					typeIdx = k;
					break;
				}
				k++;
			}
			if (typeIdx == -1) {
				log.warn("Unknown type: " + dbTypes[i] + ", continuing without adding.");
				continue;
			}
			sources.put(ids[i], typeIdx);
		}
	}

	public String toHTMLString() {
		String html = "<div class=\"record\">";
		String data_ids = "", data_dbtypes = "", sourceStr = "";
		int ids = 0;
        //these names are comming from toHtmlString of containing class say FASTPerson
        //the handling of these names is made complicated unnecessarily, the string variables of these names are not properly used.
       // short[] dbs = new short[]{Authority.FAST, Authority.FREEBASE, Authority.DBPEDIA, Authority.VIAF, Authority.LOC_NAME, Authority.LOC_SUBJECT, Authority.GEO_NAMES};
        //TODO: make the sources be printed in a pre-defined order
        for (String id : sources.keySet()) {
			String dType = null;
			String link = null;
			short dbType = sources.get(id);
			if (dbType == Authority.DBPEDIA) {
				dType = "Dbpedia";
				link = "http://dbpedia.org/resource/" + id;
			}
			else if (dbType == Authority.FAST) {
				dType = "FAST";
				link = "http://id.worldcat.org/fast/" + id;
			}
			else if (dbType == Authority.LOC_NAME) {
				dType = "LCNAF";
				link = "http://id.loc.gov/authorities/names/n79043504";
			}
			else if (dbType == Authority.LOC_SUBJECT)
				dType = "LoC Subject";
			else if (dbType == Authority.VIAF) {
				dType = "VIAF";
				link = "http://viaf.org/viaf/" + id;
			}
			else if (dbType == Authority.FREEBASE) {
				dType = "Freebase";
				link = "http://freebase.com" + id;
			}
			else
				dType = "Unknown";

            if(dbType>=0 && dbType<=types.length) {
                data_ids += id;
                data_dbtypes += types[dbType];
            }else {
                log.warn("Unknown type: " + dbType + " found in sources of: " + name);
                continue;
            }
			if (!link.equals(""))
				sourceStr += "<span class=\"authority-id\" <a href='" + link + "'>" + dType + ":" + id + "</a></span>&nbsp&nbsp";
			else
				sourceStr += dType + ":" + id + "&nbsp&nbsp";
			if (ids < (sources.size() - 1)) {
				data_ids += sep;
				data_dbtypes += sep;
			}
			ids++;
		}
		html += "<div class=\"record\"><input data-ids='" + data_ids + "' data-dbtypes='" + data_dbtypes + "' type='checkbox' checked> " + sourceStr + "</div>";
		return html;
	}

	/**
	 * expands an <id,type> pair to full http link
	 * ex: <1258932, geonames> -> http://sws.geonames.org/1258932
	 * 
	 * The input type should be one of the Authority.types[Authority.index]
	 * */
	public static String expandId(String dbId, String type) {
		if (dbId == null || type == null) {
			System.err.println("One of dbId or type is null, dbId: " + dbId + " type: " + type);
			return dbId;
		}
		if (!dbId.contains("http")) {
			if (type.equals(Authority.types[Authority.DBPEDIA]))
				dbId = "http://dbpedia.org/resource/" + dbId;
			else if (type.equals(Authority.types[Authority.VIAF]))
				dbId = "http://viaf.org/viaf/" + dbId;
			else if (type.equals(Authority.types[Authority.LOC_NAME]))
				dbId = "http://id.loc.gov/authorities/names/" + dbId;
			else if (type.equals(Authority.types[Authority.LOC_SUBJECT]))
				dbId = "http://id.loc.gov/authorities/subjects/" + dbId;
			else if (type.equals(Authority.types[Authority.GEO_NAMES]))
				dbId = "http://stopWords.geonames.org/" + dbId;
			else {
				System.err.println("Unknown type: " + type + " for: " + dbId);
				return null;
			}
		}
		return dbId;
	}
}
