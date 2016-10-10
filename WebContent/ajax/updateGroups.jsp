<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="com.google.gson.*"%>
<%@page language="java" import="com.google.gson.reflect.*"%>
<%@page language="java" import="java.lang.reflect.*"%>
<%
	String jsonForGroups = request.getParameter("groups");
	Gson gson = new GsonBuilder().setPrettyPrinting().create();
	Type groupListType = new TypeToken<List<JSONGroup>>() {}.getType();
	List<JSONGroup> groups = gson.fromJson(jsonForGroups, groupListType);
	//for (JSONGroup g: groups)
	//	JSPHelper.log.info(gson.toJson(g));

	// update groups in existing archive
	Archive archive = JSPHelper.getArchive(session);
	GroupAssigner ga = new GroupAssigner (groups, archive.addressBook);
	archive.setGroupAssigner(ga);
	// ideally, should toString some status back here
%>
