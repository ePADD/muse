package edu.stanford.muse.index;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

import com.google.gson.Gson;

import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.JSONUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.Util;

/** class to deal with just names in documents */
public class NameIndexer {
    static Log log = LogFactory.getLog(NameIndexer.class);
	private boolean cancel = false;
	public void cancel()
	{
		cancel = true;
		log.warn ("Indexer cancelled!");
	}
	
	IndexOptions io;
	public boolean isCancelled() { return cancel; }
	public String getStatusMessage() { return "Working"; }

	public List<Name> indexSubdoc(String fullText, Document d) throws ClassCastException, IOException, ClassNotFoundException
	{
		System.out.print ("D" + d.docNum + ": ");
		List<Name> list = NER.namesFromText1(fullText);
		
		for (Name n: list)
			System.out.print (n);
		System.out.println ();
		return list;
	}
	
	public void mergeNERResults(List<Name> list1, List<Name> list2) throws ClassCastException, IOException, ClassNotFoundException
	{
		// map of first list
		Map<String, Name> list1Map = new LinkedHashMap<String, Name> ();
		for (Name n: list1)
			list1Map.put (n.canonicalName, n);

		// for second list, check if it already exists in list1
		for (Name n: list2)
		{
			// entry is the entry in list1
			Name entry = list1Map.get(n.canonicalName);
			if (entry != null)
			{
				entry.weight += n.weight;
			}
			else
			{
				list1Map.put(n.canonicalName,  n);
				list1.add(n);
			}
		}
	}
	
	private Triple<String, String, Map<String, String>> indexDocumentCollection(List<Document> allDocs) throws IOException, ClassCastException, ClassNotFoundException
	{
		List<Name> result = null;
		
		try {
			for (Document d: allDocs)
			{
				if (cancel)
					break;
				String contents = "";
				if (!io.ignoreDocumentBody)
				{
					try {
						contents = d.getContents();
					} catch (Exception e) {
						log.warn ("REAL WARNING: Exception trying to read doc: " + e + "\nDoc is: " + d.getHeader() + "\nURL is: " + d.url);
					}
				}

				contents = Indexer.preprocessDocument(d, contents, null, io.includeQuotedMessages); // Util.getFileContents(inputPrefix + "." + ed.docNum);
				contents = Util.cleanupEmailMessage(contents);

				if (contents.length() > 200000)
				{
					log.warn ("Document too long, size " + contents.length() + ", dropping " + d.getHeader() +"\nURL is: " + d.url);
					contents = "";
				}

				String subject = d.getSubjectWithoutTitle();
				subject = EmailUtils.cleanupSubjectLine(subject);
				// add '.' between and after subject to avoid merging subject+subject or subject+contents (or prev. message + this subject)

				String fullText = " . \n";
				for (int i = 0; i < io.subjectWeight; i++)
					fullText += subject + " . \n";
				fullText += contents;

				List<Name> docResult = indexSubdoc(fullText, d);
				if (result == null)
					result = docResult;
				else
					mergeNERResults(result, docResult);
			}
			
			Collections.sort(result);
			System.out.println ("Final result");
			for (Name n: result)
				System.out.println (n);
			String json = new Gson().toJsonTree(result).toString();
			Map<String, String> reverseMap = null;
			String hashed_json = "";
			try {
				JSONObject o = new JSONObject("{prefs: " + json + "}");
				Pair<JSONObject, Map<String,String>> p = JSONUtils.hashJsonObject(o, null);
				hashed_json = p.getFirst().toString();
				reverseMap = p.getSecond();
			} catch (Exception e) { 
				log.warn ("EXCEPTION IN JSON (SHOULD NOT HAPPEN): " + e);
			}
			return new Triple<String, String, Map<String, String>>(json, hashed_json, reverseMap);
		} catch (OutOfMemoryError oome) {
			String s = "REAL WARNING! SEVERE WARNING! Out of memory during indexing. Please retry with more memory!" + oome;
			log.error (s);
			return null;
		}
	}
	
	public void processDocumentCollection (List<Document> docs) throws IOException, GeneralSecurityException, ClassNotFoundException
	{
		log.info ("Processing " + docs.size() + " documents");
		try {
			Triple<String, String, Map<String, String>> p = indexDocumentCollection(docs);
			System.out.println ("original json is " + p.getFirst());
			System.out.println ("hashed json is " + p.getSecond());
		} catch (OutOfMemoryError oome) {
			log.error ("Sorry, out of memory, results may be incomplete!");
			// cl.docs = null; // wipe it
			// cl.docs = null;
		}
	}

}

