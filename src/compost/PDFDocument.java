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
package edu.stanford.muse.index;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.util.Util;


public class PDFDocument extends CategoryDocument {
	private static Log log = LogFactory.getLog(CategoryDocument.class);
	private final static long serialVersionUID = 1L;

	public String relativeURLForPDF;
	public List<String> relativeURLsForPNGs = new ArrayList<String>();

	/** warning: URLs better not have '#' in them */
	public PDFDocument(int num, String s, String category, String relativeURLForPDF, List<String> relativeURLsForPNGs)
	{
		super (num, s, category);
		this.relativeURLForPDF = relativeURLForPDF;
		this.relativeURLsForPNGs = relativeURLsForPNGs;
		if (relativeURLForPDF.indexOf("#") >= 0)
			log.warn ("PDFDocument being created with a bad relative URL, we don't want hashes in them: " + relativeURLForPDF);
		for (String x: relativeURLsForPNGs)
			if (x.indexOf("#") >= 0)
				log.warn ("PDFDocument being created with a bad PNG URL, we don't want hashes in them: " + x);
	}

	public String urlForPage(int pageNum)
	{
		return relativeURLsForPNGs.get(pageNum) + "." + pageNum + ".png";
	}

	/** returns array of strings, 1 for each page, containing html for that page */
	@Override
	public List<String> getHTMLForContents(Indexer indexer) throws IOException
	{
		List<String> result = new ArrayList<String>();

		if (relativeURLsForPNGs == null)
		{
			String contents = "\nPages not converted for " + this + ". Please reset and try again.\n\n";
			try { contents = getContents(); }
			catch (Exception e) { log.warn(e + Util.stackTrace(e)); }

			contents = Util.escapeHTML(contents);
			StringBuilder page = new StringBuilder();
			contents = contents.replace("\n", "<p>\n");
			page.append (contents);
			result.add(page.toString());
			return result;
		}

		int pageNum = 1; // remember: the png's are numbered from 1 onwards

		for (String pngPrefixURL: relativeURLsForPNGs)
		{
			String pngURL = pngPrefixURL + "." + pageNum + ".png";

			StringBuilder page = new StringBuilder();
//				page.append ("<a href=\"" + relativeURLForPDF + "\">PDF</a><p>\n");
			page.append ("<table><tr>");
			page.append ("<td valign=\"top\">\n");

			// inner table to ensure spacing between PDF image and dataset title
//				page.append ("<table><tr><td>");
//			page.append ("<div align=\"center\"> <a href=\"" + relativeURLForPDF + "\"><img width=\"40\" src=\"images/pdf.jpg\"/></a></div><p>\n");
//				page.append ("</td></tr><tr><td>");
//			if (verticalText != null)
//			{
//				int margin_px = 40 + 10 * verticalText.length();
//				page.append ("<div class=\"vertical-text\" style=\"margin-top:" + margin_px + "px\">" + verticalText + "</div>\n");
//			}
//				page.append ("</td></tr></table>");

			page.append ("</td>\n");
			// magic #: page width = 800
			// min-height = 1100px seems to be not working...
			page.append ("<td><img src=\"" + pngURL + "\" width=\"800\" style=\"min-height:1100px\" alt=\"Page " + (pageNum++) + "\"/></td>\n");
			page.append ("</tr></table>");
			result.add(page.toString());
		}

		return result;
	}
}
