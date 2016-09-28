package edu.stanford.muse.wpmine;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang.StringUtils;

/**
 * @author Vihari Piratla
 * email: vihari[nospace]piratla[at]gmail[dot]com
 *
 * Indexing completed in 838298ms (10 mins)
 *
 * Reads pages and redirect tables from Wikipedia dumps and builds a lucene index with documents and fields as described towards the end of this comment box.
 * See the method indexTitlesWithRedirects, set the variables REDIRECT_FILE, PAGE_FILE and indexPath to the right locations
 * The code is a little less organized in comparison to the PageLinksIndexer.java in the same folder, it is not resilient to the table structure.
 * The tables were defined as shown when this code was last used.
 *
 * page table
 * page_id | page_namespace | page_title | page_restrictions | page_counter | page_is_redirect | page_is_new | page_random | page_touched | page_links_updated | page_latest | page_len | page_content_model |
 * redirect table that contains
 * | rd_from | rd_namespace | rd_title | rd_interwiki | rd_fragment |
 *
 * The final indexTitlesWithRedirects contains
 * Note all pages other than article namespace are excluded from being added to the indexTitlesWithRedirects.
 * A document is added for every title irrespective of whether it is a redirect or not
 * The index contains title (text field and hence tokenized), id (stringField), is_redirect(stored field), redirect (String field), length (stored field)
 */
public class WikiTitleIndexer {
    //https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-redirect.sql.gz
    static String			REDIRECT_FILE	= "wiki-data" + File.separator + "enwiki-latest-redirect.sql.gz";
    //https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-page.sql.gz
    static String			PAGE_FILE       = "wiki-data" + File.separator + "enwiki-latest-page.sql.gz";

    public static String[] parseTuple(String str) {
        List<String> vals = new ArrayList<>();
        boolean inside = false;
        String val = "";
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if ((c == ',') && (!inside)) {
                if (val.charAt(0) == '\'') {
                    val = StringUtils.stripEnd(val, "'");
                    val = StringUtils.stripStart(val, "'");
                    val = StringUtils.replaceChars(val, '_', ' ');
                }
                vals.add(val);
                val = "";
                continue;
            }
            val += c;
            //' - 39  '\'-92
            if (c == '\'') {
                int j = i - 1;
                int numEscape = 0;
                while ((j >= 0) && (str.charAt(j) == 92)) {
                    numEscape++;
                    j--;
                }
                if (numEscape % 2 == 0)
                    inside = !inside;
            }
        }
        if (val.charAt(0) == '\'') {
            val = StringUtils.stripEnd(val, "'");
            val = StringUtils.stripStart(val, "'");
            val = StringUtils.replaceChars(val, '_', ' ');
            //System.err.println(prev + " stripped to " + val);
        }
        vals.add(val);

        return vals.toArray(new String[vals.size()]);
    }

    //start parsing line from offset
    public static String[][] parseLine(String line, int offset) throws IOException {
        line = line.substring(offset);
        //remove the last ')'
        line = line.substring(0, line.length() - 1);
        //StringTokenizer st = new StringTokenizer(line, "\\),\\(");
        String[] tuples = StringUtils.splitByWholeSeparator(line, "),(");
        List<String[]> records = new ArrayList<String[]>();
        for (String tuple : tuples) {
            //String tuple = st.nextToken();
            String[] vals = parseTuple(tuple);
            records.add(vals);
        }
        return records.toArray(new String[records.size()][]);
    }

