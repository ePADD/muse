package edu.stanford.muse.index;

import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.SimpleSessions;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.util.Version;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A better highlighter that tries to highlight terms using Lucene Highlighter.
 * As it turns out, thats a terrible idea.
 * Lucene Highlighter can only add basic pre and post HTML tags, for example if a
 * hyperlink of the entity is to be added, the annotation has to be post-processed
 * to add link based on the text inside the tags
 *
 * @bug while highlighting preset query -- a regexp like: \d{3}-\d{2}-\d{4} highlights 022-29), 1114 as <B>022-29), 11</B>14.
 *      This is due to improper offsets in tokenstream or could be because lucene highlighter is considering endoffset like startoffset+token.length()
 * @bug the line breaking is not proper, sometimes line length just overflows what seems to be normal. This could be because annotation is removing '\n'.
 *       I am not sure.
 */
public class Highlighter {
	static Random			randnum			= new Random();
	static {
		randnum.setSeed(123456789);
	}
	static Log				log				= LogFactory.getLog(Highlighter.class);

	static class SimpleNumberAnalyzer extends Analyzer {
		private final Version	matchVersion;

		public SimpleNumberAnalyzer(Version matchVersion) {
			this.matchVersion = matchVersion;
		}

		@Override
		protected TokenStreamComponents createComponents(final String fieldName,
				final Reader reader) {
			final Tokenizer source = new StandardNumberTokenizer(matchVersion, reader);
			return new TokenStreamComponents(source, new LowerCaseFilter(matchVersion, source));
		}
	}

	/**
	 * @param content - text to be highlighted
	 * @param terms - terms to highlight in content
	 * @param stemmed - if to highlight the stemmed terms
	 * Phrases like: "Robert Creeley" are annotated as: "<tag>Robert</tag> <tag>Creeley</tag>" This leads to a situation tough to handle.
	 * Merge when set - will merge any such fragments into one like "<tag>Robert Creeley</tag>"
	 */
	public static String highlight(String content, String[] terms, boolean stemmed, String preTag, String postTag, boolean merge) throws Exception {
		//need to add this random string, so as to make distinction between one annotation and next annotation.
		// TODO: this is dirty solution mainly due to stupid merge algo.
		int r = randnum.nextInt();
		preTag = preTag.replaceAll(">$", " data-ignore=" + r + " >");
		preTag = "<!--" + r + "-->" + preTag;
		postTag = postTag + "<!--" + r + "-->";
        Version lv = Indexer.LUCENE_VERSION;
		//hell with reset close, stuff. initialized two analyzers to evade the problem. 
		//TODO: get rid of two analyzers.
		Analyzer snAnalyzer, snAnalyzer2;
		//Standard Analyzer tokenises phrase like: "W.S. Merwin" into "w.s" and "merwin"
		//Simple Analyzer "w", "s", "merwin"
		//WhiteSpaceAnalyzer: "W.S." and "Merwin"
		//EnglishAnalyzer "w." and "merwin" (because of stop words filter)
		// of all Simple Analyzer has more predictable behavior
		//also want the stop words to be highlighted
		//new SimpleAnalyzer(lv);
		if (!stemmed) {
			snAnalyzer = new SimpleNumberAnalyzer(lv);
			snAnalyzer2 = new SimpleNumberAnalyzer(lv);
        }
		else {
			snAnalyzer = new EnglishNumberAnalyzer(lv, Indexer.MUSE_STOP_WORDS_SET);
			snAnalyzer2 = new EnglishNumberAnalyzer(lv, Indexer.MUSE_STOP_WORDS_SET);
			//log.warn("Using english analyser for: " + str);
		}

		TokenStream stream = snAnalyzer.tokenStream(null, new StringReader(content));
		Formatter formatter = new SimpleHTMLFormatter(preTag, postTag);
		Fragmenter fragmenter = new NullFragmenter();
		BooleanQuery query = new BooleanQuery();
		QueryParser qp = new MultiFieldQueryParser(lv, new String[] { "" }, snAnalyzer2);

		for (int i = 0; i < terms.length; i++) {
            try {
                //dirty code here, we want to add quotations to multi word terms and also optionally search for term ending in 's
                //but should make sure its not a regex
                if (terms[i].charAt(0) != '/') {
                    query.add(new BooleanClause(qp.parse("\"" + terms[i] + "\""), BooleanClause.Occur.SHOULD));
                    query.add(new BooleanClause(qp.parse("\"" + terms[i] + "'s\""), BooleanClause.Occur.SHOULD));
                } else {
                    query.add(new BooleanClause(qp.parse(terms[i]), BooleanClause.Occur.SHOULD));
                }
            }catch (Exception e){
                //if there is problem in parsing a search term, try to continue
                log.info("Problem parsing query term: "+terms[i]);
            }
        }

		Scorer scorer = new QueryScorer(query);
		org.apache.lucene.search.highlight.Highlighter highlighter = new org.apache.lucene.search.highlight.Highlighter(formatter, scorer);
		highlighter.setTextFragmenter(fragmenter);
		snAnalyzer.close();
		String result = highlighter.getBestFragment(stream, content);

		//it is possible that none of the terms match and the result is null in this case.
		if (result == null)
			return content;
		if (merge)
			result = mergeContiguousFragments(result, preTag, postTag);

		return result;
	}

