package edu.stanford.muse.ie;

import java.io.*;
import java.util.*;

import edu.stanford.muse.Config;

public class KillPhrases {
	public static Set<String>	killPhrases	= new HashSet<String>();
	static {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(Config.getResourceAsStream(Config.TABOO_FILE)));
			String line = null;
			int lineNum = 0;
			while ((line = br.readLine()) != null) {
				killPhrases.add(line.trim().toLowerCase());
				lineNum++;
			}
			System.err.println("Read #" + lineNum + " from config file: " + Config.TABOO_FILE);
			br.close();
		} catch (Exception e) {
			System.err.println("Exception while reading taboo list from config file: " + Config.TABOO_FILE);
			e.printStackTrace();
		}
	}
}
