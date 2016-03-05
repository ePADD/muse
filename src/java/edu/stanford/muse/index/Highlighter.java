package edu.stanford.muse.index;

import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.util.Version;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Use this class to get the HTML content of an email document.
 * Given the content, terms to highlight, terms to hyperlink and entities in the doc, it generates the HTML of the content.
 * Bugs
 * 1. while highlighting preset query -- a regexp like: \d{3}-\d{2}-\d{4} highlights 022-29), 1114 as <B>022-29), 11</B>14.
 *      This is due to improper offsets in tokenstream or could be because lucene highlighter is considering endoffset like startoffset+token.length()
 * 2. the line breaking is not proper, sometimes line length just overflows what seems to be normal. This could be because annotation is removing '\n'.
 *       I am not sure.
 */
public class Highlighter {
    static Log log = LogFactory.getLog(Highlighter.class);

    static Random			randnum			= new Random();
	static {
		randnum.setSeed(123456789);
	}
	/**
	 * @param content - text to be highlighted
     * @param term - This can be a generic query passed to the Lucene search, for example: elate|happy|invite, hope, "Robert Creeley", /guth.+/ , /[0-9\\-]*[0-9]{3}[- ][0-9]{2}[- ][0-9]{4}[0-9\\-]+/ are all valid terms
     * @param preTag - HTML pre-tag, for ex: <B>
     * @param postTag - HTML post-tag, for ex: </B>
     * The highlighted content would have [pre Tag] matching term [post tag]
	 * When the term is "Robert Creeley, the output is "On Tue, Jun 24, 2014 at 11:56 AM, [preTag]Robert Creeley's[postTag] <creeley@acsu.buffalo.edu> wrote:"
	 */
	public static String highlight(String content, String term, String preTag, String postTag) throws IOException, ParseException, InvalidTokenOffsetsException{
        //The Lucene Highlighter is used in a hacky way here, it is intended to be used to retrieve fragments from a matching Lucene document.
        //The Lucene Highlighter introduces tags around every token that matched the query, hence it is required to merge these fragmented annotations into one inorder to fit our needs.
        //To truly differentiate contiguous fragments that match a term supplied we add a unique id to the pretag, hence the randum instance
        Version lv = Indexer.LUCENE_VERSION;
        //hell with reset close, stuff. initialized two analyzers to evade the problem.
        //TODO: get rid of two analyzers.
        Analyzer snAnalyzer, snAnalyzer2;
        snAnalyzer = new EnglishNumberAnalyzer(lv, CharArraySet.EMPTY_SET);
        snAnalyzer2 = new EnglishNumberAnalyzer(lv, CharArraySet.EMPTY_SET);

        Fragmenter fragmenter = new NullFragmenter();
        QueryParser qp = new MultiFieldQueryParser(lv, new String[]{""}, snAnalyzer2);

        BooleanQuery query = new BooleanQuery();
        TokenStream stream = snAnalyzer.tokenStream(null, new StringReader(content));
        int r = randnum.nextInt();
        String upreTag = preTag.replaceAll(">$", " data-ignore=" + r + " >");
        Formatter formatter = new SimpleHTMLFormatter(upreTag, postTag);
        query.add(new BooleanClause(qp.parse(term), BooleanClause.Occur.SHOULD));
        Scorer scorer = new QueryScorer(query);
        org.apache.lucene.search.highlight.Highlighter highlighter = new org.apache.lucene.search.highlight.Highlighter(formatter, scorer);
        highlighter.setTextFragmenter(fragmenter);
        String result = highlighter.getBestFragment(stream, content);
        snAnalyzer.close();

        if(result!=null) {
            result = mergeContiguousFragments(result, upreTag, postTag);
            //and then remove the extra info. we appended to the tags
            result = result.replaceAll(" data-ignore=" + r + " >", ">");
            return result;
        }
        else return content;
    }

    public static String highlightBatch(String content, String[] terms, String preTag, String posTag) {
        for(String term: terms){
            try {
                content = highlight(content, term, preTag, posTag);
            } catch(IOException|ParseException|InvalidTokenOffsetsException e){
                Util.print_exception("Exception while highlighting for the term: "+content, e, log);
            }
        }
        return content;
    }

