package edu.stanford.muse.ie;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.Config;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Util;

/**
 * Loads FASTfile into memory and can perform searches over it, is now used nowhere.
 * Is used in resolveFASTIds.jsp, which is in turn used in browse page for resolving assigned authorities.
 */
public class FASTEntities {
	private static Log								log					= LogFactory.getLog(FASTEntities.class);

	public static Map<String, Set<FASTPerson>>		cnameToFASTPersons	= new LinkedHashMap<String, Set<FASTPerson>>();
	public transient static Map<String, FASTPerson>	fastIDToFASTPerson	= new LinkedHashMap<String, FASTPerson>();
	static {
		log.info("Initializing FAST lookup");
		long startMillis = System.currentTimeMillis();
		ObjectInputStream oos;
		try {
			oos = new ObjectInputStream(new GZIPInputStream(new FileInputStream(Config.FAST_FILE)));
			log.info("Read serialized file " + Config.FAST_FILE + " in " + Util.commatize(System.currentTimeMillis() - startMillis) + "ms");

			cnameToFASTPersons = (Map<String, Set<FASTPerson>>) oos.readObject();
			for (Set<FASTPerson> set : cnameToFASTPersons.values())
				for (FASTPerson fp : set)
					fastIDToFASTPerson.put(fp.FAST_id, fp);
			oos.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		log.info("Total setup time for " + cnameToFASTPersons.size() + " FAST entities: " + Util.commatize(System.currentTimeMillis() - startMillis) + "ms");
	}

	public static Set<FASTPerson> lookupName(String n)
	{
		String cname = EmailUtils.normalizePersonNameForLookup(n);
		return cnameToFASTPersons.get(cname);
	}

	public static FASTPerson get(String fastId) {
		if (fastIDToFASTPerson.containsKey(fastId))
			return fastIDToFASTPerson.get(fastId);
		else
			return null;
	}
}
