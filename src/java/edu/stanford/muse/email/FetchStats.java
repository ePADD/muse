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
package edu.stanford.muse.email;


import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;

import java.io.Serializable;
import java.util.*;

/** used to be fetch stats, but is really doing most stats on the docs themselves */
public class FetchStats implements Serializable {
	private final static long serialVersionUID = 1L;

	public long lastUpdate;
	public long fetchAndIndexTimeMillis;
	public String userKey;
	public int nMessagesOriginal, nMessagesAfterFiltering, nMessages;
	public List<Pair<String,FolderInfo>> selectedFolders = new ArrayList<Pair<String,FolderInfo>>(); // selected folders and their counts
	public Filter messageFilter;
	public long firstMessageDate, lastMessageDate;
	public int spanInMonths;
	public Collection<String> dataErrors;

	public String toString()
	{
		// do not use html special chars here!
		Calendar c = new GregorianCalendar();
		c.setTime(new Date(lastUpdate));
		String s = "Import date: " + Util.formatDateLong(c) + "\n";
//		s += "user_key: " + userKey + "\n";
//		s += "total_folders: " + nFolders + "\n";
		if (selectedFolders == null)
			s += "selected_folders: null";
		else
		{
			s += "selected_folders: " + selectedFolders.size();
			for (Pair<String,FolderInfo> p: selectedFolders)
				s += " - " + p.getFirst() + " (" + p.getSecond() + ")";
		}
		s += "\n";
		s += "message_filter: " + messageFilter + "\n";

		s += "selected_messages: " + nMessages + " original: " + nMessagesOriginal +  " post_filtering: " + nMessagesAfterFiltering + " dups: " + (nMessagesAfterFiltering-nMessages) + "\n";
	//	s += "sent_messages: " + nMessagesSent + " received_messages: " + nMessagesReceived + "\n";
		s += "first_date: " + Util.formatDate(new Date(firstMessageDate)) + " last_date: " + Util.formatDate(new Date(lastMessageDate)) + " span_in_months: " + spanInMonths + "\n";
		s += "fetch_time_in_secs: " + fetchAndIndexTimeMillis/1000 + "\n";

		return s;
	}


	public String toHTML()
	{
	// do not use html special chars here!
		Calendar c = new GregorianCalendar();
		c.setTime(new Date(lastUpdate));
		String s = "Fetch Date: " + Util.formatDateLong(c) + "<br/>\n";
		s += "Fetch and index time: " + Util.pluralize((int) fetchAndIndexTimeMillis/1000, "second") + "<br/>\n";
		if (selectedFolders == null)
			s += "Selected folders: unavailable";
		else
		{
			s += "Selected folders: " + selectedFolders.size() + "<br/>";
			for (Pair<String,FolderInfo> p: selectedFolders)
				s += Util.escapeHTML(p.getFirst()) + " (" + Util.escapeHTML(p.getSecond().toString()) + ")" + "<br/>";
		}

		s += "Imported messages: " + nMessages + " (original: " + nMessagesOriginal +  " post_filtering: " + nMessagesAfterFiltering + ") duplicates: " + (nMessagesAfterFiltering-nMessages) + "<br/>\n";
		s += ((messageFilter == null) ? "No message filter" : ("message_filter: " + messageFilter)) + "<br/>\n";
	//	s += "Sent messages: " + nMessagesSent + " Received messages: " + nMessagesReceived + "<br/>\n";
		s += "Messages span: " + Util.formatDate(new Date(firstMessageDate)) + " to " + Util.formatDate(new Date(lastMessageDate)) + "<br/>\n";

		return s;
	}

	public static void main (String args[])
	{
		 FetchStats as = new FetchStats();
		 System.out.println(Util.fieldsToString(as));
	}
}
