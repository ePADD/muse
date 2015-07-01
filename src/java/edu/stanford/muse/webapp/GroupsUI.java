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
package edu.stanford.muse.webapp;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.groups.Group;
import edu.stanford.muse.groups.SimilarGroup;
import edu.stanford.muse.groups.SimilarGroupMethods;
import edu.stanford.muse.groups.SimilarSuperGroup;
import edu.stanford.muse.index.GroupAssigner;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.ProtovisUtil;
import edu.stanford.muse.util.Util;

public class GroupsUI {

	public static String getDisplayForGroupHierarchy(List<SimilarGroup<String>> rootGroups, Map<SimilarGroup<String>, List<SimilarGroup<String>>> parentToChildrenMap, Map<SimilarGroup<String>, String> protovisMap)
	{
		StringBuilder sb = new StringBuilder();
		int count = 0;

		for (SimilarGroup<?> g: rootGroups)
		{
			sb.append ("Group " + count + ". <br/>" + GroupsUI.getDisplayForGroup(parentToChildrenMap, g, protovisMap) + "<br/>");
			count++;
		}
		return sb.toString();
	}

	/* get display string for a group and all its descendants */
	private static String getDisplayForGroup(Map<SimilarGroup<String>, List<SimilarGroup<String>>> parentToChildrenMap, SimilarGroup<?> g, Map<SimilarGroup<String>, String> protovisMap)
	{
		StringBuilder sb = new StringBuilder();
		String style = (g instanceof SimilarSuperGroup) ? "style=\"border-color:red;border-style:dashed;border-width:3px;\"" : "";
	    sb.append ("<table class=\"protovizcontacts\"><tr><td " + style + ">");
	    sb.append (protovisMap.get(g));
	    sb.append ("</td>");

		// recurse into children if any
		List<SimilarGroup<String>> children = parentToChildrenMap.get(g);
		if (children != null && children.size() > 0) // don't print <td></td> if there are no children
		{
	        sb.append ("<td>");
			for (SimilarGroup<?> child: parentToChildrenMap.get(g))
				sb.append (getDisplayForGroup(parentToChildrenMap, child, protovisMap));
	        sb.append ("</td>");
		}
	    sb.append ("</tr></table>");

		return sb.toString();
	}

	public static String htmlForPersonList(Group<String> g, AddressBook addressBook, String className, boolean editingAllowed, boolean inline)
	{
		// usually if editing is allowed, hrefForPerson is false because having the href interferes with dnd
		boolean hrefForPerson = !editingAllowed;
		return GroupsUI.htmlForPersonList(g, addressBook, className, editingAllowed, hrefForPerson, inline);
	}

