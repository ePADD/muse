package edu.stanford.muse.test;

import com.google.common.collect.*;
import edu.stanford.muse.util.DictUtils;
import edu.stanford.muse.util.Util;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by hangal on 12/29/15.
 */
public class Variants {
    static final String BASE_DIR = "/Users/hangal/data";
    static final int THRESHOLD = 10;

    // remove unnecessary punctuation
    public static String withoutPunctuation(String s) {
        // replace Cancer_(constellation) with just Cancer by stripping the _(...type...) part of the Wikipedia title, if its present;
        s = s.replaceAll("_\\(.*\\)", "");

        // replace leading and trailing brackets, underscores, commas (e.g. "stanford," parsed from "stanford, california") with a blank
        return s.replaceAll("\\(.*\\)", "").replaceAll("_", " ").replaceAll("\\(", " ").replaceAll("\\)", " ").replaceAll(",", " ");
    }

    /** returns true if s's chars are not all alpha num, period, dash or comma */
    public static boolean hasFunnyChars(String s) {
        for (char ch: s.toCharArray()) {
            if (!Character.isAlphabetic(ch) && !Character.isDigit(ch) && !Character.isSpaceChar(ch) && ch != '.' && ch != '-' && ch != ',')
                return true;
        }
        return false;
    }

    public static void main (String args[]) throws IOException {

        // set up stop words
        Set<String> stopWords = new LinkedHashSet<>();
        InputStream is = new FileInputStream(BASE_DIR + File.separator + "stop.words.full");
        stopWords = DictUtils.readStreamAndInternStrings(new InputStreamReader(is, "UTF-8"));
        is.close();

        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(BASE_DIR + File.separator + "variants.txt")));
        Multiset<String> variantTitleSet = HashMultiset.create(); // contains strings like "variant → title", using a set because it may be too expensive to keep a map/multimap of variant -> title -> count
        Map<String, String> example = new HashMap<>();

        LineNumberReader lnr = new LineNumberReader(new FileReader(BASE_DIR + File.separator + "SurfaceForms_LRD-WAT.nofilter.tsv"));
        int count = 0;
        while (true) {
            if (++count % 100000 == 0)
                System.out.println (count + " lines");

            String line = lnr.readLine();
            // line: Cancer  Malignancy      1.5029          1       8082.0  44.0
            if (line == null)
                break;

            List<String> vals = Util.tokenize(line, "\t");
            if (vals.size() < 2) // sanity check
                continue;

            String title = vals.get(0), variant = vals.get(1);
            // title: Cancer, variant: Malignancy

            title = withoutPunctuation(title);
            variant = withoutPunctuation(variant);

            // allow only some chars, skip lines with funny chars like:
            // !Action_Pact!   !Action Pact!   5.2295  L0      7       11.0    7.0
            // %22A%22_Device  A Device        5.4704  L1      8       12.0    8.0
            if (hasFunnyChars(title) || hasFunnyChars(variant))
                continue;

            // tokenize and remove stop tokens because they are low-signal
            Set<String> titleTokens = new LinkedHashSet<>(Util.tokenize(title.toLowerCase()));
            Set<String> variantTokens = new LinkedHashSet<>(Util.tokenize(variant.toLowerCase()));
            titleTokens.removeAll(stopWords);
            variantTokens.removeAll(stopWords);

            // now remove common words between variant and title
            Set<String> v0TokensCopy = new HashSet<>(titleTokens);
            titleTokens.removeAll(variantTokens);
            variantTokens.removeAll(v0TokensCopy);

            // filter out tokens of 1 letter or 1 letter + "." (we see a lot of tokens like "v.")
            titleTokens = titleTokens.stream().filter(t -> t.length() > 2 || (t.length() == 2 && !t.endsWith("."))).collect(Collectors.toSet());
            variantTokens = variantTokens.stream().filter(t -> t.length() > 2 || (t.length() == 2 && !t.endsWith("."))).collect(Collectors.toSet());

            // now map every token in title to every token in variant
            for (String titleToken: titleTokens)
                for (String variantToken: variantTokens) {
                    // don't consider simple plurals
                    if (Math.abs(titleToken.length() - variantToken.length()) == 1) {
                        if (titleToken.equals(variantToken + "s") || variantToken.equals(titleToken + "s")) {
                            break;
                        }
                    }
                    String variantStr = variantToken + " → " + titleToken;
                    variantTitleSet.add(variantStr); // variant -> title

                    // store an example of the full variant->title, but only when count is about to cross threshold (to save memory). we'll only be printing things > THRESHOLD
                    if (variantTitleSet.count(variantStr) == (THRESHOLD-1)) {
                        String exampleStr = variant + " → " + title;
                        example.put(variantStr, exampleStr);
                    }
                }
        }

        out.println("Total unique variant-title tokens = " + variantTitleSet.size());

        // print all variant-title tokens above THRESHOLD, along with an example
        List<String> variantTitles = new ArrayList<>(Multisets.copyHighestCountFirst(variantTitleSet).elementSet());
        for (String vt: variantTitles) {
            int times = variantTitleSet.count(vt);
            if (times < THRESHOLD)
                break;
            out.println (vt + " x" + times + " (example: " + example.get(vt) + ")");
        }

        out.close();
    }
}
