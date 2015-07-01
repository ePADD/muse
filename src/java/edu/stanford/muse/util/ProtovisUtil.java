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
package edu.stanford.muse.util;


import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.CalendarUtil;
import edu.stanford.muse.groups.Group;
import edu.stanford.muse.groups.SimilarGroup;
import edu.stanford.muse.index.DatedDocument;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.webapp.JSPHelper;

public class ProtovisUtil {

	/** gets protoviz javascript mark to plot normalized frequencies for a contact: out and in.
	 * all out's and in's should be between 0 and 1
	 * height of bar is sqrt of comm. volume
	 * chartSpec can be null in which case it will draw the chart at the current location.
	 * if chartSpec is specified, it will draw the chart inside the specified div "chartCanvas"
	 * with date slider for zooming where the slider is specified by dateSlider and dateSliderText
	 * (see filter.jsp / filter_common.html for detail on make_date_slider()).
	 * chartSpec[0..2] = chartCanvas, dateSliderBar, dateSliderText.
	 * firstDate is inclusive, lastDate is exclusive.
	 */
	public static String getProtoVizMark(String[] chartSpec, int[] out, int[] in, int normalizer, int width, int height, boolean inNOut, Date firstDate, Date lastDate, boolean focusOnly, String browseParams)
	{
	//	  example outcome
	//
	//	    <script type="text/javascript+protovis">
	//	          var w = 100, h = 40;
	//	        new pv.Panel().width(w).height(h)
	//	            .add(pv.Rule).bottom(h/2).lineWidth(2).left(200).right(200)
	//	            .add(pv.Bar).data([0.1,0.2,0.3,0.4,0.5,1.0]).width(4)
	//	              .left(function() 5 * this.index)
	//	              .height(function(d) Math.round(h/2 * d))
	//	              .bottom(h/2)
	//	            .add(pv.Bar).data([0.3,0.4,0.5,1.0]).width(4)
	//	              .left(function() 5 * this.index)
	//	              .height(function(d) Math.round(h/2*d))
	//	              .bottom(function(d) h/2 - Math.round(h/2 * d))
	//	        .root.render();
	//
	//	</script>

		if (Util.nullOrEmpty(browseParams))
			browseParams = "''";

		// add padding to make first and last data always 0 to nicely contain/bound the area chart
		StringBuilder outgoingData = new StringBuilder("[0");
		// bar for incoming counts
		for (int x = 0; x < out.length; x++)
			//outgoingData.append ((x==0 ? "" : ",") + out[x]);
			outgoingData.append ("," + out[x]);
		outgoingData.append(",0]");

		StringBuilder incomingData = null;
		if (inNOut)
		{
			incomingData = new StringBuilder("[0");
			for (int x = 0; x < in.length; x++)
				//incomingData.append ((x==0 ? "" : ",") + in[x]);
				incomingData.append ("," + in[x]);
			incomingData.append(",0]");
		}

		// Date.getYear() is deprecated.
		Calendar cFirst = new GregorianCalendar(); cFirst.setTime(firstDate); // inclusive
		Calendar cLast = new GregorianCalendar(); cLast.setTime(lastDate); // exclusive
		int cFirst_year = cFirst.get(Calendar.YEAR);
		int cFirst_month = cFirst.get(Calendar.MONTH) - 1; // to represent the left dummy pad since cFirst is inclusive
		int nMonths = CalendarUtil.getDiffInMonths(firstDate, lastDate);
		Util.softAssert(nMonths == in.length);

	    StringBuilder result = new StringBuilder("");

	    String chartDivId = null;
	    if (chartSpec != null) {
	    	Util.ASSERT(chartSpec[0].startsWith("#"));
	    	chartDivId = chartSpec[0].substring(chartSpec[0].startsWith("#") ? 1 : 0);
	    	// the canvas div must appear before the protovis invocation 
	    	//if (focusOnly) result.append("<a href='#?custom=true&amp;id=" + chartDivId + "' rel='subchart'>"); // should probably inject 'subchart' tag here but we don't have info for 'title' so we inject at an upper layer instead.
	    	result.append("<div id='" + chartDivId + "'></div>");
	    	//if (focusOnly) result.append("</a>");
	    }

	    result.append("<script type=\"text/javascript\">");

	    if (chartSpec == null) {
	    	result.append("draw_protovis_box(" + incomingData + ", " + outgoingData
	    										+ ", " + width + ", " + height
	    										+ ", " + normalizer
	    										+ ", " + cFirst_year + "," + cFirst_month
	    										+ ", " + cLast.get(Calendar.YEAR) + "," + cLast.get(Calendar.MONTH)
	    										+ ");");
	    } else {
	    	result.append("draw_chart('" + chartSpec[0] + "'"
									 + ", data_bottom['" + chartDivId + "']=" + incomingData
									 + ", data_top['" + chartDivId + "']=" + outgoingData
									 + ", " + normalizer
									 + ", " + cFirst_year + "," + cFirst_month
									 + ", " + width + ", " + height
									 + ", " + focusOnly
									 + ", browse_params['" + chartDivId + "']=" + browseParams
									 + ")");
//			int lastMonth_inclusive = nMonths - 1; // slider will be created with range [0, lastMonth_inclusive] inclusive on both ends for the total of nMonths.
//	    	result.append("make_date_slider('" + chartSpec[1] + "','" + chartSpec[2] + "'");
//	    	result.append(					"," + lastMonth_inclusive + "," + cFirst_year + "," + cFirst_month); // global range
//	    	result.append(					"," + lastMonth_inclusive + "," + cFirst_year + "," + cFirst_month); // current range
//	    	result.append(					", get_date_change_func('" + chartSpec[0] + "'"
//	    										+ ", " + incomingData + ", " + outgoingData
//	    										+ ", " + width + ", " + height
//	    										+ ", " + cFirst_year + "," + cFirst_month
//	    										+ ")");
//	    	result.append(					");");
	    }

	    result.append("</script>");

	    return result.toString();
	}

