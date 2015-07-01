<%@ page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.bespoke.mining.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.graph.*"%>
<% 	JSPHelper.logRequest(request); %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<script type="text/javascript" src="protovis.js"></script>
<title>Contact Groups</title>
<jsp:include page="css/css.jsp"/>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
</head>
<body>


<%@include file="header.html"%>
<p/> <p/> <p/> <p/>
<script type="text/javascript" src="muse.js"></script>

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
	float ERROR_WEIGHT = AlgoStats.DEFAULT_ERROR_WEIGHT;
    String s = (String)request.getParameter("errweight");
    if (s != null && !s.isEmpty()) {
    	try { ERROR_WEIGHT = Float.parseFloat(s); } catch (NumberFormatException nfe) { }
	}

    int NUM_GROUPS = AlgoStats.DEFAULT_NUM_GROUPS;
    s = request.getParameter("numGroups");
    if (s != null && !s.isEmpty()) {
        try { NUM_GROUPS = Integer.parseInt(s); } catch (NumberFormatException nfe) { }
    }



	AddressBook addressBook = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
    String rootDir = JSPHelper.getRootDir(request);
	List<EmailDocument> allEmails = (List<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");

	boolean threadsOnly = request.getParameter("threadsOnly") != null;
	if (threadsOnly)
	    allEmails = EmailUtils.threadHeaders(allEmails); // uncomment to process only threads

	List<Group<String>> input = addressBook.convertEmailsToGroups(allEmails);
	//List<Group<String>> input = JSONUtils.parseXobniFormat("/home/xp/DUMP.peter.42519.anon");
//	SimpleGraph<String> graph = SimilarGroupMethods.setupGraph(input);

//	List<SimilarGroup<String>> rootGroups = new ArrayList<SimilarGroup<String>>();
//	Map<SimilarGroup<String>, List<SimilarGroup<String>>> parentToChildGroupMap
//	= new LinkedHashMap<SimilarGroup<String>, List<SimilarGroup<String>>>();

	Move.errWeight = ERROR_WEIGHT;
	Grouper<String> grouper = new Grouper<String>();
	GroupHierarchy<String> hierarchy =grouper.findGroups(input, NUM_GROUPS, ERROR_WEIGHT); //, rootGroups, parentToChildGroupMap);
	out.println(grouper.getMovesAsHTML());

	// Process the hierarchy
    Map<SimilarGroup<String>, List<SimilarGroup<String>>> parentToChildGroupMap = hierarchy.parentToChildrenMap;
    List<SimilarGroup<String>> rootGroups = new ArrayList<SimilarGroup<String>>();
    rootGroups.addAll(hierarchy.rootGroups);

    AddressBook.sortByMass(allEmails, addressBook, rootGroups);
	List<SimilarGroup<String>> orderedRootGroups
	= SimilarGroupMethods.orderGroupsBySimilarity(rootGroups);
	//Map<SimilarGroup<String>, List<SimilarGroup<String>>> emptyMap
	//= new LinkedHashMap<SimilarGroup<String>, List<SimilarGroup<String>>>();
	JSONObject socialflowsTopology
	= JSONUtils.jsonForHierarchy(addressBook, orderedRootGroups, parentToChildGroupMap);

	// Append source type info, algo params and run information
    socialflowsTopology.put("sourceType", "email");
    socialflowsTopology.put("algoType", "newAlgo");
    //socialflowsTopology.putOpt("runInfo", runInformation.toString());
    socialflowsTopology.putOpt("runTimestamp", new Date().toString());

    JSONObject algoParamValues = new JSONObject();
    algoParamValues.put("errWeight", Float.toString(ERROR_WEIGHT));
    algoParamValues.put("numGroups", Integer.toString(NUM_GROUPS));
    socialflowsTopology.putOpt("algoParams", algoParamValues);

    String json = socialflowsTopology.toString(2); // indent factor, pretty print the json;

	new File(rootDir).mkdirs();
	String file = rootDir + File.separator + "json.txt";
    PrintWriter pw = new PrintWriter(file, "UTF-8"); pw.println(json); pw.close();
    String userKey = (String) JSPHelper.getSessionAttribute(session, "userKey");
	out.println ("<br/><a href=\"" + userKey + "/json.txt\">JSON</a><br/><br/>\n");


	if ("socialflow".equals(JSPHelper.getSessionAttribute(session, "mode")))
    {
       String callbackURL = (String) JSPHelper.getSessionAttribute(session, "socialflow");
	   if (callbackURL != null)
	   {
%>
	      <p/>
	      Done! <%=rootGroups.size()%> sets of groups have been inferred from your email (using error rate <%=ERROR_WEIGHT%>).<br/>
	      <form method="post" action="<%=callbackURL%>">
	      <input type="hidden" name="input" value="<%=Util.escapeHTML(json)%>"></input>
	      <button name="submit" value="submit" type="submit">Continue with SocialFlows</button>
	      </form>
	      <%
       }
	}


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
