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
<%@ page contentType="text/html; charset=UTF-8"%>
<%
	/*** NEW ALGO ***/
    float ERROR_WEIGHT = AlgoStats.DEFAULT_ERROR_WEIGHT;
    String s = (String)request.getParameter("errweight");
    if (s != null && s.trim().length() > 0) {
        try { ERROR_WEIGHT = Float.parseFloat(s); } catch (NumberFormatException nfe) { }
    }

    int NUM_GROUPS = AlgoStats.DEFAULT_NUM_GROUPS;
    s = request.getParameter("numGroups");
    if (s != null && s.trim().length() > 0) {
        try { NUM_GROUPS = Integer.parseInt(s); } catch (NumberFormatException nfe) { }
    }


    /*** OLD ALGO ***/
    // Check if using old algo
    String USEOLDALGO = request.getParameter("useOldGroupsAlgo");
    if (USEOLDALGO == null || USEOLDALGO.trim().length() <= 0)
       USEOLDALGO = "false";

    int MINCOUNT = AlgoStats.DEFAULT_MIN_FREQUENCY;
    s = request.getParameter("minCount");
    if (s != null && s.trim().length() > 0) {
        try { MINCOUNT = Integer.parseInt(s); } catch (NumberFormatException nfe) { }
    }

    float UTILITY_MULTIPLIER = AlgoStats.DEFAULT_UTILITY_MULTIPLIER;
    s = request.getParameter("multiplier");
    if (s != null && s.trim().length() > 0) {
        try { UTILITY_MULTIPLIER = Float.parseFloat(s); } catch (NumberFormatException nfe) { }
    }

    float MAX_ERROR = AlgoStats.DEFAULT_MAX_ERROR;
    s = request.getParameter("maxError");
    if (s != null && s.trim().length() > 0) {
        try { MAX_ERROR = Float.parseFloat(s); } catch (NumberFormatException nfe) { }
    }

    int MIN_GROUP_SIZE = AlgoStats.DEFAULT_MIN_GROUP_SIZE;
    s = request.getParameter("minGroupSize");
    if (s != null && s.trim().length() > 0) {
        try { MIN_GROUP_SIZE = Integer.parseInt(s); } catch (NumberFormatException nfe) { }
    }

    float MIN_MERGE_GROUP_SIM = AlgoStats.DEFAULT_MIN_GROUP_SIMILARITY;
    s = request.getParameter("minMergeGroupSim");
    if (s != null && s.trim().length() > 0) {
        try { MIN_MERGE_GROUP_SIM = Float.parseFloat(s); } catch (NumberFormatException nfe) { }
    }

    /*
    float MIN_GROUP_SIM_EVOLUTION = 0.1f;
    s = request.getParameter("minGroupSimEvolution");
    try { MIN_GROUP_SIM_EVOLUTION = Float.parseFloat(s); } catch (NumberFormatException nfe) { }
	*/


	/* Get standard Muse data structures */
	JSPHelper.logRequest(request);
    AddressBook addressBook = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
    List<EmailDocument> allEmails = (List<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
    GroupAlgorithmStats<String> groupAlgorithmStats = null;

    boolean threadsOnly = request.getParameter("threadsOnly") != null;
    if (threadsOnly)
	    allEmails = EmailUtils.threadHeaders(allEmails); // uncomment to process only threads
    // List of unique email groups we ever contacted
	List<Group<String>> input = addressBook.convertEmailsToGroups(allEmails);

	// Now determine whether using new algo or old algo
	GroupHierarchy<String> hierarchy = null;
	List<SimilarGroup<String>> rootGroups = null;
	Map<SimilarGroup<String>, List<SimilarGroup<String>>> parentToChildGroupMap = null;
	StringBuilder runInformation = null;

	String algoStats = null, anonMap = null;

	if (USEOLDALGO.equals("true"))
    {
	    GroupAlgorithmStats<String> stats = new GroupAlgorithmStats<String>();

	    String utilityType = (String)request.getParameter("utilityType");
	    if (utilityType == null || utilityType.trim().length() <= 0) {
	        utilityType = "linear";
	    }
	    hierarchy
	    = SimilarGroupMethods.findContactGroupsIUI(input, MINCOUNT,
	    		                                   MIN_GROUP_SIZE, MAX_ERROR,
	    		                                   MIN_MERGE_GROUP_SIM,
	    		                                   utilityType,
	    		                                   UTILITY_MULTIPLIER, stats);

	    runInformation = new StringBuilder();

	    AddressBook.AddressBookStats abStats = addressBook.getStats();
	    String str = abStats.toString();
	    runInformation.append("\nAddress book stats: " + str);
	    runInformation.append("\nGrouping stats: " + stats);

	    //System.out.println ("Address book stats: " + str);
	    //System.out.println ("Grouping stats: " + stats);
    }
	else
	{

		Grouper<String> grouper = new Grouper<String>();
		session.setAttribute("grouper", grouper);
		session.setAttribute("statusProvider", grouper);

	    hierarchy = grouper.findGroups(input, NUM_GROUPS, ERROR_WEIGHT);
	    algoStats = grouper.getGrouperStats();
	    anonMap   = grouper.getAnonMappings();

	    //System.out.println("\nTHE ALGO STATS:\n"+algoStats+"\n");
	    //System.out.println("\nTHE ANON MAPPINGS:\n"+anonMap+"\n");

	    /*
	    // just sanity checking group's hashcodes and equals
	    List<SimilarGroup<String>> allGroups = hierarchy.getAllGroups();
	    for (SimilarGroup<String> sg: allGroups)
	        Util.ASSERT (allGroups.contains (sg));
	    Collections.sort (allGroups);
	    */
	}


    // Process the hierarchy for UI display
    parentToChildGroupMap = hierarchy.parentToChildrenMap;
    rootGroups = new ArrayList<SimilarGroup<String>>();
    rootGroups.addAll(hierarchy.rootGroups);

    AddressBook.sortByMass(allEmails, addressBook, rootGroups);
    List<SimilarGroup<String>> orderedRootGroups
    = SimilarGroupMethods.orderGroupsBySimilarity(rootGroups);
    JSONObject socialflowsTopology
    = JSONUtils.jsonForHierarchy(addressBook, orderedRootGroups, parentToChildGroupMap);


	// Append source type info, algo params and run information
	socialflowsTopology.put("sourceType", "email");
	if (!USEOLDALGO.equals("true"))
	   socialflowsTopology.put("algoType", "newAlgo");
	socialflowsTopology.putOpt("logAlgoStats", algoStats);
	socialflowsTopology.putOpt("logAnonMap", anonMap);
	if (runInformation != null)
	   socialflowsTopology.putOpt("runInfo", runInformation.toString());
    socialflowsTopology.putOpt("runTimestamp", new Date().toString());

    if (USEOLDALGO.equals("true"))
    {
        JSONObject algoParamValues = new JSONObject();
        algoParamValues.put("minCount", Integer.toString(MINCOUNT));
        algoParamValues.put("maxError", Float.toString(MAX_ERROR));
        algoParamValues.put("minGroupSize", Integer.toString(MIN_GROUP_SIZE));
        algoParamValues.put("minMergeGroupSim", Float.toString(MIN_MERGE_GROUP_SIM));
        socialflowsTopology.putOpt("algoParams", algoParamValues);

        JSONObject defaultAlgoParams = new JSONObject();
        defaultAlgoParams.optString("minCount", String.valueOf(AlgoStats.DEFAULT_MIN_FREQUENCY));
        defaultAlgoParams.optString("minGroupSize", String.valueOf(AlgoStats.DEFAULT_MIN_GROUP_SIZE));
        defaultAlgoParams.optString("minMergeGroupSim", String.valueOf(AlgoStats.DEFAULT_MIN_GROUP_SIMILARITY));
        defaultAlgoParams.optString("maxError", String.valueOf(AlgoStats.DEFAULT_MAX_ERROR));
        socialflowsTopology.put("algoParamsDefault", defaultAlgoParams);
    }
    else
    {
    	JSONObject algoParamValues = new JSONObject();
    	algoParamValues.put("errWeight", Float.toString(ERROR_WEIGHT));
        algoParamValues.put("numGroups", Integer.toString(NUM_GROUPS));
        socialflowsTopology.putOpt("algoParams", algoParamValues);
    }


    // Output HTML form to do POST to SocialFlows
    String json = socialflowsTopology.toString();
    String callbackSocialFlowsURL = (String) JSPHelper.getSessionAttribute(session, "socialflow");

    // Done! orderedRootGroups.size() sets of groups have been inferred from your email.
%>
<html>
<head>
<title>Contact Groups To SocialFlows</title>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript">
$(document).ready(function () {
    // Create or identify form
    var selectedForm = document.forms["postToSocialFlows"];

    // Submit form automatically & programmatically using JS
    // Redirect to SocialFlows
    selectedForm.submit();

});
</script>
</head>
<body>
	<form id="postToSocialFlows" method="post" action="<%=callbackSocialFlowsURL%>">
	<input type="hidden" name="input" value="<%=Util.escapeHTML(json)%>"></input>
	</form>
</body>
</html>
