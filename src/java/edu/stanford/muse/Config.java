package edu.stanford.muse;

import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class Config {
	public static Log log = LogFactory.getLog(Config.class);

	// we may want to set this differently when running as muse vs. epadd
	public static String	SETTINGS_DIR		= System.getProperty("user.home") + File.separator + ("epadd".equalsIgnoreCase(Version.appName) ? "epadd-settings" : "muse-settings") + File.separator;
	public static String	FAST_FILE = SETTINGS_DIR + "cnameToFASTPersons.db.gz";

	public static String	NER_MODEL_FILE, WORD_FEATURES;

	public static String    MODELS_FOLDER       = "models";
	public static String    CACHE_FOLDER        = "cache";
	public static String 	FAST_INDEX, AUTHORITIES_FILENAME, AUTHORITIES_CSV_FILENAME, AUTHORITY_ASSIGNER_FILENAME;
	public static String	FEATURES_INDEX, TABOO_FILE;

	//this is the folder name that contains the cache for internal authority assignment
	public static int		MAX_ENTITY_FEATURES			= 200;
	public static int		MAX_TRY_TO_RESOLVE_NAMES	= 10;
	public static int		MAX_DOCS_PER_QUERY	= 10000;

	public static Boolean 	OPENNLP_NER = false;

	static {
		Properties props = new Properties();
		String propFilename = SETTINGS_DIR + "config.properties";
		File f = new File(propFilename);
		if (f.exists() && f.canRead())
		{
			log.info ("Reading configuration from: " + propFilename);
			try
			{
				InputStream is = new FileInputStream(propFilename);
				props.load(is);
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		TABOO_FILE = props.getProperty("TABOO_FILE", SETTINGS_DIR + File.separator + "kill.txt");
		FEATURES_INDEX = props.getProperty("FEATURES_INDEX", "features");
		AUTHORITIES_FILENAME		= props.getProperty("AUTHORITIES_FILENAME", "authorities.ser");
		AUTHORITIES_CSV_FILENAME	= props.getProperty("AUTHORITIES_CSV_FILENAME", "authorities.csv");
		AUTHORITY_ASSIGNER_FILENAME	= props.getProperty("AUTHORITY_ASSIGNER_FILENAME", "InternalAuthorityAssigner.ser");
		FAST_INDEX = SETTINGS_DIR + File.separator + "fast_index";
		NER_MODEL_FILE		= props.getProperty("NER_MODEL_FILE", "svm.model");
		WORD_FEATURES		= props.getProperty("WORD_FEATURES", "WordFeatures.ser");

		// set the int features
		try { MAX_ENTITY_FEATURES = Integer.parseInt(props.getProperty("MAX_ENTITY_FEATURES")); } catch (Exception e) { }
		try { MAX_TRY_TO_RESOLVE_NAMES = Integer.parseInt(props.getProperty("MAX_TRY_TO_RESOLVE_NAMES")); } catch (Exception e) { }
		try { MAX_DOCS_PER_QUERY = Integer.parseInt(props.getProperty("MAX_DOCS_PER_QUERY")); } catch (Exception e) { }

		String s = props.getProperty("OPENNLP_NER");
		if (!Util.nullOrEmpty(s))
			OPENNLP_NER = Boolean.parseBoolean(s);
	}

}
