package edu.stanford.muse.ie;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;

import edu.stanford.muse.util.Network;
import edu.stanford.muse.util.Pair;

/**
 * Notes: Reconcilation api is bad, due to the way the confidence is assigned to
 * a hit.
 * Confidence of a hit is based on uniqueness of the result given the props
 * supplied with the query.
 * Hence:
 * https://www.googleapis.com/freebase/v1/reconcile?kind=/location/location
 * &name=
 * new+york&key=AIzaSyCWESqjiJ_gNU276PHa-VJahMgUZHI2gtA&limit=20&exact=true
 * a search like this gives many dummy entries for New york which contain no
 * other info other than domain and name.
 * On the other hand, score in search api as described here:
 * https://developers.google.com/freebase/v1/search-cookbook#scoring-and-ranking
 * is based on the popularity count and
 * inbound and outbound link counts is more reliable and relevant.
 */
public class FreebaseSearcher {
	static String	API_KEY	= "AIzaSyCWESqjiJ_gNU276PHa-VJahMgUZHI2gtA";
	static Pattern	wikiMatcher	= Pattern.compile("<(http://en.wikipedia.org/wiki/.*?)>"),
								labelP = Pattern.compile("rdfs:label\\s+\"(.*?)\"(.*?)\\."),
								aliasP = Pattern.compile("ns:common.topic.alias\\s+\"(.*?)\"@(.*?);");
	static Integer	NUM_RESULTS	= 10;

	public static FreebaseApi search(String query, FreebaseType.Type type) {
		if (query == null || query.equals(""))
			return null;
		try {
			String serviceUrl = "https://www.googleapis.com/freebase/v1/search";
			//"https://www.googleapis.com/freebase/v1/search";
			//https://www.googleapis.com/freebase/v1/reconcile?kind=%2Ffilm%2Ffilm&name=Prometheus&key=AIzaSyCWESqjiJ_gNU276PHa-VJahMgUZHI2gtA&prop=%2Ffilm%2Ffilm%2Fdirected_by%3ARidley+Scott
			String url = serviceUrl + "?";
			if (type != FreebaseType.Type.All) {
				if (type == FreebaseType.Type.Location)
					url += "domain=" + "/location/";
				else if (type == FreebaseType.Type.Organization)
					url += "domain=" + "/organization/";
				else
					url += "domain=" + type.txt;
				url += "&";
			}

			url += "query=" + URLEncoder.encode(query);
			url += "&";
			url += "key=" + API_KEY;
			url += "&";
			url += "limit=" + NUM_RESULTS;
			//url += "&";
			//url += "exact=true";

			System.err.println("fetching from: " + url);
			String res = Network.getContentFromURL(new URL(url));
			Gson gson = new Gson();
			FreebaseApi fa = gson.fromJson(res, FreebaseApi.class);
			//populate the results so that the view is unified irrespective of search or reconcile api is used.
			if (serviceUrl.contains("v1/reconcile") && fa != null) {
				fa.result = new ArrayList<FreebaseType>();
				if (fa.candidate != null)
					for (int i = 0; i < Math.min(NUM_RESULTS, fa.candidate.size()); i++)
						fa.result.add(fa.candidate.get(i));
				
				if (fa.match != null)
					fa.result.add(fa.match);
			}
			return fa;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public static FreebaseApi search(String query, FreebaseType.Type type, int limit) {
		NUM_RESULTS = limit;
		return search(query, type);
	}

	public static String getRdf(String mid) {
		if (mid == null)
			return null;
		try {
			String serviceUrl = "https://www.googleapis.com/freebase/v1/rdf/";
			String url = serviceUrl + mid + "?key=" + API_KEY;
			String tmpDir = System.getProperty("java.io.tmpdir");
			File fb = new File(tmpDir + File.separator + "freebase");
			if (!fb.exists()) {
				fb.mkdir();
				fb = new File(tmpDir + File.separator + "freebase" + File.separator + "m");
				if (!fb.exists())
					fb.mkdir();
			}
			File f = new File(tmpDir + File.separator + "freebase" + File.separator + mid);
			String res = null;
			if (!f.exists()) {
				System.err.println("Getting wiki page from:" + url);
				res = Network.getContentFromURL(new URL(url));
				FileWriter fw = new FileWriter(f);
				fw.write(res);
				fw.close();
			} else {
				System.err.println("Reading from: " + f.getAbsolutePath());
				FileReader fr = new FileReader(f);
				BufferedReader br = new BufferedReader(fr);
				res = "";
				String line = null;
				while ((line = br.readLine()) != null)
					res += line;
				br.close();
			}
			return res;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static String convertUTF16(String s) {
		List<Character> out = new ArrayList<Character>();
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == '\\' && (i + 5 < s.length()) && s.charAt(i + 1) == 'u') {
				String seq = Character.toString(s.charAt(i + 2))
						+ Character.toString(s.charAt(i + 3))
						+ Character.toString(s.charAt(i + 4))
						+ Character.toString(s.charAt(i + 5));
				ch = (char) Integer.parseInt(seq, 16);
				i += 5;
			}

			out.add(ch);
		}
		StringBuilder sb = new StringBuilder();
		for (char c : out)
			sb.append(c);
		return sb.toString();
	}

	public static List<Pair<String, String>> getLabel(String mid) {
		String rdf = getRdf(mid);
		if (rdf == null)
			return null;

		List<Pair<String, String>> labels = new ArrayList<Pair<String, String>>();
		Matcher m = labelP.matcher(rdf);
		while (m.find()) {
			String name = m.group(1);
			String lang = m.group(2);
			if (name.contains("\\u")) {
				name = convertUTF16(name);
			}
			labels.add(new Pair<String, String>(name, lang));
		}
		return labels;
	}

	//along with the primary name.
	public static List<Pair<String, String>> getAliases(String mid) {
		//also include the primary name.
		String rdf = getRdf(mid);
		if (rdf == null)
			return null;
		List<Pair<String, String>> aliases = new ArrayList<Pair<String, String>>();

		Matcher m = aliasP.matcher(rdf);
		while (m.find()) {
			if (m.groupCount() > 1) {
				String name = m.group(1);
				String lang = m.group(2);
				if (name.contains("\\u")) {
					name = convertUTF16(name);
				}
				aliases.add(new Pair<String, String>(name, lang));
			}
		}
		//include primary name too.
		List<Pair<String, String>> labels = getLabel(mid);
		if (labels != null)
			aliases.addAll(labels);
		return aliases;
	}

	public static String getWikiPage(String mid) {
		if (mid == null)
			return null;
		mid = mid.replaceAll("^/+?/", "");
		try {
			String res = getRdf(mid);
			if (res == null)
				return null;
			String wikiUrl = null;
			if (res != null) {
				Matcher m = wikiMatcher.matcher(res);
				while (m.find()) {
					if (m.groupCount() > 0) {
						wikiUrl = m.group(1);
						System.err.println(m.group(1));
					}
				}
			}
			return wikiUrl;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void main(String[] args) {
		//getWikiPage("///m/015cp");
		FreebaseApi fa = search(args[0], FreebaseType.Type.Location);

		if (fa != null && fa.result != null)
			for (FreebaseType fr : fa.result)
				System.err.println(fr.name);
		//		List<Pair<String, String>> list = getAliases("/m/0rh6k");
		//		for (Pair<String, String> p : list)
		//			System.err.println(p.getFirst() + "," + p.getSecond());
	}
}