	//In this particular case, regexp replacing is much easier than html parsing.
	static String mergeContiguousFragments(String result, String preTag, String postTag) {
		Pattern patt;
        if(edu.stanford.muse.webapp.ModeConfig.isPublicMode())
            patt = Pattern.compile(postTag + "([ ]+)" + preTag);
        else
            patt = Pattern.compile(postTag + "([ \\.]+)" + preTag);

		Matcher m = patt.matcher(result);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			m.appendReplacement(sb, m.group(1));
		}
		m.appendTail(sb);
		result = sb.toString();
		//	result = result.replaceAll();
		return result;
	}

	static String annotateSensitive(String content, String preTag, String postTag) {
		if (Archive.getPresetQueries() == null)
			Archive.readPresetQueries();
		String[] qs = Archive.getPresetQueries();
		if (qs == null || qs.length == 0) {
			//log.warn("Preset queries are not set, not annotating sensitive stuff");
			return content;
		}
		//We expand the query to match any numbers to the left and right of queried regular exp as the chunker is aggressive and chunks any numbers occurring together into one even if they are in different lines
		//qs = new String[] { "[0-9]{3}[- ][0-9]{2}[- ][0-9]{4}", "3[0-9]{3}[-. ][0-9]{6}[-. ][0-9]{5}" };
		String[] queries = new String[qs.length];
		for (int i = 0; i < qs.length; i++) {
			queries[i] = "/"+qs[i]+"/";
		}
		String result = null;
		try {
			result = highlightBatch(content, queries, preTag, postTag);
		} catch (Exception e) {
			Util.print_exception("Exception while highlighting sensitive stuff", e, log);
		}
		if (result == null) {
			System.err.println("Result is null!!");
			return content;
		}
		return result;
	}

	/**
     * @param contents is the content to be annotated, typically the text in email body
	 * A convenience method to do the bulk job of annotating all the terms in termsToHighlight, termsToHyperlink and entitiesWithId
     * Also hyperlinks any URLs found in the content
     * @param sensitive - when set will highlight all the expressions matching Indexer.presetQueries
     * @param showDebugInfo - when set will append to the output some debug info. related to the entities present in the content and passed through entitiesWithId
	 * */
	public static String getHTMLAnnotatedDocumentContents(String contents, Date d, String docId, Boolean sensitive,
			Set<String> termsToHighlight, Map<String, Archive.Entity> entitiesWithId,
			Set<String> termsToHyperlink, boolean showDebugInfo) {
      	short HIGHLIGHT = 0, HYPERLINK = 1;
        Random rand = new Random();
		//pp for post process, as we cannot add complex tags which highlighting
		String preHighlightTag = "<span class='hilitedTerm rounded' >", postHighlightTag = "</span>";
		String preHyperlinkTag = "<span data-process='pp'>", postHyperlinkTag = "</span>";

		//since the urls are not tokenized as one token, it is not possible to highlight with Lucene highlighter.
		Pattern p = Pattern.compile("https?://[^\\s\\n]*");
		Matcher m = p.matcher(contents);

		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String link = m.group();
			String url = link;
			if (d != null) {
				Calendar c = new GregorianCalendar();
				c.setTime(d);
				String archiveDate = c.get(Calendar.YEAR) + String.format("%02d", c.get(Calendar.MONTH)) + String.format("%02d", c.get(Calendar.DATE))
						+ "120000";
				url = "http://web.archive.org/web/" + archiveDate + "/" + link;
			}
			m.appendReplacement(sb, Matcher.quoteReplacement("<a target=\"_blank\" href=\"" + url + "\">" + link + "</a> "));
		}
		m.appendTail(sb);
		contents = sb.toString();

		if (sensitive!=null && sensitive) {
			contents = annotateSensitive(contents, preHighlightTag, postHighlightTag);
		}

		//entitiesid stuff is already canonicalized with tokenizer used with analyzer
		if (entitiesWithId != null)
			termsToHyperlink.addAll(entitiesWithId.keySet().stream().map(term -> "\"" + term + "\"").collect(Collectors.toSet()));

		//If there are overlapping annotations, then they need to be serialised.
		//This is serialized order for such annotations.
		//map strings to be annotated -> boolean denoting whether to highlight or hyperlink.
		List<Pair<String, Short>> order = new ArrayList<>();
		Set<String> allTerms = new HashSet<>();
		allTerms.addAll(termsToHighlight);
		allTerms.addAll(termsToHyperlink);

		/*
		 * We ant to assign order in which terms are highlighted or hyperlinked.
		 * for example: if we want to annotate both "Robert" and "Robert Creeley", and if we annotate "Robert" first then we may miss on "Robert Creeley"
		 * so we assign order over strings that share any common words as done in the loop below
		 * TODO:
		 * This test can still miss cases when a regular expression that eventually matches a word already annotated and
		 * when two terms like "Robert Creeley" "Mr Robert" to match a text like: "Mr Robert Creeley".
		 * In such cases one of the terms may not be annotated.
		 * Terms that are added to o are those that just share at-least one word
		 */
		Map<Pair<String, Short>, Integer> o = new LinkedHashMap<>();
        //prioritised terms
		List<String> catchTerms = Arrays.asList("class","span","data","ignore");
        Set<String> consTerms = new HashSet<>();
        for (String at : allTerms) {
            //Catch: if we are trying to highlight terms like class, span e.t.c,
            //we better annotate them first as it may go into span tags and annotate the stuff, causing the highlighter to break
			Set<String> substrs = IndexUtils.computeAllSubstrings(at);
			for (String substr : substrs) {
                if(at.equals(substr) || at.equals("\""+substr+"\""))
                    continue;
                short tag = -1;
                boolean match = catchTerms.contains(substr.toLowerCase());
                int val = match?Integer.MAX_VALUE:substr.length();
                //remove it from terms to be annotated.
                //The highlight or hyperlink terms may have quotes, specially handling below is for that.. is there a better way?
				if (termsToHighlight.contains(substr) || termsToHighlight.contains("\""+substr+"\"")) {
                    tag = HIGHLIGHT;
					termsToHighlight.remove(substr);
                    termsToHighlight.remove("\""+substr+"\"");
                }
				if (termsToHyperlink.contains(substr) || termsToHyperlink.contains("\""+substr+"\"")) {
                	tag = HYPERLINK;
                    termsToHyperlink.remove(substr);
                    termsToHyperlink.remove("\""+substr+"\"");
                }

                //there should be no repetitions in the order array, else it leads to multiple annotations i.e. two spans around one single element
                if(!consTerms.contains(substr) && tag>=0) {
                    o.put(new Pair<>(substr, tag), val);
                    consTerms.add(substr);
                }
            }
		}

		//now sort the phrases from longest length to smallest length
		List<Pair<Pair<String, Short>, Integer>> os = Util.sortMapByValue(o);
        order.addAll(os.stream().map(pair -> pair.first).collect(Collectors.toSet()));

		//annotate whatever is left in highlight and hyperlink Terms.
		String result = highlightBatch(contents, termsToHighlight.toArray(new String[termsToHighlight.size()]), preHighlightTag, postHighlightTag);
        result = highlightBatch(result, termsToHyperlink.toArray(new String[termsToHyperlink.size()]), preHyperlinkTag, postHyperlinkTag);