//    public static void indexTitlesWithRedirects() {
//        //not so big, probably around 500MB
//        Map<String, String> redirects = new LinkedHashMap<>();
//        final String REDIRECT_INSERT_STATEMENT = "INSERT INTO `redirect` VALUES (";
//        try {
//            LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(new File(REDIRECT_FILE)))));
//            String line;
//            int ln = 0;
//            while ((line = lnr.readLine()) != null) {
//                if (!line.startsWith("INSERT INTO")) {
//                    System.err.println("Skipping initial comments...");
//                    continue;
//                }
//
//                //System.err.println("Parsing line");
//                String[][] tuples = parseLine(line, REDIRECT_INSERT_STATEMENT.length());
//                //System.err.println("Done parsing line");
//                for (String[] tuple : tuples) {
//                    if (tuple.length < 3) {
//                        System.err.println("What?! Tuple size is less than 3 len:" + tuple.length);
//                        continue;
//                    }
//                    if ("0".equals(tuple[1]))
//                        redirects.put(tuple[0], tuple[2]);
//                }
//
//                if ((ln++) % 100 == 0)
//                    System.err.println("Parsed: " + ln + " lines in redirect");
//            }
//            lnr.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        final String PAGES_INSERT_STATEMENT = "INSERT INTO `pages` VALUES (";
//        int ln = 0;
//        try {
//            LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(new File(PAGE_FILE)))));
//            String line = null;
//            while ((line = lnr.readLine()) != null) {
//                if (!line.startsWith("INSERT INTO"))
//                    continue;
//
//                String[][] tuples = parseLine(line, PAGES_INSERT_STATEMENT.length());
//                for (String[] tuple : tuples) {
//                    if (tuple.length < 13) {
//                        System.err.println("What?! Tuple size is less than 13 len:" + tuple.length);
//                        continue;
//                    }
//                    if ("0".equals(tuple[1])) {
//                        //tuple[0] os page id tuple[2] is page title it redirects to
//                        String id = tuple[0];
//                        String title = tuple[2];
//                        String is_redirect = tuple[5];
//                        String redirect = null;
//                        if ("1".equals(is_redirect))
//                            redirect = redirects.get(id);
//                        String len = tuple[11];
//                        Document doc = new Document();
//                        doc.add(new TextField("title", title, Field.Store.YES));
//                        doc.add(new StringField("id", id, Field.Store.YES));
//                        doc.add(new StoredField("is_redirect", is_redirect));
//                        if (redirect != null) {
//                            //System.err.println("Id: " + tuple[0] + ", title: " + tuple[2] + ", is redirect: " + tuple[5] + ", len: " + tuple[11] + ", redirect: " + redirect);
//                            doc.add(new StringField("redirect", redirect, Field.Store.YES));
//                        }
//                        doc.add(new StoredField("length", len));
//                        w.addDocument(doc);
//                    }
//                }
//                if ((++ln) % 1000 == 0)
//                    System.err.println("Parsed: " + ln + " lines in pages");
//            }
//            lnr.close();
//            w.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * This method is used to identify single word titles (including redirects) in the entire dump.
//     * The titles are further filtered on page length
//     * This method outputs a file containing such titles, with their redirects (if exist) and page lengths*/
//    static void extractAllSingleWordTypes(){
//        prepareToReadIndex(indexPath);
//        try {
//            //a threshold page length for a page to be considered of quality
//            int qualityPageLength = 5000;
//            int numRecords = 0;
//            FileWriter fw = new FileWriter(new File("data/TokenTypes.txt"));
//            Map<String, Integer> pageLens = new LinkedHashMap<>();
//            for (int di = 0; di < reader.maxDoc(); di++) {
//                Document doc = reader.document(di);
//                if ("0".equals(doc.get("is_redirect"))) {
//                    int pageLen = Integer.parseInt(doc.get("length"));
//                    String title = doc.get("title");
//                    if(title.contains("(disambiguation)"))
//                        continue;
//
//                    if (pageLen > qualityPageLength)
//                        pageLens.put(title, pageLen);
//                    else
//                        continue;
//                    String origTitle = title;
//                    title = title.replaceAll(" \\(.+\\)","");
//                    title = title.replaceAll(", .+","");
//                    if(!title.contains(" ")) {
//                        fw.write(title+"\t"+origTitle+"\t"+null+"\t"+pageLen+"\n");
//                        numRecords++;
//                    }
//                }
//                if(di%1000000 == 0)
//                    System.err.println("Step-1: ("+di+"/"+reader.maxDoc()+")");
//            }
//
//            for (int di = 0; di < reader.maxDoc(); di++) {
//                Document doc = reader.document(di);
//                if ("1".equals(doc.get("is_redirect"))) {
//                    String title = doc.get("title");
//                    String origTitle = title;
//                    title = title.replaceAll(" \\(.+\\)","");
//                    title = title.replaceAll(", .+","");
//                    if(title.contains(" "))
//                        continue;
//                    String redirect = doc.get("redirect");
//                    Integer pageLen = pageLens.get(redirect);
//                    if (pageLen!=null && pageLen > qualityPageLength) {
//                        fw.write(title+"\t"+origTitle+"\t"+redirect+"\t"+pageLen+"\n");
//                        numRecords++;//System.out.println();
//                    }
//                }
//                if(di%1000000 == 0)
//                    System.err.println("Step-2: ("+di+"/"+reader.maxDoc()+")");
//            }
//            System.out.println("Total number of records: "+numRecords);
//            fw.close();
//        }catch(IOException e){
//            e.printStackTrace();
//        }
//    }

//    public static void main(String[] args) throws IOException {
//        long st = System.currentTimeMillis();
//        indexTitlesWithRedirects();
//        long time = System.currentTimeMillis()-st;
//        System.out.println("Indexing completed in " + time + " ms");
//
//        extractAllSingleWordTypes();
//    }
}