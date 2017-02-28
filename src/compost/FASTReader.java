package edu.stanford.muse.ie;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import edu.stanford.muse.util.EmailUtils;

/** this class converts a FASTPersonal.nt file to a cnameToFASTPersons.db.gz */
public class FASTReader {

	public static Map<String, FASTPerson>		idToFASTPerson		= new LinkedHashMap<String, FASTPerson>();
	public static Map<String, Set<FASTPerson>>	cnameToFASTPersons	= new LinkedHashMap<String, Set<FASTPerson>>();

	/* <http://id.worldcat.org/fast/1773461> -> 1773461 */
	public static String extractTail(String s) {
		String s1 = s;
		try {
			s1 = s1.substring(s1.lastIndexOf("/") + 1, s.length() - 1); // s.length()-1
																		// to
																		// exclude
																		// the
																		// trailing
																		// ">"
																		// character
		} catch (Exception e) {
			System.err.println("unable to extract tail from string " + s);
			return "dummy";
		}
		return s1;
	}

	// will parse Vila Dinar\u00E9s, Pau to map the \u00E9 to the right unicode
	// char
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

	static List<Pattern>	namePatternsToMatchAndRemove	= new ArrayList<Pattern>();
	static {

		// remove everything after , <space>d./b./ca./fl. <numbers>
		// Langford, James, d. 1777
		// Hoffman, U. J. (Urias John), b. 1855
		// Parkman, Gideon, ca. 1714-1789
		// "Joannes, de Alta Silva, fl. 1184-1212" .
		namePatternsToMatchAndRemove.add(Pattern
				.compile(",\\s+(d|b|fl|ca)\\.\\s+\\d+.*"));
		// Larkin, Mr. (George), b. ca. 1642
		namePatternsToMatchAndRemove.add(Pattern
				.compile(",\\s+(b|d)\\.\\s+ca\\.\\s+\\d+.*"));
		// Parkman, Gideon, approximately 1714-1789
		namePatternsToMatchAndRemove.add(Pattern
				.compile(",\\s+approximately.*"));
		// "Creeley, Robert, 1926-2005"
		// Kittel, F. (Ferdinand), 1832-1903
		// "Gwin, Thomas, 1656?-1720" .
		// Kleeberg, Franciszek, 1888-
		// Bernardo, de Claraval, Saint, 1090 or 91-1153
		// Scott, Michael, 1906?-
		namePatternsToMatchAndRemove.add(Pattern
				.compile(",\\s+\\d+\\?*(-| or ).*"));

		// Calderini, Giovanni, -1365
		namePatternsToMatchAndRemove.add(Pattern.compile(",\\s+-\\d+.*"));

		// Sammuramat, Queen, consort of Shamshi-Adad V, King of Assyria, 9th
		// cent. B.C.
		// Jean de Hauteseille, 12th cent (may not have a period after cent)
		namePatternsToMatchAndRemove.add(Pattern
				.compile(",\\s+\\d+(st|nd|rd|th|) cent.*"));

		// Xu, Zhongjie, jin shi 1508
		namePatternsToMatchAndRemove.add(Pattern.compile("\\d.*"));
	}

	public static String extractName(String s) {
		if (s.contains("\\u"))
			s = convertUTF16(s);
		// expect s to be quoted
		if (!s.startsWith("\"") || !s.endsWith("\"")) {
			System.err.println("Sorry, bad name: " + s);
			return "DUMMY";
		}

		String s1 = s;
		s1 = s1.replaceAll("\"", ""); // remove quotes
		s1 = s1.trim();

		for (Pattern p : namePatternsToMatchAndRemove)
			s1 = p.matcher(s1).replaceAll("");

		for (char c : s1.toCharArray())
			if (Character.isDigit(c)) {
				System.err.println("Warning: numeric digit in " + s1);
				break;
			}
		return s1;
	}

	public static FASTPerson getFASTPersonfor(String FAST_id) {
		FASTPerson fp = idToFASTPerson.get(FAST_id);
		if (fp == null) {
			fp = new FASTPerson();
			idToFASTPerson.put(FAST_id, fp);
			fp.FAST_id = FAST_id;
		}
		return fp;
	}

	public static void dumpFASTDB() {
		for (String cname : cnameToFASTPersons.keySet()) {
			System.out.println("key: " + cname);
			Set<FASTPerson> set = cnameToFASTPersons.get(cname);
			int i = 0;
			for (FASTPerson fp : set)
				System.out.println(++i + ". " + fp);
		}
	}

