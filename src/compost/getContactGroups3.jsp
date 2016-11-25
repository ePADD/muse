<%@ page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.bespoke.mining.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<% 	JSPHelper.logRequest(request); %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<script type="text/javascript" src="js/protovis.js"></script>
<title>Contact Groups</title>
<jsp:include page="css/css.jsp"/>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
</head>
<body>

<%@include file="header.html"%>
<p/> <p/> <p/> <p/>
<script type="text/javascript" src="js/muse.js"></script>

<script type="text/javascript">
function toggleVisibility()
{
	var x = $('.superseded');
	if (x.hasClass('hidden'))
	{
		x.removeClass('hidden'); x.addClass('shown'); $('#toggleButton').html("Hide subsumed groups");
	}
	else
	{
		x.removeClass('shown'); x.addClass('hidden');
	}
}
</script>
<br/>
<%
	AddressBook addressBook = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
	String rootDir = JSPHelper.getRootDir(request);
    List<EmailDocument> allEmails = (List<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");

    int MINCOUNT = 3;
    String s = request.getParameter("minCount");
    try { MINCOUNT = Integer.parseInt(s); } catch (NumberFormatException nfe) { }

    float UTILITY_MULTIPLIER = 1.5f;
    s = request.getParameter("multiplier");
    try { UTILITY_MULTIPLIER = Float.parseFloat(s); } catch (NumberFormatException nfe) { }

    float MAX_ERROR = 0.1f;
    s = request.getParameter("maxError");
    try { MAX_ERROR = Float.parseFloat(s); } catch (NumberFormatException nfe) { }

    int MIN_GROUP_SIZE = 1;
    s = request.getParameter("minGroupSize");
    try { MIN_GROUP_SIZE = Integer.parseInt(s); } catch (NumberFormatException nfe) { }

    float MIN_MERGE_GROUP_SIM = 0.5f;
    s = request.getParameter("minMergeGroupSim");
    try { MIN_MERGE_GROUP_SIM = Float.parseFloat(s); } catch (NumberFormatException nfe) { }

    /*
    float MIN_GROUP_SIM_EVOLUTION = 0.1f;
    s = request.getParameter("minGroupSimEvolution");
    try { MIN_GROUP_SIM_EVOLUTION = Float.parseFloat(s); } catch (NumberFormatException nfe) { }

    int WINDOW_SIZE=12, WINDOW_SCROLL=3;
    s = request.getParameter("windowSize");
    try { WINDOW_SIZE = Integer.parseInt(s); } catch (NumberFormatException nfe) { }

    s = request.getParameter("windowScroll");
    try { WINDOW_SCROLL = Integer.parseInt(s); } catch (NumberFormatException nfe) { }
	*/

    boolean threadsOnly = request.getParameter("threadsOnly") != null;
    if (threadsOnly)
	    allEmails = EmailUtils.threadHeaders(allEmails); // uncomment to process only threads

	List<Group<String>> input = addressBook.convertEmailsToGroups(allEmails);

	GroupAlgorithmStats<String> stats = new GroupAlgorithmStats<String>();

	String utilityType = request.getParameter("utilityType");
	GroupHierarchy<String> hierarchy = SimilarGroupMethods.findContactGroupsIUI(input, MINCOUNT, MIN_GROUP_SIZE, MAX_ERROR, MIN_MERGE_GROUP_SIM, utilityType, UTILITY_MULTIPLIER, stats);

	AddressBook.AddressBookStats abStats = addressBook.getStats();
	String str = abStats.toString();
	out.println ("<b>Contacts information</b><p/> " + str.replace("\n", "<br/>\n") + "<br/>");
	System.out.println ("Address book stats: " + str);

	out.println ("<b>Grouping statistics</b><p/> " + stats.toString().replace("\n", "<br/>\n"));
	System.out.println ("Grouping stats: " + stats);

		//// end computing groups, the rest is display
		Map<SimilarGroup<String>, List<SimilarGroup<String>>> parentToChildGroupMap = hierarchy.parentToChildrenMap;
		Set<SimilarGroup<String>> rootGroups = hierarchy.rootGroups; // convert from set to list
		List<SimilarGroup<String>> allGroups = hierarchy.getAllGroups();

		// compute protovis strings for each group
		List<String> protovis = ProtovisUtil.getProtovisForGroups(addressBook, allGroups, allEmails, 40, 160, 40, true);
		Map<SimilarGroup<String>, String> protovisMap = new LinkedHashMap<SimilarGroup<String>, String>();
		for (int i = 0; i < allGroups.size(); i++)
	protovisMap.put(allGroups.get(i), protovis.get(i));

		// just sanity checking group's hashcodes and equals
		for (SimilarGroup<String> sg: allGroups)
		 	Util.ASSERT (allGroups.contains (sg));

		Collections.sort (allGroups);
%>
	<% if (!"socialflow".equals(JSPHelper.getSessionAttribute(session, "mode")))	{ %>
	<br/><br/>
	<b><%=allGroups.size()%> groups with min. frequency <%= MINCOUNT%></b><br/>

	<%=rootGroups.size()%> root groups and their subsets are shown below.<br/>
	The groups in red boxes are created by merging groups with similarity at least <%=MIN_MERGE_GROUP_SIM%>.<br/>
	<% } %>

	<%
	List<SimilarGroup<String>> groupsList = new ArrayList<SimilarGroup<String>>();
	groupsList.addAll(rootGroups);
	AddressBook.sortByMass(allEmails, addressBook, groupsList);
	List<SimilarGroup<String>> orderedRootGroups = SimilarGroupMethods.orderGroupsBySimilarity(groupsList);
	String json = JSONUtils.jsonForHierarchy(addressBook, orderedRootGroups, parentToChildGroupMap).toString(2); // indent factor, pretty toString the json;

	if ("socialflow".equals(JSPHelper.getSessionAttribute(session, "mode")))
	{
		String callbackURL = (String) JSPHelper.getSessionAttribute(session, "socialflow");
		if (callbackURL != null)
		{
	%>
	<p>
	Done! <%=orderedRootGroups.size()%> sets of groups have been inferred from your email.<br/>
	<form method="post" action="<%=callbackURL%>">
	<input type="hidden" name="input" value="<%=Util.escapeHTML(json)%>"></input>
	<button name="submit" value="submit" type="submit">Continue with SocialFlows</button>
	</form>
	<%
		}
	}


	// Generate JSON output for input groups
	String inputgroups_json = "";
	new File(rootDir).mkdirs();
    String file = rootDir + File.separator + "inputgroups_json.txt";
    JSONObject inputObj = JSONUtils.jsonForGroupsInput(addressBook, input);
    PrintWriter pw = new PrintWriter(file, "UTF-8"); pw.println(inputObj.toString(2)); pw.close();
    String userKey = (String) JSPHelper.getSessionAttribute(session, "userKey");
    out.println ("<br/><a href=\"" + userKey + "/inputgroups_json.txt\">Input Groups JSON</a>\n");

	// Generate JSON output for final groups
	new File(rootDir).mkdirs();
	file = rootDir + File.separator + "json.txt";
    pw = new PrintWriter(file, "UTF-8"); pw.println(json); pw.close();
    userKey = (String) JSPHelper.getSessionAttribute(session, "userKey");
	out.println ("<br/><a href=\"" + userKey + "/json.txt\">JSON</a><br/><br/>\n");
    if (!"socialflow".equals(JSPHelper.getSessionAttribute(session, "mode")))
		out.println (GroupsUI.getDisplayForGroupHierarchy(orderedRootGroups, parentToChildGroupMap, protovisMap));

	/*  Evolution stuff:

		out.println ("<hr/>");
		out.println ("<h3>" + uiGroups.size() + " UI groups, no subsets, min group size 2</h3>");

		// json
//		String json = JSONUtils.jsonForGroups(addressBook, uiGroups);

		count = 0;
		for (SimilarGroup<String> group: uiGroups)
		{
			// build url to all messages to the group
			// e.g docsWithPerson.jsp?person=email1&person=email2
			StringBuilder url = new StringBuilder("docsWithPerson.jsp?");
			int groupSize = group.size();
			for (int x = 0; x < groupSize; x++)
			{
				String email = group.get(x);
				url.append ("person=" + email);
				if (x < groupSize-1) // no '&' for last person
					url.append ("&");
			}

			out.toString ("<a href=\"" + url + "\">G" + count +"</a> ");
			out.println (group);
			out.println ("<br/>");
			count++;
		}

	    out.println ("<p/><hr/><p/><h3>Counts matrix</h3><p/>");

	    List<EmailDocument> allDocs = (List<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");

	    List<List<Contact>> cGroups = new ArrayList<List<Contact>>();

	    for (SimilarGroup<String> sg: allGroups)
	    {
	    	List<Contact> list = new ArrayList<Contact>();
	    	for (String email: sg.elements)
		    	list.add(addressBook.lookupByEmail(email));
		    cGroups.add(list);
	    }
	    int[][] counts = JSPHelper.getGroupFreqByTime(addressBook, cGroups, allDocs); // counts is [group][timeclusters]

	    Date d = CalendarUtil.computeEarliestDate(addressBook.computeCIList());
	    Calendar c = new GregorianCalendar();
	    c.setTime(d);
	    int m = c.get(Calendar.MONTH);
	    int y = c.get(Calendar.YEAR);

	    int nMonths = 0;
	    if  (counts.length > 0)
	    	nMonths = counts[0].length;

	    out.println ("-- ");

	    for (int i = 0; i < nMonths; i++)
	    {
	        out.println ((m+1) + "/" + String.format("%02d", (y%100)) + " ");
	        m++;
	        if (m == 12)
	        {
	        	m = 0;
	        	y++;
	        }
	    }
	    out.println ("\n<br/>\n");

	    for (int i = 0; i < counts.length; i++)
	    {
	        out.println ("G" + i + " ");
	        int[] x = counts[i];
	    	for (int k: x)
	    		out.println (k + " ");
	    	out.println ("\n<br/>\n");
	    }
	    out.println ("<p/><hr/><p/><b>Groups across time</b><p/>");
		String gByT = GroupEvolution.groupsByTime(rootDir, userKey, allDocs, addressBook, MAX_ERROR, MINCOUNT, MIN_GROUP_SIZE, MIN_GROUP_SIM_EVOLUTION, true,  WINDOW_SIZE, WINDOW_SCROLL);
		out.println (gByT);
		*/

	%>
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<jsp:include page="footer.jsp"/>
</body>
</html>