	//In this particular case, regexp replacing is much easier than html parsing.
	static String mergeContiguousFragments(String result, String preTag, String postTag) {
		Pattern patt = null;
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
			result = highlight(content, queries, false, preTag, postTag, true);
		} catch (Exception e) {
			Util.print_exception("Exception while highlighting sensitive stuff", e, log);
		}
		if (result == null) {
			System.err.println("Result is null!!");
			return content;
		}
		return result;
	}

	public static String canonicalizeMultiWordTerm(String phrase) {
		//Use the same tokenizer used in the analyzer.
		//this tokenisation is required so that search terms does not contain chars that are removed in the corpus for example if the search term is A L Lewis and actual term is A.L. Lewis which is tokenised as A.L and Lewis
		String cTerm = "";

		Tokenizer t = new StandardNumberTokenizer(Version.LUCENE_CURRENT, new StringReader(phrase));
		try {
			t.reset();
			CharTermAttribute charTermAttribute = t.addAttribute(CharTermAttribute.class);

			while (t.incrementToken()) {
				cTerm += charTermAttribute.toString() + " ";
			}
			if (cTerm.length() > 0)
				cTerm = cTerm.substring(0, cTerm.length() - 1);
			t.close();
			return cTerm;
		} catch (Exception e) {
			Util.print_exception("Minor error when tokenizing: " + phrase, e, log);
			e.printStackTrace();
			return null;
		}
	}

	public static Set<String> canonicalizeMultiWordTerms(Set<String> phrases) {
		Set<String> cterms = new HashSet<String>();
		if (phrases == null)
			return cterms;
		for (String phrase : phrases) {
            cterms.add(canonicalizeMultiWordTerm(phrase));
        }
		return cterms;
	}

	/**
	 * With lucene formatter we cannot have complex pre and post tags that depends on the text inside.
	 * Add such complex attributes during post processing.
	 * */
	public static String getHTMLAnnotatedDocumentContents(String contents, Date d, String docId, Boolean sensitive,
			Set<String> stemmedTermsToHighlight, Set<String> unstemmedTermsToHighlight, Map<String, Archive.Entity> entitiesWithId,
			Set<String> stemmedTermsToHyperlink, Set<String> unstemmedTermsToHyperlink, boolean showDebugInfo) {
		short HIGHLIGHT_STEMMED = 0, HIGHLIGHT_UNSTEMMED = 1, HYPERLINK_STEMMED = 3, HYPERLINK_UNSTEMMED = 4;
        Random rand = new Random();
		//pp for post process, as we cannot add complex tags which highlighting
		String preHighlightTag = "<span class='hilitedTerm rounded' >", postHighlightTag = "</span>";
		String preHyperlinkTag = "<span data-process='pp'>", postHyperlinkTag = "</span>";

		//to merge contiguous annotations.
		Boolean merge = true;

		//When highlighting, lucene highlighter ignores any non-word chars for example in Rachelle K. Learner will be highlighted as <>Rachelle</> <>K</>. <>Learner</>
		//this kind of annotation makes it hard to cross reference the string to find the type of the entity.
		//mimicking the analyzer function
		Map<String, Archive.Entity> neweids = new LinkedHashMap<>();
		if (entitiesWithId != null) {
			for (String str : entitiesWithId.keySet()) {
				String term = canonicalizeMultiWordTerm(str);

				neweids.put(term, entitiesWithId.get(str));
			}
            // log.info("oeids: " + entitiesWithId.keySet());
            // log.info("eids: " + neweids.keySet());
		}
		entitiesWithId = neweids;

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
			//log.info("Annotating sensitive stuff");
			contents = annotateSensitive(contents, preHighlightTag, postHighlightTag);
		}

		Set<String> cStemmedTermsToHighlight = canonicalizeMultiWordTerms(stemmedTermsToHighlight);
		Set<String> cUnstemmedTermsToHighlight = canonicalizeMultiWordTerms(unstemmedTermsToHighlight);
		Set<String> cStemmedTermsToHyperlink = canonicalizeMultiWordTerms(stemmedTermsToHyperlink);
		Set<String> cUnstemmedTermsToHyperlink = canonicalizeMultiWordTerms(unstemmedTermsToHyperlink);
		//entitiesid stuff is already canonicalized with tokenizer used with analyzer
		if (entitiesWithId != null)
			cUnstemmedTermsToHyperlink.addAll(entitiesWithId.keySet());