	public static void main(String args[]) throws IOException {
		if (args.length == 0) {
			System.err.println("Please provide a FAST .nt file as an argument");
			System.exit(1);
		}

		String FAST_file = args[0];
		LineNumberReader lnr = new LineNumberReader(new BufferedReader(
				new InputStreamReader(new FileInputStream(FAST_file))));

		Pattern triple = Pattern.compile("([^\\s]*)\\s+([^\\s]*)\\s+(.*) \\.");
		int lineNum = 0;
		int numSubjects = 0, numDbpedia = 0, numViaf = 0, numLocName = 0;
		while (true) {
			String line = lnr.readLine();
			if (line == null)
				break;

			lineNum++;
			if (lineNum % 100000 == 0)
				System.out.println("Line number: " + lineNum);

			Matcher m = triple.matcher(line);

			if (!m.find()) {
				System.err.println("What!? This is not an nt file! line#"
						+ lineNum + ": " + line);
				continue;
			}

			int count = m.groupCount();
			if (count != 3) {
				System.err.println("What!? This is not an nt file! line#"
						+ lineNum + ": " + line);
				continue;
			}
			String subject = m.group(1), predicate = m.group(2), object = m
					.group(3);
			if (!subject.contains("/fast/"))
				continue;
			String fast_id = extractTail(subject);

			// http://id.worldcat.org/fast/148474> <http://schema.org/name>
			// "Stampford, Lord (Henry Grey), 1599?-1673" .
			if (predicate.equalsIgnoreCase("<http://schema.org/name>")) {
				String name = extractName(object);
				FASTPerson fp = getFASTPersonfor(fast_id);
				fp.names.add(name);
			}

			// <http://id.worldcat.org/fast/212611> <http://schema.org/sameAs>
			// <http://id.loc.gov/authorities/subjects/sh85083889> .
			if (predicate.equalsIgnoreCase("<http://schema.org/sameAs>")) {
				if (object.startsWith("<http://viaf.org/viaf/")) {
					// <http://id.worldcat.org/fast/1773461>
					// <http://schema.org#sameAs>
					// <http://viaf.org/viaf/34568446> .
					FASTPerson fp = getFASTPersonfor(fast_id);
					fp.viaf = extractTail(object);
					numViaf++;
				} else if (object
						.startsWith("<http://id.loc.gov/authorities/subjects")) {
					// <http://id.worldcat.org/fast/367046>
					// <http://schema.org#sameAs>
					// <http://id.loc.gov/authorities/subjects/sh95008980> .
					FASTPerson fp = getFASTPersonfor(fast_id);
					fp.locSubject = extractTail(object);
					numSubjects++;
				} else if (object
						.startsWith("<http://id.loc.gov/authorities/names")) {
					// <http://id.worldcat.org/fast/51951>
					// <http://schema.org#sameAs>
					// <http://id.loc.gov/authorities/names/n80033928> .
					FASTPerson fp = getFASTPersonfor(fast_id);
					fp.locName = extractTail(object);
					numLocName++;
				} else
					System.err
							.println("Warning: sameAs has unexpected object: "
									+ object);
			}

			// <http://id.worldcat.org/fast/1773461>
			// <http://xmlns.com/foaf/0.1/focus>
			// <http://dbpedia.org/resource/Ferdinand_Kittel> .
			if (predicate.equalsIgnoreCase("<http://xmlns.com/foaf/0.1/focus>")
					&& object.startsWith("<http://dbpedia.org/resource")) {
				FASTPerson fp = getFASTPersonfor(fast_id);
				fp.dbpedia = extractTail(object);
				numDbpedia++;
			}
		}
		lnr.close();

		System.out.println("Found " + idToFASTPerson.size() + " fast ids");

		for (FASTPerson fp : idToFASTPerson.values()) {
			for (String n : fp.names) {
				String cname = EmailUtils.normalizePersonNameForLookup(n);
				Set<FASTPerson> set = cnameToFASTPersons.get(cname);

				if (set == null) {
					set = new LinkedHashSet<FASTPerson>();
					cnameToFASTPersons.put(cname, set);
				}
				set.add(fp);
			}
		}

		idToFASTPerson = null;

		ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(
				new FileOutputStream("cnameToFASTPersons.db.gz")));
		oos.writeObject(cnameToFASTPersons);
		oos.close();
		System.out.println("Found: \n" + "DBPedia: " + numDbpedia + "\nViaf: " + numViaf + "\nLocNames: " + numLocName + "\nLocSubjects: " + numSubjects + "\n");
		// dumpFASTDB();
	}
}