	/** gets a protoviz mark with the in/out counts on the side */
	public static String getProtoVizBox(String[] chartSpec, int outCount, int inCount, int[] out, int[] in, int normalizer, int width, int height, boolean showTotals, boolean inNOut, Date firstDate, Date lastDate, boolean focusOnly, String browseParams)
	{
		String protovisBox;
		// little table here just to align the O and I
		if (inCount > 0 || outCount > 0)
		{
			protovisBox = getProtoVizMark(chartSpec, out, in, normalizer, width, height, inNOut, firstDate, lastDate, focusOnly, browseParams);
			if (showTotals)
			{
				protovisBox = "<table class=\"protovis-text\" style=\"width:" + (width+20) + ";border-width:0;border-style:none\"><tr>"
				+ "<td style=\"font-size:small;border-width:0;border-style:none\">"
				+ "&uarr;"
				+ outCount
				+ "<br/>&darr;"
				+ inCount
				+ (focusOnly ? "" : "<br/><br/><br/>&nbsp;") // to center align the up/down arrows with the focus+context chart
				+ "</td>"
				+ "<td>"
				+ protovisBox
				+ "</td>"
				+ "</tr></table>";
			}
		}
		else
			protovisBox = ("<span class=\"protovis-text\" align=\"center\">0</span>");

		return protovisBox;
	}

	public static String getProtovisForDates(String[] chartSpec, List<Date> outDates, List<Date> inDates, List<Date> intervals, int normalizeCount, int width, int height, boolean showTotals, boolean inNOut, String browseParams)
	{
		int[] inGram = CalendarUtil.computeHistogram(inDates,intervals);
	//	double[] normalizedInGram = Util.normalizeHistogramToBase(inGram, normalizeCount);
		int[] outGram = CalendarUtil.computeHistogram(outDates, intervals);
	//	double[] normalizedOutGram = Util.normalizeHistogramToBase(outGram, normalizeCount);

		return getProtoVizBox(chartSpec, outDates.size(), inDates.size(), outGram, inGram, normalizeCount, width, height, showTotals, inNOut, intervals.get(0), intervals.get(intervals.size()-1), false/*focusOnly*/, browseParams);
	}

