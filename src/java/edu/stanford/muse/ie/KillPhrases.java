package edu.stanford.muse.ie;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

import edu.stanford.muse.Config;

public class KillPhrases {
	public static Set<String>	killPhrases	= new HashSet<String>();
	static {
		try {
			FileReader fr = new FileReader(new File(Config.TABOO_FILE));
			BufferedReader br = new BufferedReader(fr);
			String line = null;
			int lineNum = 0;
			while ((line = br.readLine()) != null) {
				killPhrases.add(line.trim().toLowerCase());
				lineNum++;
			}
			System.err.println("Read #" + lineNum + " from: " + Config.TABOO_FILE);
			br.close();
		} catch (Exception e) {
			System.err.println("Exception while reading taboo list from file: " + Config.TABOO_FILE);
			e.printStackTrace();
		}
	}
}
