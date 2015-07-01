<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>
<%
	try {
	// disable caching
	response.setHeader("Cache-Control","no-cache");
	response.setHeader("Pragma","no-cache");
	response.setHeader("Expires","-1");

	JSPHelper.logRequest(request);
	String jsonFile = request.getParameter("json");
	// convert xobni emails to email docs
	String[] ownEmails = new String[]{"peter.monaco@xobni.com", "peter@xobni.com"};
	String own = request.getParameter("ownEmails");
	if (!Util.nullOrEmpty(own))
	{
		List<String> ownList = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer(own, ", \t");
		while (st.hasMoreTokens())
	ownList.add(st.nextToken());
		ownEmails = new String[ownList.size()];
		for (int i = 0; i < ownList.size(); i++)
	ownEmails[i] = ownList.get(i);
	}

	boolean sentOnly = (request.getParameter("sentOnly") != null);
	Pair<List<EmailDocument>, AddressBook> p = JSONUtils.convertXobniEmails(jsonFile, sentOnly, ownEmails);

	AddressBook addressBook = p.getSecond();
	List<EmailDocument> allDocs = p.getFirst();

	JSPHelper.log.info ("Starting with " + allDocs.size() + " documents");
	String dateRange = request.getParameter("dateRange");
	if (dateRange != null)
	{
		List<EmailDocument> newAllDocs = new ArrayList<EmailDocument>();
		edu.stanford.muse.email.Filter filter = edu.stanford.muse.email.Filter.parseFilter(JSPHelper.convertRequestToMap(request));
		for (EmailDocument ed: allDocs)
	if (filter.matches(ed))
		newAllDocs.add(ed);

		allDocs = newAllDocs; //  (List) JSPHelper.filterByDate((Collection) allDocs, dateRange);
	}
	JSPHelper.log.info ("After filtering, " + allDocs.size() + " documents");

	session.setAttribute("emailDocs", allDocs);
	session.setAttribute("addressBook", addressBook);

	// convert to groups
	List<Group<String>> input = addressBook.convertEmailsToGroups(allDocs);

	// color assigner just stores a bunch of groups (and can be updated by the editor)
	GroupAssigner ca = new GroupAssigner();

	int NGROUPS = 15;
	String errWeight = request.getParameter("errWeight");
	GroupHierarchy<String>	hierarchy;

	try {
		float err = Float.parseFloat(errWeight);
		hierarchy = new Grouper<String>().findGroups(input, NGROUPS, err);
	}
	catch (Exception e) {
		hierarchy = new Grouper<String>().findGroups(input, NGROUPS);
	}

	List<SimilarGroup<String>> selectedGroups = SimilarGroupMethods.topGroups(hierarchy, NGROUPS);

	// Generate the JSON for groups output
	// Process the hierarchy
	Map<SimilarGroup<String>, List<SimilarGroup<String>>> parentToChildGroupMap = hierarchy.parentToChildrenMap;
	List<SimilarGroup<String>> rootGroups = new ArrayList<SimilarGroup<String>>();
    rootGroups.addAll(hierarchy.rootGroups);

    AddressBook.sortByMass(allDocs, addressBook, rootGroups);
    List<SimilarGroup<String>> orderedRootGroups = SimilarGroupMethods.orderGroupsBySimilarity(rootGroups);
    //Map<SimilarGroup<String>, List<SimilarGroup<String>>> emptyMap
    //= new LinkedHashMap<SimilarGroup<String>, List<SimilarGroup<String>>>();
    JSONObject socialflowsTopology = JSONUtils.jsonForHierarchy(addressBook, rootGroups, parentToChildGroupMap);
	session.setAttribute("GroupsJSON", socialflowsTopology);
	
	System.out.println("computeGroupsFromJson.jsp: Generated social topology!!!");

	ca.setupGroups (allDocs, selectedGroups, addressBook, 0); // 0 individuals, no need for special handling now
	session.setAttribute("groupAssigner", ca);
	response.setContentType("text/xml");
	response.setHeader("Cache-Control", "no-cache");
	out.println("<result><div resultPage=\"groups\"></div></result>");

} catch (Exception e) {
	// for easier debugging, report exception to javascript
	session.setAttribute("errorMessage", e.toString() + "\n" + Util.stackTrace(e));
	e.printStackTrace(System.err);
	response.setContentType("text/xml");
	response.setHeader("Cache-Control", "no-cache");
	out.println("<result><div resultPage=\"error.jsp\"></div></result>");
}
JSPHelper.logRequestComplete(request);
%>