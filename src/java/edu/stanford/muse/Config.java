package edu.stanford.muse;

import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
	public static Log log = LogFactory.getLog(Config.class);

	//Ideally Muse should not even have the concept of settings file, by default everything should be in the WEB-INF
	public static String	SETTINGS_DIR		= System.getProperty("user.home") + File.separator + "epadd-settings" + File.separator;
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
		String s = props.getProperty("MAX_ENTITY_FEATURES"); if (s != null) { try { MAX_ENTITY_FEATURES = Integer.parseInt(s); } catch (Exception e) { Util.print_exception(e, log); } }
		s = props.getProperty("MAX_TRY_TO_RESOLVE_NAMES"); if (s != null) { try { MAX_TRY_TO_RESOLVE_NAMES = Integer.parseInt(s); } catch (Exception e) { Util.print_exception(e, log); } }
		s = props.getProperty("MAX_DOCS_PER_QUERY"); if (s != null) { try { MAX_DOCS_PER_QUERY = Integer.parseInt(s); } catch (Exception e) { Util.print_exception(e, log); } }

		s = props.getProperty("OPENNLP_NER");
		if (!Util.nullOrEmpty(s))
			OPENNLP_NER = Boolean.parseBoolean(s);
	}

	/** reads a resource with the given offset path. Path components are always separated by forward slashes, just like resource paths in Java.
	 * First looks in settings folder, then on classpath (e.g. inside war).
	 * typically for the */
	public static InputStream getResourceAsStream(String path) {
		File f = new File(SETTINGS_DIR + File.separator+ path.replaceAll("/", "\\"+File.separator));
		if (f.exists()) {
			if (f.canRead()) {
				log.info ("Reading resource " + path + " from " + f.getAbsolutePath());
				try {
					InputStream is = new FileInputStream(f.getAbsoluteFile());
					return is;
				} catch (FileNotFoundException fnfe) {
					Util.print_exception(fnfe, log);
				}
			}
			else
				log.warn ("Sorry, file exists but cannot read it: " + f.getAbsolutePath());
		}

		InputStream is = Config.class.getClassLoader().getResourceAsStream(path);
		if (is == null)
			log.warn ("UNABLE TO READ RESOURCE FILE: " + path);
		return is;
	}
}