	/** emit the people in the group as a <ul> */
	public static String htmlForPersonList(Group<String> g, AddressBook addressBook, String className, boolean editingAllowed, boolean hrefForPerson, boolean inline)
	{
		String result = "<ul class=\"personList " + className + "\">\n"; // right padding for personMenu

		// check if label is required, currently this is implied by !inline, but we can consider separating it out in future
		if (!inline)
		{
			if (editingAllowed)
			{
				String onfocus = "groupNameEditStart(this, 'Label')";
				String onblur = "groupNameEditEnd(this)";
				String groupName = ((SimilarGroup) g).name;
				String label = Util.nullOrEmpty(groupName) ? "Label" : groupName;
				label = Util.escapeHTML(label);
				if (g.size() > 1)
					result += "<li class=\"group-header\"><input onfocus=\"" + onfocus + "\" onblur=\"" + onblur + "\" style=\"width:100px;\" class=\"unhilited-group-name groupname b" + className + "\" value=\"" + label + "\"/>"; // for the menu
				else
				{
					// rather messy internal div to account for the fact that the bar was getting aligned center instead of left...
					// font-size and the blanks seem to be necessary
					result += "<li class=\"group-header\"><div style=\"display:inline;font-size:18px;\" class=\"b" + className + "\"> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</div></li>";
				}
				result += "<span class=\"groupsmenu b" + className + "\" style=\"padding:0px; z-index:99;display:none;position:absolute;top:12px;right:7px;height:18px;\">";
			//	htmlDescription += "<span onclick=\"'dupGroup(this);'\" class=\"menuitem\">+</span>\n";
				result += "<a title=\"Merge group\" onclick=\"javascript:mergeGroup(this)\"><img height=\"16\" src=\"images/merge.png\"/></a>";
				result += "<a title=\"Clone group\"onclick=\"javascript:cloneGroup(this)\"><img height=\"16\" src=\"images/restore.gif\"/></a>";
				//	htmlDescription += "<span onclick=\"'dupGroup(this);'\" class=\"menuitem\">+</span>\n";
				result += "<a title=\"Delete group\" onclick=\"javascript:deleteGroup(this)\"><img height=\"16\" src=\"images/closetab.png\"/></a>";
				result += "</span>";
				result += "</li>";
			}
			else
			{
				String groupName = ((SimilarGroup) g).name;
				if (!Util.nullOrEmpty(groupName))
				{
					String label = groupName;
					label = Util.escapeHTML(label);
					result += "<li><div style=\"display:inline;min-width:100px;\" class=\"unhilited-group-name groupname b" + className + "\"/>" + label; // for the menu
					result += "</li>";
				}
			}
		}

		StringBuilder names = new StringBuilder();
		for (int j = 0; j < g.size(); j++)
		{
		//	if (j > 200)
		//	{
		//		JSPHelper.log.warn ("Truncating extraordinarily large group of size " + g.size());
		//		break;
		//	}
			String email = g.get(j);
			String link = "browse?person=" + email + "";
			// s is the email address, but if we can find a name for this person,
			// that's even better.
			Contact c = addressBook.lookupByEmail(email);
			if (c == null)
			{
				JSPHelper.log.warn("No contact for email address! " + email);
				continue;
			}
			String personDescription;
			if (c.names != null && c.names.size() > 0)
				personDescription = c.pickBestName(); // just pick the first name
			else
				personDescription = email;

			String lookingFor = Util.escapeHTML(c.toMozContactsDescriptionStr());
			String tooltip = Util.escapeHTML(c.toTooltip());
			// warning: foll. style string will be dropped when this item is dragged into any real group.
			// so it should apply to things that exist when this item is in the ALL group (i.e. inline = true).
			String styleStr = inline ? "style=\"display:inline;float:left;width:250px;\"" : "";
			names.append("<li title=\"" + tooltip + "\" " + styleStr + " personId=\"" + email + "\" class=\"person\"  personDescription=\"" + lookingFor + "\">\n");
			if (hrefForPerson)
				names.append("<a style=\"text-decoration:none;\" href=\"" + link + "\">\n");

			// person desc. should never be more than 25 chars
			int MAX_DESCR_CHARS = 30;
			personDescription = Util.ellipsize(personDescription, MAX_DESCR_CHARS);

			// hack to get things to somewhat line up when an inline box.
			int spacesNeeded = MAX_DESCR_CHARS - personDescription.length();
			personDescription = Util.escapeXML(personDescription);
			if (inline)
				for (int x = 0; x < spacesNeeded; x++)
					personDescription += "&nbsp;";

			if (inline)
			{
				names.append("<span class=\"nameAndImage " + className + "\" style=\"text-decoration:none;\">" + personDescription + "&nbsp;\n");
			}
			else
				names.append("<span class=\"nameAndImage " + className + "\" style=\"text-decoration:none;\">" + personDescription + "&nbsp;\n");
			if (hrefForPerson)
				names.append("</a>\n");

			// by definition, person menu disabled for inline's
			if (editingAllowed && !inline)
			{
				names.append("<span class=\"personmenu \" style=\"position:absolute;right:7;z-index:99;visibility:hidden;\">");
				names.append("<a onclick=\"javascript:clonePerson(this)\"><img width=\"16\" src=\"images/restore.gif\"/></a><span>");
				names.append("<a onclick=\"javascript:deletePerson(this)\"><img width=\"16\" src=\"images/closetab.png\"/></a><span>");
			}
			names.append("</span>");
			names.append("</li>\n");
			if (inline && (j+1) % 4 == 0)
				names.append("<br/>");
		}
		result += names + "</ul>\n";

		return result;
	}

