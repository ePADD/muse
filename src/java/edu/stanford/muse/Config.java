package edu.stanford.muse;

import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.ModeConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
	public static Log log = LogFactory.getLog(Config.class);
    public static String	admin, holder, holderContact, holderReadingRoom;

    /* default location for dir under which archives are imported/stored. Should not end in File.separator */
    public final static String	REPO_DIR_APPRAISAL;
    public final static String	REPO_DIR_PROCESSING;
    public final static String	REPO_DIR_DISCOVERY;
    public final static String	REPO_DIR_DELIVERY;

    static {
    }

	//Ideally Muse should not even have the concept of settings file, by default everything should be in the WEB-INF
	public static String	SETTINGS_DIR, BASE_DIR;
    public static String	FAST_FILE;

	public static String	NER_MODEL_FILE, WORD_FEATURES;

	public static String    MODELS_FOLDER       = "models";
	public static String    CACHE_FOLDER        = "cache";
	public static String 	FAST_INDEX, AUTHORITIES_FILENAME, AUTHORITIES_CSV_FILENAME, AUTHORITY_ASSIGNER_FILENAME;
	public static String	FEATURES_INDEX, TABOO_FILE = "kill.txt";

	//this is the folder name that contains the cache for internal authority assignment
	public static int		MAX_ENTITY_FEATURES			= 200;
	public static int		MAX_TRY_TO_RESOLVE_NAMES	= 10;
	public static int		MAX_DOCS_PER_QUERY	= 10000;

	public static Boolean 	OPENNLP_NER = false;
    public static String EPADD_PROPS_FILE = System.getProperty("user.home") + File.separator + "epadd.properties";
    public static String DEFAULT_SETTINGS_DIR = System.getProperty("user.home") + File.separator + "epadd-settings";
    public static String DEFAULT_BASE_DIR = System.getProperty("user.home");

	static {
        Properties props = new Properties();

        File f = new File(EPADD_PROPS_FILE);
        if (f.exists() && f.canRead()) {
            log.info("Reading configuration from: " + EPADD_PROPS_FILE);
            try {
                InputStream is = new FileInputStream(EPADD_PROPS_FILE);
                props.load(is);
            } catch (Exception e) {
                Util.print_exception("Error reading epadd properties file " + EPADD_PROPS_FILE, e, log);
            }
        } else {
            log.warn("ePADD properties file " + EPADD_PROPS_FILE + " does not exist or is not readable");
        }

        // set up settings_dir
        SETTINGS_DIR = System.getProperty("epadd.settings.dir");
        if (Util.nullOrEmpty(SETTINGS_DIR))
            SETTINGS_DIR = props.getProperty("epadd.settings.dir");
        if (Util.nullOrEmpty(SETTINGS_DIR))
            SETTINGS_DIR = DEFAULT_SETTINGS_DIR;

        // set up base_dir and its subdirs
        BASE_DIR = System.getProperty("epadd.base.dir");
        if (Util.nullOrEmpty(BASE_DIR))
            BASE_DIR = props.getProperty("epadd.base.dir");
        if (Util.nullOrEmpty(BASE_DIR))
            BASE_DIR = DEFAULT_BASE_DIR;

        REPO_DIR_APPRAISAL = BASE_DIR + java.io.File.separator + "epadd-appraisal"; // this needs to be in sync with system property muse.dirname?
        REPO_DIR_PROCESSING = BASE_DIR + java.io.File.separator + "epadd-processing";
        REPO_DIR_DISCOVERY = BASE_DIR + java.io.File.separator + "epadd-discovery";
        REPO_DIR_DELIVERY = BASE_DIR + java.io.File.separator + "epadd-delivery";

        // set up epadd stuff
        admin = props.getProperty("admin", "NOT SET");
        holder = props.getProperty("holder", "NOT SET");
        holderContact = props.getProperty("holderContact", "NOT SET");
        holderReadingRoom = props.getProperty("holderReadingRoom", "NOT SET");

        FEATURES_INDEX = props.getProperty("FEATURES_INDEX", "features");
		AUTHORITIES_FILENAME		= props.getProperty("AUTHORITIES_FILENAME", "authorities.ser");
		AUTHORITIES_CSV_FILENAME	= props.getProperty("AUTHORITIES_CSV_FILENAME", "authorities.csv");
		AUTHORITY_ASSIGNER_FILENAME	= props.getProperty("AUTHORITY_ASSIGNER_FILENAME", "InternalAuthorityAssigner.ser");
		FAST_INDEX = SETTINGS_DIR + File.separator + "fast_index";
        FAST_FILE = SETTINGS_DIR + File.separator + "cnameToFASTPersons.db.gz";

		// set the int features
		String s = props.getProperty("MAX_ENTITY_FEATURES"); if (s != null) { try { MAX_ENTITY_FEATURES = Integer.parseInt(s); } catch (Exception e) { Util.print_exception(e, log); } }
		s = props.getProperty("MAX_TRY_TO_RESOLVE_NAMES"); if (s != null) { try { MAX_TRY_TO_RESOLVE_NAMES = Integer.parseInt(s); } catch (Exception e) { Util.print_exception(e, log); } }
		s = props.getProperty("MAX_DOCS_PER_QUERY"); if (s != null) { try { MAX_DOCS_PER_QUERY = Integer.parseInt(s); } catch (Exception e) { Util.print_exception(e, log); } }

		s = props.getProperty("OPENNLP_NER");
		if (!Util.nullOrEmpty(s))
			OPENNLP_NER = Boolean.parseBoolean(s);

		String mode = props.getProperty("epadd.mode");
		if ("appraisal".equalsIgnoreCase(mode))
			ModeConfig.mode = ModeConfig.Mode.APPRAISAL;
		else if ("processing".equalsIgnoreCase(mode))
			ModeConfig.mode = ModeConfig.Mode.PROCESSING;
		else if ("discovery".equalsIgnoreCase(mode))
			ModeConfig.mode = ModeConfig.Mode.DISCOVERY;
		else if ("delivery".equalsIgnoreCase(mode))
			ModeConfig.mode = ModeConfig.Mode.DELIVERY;
		else if (mode != null)
			log.warn ("Invalid value for epadd.mode: " + mode);
		// if null or invalid, we'll leave epadd.mode in APPRAISAL which is the default
	}

	/** reads a resource with the given offset path. Path components are always separated by forward slashes, just like resource paths in Java.
	 * First looks in settings folder, then on classpath (e.g. inside war).
	 * typically for the */
	public static InputStream getResourceAsStream(String path) {
		File f = new File(SETTINGS_DIR + File.separator + path.replaceAll("/", "\\" + File.separator));
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
