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


import java.io.Serializable;
import java.util.Map;

import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;

/** A card term is a single term in a card */
public class CardTerm implements Serializable {
	private final static long serialVersionUID = 1L;

public int size;
/* color weights is a map carrying the affinity to each color of this term. 
 * Currently we just use the color with the highest weight, but storing the full map in case we want to do fancier viz in the future */
public Map<Integer, Float> colorWeights; 

public String lookupTerm, originalTerm; // canonical v/s original term, original term should be displayed, canonical term used for internal query
public int nDocs;
public float score;
public int tf;
public float idf;

public CardTerm(int size,  String originalTerm, String lookupTerm, float score, int tf, float idf, int nDocs)
{
	this.size = size;
	this.originalTerm = originalTerm;
	this.lookupTerm = lookupTerm;
	this.score = score;
	this.tf = tf;
	this.idf = idf;
	this.nDocs = nDocs;
}

public int bestColor()
{
	Map.Entry<Integer, Float> e = GroupAssigner.highestValueEntry(colorWeights);
	if (e == null)
		return -1;
	else
		return e.getKey();
}

public void setColors(Map<Integer, Float> c)
{
	this.colorWeights = c;
}

public String toHTML(boolean colored, int startYear, int startMonth, String browseParam)
{
	// be real careful with quoting here!
	// we will url encode the term in the url, so right now just escape the single quote
	// String correctedLookupTerm = lookupTerm.replace("'", "\\'").trim();	
	String correctedLookupTerm = lookupTerm.trim();
	if (correctedLookupTerm.length() == 0) {
		// hack: not interesting term
		return "";
	}

	// tag already should have colors computed
	int color = bestColor();
	String colorClass = "NONE";
	if (colored)
		colorClass = "colorClass" + (color+1)%JSPHelper.nColorClasses;

	// important: correctedLookupTerm is enclosed in quotes because it may be a phrase with embedded spaces.
	// it also has to be url encoded to escape the dquotes and also any special characters inside it
	String link = "/muse/browse?" + browseParam + "&term=" + Util.URLEncode("\"" + correctedLookupTerm + "\"") + "&startYear=" + startYear + "&startMonth=" + startMonth; // &quot; needed because the string goes inside an onclick="..."
	String correctedDisplayTerm = originalTerm.replace("'", "\\'"); // .replace("\"", " ");
	String title = correctedDisplayTerm + " (occurs " + Util.pluralize(this.tf, "time") + ")";
	String html = "<a onclick=\"if (!event.shiftKey) { window.open('" + link + "'); } else { copyTermToDiary(this);} return false;\" href=\"dummy\" title=\"" + title + "\" class=\"tagcloud-anchor " + colorClass + "\">" + Util.escapeHTML(Util.ellipsize(correctedDisplayTerm, 30)) + "</a>";
	return html;
}

public String toString()
{
	return Util.fieldsToString(this, true);
}
}