//        System.out.println("Terms to highlight: " + termsToHighlight);
//        System.out.println("Terms to hyperlink: "+termsToHyperlink);
//        System.out.println("order: "+order);

		//need to post process.
		//now highlight terms in order.
		for (Pair<String, Short> ann : order) {
            short type = ann.second;
			String term = ann.first;
			String preTag = null, postTag = null;
			if (type == HYPERLINK) {
				preTag = preHyperlinkTag;
				postTag = postHyperlinkTag;
			} else if (type == HIGHLIGHT) {
				preTag = preHighlightTag;
				postTag = postHighlightTag;
			}

			try {
				result = highlight(result, term, preTag, postTag);
			} catch (IOException|InvalidTokenOffsetsException e) {
				Util.print_exception("Exception while adding html annotation: " + ann.first, e, log);
			} catch(ParseException e){}
		}
		//do some line breaking and show overflow.
		String[] lines = result.split("\\n");
		StringBuilder htmlResult = new StringBuilder();
		boolean overflow = false;
		for (String line : lines) {
			htmlResult.append(line);
			htmlResult.append("\n<br/>");
		}
		if (overflow)
		{
			htmlResult.append("</div>\n");
			// the nojog class ensures that the jog doesn't pop up when the more
			// button is clicked
			htmlResult
					.append("<span class=\"nojog\" style=\"color:#500050;text-decoration:underline;font-size:12px\" onclick=\"muse.reveal(this, false);\">More</span><br/>\n");
		}

		//Now do post-processing to add complex tags that depend on the text inside. title, link and cssclass
		org.jsoup.nodes.Document doc = Jsoup.parse(htmlResult.toString());
		Elements elts = doc.select("[data-process]");

		for (int j = 0; j < elts.size(); j++) {
			Element elt = elts.get(j);
			String entity = elt.text();
            int span_j = j;

			String best_e = entity;
			int best_j = span_j;
			//A minor issue: the following code expands a span without checking for sanity under the assumption that the expanded span contained in the entitiesWithId and not sensible is rare.
            //if the content has Robert Creeley's and if we highlight "Robert Creeley" the span is introduced around "Robert Creeley's" hence it is necessary to remove such erroneous "'s"
            //The reasons why we crawl the span tags in a while loop are
            //The highlight terms and hyperlink terms may overlap, for example highlighting/hyperlinking both "Robert Creeley" and Robert will lead to "data-process" span tag curled up in multiple span tags
            //Sometimes we may not be able to merge few span tags which should have been together, for example: "Edge" and "Books" separated by new line
            //So it is required that we crawl and dig the correct entity mention
			while (true) {
				entity = entity.replaceAll("'s", "");
                if (entitiesWithId.containsKey(entity)) {
					best_e = entity;
					best_j = span_j;
				}
				if (span_j >= (elts.size() - 1))
					break;
				String nxtText = elts.get(++span_j).text();

				if (elt.childNodes().contains(elts.get(span_j)) && !elt.text().equals(nxtText)) {
					continue;
				}
				if (nxtText == null)
					continue;
				entity += " " + nxtText;
			}

			if (best_e != null && best_j > -1) {
				entity = best_e;
				span_j = best_j;
			} else {
				continue;
			}

			String link = "browse?term=\"" + Util.escapeHTML(entity) + "\"";
			//note &quot here because the quotes have to survive
			//through the html page and reflect back in the URL
			link += "&initDocId=" + docId; // may need to URI escape docId?

            String title = "";
			try {
				String cssclass = "";
				Archive.Entity info = entitiesWithId.get(entity);
				if (info != null) {
					if (info.ids != null) {
						title += "<div id=\"fast_" + info.ids + "\"></div>";
						title += "<script>getFastData(\"" + info.ids + "\");</script>";
						cssclass = "resolved";
					}
					else {
						//the last three are the OpenNLPs'
						//could have defined overlapping sub-classes, which would have reduced code repetitions in css file; but this way more flexibility
						String[] types = new String[] { "cp", "cl", "co", "person", "org", "place", "acr"};
						String[] cssclasses = new String[] { "custom-people", "custom-loc", "custom-org", "opennlp-person", "opennlp-org", "opennlp-place", "acronym" };
						outer:
                        for (String et : info.types) {
							for (int t = 0; t < types.length; t++) {
								String type = types[t];
								if (type.equals(et)) {
									if (t < 3) {
										cssclass += cssclasses[t] + " ";
							            //consider no other class
                                        continue outer;
									}
									else {
										cssclass += cssclasses[t] + " ";
									}
								}
							}
						}
					}
				} else {
					cssclass += " unresolved";
				}

				// enables completion (expansion) of words while browsing of messages.
				if (entity != null) {
					//enable for only few types
					if (cssclass.contains("custom-people") || cssclass.contains("acronym") || cssclass.contains("custom-org") || cssclass.contains("custom-loc")) {
                        //TODO: remove regexs
                        entity = entity.replaceAll("(^\\s+|\\s+$)", "");
                        if (!entity.contains(" ")) {
                            String rnd = rand.nextInt() + "";
                            //<img src="images/spinner.gif" style="height:15px"/>
                            //<script>expand("" + entity + "\",\"" + StringEscapeUtils.escapeJava(docId) + "\",\"" + rnd + "");</script>
                            if(info.expandsTo!=null)
                                title += "<div class=\"resolutions\" id=\"expand_" + rnd + "\"><a href='browse?term=\""+info.expandsTo+"\"'>"+info.expandsTo+"</a></div>";
                        }
                    }
				}

				for (int k = j; k <= span_j; k++) {
					elt = elts.get(k);
                    //don't annotate nested tags-- double check if the parent tag is highlight related tag or entity related annotation
                    if(elt.parent().tag().getName().toLowerCase().equals("span") && elt.parent().classNames().toString().contains("custom")) {
                        continue;
                    }

					String cc = elt.attr("class");
					elt.attr("class", cc + " " + cssclass);
					elt.attr("title", title);
					elt.attr("onclick", "window.location='" + link + "'");
				}
			} catch (Exception e) {
			    Util.print_exception("Some unknown error while highlighting", e, log);
			}
		}
		//The output Jsoup .html() will dump each tag in separate line
		String html = doc.html();

      	if (showDebugInfo) {
			String debug_html = html + "<br>";
			debug_html += "<div class='debug' style='display:none'>";
            debug_html += "docId: "+docId;
			debug_html += "<br>-------------------------------------------------<br>";
			for (String str : entitiesWithId.keySet())
				debug_html += str + ":" + entitiesWithId.get(str).types + ";;; ";
			debug_html += "<br>-------------------------------------------------<br>";
			String[] opennlp = new String[] { "person", "place", "org" };
			String[] custom = new String[] { "cp", "cl", "co" };
			for (int j = 0; j < opennlp.length; j++) {
				String t1 = opennlp[j];
				String t2 = custom[j];
				Set<String> e1 = new HashSet<>();
				Set<String> e2 = new HashSet<>();
				for (String str : entitiesWithId.keySet()) {
					Set<String> types = entitiesWithId.get(str).types;
					if (types.contains(t1) && !types.contains(t2))
						e1.add(entitiesWithId.get(str).name);
					else if (types.contains(t2) && !types.contains(t1))
						e2.add(entitiesWithId.get(str).name);
				}
				debug_html += opennlp[j] + " entities recognised by only opennlp: " + e1;
				debug_html += "<br>";
				debug_html += opennlp[j] + " entities recognised by only custom: " + e2;
				debug_html += "<br><br>";
			}
			debug_html += "-------------------------------------------------<br>";
			lines = contents.split("\\n");
			for (String line : lines)
				debug_html += line + "<br>";
			debug_html += "</div>";
			debug_html += "<button onclick='$(\".debug\").style(\"display\",\"block\");'>Show Debug Info</button>";
			return debug_html;
		}

		return html;
	}

	public static void main(String args[])
	{
		try {
	        String text = "On Tue, Jun 24, 2014 at 11:56 AM, Aparna Vedant's <aparna.vedant@XXX.edu.in> wrote: Rachelle K. Learner W.S. Merwin\nVery elated to see you yesterday. Can wee meet over a cup of coffee?\nI am hoping to hear back from you. BTW I also invited Tanmai Gopal to join. WHat a pleasure to have the company of Tanmai  and I'd like to do so one last time..\n---Aparna\nUniversity of Florida";
			List<String> arr = Arrays.asList( "\"Keep it to yourself\"","\"University of Florida\"","\"Aparna Vedant\"", "\"tanmai gopal\"","\"W.S. Merwin\"","Rachelle K. Learner", "elate|happy|invite", "hope","met", "/guth.*/", "/[0-9\\-]*[0-9]{3}[- ][0-9]{2}[- ][0-9]{4}[0-9\\-]*/ ", "you", "yours" );
            String str = Highlighter.highlightBatch(text, arr.toArray(new String[arr.size()]), "<B >", "</B>");
            System.out.println(arr);
            //str = Highlighter.highlightBatch(str, new String[] {"Aparna"}, "<B >", "</B>");
            System.err.println("Highlighted content: "+str);
            getHTMLAnnotatedDocumentContents("", new Date(), "", false, new LinkedHashSet<>(Arrays.asList("Robert Creeley")), null, new LinkedHashSet<>(Arrays.asList("Charles", "Susan Howe", "Betty", "Charles Bernstein", "Carl Dennis", "Joseph Conte", "Bob Creeley", "Residence", "Uday", "LWOP", "U Penn", "Joseph", "Betty Capaldi", "Capen Chair")), false);
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
}