	public static String getGroupNamesWithColor(GroupAssigner groupAssigner)
	{
		List<SimilarGroup<String>> groups = groupAssigner.getSelectedGroups();
		int count = 1;
		StringBuilder sb = new StringBuilder();

		for (SimilarGroup<String> g: groups)
		{
			String colorClass;
			colorClass = "colorClass" + count%JSPHelper.nColorClasses; // className = colorClasses[0];

			count ++;
			String title = g.elementsToString();
			sb.append ("<span class=\"" + colorClass + "\" title=\"" + Util.escapeHTML(title) + "\">" + Util.escapeHTML(g.name) + "</span><br/>\n");
		}
		return sb.toString();
	}

	/** returns the html for the colors table */
	public static String getGroupsDescriptionWithColor(AddressBook addressBook, GroupAssigner groupAssigner, Collection<EmailDocument> allEmailDocs, boolean editingAllowed, int nColumns)
	{
		if (addressBook == null || allEmailDocs == null)
			return "";
		if (allEmailDocs.size() == 0)
			return "";

		// this applies only for email docs
		if (!(allEmailDocs.iterator().next() instanceof EmailDocument))
			return "";

		int color = 1;

		List<SimilarGroup<String>> coloredGroups = groupAssigner.getSelectedGroups(), individualGroups = new ArrayList<SimilarGroup<String>>(), multiPersonGroups = new ArrayList<SimilarGroup<String>>();

		for (SimilarGroup<String> g: coloredGroups)
		{
			if (g.size() == 1)
				individualGroups.add(g);
			else
				multiPersonGroups.add(g);
		}

	//	List<List<EmailDocument>> partitionedDocs = GroupsUI.partitionDocsByGroup(allEmailDocs, coloredGroups, addressBook);

		if (JSPHelper.log.isDebugEnabled())
		{
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < coloredGroups.size(); i++)
				sb.append ("Group #" + i + ": " + coloredGroups.get(i).size() + " members."); // + partitionedDocs.get(i).size() + " document(s) for " + coloredGroups.get(i) + "\n");
			JSPHelper.log.debug(sb.toString());
		}

		String html = "";

//		htmlDescription += "<br/>We have identified " + nGroups + " groups and " + nIndividuals + " individuals."
//					+ " Terms connected to other groups appear in gray.<br/>\n";
		html += "<div id=\"allGroups\" style=\"width:100%\">\n";

		html += "<table><tr>";
		if (individualGroups.size() > 0)
		{
			html += "<td class=\"groups\" valign=\"top\">";
			html += "<div id=\"superIndividuals\" align=\"center\"><h3>Individuals</b></h3><br/>\n";

			for (int i = 0; i < individualGroups.size(); i++)
			{
				String className;
				className = "colorClass" + color%JSPHelper.nColorClasses; // className = colorClasses[0];

				SimilarGroup<String> g = individualGroups.get(i);
				html += "<div class=\"outergroup\">";
				html += "<span class=\"db-hint\">";
				String descriptiveTags = g.descriptiveTags();
				if (descriptiveTags != null)
				{
					html += descriptiveTags;
				}
				html += "</span>";

				html += "<div style=\"position:relative;align:left\" class=\"individualgroup group\" title=\"Value=" + g.utility + "\">\n";
				html += htmlForPersonList(g, addressBook, className, editingAllowed, false);
				html += "</div>\n"; // .group
				html += "</div>\n"; // .outerGroup
//				if ((i+1) % nColumns == 0)
//					html += "</tr><tr>";
				color++;
			}
			html += "</td>";
		}