//		log.info("hyperlink terms: " + cStemmedTermsToHyperlink + ", " + cUnstemmedTermsToHyperlink);
//		log.info("highlight terms: " + cStemmedTermsToHighlight + ", " + cUnstemmedTermsToHighlight);
		//if there are overlapping annotations, then they need to be serialised.
		//this is serialized order for such annotations.
		//map strings to be annotated -> boolean denoting whether to highlight or hyperlink.
		List<Pair<String, Short>> order = new ArrayList<Pair<String, Short>>();
		Set<String> allTerms = new HashSet<String>();
		allTerms.addAll(cStemmedTermsToHighlight);
		allTerms.addAll(cUnstemmedTermsToHighlight);
		allTerms.addAll(cStemmedTermsToHyperlink);
		allTerms.addAll(cUnstemmedTermsToHyperlink);

		/*
		 * TODO: This test can still miss cases when a regular expression that eventually matches a word already annotated and
		 * when two terms like "Robert Creeley" "Mr Robert" to match a text like: "Mr Robert Creeley".
		 * In such cases one of the terms may not be annotated.
		 * Terms that are added to o are those that just share at-least one word, TODO: this is undesired
		 */
		Map<Pair<String, Short>, Integer> o = new LinkedHashMap<Pair<String, Short>, Integer>();
        //prioritised terms
		List<String> priorTerms = Arrays.asList("class","span","data","ignore");
        Set<String> consTerms = new HashSet<String>();
        for (String at : allTerms) {
            //Catch: if we are trying to highlight terms like class, span e.t.c,
            //we better annotate them first as it may go into span tags and annotate the stuff, causing the highlighter to break
			Set<String> substrs = IndexUtils.computeAllSubstrings(at);
			for (String substr : substrs) {
                short tag = -1;
                boolean match = priorTerms.contains(substr.toLowerCase());
                int val = match?Integer.MAX_VALUE:substr.length();
                //remove it from terms to be annotated.
				if (cStemmedTermsToHighlight.contains(substr)) {
                    o.put(new Pair<>(substr, HIGHLIGHT_STEMMED), val);
					cStemmedTermsToHighlight.remove(substr);
                }
				if (cUnstemmedTermsToHighlight.contains(substr)) {
					tag = HIGHLIGHT_UNSTEMMED;
                    cUnstemmedTermsToHighlight.remove(substr);
                }
				if (cStemmedTermsToHyperlink.contains(substr)) {
					tag = HYPERLINK_STEMMED;
                    cStemmedTermsToHyperlink.remove(substr);
                }
				if (cUnstemmedTermsToHyperlink.contains(substr)) {
					tag = HYPERLINK_UNSTEMMED;
                    cUnstemmedTermsToHyperlink.remove(substr);
      		    }

                //there should be no repetitions in the order array, else it leads to multiple annotations i.e. two spans around one single element
                if(!consTerms.contains(substr) && tag>=0) {
                    o.put(new Pair<String, Short>(substr, tag), val);
                    consTerms.add(substr);
                }
            }
		}

		//now sort the phrases from longest length to smallest length
		List<Pair<Pair<String, Short>, Integer>> os = Util.sortMapByValue(o);
		for (Pair<Pair<String, Short>, Integer> pair : os) {
			order.add(pair.first);
		}

		//annotate whatever is left in highlight and hyperlink Terms.
		//add quotations for multi word phrases.
		String[] highlightArray = new String[cStemmedTermsToHighlight.size()];
		int i = 0;
		for (String str : cStemmedTermsToHighlight)
			highlightArray[i++] = str;
		String result = null;
		try {
			result = highlight(contents, highlightArray, true, preHighlightTag, postHighlightTag, merge);
		} catch (Exception e) {
			Util.print_exception("Exception while adding html annotation to highlight", e, log);
		}

		highlightArray = new String[cUnstemmedTermsToHighlight.size()];
		i = 0;
		for (String str : cUnstemmedTermsToHighlight)
			highlightArray[i++] = str;
		try {
			result = highlight(contents, highlightArray, false, preHighlightTag, postHighlightTag, merge);
		} catch (Exception e) {
			Util.print_exception("Exception while adding html annotation to highlight", e, log);
		}

		String[] hyperlinkArray = new String[cStemmedTermsToHyperlink.size()];
		i = 0;
		for (String str : cStemmedTermsToHyperlink)
			hyperlinkArray[i++] = str;
		try {
			result = highlight(contents, hyperlinkArray, true, preHighlightTag, postHighlightTag, merge);
		} catch (Exception e) {
			Util.print_exception("Exception while adding html annotation to highlight", e, log);
		}

		hyperlinkArray = new String[cUnstemmedTermsToHyperlink.size()];
		i = 0;
		for (String str : cUnstemmedTermsToHyperlink)
			hyperlinkArray[i++] = str;
		try {
			result = highlight(contents, hyperlinkArray, false, preHighlightTag, postHighlightTag, merge);
		} catch (Exception e) {
			Util.print_exception("Exception while adding html annotation to highlight", e, log);
		}

		//need to post process.
		//now highlight terms in order.
		for (Pair<String, Short> ann : order) {
            short type = ann.second;
			String term = ann.first;
			String preTag = null, postTag = null;
			boolean stemmed = false;
			if (type == HYPERLINK_STEMMED) {
				preTag = preHyperlinkTag;
				postTag = postHyperlinkTag;
				stemmed = true;
			} else if (type == HYPERLINK_UNSTEMMED) {
				preTag = preHyperlinkTag;
				postTag = postHyperlinkTag;
				stemmed = false;
			} else if (type == HIGHLIGHT_STEMMED) {
				preTag = preHighlightTag;
				postTag = postHighlightTag;
				stemmed = true;
			}
			else if (type == HIGHLIGHT_UNSTEMMED) {
				preTag = preHighlightTag;
				postTag = postHighlightTag;
				stemmed = false;
			}

			try {
				result = highlight(result, new String[] { term }, stemmed, preTag, postTag, merge);
			} catch (Exception e) {
				Util.print_exception("Exception while adding html annotation: " + ann.first, e, log);
			}
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

			String best_e = null;
			int best_j = -1;
			//TODO expands a span without checking if they are continuous, under the assumption that the expanded span contained in the entitiesWithId and not sensible is rare.
			while (true) {
				entity = entity.replaceAll("'s", "");
                entity = canonicalizeMultiWordTerm(entity);
				if (entitiesWithId.containsKey(entity)) {
					best_e = entity;
					best_j = span_j;
				}
				boolean con = false;
				for (String str : entitiesWithId.keySet()) {
                    str = canonicalizeMultiWordTerm(str);
                    //TODO: do we really need nested annotation
                    //if(str.contains(entity)) -- this allows nested annotation i.e. annotation with different offsets on the same string
                    if (str.equals(entity)) {
                        con = true;
                        break;
                    }
                }
				if (!con)
					break;
				if (span_j >= (elts.size() - 1))
					break;
				String nxtText = elts.get(++span_j).text();

				//Consider the case when OpenNLP recognises "Andrew Solt's" and SVM recognises "Andrew Solt" in a text like "Andrew Solt's unpredictable screenplay"
				//"Andrew Solt" will have two nested annotations on top of it.
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
			// note &quot here because the quotes have to survive
			// through
			// the html page and reflect back in the URL
			link += "&initDocId=" + docId; // may need to URI escape
											// docId?
											// I think it's nice to
											// initialize the new view
											// at
											// the same message
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
						boolean c = false;
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
                            title += "<div class=\"resolutions\" id=\"expand_" + rnd + "\"><img src=\"images/spinner.gif\" style=\"height:15px\"/></div><script>expand(\"" + entity + "\",\"" + StringEscapeUtils.escapeJava(docId) + "\",\"" + rnd + "\");</script>";
                        }
                    }
				}

				for (int k = j; k <= span_j; k++) {
					elt = elts.get(k);
                    //don't annotate nested tags
                    if(elt.parent().tag().getName().toLowerCase().equals("span")) {
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
		//span elements in different lines are separated by spaces which is undesired.
		//TODO: also have to fix the spacing to the left
		String html = doc.html();
		html = html.replaceAll("\\-\\->\\n\\s+?<span", "\\-\\-><span");
		html = html.replaceAll("\\n\\s+?<\\!\\-\\-", "<\\!\\-\\-");
		html = html.replaceAll("</span>\\n\\s+?<\\!\\-\\-", "</span><\\!\\-\\-");

        showDebugInfo = false;
		if (showDebugInfo) {
			String debug_html = html + "<br>";
			debug_html += "<div id='debug' style='display:none'>";
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
				Set<String> e1 = new HashSet<String>();
				Set<String> e2 = new HashSet<String>();
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
			debug_html += "<button onclick='block=document.getElementById(\"debug\");block.style.display=\"block\"'>Show Debug Info</button>";
			return debug_html;
		}

		return html;
	}

	public static void main(String args[])
	{
		try {
			//String text = "Dear Robert,\nIn the hope that you and yours are safe.\nhttp://www.itsonlywords.com/view.cfm?id=874\nIn Sadness, \nHammond Guthrie 234 12-7687";
			//String text = "dear Bob (hello Pen) how does the time get away like this?? total franticity here; I look longingly at the Maine pic set as wallpaper on this machine and an GLAD we had that great time with you. it's about as bitterly winter here as Akld gets (remember 1995!) and the rains have set in all weekdays that require some movement toward school or workpl;aces. but the house is warm and getting more painted by the weekend, and I am learning to drive the new appliances, yay! there ius a copy of Roger's Len Lye biography almost ready for posting to you; the author is signing it and I'll get it off him this week with luck. question: to which address should the package go, Waldoboro or Buffalo? Roger was delighted when I passed on your good wishes and congrats about the book. Lye should be next poet on nzepc pages if everything pans out.. http://www.nzepc.auckland.ac.nz/ public announcement of nzepc occurred Friday (NZ Poetry Day; wet and cold) and so far reception is bright and interested. the great hunt for permamnent funding swings into action as Janet Copsey marshalls her resources, now with the trial site under one wing, and heads off towards V-C's Development Fund (the one I tried unsuccessfully last year) and various corporate sponsors. she (Janet) declares no more work can be done on the site without proper money (fair enough), so I have to pull in my horns for a while -- but Brian is saying he can probably get the odd thing up there as needed. good. he's been working like a Trojan and the site is beginning to hang together. plotting ahead: when we hear about Fulbright (whether yes or no) might be the time to announce public affiliation with EPC -- i.e. it would become our next piece of 'news' on the site. if we could mark that by also putting up 'The Dogs of Auckland' or similar, so much the better. what do you think? I'm pooped after a huge week of webbing, and I think Brian is about ready to drop. but it's all worthwhile. the home page images are a fave -- Killeens which change each time you come into the site; click on one to see whole range -- very generous of Rick. lots of love to you both, in Waldoboro, Buffalo or whever elkse you are XXMichele";
            String userDir = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            EmailDocument doc = archive.docForId("/Users/vihari/epadd-data/Bush small 2/Top of Outlook data file.mbox-64");
            String text = archive.getContents(doc, false);
            text = "On Tue, Jun 24, 2014 at 11:56 AM, Aparna Vaidik <aparna.vaidik@ashoka.edu.in> wrote:\n";
			Set<String> highlightTerms = new HashSet<String>();
			highlightTerms.add("poetry");
			highlightTerms.add("yours");
			highlightTerms.add("safe");
			highlightTerms.add("The dogs of auckland");
            highlightTerms.add("class");
            highlightTerms.add("span");
            Set<String> hyperlinkTerms = new HashSet<String>();
			hyperlinkTerms.add("Robert");
			hyperlinkTerms.add("Hammond Guthrie");
			String str = Highlighter.highlight(text, new String[] { "Aparna Vaidik", "\"tanmai gopal\"", "/guth.*/", "/[0-9\\-]*[0-9]{3}[- ][0-9]{2}[- ][0-9]{4}[0-9\\-]*/ ", "you", "yours" }, false, "<B >", "</B>", true);
            System.err.println("Highlight: "+str);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}