	/** max is a normalizer for the # of messages. inDates/outDates can be null in which case they are treated as empty. */
	/* this routine is not used anywhere?
	public static String getProtovisForPerson(Contact ci, List<Date> inDates, List<Date> outDates, List<Date> intervals, int normalizer)
	{
		if (inDates == null)
			inDates = new ArrayList<Date>();
		if (outDates == null)
			outDates = new ArrayList<Date>();
		
		StringBuilder sb1 = new StringBuilder();
		int i = 0;
		for (String s : ci.emails) {
			if (i != 0)
				sb1.append("<br/>");
			sb1.append(s);
			if (i == 0)
				break; // optionally in future provide some way of expanding emails
			i++;
		//			if (ci.maybeMailingList == 1)
		//				sb1.append ("<br/>(ML)");
		//			else if (ci.maybeMailingList == 2)
		//				sb1.append ("<br/>(ML?)");
		}

		List<Date> allDates = new ArrayList<Date>();
		allDates.addAll(inDates);
		allDates.addAll(outDates);
		int[] inGram = CalendarUtil.computeHistogram(inDates,intervals);
//		double[] normalizedInGram = Util.normalizeHistogramToBase(inGram, normalizer);
		int[] outGram = CalendarUtil.computeHistogram(outDates, intervals);
//		double[] normalizedOutGram = Util.normalizeHistogramToBase(outGram, normalizer);

		String addr = sb1.toString().replace("'", "\\'"); // some crazy addresses may have quotes embedded in them, escape 'em

		String protovisCell = getProtoVizBox(outDates.size(), inDates.size(), outGram, inGram, normalizer, 200, 40, true, true);

		// end of protovis
		StringBuilder sb2 = new StringBuilder(); // construct html string for all names
		i = 0;
		String bestName = ci.pickBestName();
		if (!Util.nullOrEmpty(bestName))
			sb2.append(Util.escapeHTML(bestName));

		String names = sb2.toString().replace("'", "\\'"); // some crazy names may have quotes embedded in them, escape 'em
		if (names.length() == 0)
			names = "&nbsp;"; // dummy space otherwise the table cell border doesn't get drawn!

		String msgLink = null;
		String attachmentsLink = null;
		if (inDates.size() > 0 || outDates.size() > 0)
		{
			msgLink = "browse?person=" + ci.getCanonicalEmail();
			attachmentsLink = "attachments?person=" + ci.getCanonicalEmail();
		}
		String info = JSPHelper.docControls(msgLink, attachmentsLink, null);

	//	out.print("<td>" + names + "</td>\n");
	//	out.println (protovisCell);
	//	out.print("</tr>\n");
		// somehow the <td> below needs a specific style to suppress border, class="noborder" doesn't seem to work.
		return (protovisCell // + "<br/>"
			+ "<table class=\"noborder protovis-text\" width=\"100%\"><tr><td class=\"noborder\" style=\"border-style:none;border-width:0px\"><span style=\"font-size:small;font-variant:small-caps\">" + names + "</span><br/>"
			+ "<span style=\"font-size:small\">" + addr + "</span></td><td class=\"noborder\" style=\"border-style:none;border-width:0px\" align=\"right\" valign=\"bottom\">" + info + "</td></tr></table>");
	}
	*/

