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
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.stanford.muse.util.Util;

/** A card is a collection of card terms, typically represents all the terms for a month */
public class Card implements Serializable {
	private final static long serialVersionUID = 1L;

	public String description;
	public List<CardTerm> terms;
	public int nMessages;

	/* we _generally_ have a card per month. But leave the door open for cards of other types in the future, 
	 * e.g. cards for a given facet of messages, that may not necessarily be in the same month */
	public int startYear, startMonth;  // month is 0-based
	
	public Card (String description, List<CardTerm> terms, int nMessages)
	{
		this.description = description;
		this.terms = terms;
		this.nMessages = nMessages;
	}

	public void setYearAndMonth(int year, int month)
	{
		Util.softAssert (month >= 0 && month < 12);
		this.startYear = year;
		this.startMonth = month;
	}
	
	public String toString()
	{
		return "Tag cloud for " + description + " with " + nMessages + " messages";
	}
	
	public String toJson() throws JSONException
	{
		JSONObject result = new JSONObject();
		result.put("description", description);
		result.put("nMessages", nMessages);
		JSONArray ts = new JSONArray();
		for (int i = 0; i < terms.size(); i++)
			ts.put(i, terms.get(i).originalTerm);
		result.put ("terms", ts);
		return result.toString();
	}
}
