/*
 Copyright (C) 2012 The Stanford MobiSocial Laboratory

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package edu.stanford.muse;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.email.FetchConfig;
import edu.stanford.muse.email.MuseEmailFetcher;
import edu.stanford.muse.exceptions.CancelledException;
import edu.stanford.muse.groups.Group;
import edu.stanford.muse.groups.GroupHierarchy;
import edu.stanford.muse.groups.GroupUtils;
import edu.stanford.muse.groups.Grouper;
import edu.stanford.muse.groups.SimilarGroup;
import edu.stanford.muse.groups.SimilarGroupMethods;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.GroupAssigner;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.SimpleSessions;

/* Batch mode main! -- not Jetty main. Not tested, may not work. */
public class Main {
    public static Log log = LogFactory.getLog(Main.class);

    static String defaultCacheDir = System.getProperty("user.home") + File.separatorChar + ".muse" + File.separatorChar + "user";
	static String defaultAlternateEmailAddrs = "hangal@cs.stanford.edu, s_hangal@yahoo.com, hangal@gmail.com";

	private static Options getOpt()
	{
		// create the Options
		Options options = new Options();
		options.addOption( "h", "help", false, "toString this message");
		options.addOption( "a", "alternate-email-addrs", true, "use <arg> as alternate-email-addrs");
		options.addOption( "c", "cache-dir", true, "use <arg> as cache-dir");
		options.addOption( "d", "debug", true, "turn debug messages on");
		options.addOption( "df", "debug-fine", true, "turn detailed debug messages on (can result in very large logs!)");
		options.addOption( "dab", "debug-address-book", true, "turn debug messages on for address book");
		options.addOption( "dg", "debug-groups", true, "turn debug messages on for groups");
		return options;
	}
	
	public static void main(String args[]) throws Exception
	{
		Options options = getOpt();
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = parser.parse(options, args);
	    if (cmd.hasOption("help"))
	    {
	    	HelpFormatter formatter = new HelpFormatter();
	    	formatter.printHelp( "Muse batch mode", options);
	    	return;
	    }
	    
	    if (cmd.hasOption("debug"))
	        PropertyConfigurator.configure("log4j.properties.debug");
	    else if (cmd.hasOption("debug-address-book"))
	        PropertyConfigurator.configure("log4j.properties.ab");
	    else if (cmd.hasOption("debug-groups"))
	        PropertyConfigurator.configure("log4j.properties.groups");
	    
		String cacheDir = cmd.getOptionValue('c');
		if (cacheDir == null)
			cacheDir = defaultCacheDir;
		Archive.prepareBaseDir(cacheDir); // prepare default lexicon files etc.
		String alternateEmailAddrs = cmd.getOptionValue('a');
		if (alternateEmailAddrs == null)
			alternateEmailAddrs = defaultAlternateEmailAddrs;
	    
	    String[] files = cmd.getArgs();
	    for (String file: files) {
	    	if (!new File(file).canRead()) {
	    		System.err.println ("Sorry, cannot read file: " + file);
	    		System.exit(2);
	    	}
	    }
	    Archive archive = getMessages(alternateEmailAddrs, cacheDir, files);
		
		String sessionName = "default";
		
//		GroupAssigner groupAssigner = doGroups(addressBook, allDocs);
		
        archive.postProcess();
        // set up results with default # of terms per superdoc
//        archive.indexer.summarizer.recomputeCards((Collection) archive.getAllDocs(), archive.getAddressBook().getOwnNamesSet(), Summarizer.DEFAULT_N_CARD_TERMS);
        SimpleSessions.saveArchive(cacheDir, sessionName, archive);
	}
	
	public static Archive getMessages(String alternateEmailAddrs, String baseDir, String files[]) throws Exception
	{
		MuseEmailFetcher m = new MuseEmailFetcher();
		List<String> selectedFoldersList = new ArrayList<String>();
		File dot = new File (".");
		String pwd = dot.getCanonicalPath();
		String sessionName = null;
		
		if (files.length == 0)
		{
			log.error("No inputs specified?!");
			return null;
		}
		
		for (String f: files)
		{
			String dir = Util.dirName(f);
			if (dir == null)
				dir = pwd;
			String file = Util.baseName(f);
			m.addMboxAccount(dir, false);
			selectedFoldersList.add(dir + "^-^" + f);
			if (sessionName == null)
				sessionName = file; // assign first file as the session name
		}
		
		Archive archive = Archive.createArchive();
		archive.setup(baseDir, null, new String[0] /* default indexoptions */);
		// need to set up its blobs etc
		
		String[] selectedFolders = selectedFoldersList.toArray(new String[selectedFoldersList.size()]);
		FetchConfig fc = new FetchConfig();
		fc.downloadMessages = fc.downloadAttachments = true;
		m.fetchAndIndexEmails(archive, selectedFolders, false, fc, null); 
		return archive; // TODO: need to return proper result
	}

	public static GroupAssigner doGroups(AddressBook addressBook, Collection<EmailDocument> allDocs) throws CancelledException
	{		
		Grouper<String> grouper = new Grouper<String>();
		// we'll ignore the one-sies
		int threshold = 1;
		Set<Contact> contactsToIgnore = GroupUtils.contactsAtOrBelowThreshold(addressBook, allDocs, threshold);
		log.info (contactsToIgnore.size() + " contacts will be ignored because they are below threshold of " + threshold);

		Map<Group<String>, Float> weightedInputMap = GroupUtils.convertEmailsToGroupsWeighted(addressBook, allDocs, contactsToIgnore);
		List<Pair<Group<String>, Float>> weightedInputList = Util.mapToListOfPairs(weightedInputMap);
		Util.sortPairsBySecondElement(weightedInputList);
		Map<String, Float> individualElementsValueMap = GroupUtils.getScoreForContacts(addressBook, allDocs, contactsToIgnore);
		individualElementsValueMap = Util.reorderMapByValue(individualElementsValueMap);
//		hierarchy = grouper.findGroups(input, 25);
		try {
			grouper.setAffinityMap(GroupUtils.computeAffinityMap(addressBook.allContacts()));
			grouper.setIndividualElementsValueMap(individualElementsValueMap);		
		} catch (Throwable t) { log.warn ("Exception trying to compute grouper affinity map " + t); Util.print_exception(t, log); }
		
		float errWeight = 0.4f;
		int nGroups = 20;

		GroupAssigner ca = new GroupAssigner();

		GroupHierarchy<String> hierarchy = grouper.findGroupsWeighted(weightedInputList, nGroups-1, errWeight); // err weight of 0.4 seems to work well.
		if (hierarchy != null)
		{
			List<SimilarGroup<String>> selectedGroups = SimilarGroupMethods.topGroups(hierarchy, nGroups-1);
			ca.setupGroups (allDocs, selectedGroups, addressBook, 0);
		}
		return ca;
	}
}