	/** max is a normalizer for the # of messages. inDates/outDates can be null in which case they are treated as empty. */
//	public static String getProtovisForDetailedFacet(DetailedFacetItem df, List<Date> inDates, List<Date> outDates, List<Date> intervals, int normalizer)
//	{		
//		if (inDates == null)
//			inDates = new ArrayList<Date>();
//		if (outDates == null)
//			outDates = new ArrayList<Date>();
//		
//		List<Date> allDates = new ArrayList<Date>();
//		allDates.addAll(inDates);
//		allDates.addAll(outDates);
//		int[] inGram = CalendarUtil.computeHistogram(inDates,intervals);
////		double[] normalizedInGram = Util.normalizeHistogramToBase(inGram, normalizer);
//		int[] outGram = CalendarUtil.computeHistogram(outDates, intervals);
////		double[] normalizedOutGram = Util.normalizeHistogramToBase(outGram, normalizer);
//
//		return getProtovisForDetailedFacet(df, inDates.size(), outDates.size(), inGram, outGram, intervals, normalizer);
//	}

	/** max is a normalizer for the # of messages. inDates/outDates can be null in which case they are treated as empty. */
	private static String getProtovisForDetailedFacet(String[] chartSpec, DetailedFacetItem df, int inCount, int outCount, int[] inGram, int[] outGram, List<Date> intervals, int normalizer)
	{
		String protovisCell = getProtoVizBox(chartSpec, outCount, inCount, outGram, inGram, normalizer, 200, 60, true, true, intervals.get(0), intervals.get(intervals.size()-1), true/*focusOnly*/, "'" + df.messagesURL + "'");

		// end of protovis
		String descr = df.name;
		if (!Util.nullOrEmpty(descr))
			descr = Util.escapeHTML(descr);
		descr = descr.replace("'", "\\'"); // some crazy names may have quotes embedded in them, escape 'em
		if (descr.length() == 0)
			descr = "&nbsp;"; // dummy space otherwise the table cell border doesn't get drawn!

		String msgLink = null;
		String attachmentsLink = null;
		if (inCount > 0 || outCount > 0)
		{
			msgLink = "/muse/browse?" + df.messagesURL;
			// many times we don't have an explicit attachments url, but its just a filter like sentiments=congratulations so just append that to attachments?s
			attachmentsLink = "/muse/attachments?" + df.messagesURL;
		}
		String info = JSPHelper.docControls(msgLink, attachmentsLink, null);

	//	out.print("<td>" + names + "</td>\n");
	//	out.println (protovisCell);
	//	out.print("</tr>\n");
		// somehow the <td> below needs a specific style to suppress border, class="noborder" doesn't seem to work.
		String chartDivId = chartSpec[0].substring(chartSpec[0].startsWith("#") ? 1 : 0);
		// the canvas div must appear before the protovis invocation 
		return ("<a href='#?custom=true&amp;id=" + chartDivId + "' rel='subchart[gallery]' title='" + descr + "'>"
				+ protovisCell // + "<br/>"
				+ "</a>"
				+ protovisCaption(descr, info));
	}

	private static String protovisCaption(String descr, String info)
	{
		return	"<table class=\"noborder protovis-text\" width=\"100%\"><tr><td class=\"noborder\" style=\"border-style:none;border-width:0px\"><span style=\"font-size:small;font-variant:small-caps\">" + Util.escapeHTML(Util.ellipsize(descr, 30)) + "</span><br/>" +
				"<span style=\"font-size:small\">" + " " /* removing secondary descr. */ + "</span></td><td class=\"noborder\" style=\"border-style:none;border-width:0px\" align=\"right\" valign=\"bottom\">" + info + "</td></tr></table>";
	}

	/** max is a normalizer for the # of messages. inDates/outDates can be null in which case they are treated as empty. */
	public static String getProtovisForDetailedFacet(String[] chartSpec, DetailedFacetItem df, int[] inGram, int[] outGram, List<Date> intervals, int normalizer)
	{
		int inCount = 0;
		for (int i = 0; i < inGram.length; inCount += inGram[i++]);
		int outCount = 0;
		for (int i = 0; i < outGram.length; outCount += outGram[i++]);
		return getProtovisForDetailedFacet(chartSpec, df, inCount, outCount, inGram, outGram, intervals, normalizer);
	}

