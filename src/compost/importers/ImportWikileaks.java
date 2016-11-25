package edu.stanford.muse.importers;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.internet.InternetAddress;

import org.apache.lucene.store.Directory;

import au.com.bytecode.opencsv.CSVReader;
import edu.stanford.muse.graph.directed.Digraph;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.IndexUtils;
import edu.stanford.muse.index.LuceneIndexer;
import edu.stanford.muse.index.NER;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;

public class ImportWikileaks {
	
public static String getSubject(String body)
{
	int idx = body.indexOf("\nSUBJECT:");
	if (idx == -1)
		return "";

	body = body.substring(idx+1+"\nSUBJECT:".length()); // skip the newline
	int nLines = 0;
	String subject = "";
	while ((idx = body.indexOf("\n")) != -1 && nLines < 3)
	{
		String line = body.substring(0, idx).trim();		
		if (line.length() == 0)
			break;
		subject += line + " ";
		body = body.substring(idx+1); // skip the newline
		nLines++;
	}
	return subject;
}	

public static Collection<EmailDocument> read (String file, String docsDir) throws IOException, ClassCastException, ClassNotFoundException, ParseException, org.apache.lucene.queryParser.ParseException, GeneralSecurityException
{
	CSVReader reader = new CSVReader(new FileReader(file));
	String [] nextLine;
	int num = 0;
	String DATE_FORMAT = "m/d/y hh:mm";
	SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
	Collection<EmailDocument> docs = new ArrayList<EmailDocument>();
	
	while ((nextLine = reader.readNext()) != null) 
	{
		System.out.print (".");
		try {
			// nextLine[] is an array of values from the line
			// System.out.println(nextLine[0] + " #fields:" + nextLine.length);
			String id = nextLine[0], date = nextLine[1], sender = nextLine[3], body = nextLine[7]; 
	
			String subject = getSubject(body);
			// System.out.println (nextLine[1] + "  " + " " + nextLine[3] + " " + nextLine[4] + " " + subject);
			Date d = sdf.parse(date);
			InternetAddress ia = new InternetAddress(sender + "@none", sender);
			PrintWriter pw = new PrintWriter (new FileOutputStream(docsDir + File.separator + id + ".txt"));
			pw.println (body);
			pw.close();
			String url = "file://" + docsDir + "/" + id + ".txt";
			int numId = Integer.parseInt(id);		
			EmailDocument ed = new EmailDocument(numId, "Wikileaks", null, null, null, new InternetAddress[]{ia}, subject, null, d, url);
			docs.add(ed);
		} catch (Exception e) { Util.report_exception(e); }
	}
	System.out.println ();
	return docs;
}
	

public static Map<Integer, Collection<Collection<String>>> wlContents(Collection<EmailDocument> docs) throws IOException, GeneralSecurityException, ClassCastException, ClassNotFoundException
{
	Map<Integer, Collection<Collection<String>>> namesMap = IndexUtils.nameCooccurrenceInParas(docs);
	// writeToFile("/Users/hangal/wikileaks/cables.500.nums", numMap);
	return namesMap;
}

public static void mostCommonNames(Map<Integer, List<String>> namesMap)
{
	Map<String, Integer> map = new LinkedHashMap<String, Integer>();
	for (Integer docNum: namesMap.keySet())
	{
		List<String> names = namesMap.get(docNum);
		for (String s: names)
		{
			Integer I = map.get(s);
			if (I == null)
				map.put(s, 1);
			else
				map.put (s, I+1);
		}
	}
	
	List<Pair<String, Integer>> pairs = Util.sortMapByValue(map);
	for (Pair<String, Integer> p: pairs)
		System.out.println (p.getSecond() + " " + p.getFirst());
}

public static void writeToFile(String file, Object o) throws FileNotFoundException, IOException
{
	ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream(file));
	oos.writeObject(o);
	oos.close();	
}

public static Object readFromFile(String file) throws FileNotFoundException, IOException, ClassNotFoundException
{
	ObjectInputStream ois = new ObjectInputStream (new FileInputStream(file));
	Object o = ois.readObject();
	ois.close();	
	return o;
}

public static void main(String args[]) throws Exception
{
	Collection<EmailDocument> docs = read("/Users/hangal/wikileaks/cables.500.csv", "/Users/hangal/wikileaks/docs");
	LuceneIndexer lindexer = new LuceneIndexer(docs);
	writeToFile("/Users/hangal/wikileaks/cables.500.lindex", lindexer);
	writeToFile("/Users/hangal/wikileaks/cables.500.docs", docs);
	//lindexer.close();

	lindexer.luceneLookup("india");

	if (System.out != null) return;
	
//	for (EmailDocument ed: docs)
//		System.out.println (ed);
	Collection<EmailDocument> docs1 = (Collection<EmailDocument>) readFromFile("/Users/hangal/wikileaks/cables.5000.docs");
	
	Map<Integer, Collection<Collection<String>>> namesMap = wlContents (docs1);
	writeToFile("/Users/hangal/wikileaks/cables.5000.names", namesMap);
	// Map<Integer, List<String>> namesMap = (Map<Integer, List<String>>) readFromFile("/Users/hangal/wikileaks/cables.500.names");
	Digraph.doIt(namesMap, "graph.txt");

	// mostCommonNames (namesMap);
}

	public static void processNames(List<Pair<String,Integer>> names)
	{
	//	List<JSONObject> list = JSPHelper.getHits (names, indexer, ab, baseURL, allDocs);
	}
}