		if (multiPersonGroups.size() > 0)
		{
			html += "<td valign=\"top\">";
			html += "<div align=\"center\"><h3>Groups</h3><br/></div>\n";

			html += "<table align=\"center\" class=\"border\">\n";
			html += "<tr><td class=\"groups\">\n";

			int peopleInMultiPersonGroups = 0;
			for (Group<String> g: multiPersonGroups)
				peopleInMultiPersonGroups += g.size();

			multiPersonGroups = SimilarGroupMethods.orderGroupsBySimilarity(multiPersonGroups);

			// approx. attempt to balance the columns. there must be a better way of doing this...
			int personsInThisColumn = 0, PERSONS_PER_COLUMN = 2 + (peopleInMultiPersonGroups + multiPersonGroups.size())/nColumns;

			List<String> protovizForGroupActivity = ProtovisUtil.getProtovisForGroups(addressBook, coloredGroups, allEmailDocs, 40, 160, 40, false);

			for (int i = 0; i < multiPersonGroups.size(); i++)
			{
				SimilarGroup<String> g = multiPersonGroups.get(i);
				String className = "colorClass" + color%JSPHelper.nColorClasses; // className = colorClasses[0];

				// switch to a new column if we are above the line count
				if (personsInThisColumn >=  PERSONS_PER_COLUMN)
				{
					html += "</td><td class=\"groups\">";
					personsInThisColumn = 0;
				}

				html += "<div class=\"outergroup\">\n";

				html += "<span class=\"db-hint\">";
				String descriptiveTags = g.descriptiveTags();
				if (descriptiveTags != null)
				{
					html += descriptiveTags;
				}
				html += "</span>";

				html += "<div style=\"position:relative;\" class=\"multigroup group\" title=\"Value=" + g.utility + "\">\n";
				// see if group has any descriptive tags

	//			htmlDescription += "<div class=\"group\" style=\"background-image:url('images/closetab.png');background-position:top right;background-repeat:no-repeat\">\n";
				html += htmlForPersonList(g, addressBook, className, editingAllowed, false);
				personsInThisColumn += g.size();

				// link for browsing all messages associated with all members of the group
				if (!editingAllowed)
				{
					String msgLink = JSPHelper.getURLForGroupMessages(i);
					String nMessagesStr = "<table width=\"100%\"><tr>\n";
		//			nMessagesStr += "<td><small>(" + partitionedDocs.get(i).size() + ")</small></td>\n";
					nMessagesStr += "<td>" + JSPHelper.docControls(msgLink, "attachments?groupIdx=" + i, "links?groupIdx=" + i) + "</td>\n";

					nMessagesStr += "<td><small><a title=\"Index messages from just this group\" href=\"#\" onclick='muse.indexGroup(" + i + ")'>Index</a></small></td>\n";
					nMessagesStr += "<td style=\"margin-right:10px; width:250;\">" + protovizForGroupActivity.get(i) + "</td><td>&nbsp;</td>"; // protovisForDocs(partitionedDocs.get(i));
					nMessagesStr += "</tr></table>\n";
					html += "<br/>\n" + nMessagesStr;
				}

				personsInThisColumn++;
				html += "</div>"; // class="group"
				html += "</div>"; // class="outergroup"

				color++;
			}
			html += "</td></tr></table></div>";
			html += "</td>";
		}
		html += "</tr></table>";
		html += "</div>"; // allGroups
		return html;
	}

	private static List<List<EmailDocument>> partitionDocsByGroup(Collection<EmailDocument> docs, List<SimilarGroup<String>> groups, AddressBook addressBook)
	{
		// partition docs into #groups lists of docs
		List<List<EmailDocument>> partitionedDocs = new ArrayList<List<EmailDocument>>();
		for (int i = 0; i < groups.size(); i++)
			partitionedDocs.add(new ArrayList<EmailDocument>());

		for (EmailDocument ed: docs)
		{
			List<String> rawEmailAddrs = ed.getParticipatingAddrsExcept(addressBook.getOwnAddrs());
			List<String> canonicalEmailAddrs = addressBook.convertToCanonicalAddrs(rawEmailAddrs);
			Collections.sort(canonicalEmailAddrs);
			Group<String> emailGroup = new Group<String>(canonicalEmailAddrs);
			int x = Group.bestFit((List) groups, emailGroup);
			if (x != -1)
				partitionedDocs.get(x).add(ed);
		}
		return partitionedDocs;
	}

}