	/** generates protovis string for group activity (in/out) chart + all names in the group.
	 * normalized across all groups.
	 * optionally group members names are included.
	 * @return
	 */
	public static List<String> getProtovisForGroups(AddressBook addressBook, List<SimilarGroup<String>> groups, Collection<EmailDocument> allDocs, int nIntervals, int width, int height, boolean generateNames)
	{
		// compute in/out dates for each group
		List<Date>[] inDates = new ArrayList[groups.size()];
		List<Date>[] outDates = new ArrayList[groups.size()];
		for (int i = 0; i < groups.size(); i++)
		{
			inDates[i] = new ArrayList<Date>();
			outDates[i] = new ArrayList<Date>();
		}

		for (EmailDocument ed: allDocs)
		{
			List<String> rawEmailAddrs = ed.getParticipatingAddrsExcept(addressBook.getOwnAddrs());
			List<String> canonicalEmailAddrs = addressBook.convertToCanonicalAddrs(rawEmailAddrs);
			Collections.sort(canonicalEmailAddrs);
			Group<String> emailGroup = new Group<String>(canonicalEmailAddrs);
			int x = Group.bestFit(groups, emailGroup);
			if (x != -1)
			{
				int sentOrReceived = ed.sentOrReceived(addressBook);
				if ((sentOrReceived & EmailDocument.RECEIVED_MASK) != 0)
					inDates[x].add(ed.date);
				if ((sentOrReceived & EmailDocument.SENT_MASK) != 0)
					outDates[x].add(ed.date);
			}
		}

		// find normalizing max
		int max = Integer.MIN_VALUE;
		Pair<Date, Date> p = EmailUtils.getFirstLast(allDocs);
		Date globalStart = p.getFirst();
		Date globalEnd = p.getSecond();
		List<Date> intervals = CalendarUtil.divideIntoIntervals(globalStart, globalEnd, nIntervals);

		for (int i = 0; i < groups.size(); i++)
		{
			int x = normalizingMax(inDates[i], outDates[i], intervals, /* inNOut */ true);
			if (x >= max)
				max = x;
		}

		// generate protovis
		List<String> result = new ArrayList<String>();
		for (int i = 0; i < groups.size(); i++)
		{
			int[] inGram = CalendarUtil.computeHistogram(inDates[i],intervals);
		//	double[] normalizedInGram = Util.normalizeHistogramToBase(inGram, max);
			int[] outGram = CalendarUtil.computeHistogram(outDates[i], intervals);
		//	double[] normalizedOutGram = Util.normalizeHistogramToBase(outGram, max);
			String url = JSPHelper.getURLForGroupMessages(i);
			StringBuilder sb = new StringBuilder();
			sb.append (getProtoVizBox(null, outDates[i].size(), inDates[i].size(), outGram, inGram, max, width, height, true, true, intervals.get(0), intervals.get(intervals.size()-1), true/*focusOnly*/, "'" + url + "'"));

			// add names to the mark if needed
			if (generateNames)
			{
				sb.append ("<br/><span style=\"font-size:small\">");
				for (String str: groups.get(i).elements)
					sb.append (Util.strippedEmailAddress(str) + "<br/>");

				sb.append ("<a href=\"" + url + "\" target=\"_new\"><img title=\"Messages\" src=\"/muse/images/email.jpg\" width=\"25\"/>" +"</a>");

				sb.append ("</span>");
			}
			result.add(sb.toString());
		}

		return result;
	}


	/** addressBook is needed only for telling whether we received or sent the message. if its null we assume sent (i.e. protovis is shown above x-axis) */
	public static String getProtovisForDocs(String[] chartSpec, Collection<DatedDocument> docs, AddressBook ab, List<Date> intervals, int normalizer, int width, int height, boolean showTotals, boolean inNOut, String browseParams, String caption)
	{
		Pair<List<Date>, List<Date>> p = ab.splitInOutDates(docs, inNOut);
		// compute in/out dates for each group
		List<Date> inDates = p.getFirst();
		List<Date> outDates = p.getSecond();

		String info = JSPHelper.docControls("/muse/browse", "attachments", null);

		return	getProtovisForDates(chartSpec, outDates, inDates, intervals, normalizer, width, height, showTotals, inNOut, browseParams) +
				((caption == null) ? "" : protovisCaption(caption, info));
	}

//	public static String protovisForDocs (Collection<DatedDocument> docs, AddressBook addressBook, int nIntervals, int width, int height)
//	{
//		return protovisForDocs (docs, addressBook, nIntervals, width, height, true, true);
//	}

//	public static String protovisForDocs (Collection<DatedDocument> docs, AddressBook addressBook, int nIntervals, int width, int height, boolean showTotals, boolean inNOut)
//	{
//		Pair<Date, Date> p = EmailUtils.getFirstLast(docs);
//		Date globalStart = p.getFirst();
//		Date globalEnd = p.getSecond();
//		List<Date> intervals = CalendarUtil.divideIntoIntervals(globalStart, globalEnd, nIntervals);
//		int normalizer = normalizingMax(docs, addressBook, intervals);
//		return getProtovisForDocs(docs, addressBook, intervals, normalizer, width, height, showTotals, inNOut);
//	}

	public static int normalizingMax(Collection<DatedDocument> docs,  AddressBook addressBook, List<Date> intervals)
	{
		List<Date> dates = new ArrayList<Date>();
		for (DatedDocument dd: docs)
			dates.add(dd.date);

		return findNormalizingMax(dates, intervals);
	}

	private static int normalizingMax(List<Date> inDates, List<Date> outDates, List<Date> intervals, boolean inNOut)
	{
		int max;
		if (inNOut)
		{
			int max1 = findNormalizingMax(inDates, intervals);
			int max2 = findNormalizingMax(outDates, intervals);
			max = Math.max(max1, max2);
		}
		else
		{
			List<Date> list = new ArrayList<Date>();
			list.addAll(inDates);
			list.addAll(outDates);
			max = findNormalizingMax(outDates, intervals);
		}
		return max;
	}

	private static int findNormalizingMax(List<Date> dates, List<Date> intervals)
	{
		if (dates == null)
			return Integer.MIN_VALUE;
		int[] histogram = CalendarUtil.computeHistogram(dates, intervals);
		int max = Integer.MIN_VALUE;
		for (int x : histogram)
			if (x > max)
				max = x;
		return max;
	}

	public static int findNormalizingMaxForDocs(Collection<DatedDocument> docs, List<Date> intervals)
	{
		if (docs == null)
			return Integer.MIN_VALUE;
		List<Date> dates = new ArrayList<Date>();
		for (DatedDocument dd: docs)
			dates.add(dd.date);
		return findNormalizingMax(dates, intervals);
	}

	public static int findMaxInOrOutInAnInterval (List<Date> dates, List<Date> intervals)
	{
		int[] histogram = CalendarUtil.computeHistogram(dates, intervals);
		int max = Integer.MIN_VALUE;
		for (int x : histogram)
			if (x > max)
				max = x;
		return max;
	}

	public static int findMaxInOrOutInAnInterval (List<Date> inDates, List<Date> outDates, List<Date> intervals)
	{
		int[] histogram = CalendarUtil.computeHistogram(inDates, intervals);
		int max = Integer.MIN_VALUE;
		for (int x : histogram)
			if (x > max)
				max = x;

		histogram = CalendarUtil.computeHistogram(outDates, intervals);
		for (int x : histogram)
			if (x > max)
		max = x;
		return max;
	}

	public static int findMaxInOrOutInAnInterval (int[] inGram, int[] outGram, List<Date> intervals)
	{
		int max = Integer.MIN_VALUE;
		for (int x : inGram)
			if (x > max)
				max = x;

		for (int x : outGram)
			if (x > max)
				max = x;
		return max;
	}

}
