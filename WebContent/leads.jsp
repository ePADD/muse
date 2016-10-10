<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Archive Cues</title>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
</head>
<body>
<jsp:include page="header.jsp"/>
<div class="panel rounded">

<%
	String text = request.getParameter("refText");
	Archive archive = JSPHelper.getArchive(session);
	AddressBook ab = archive.addressBook;
	Indexer indexer = null;
	if (archive != null)
		indexer = archive.indexer;

	String bestName = ab.getBestNameForSelf();
//	Pair<String, List<Pair<String,Integer>>> p = JSONUtils.getDBpediaForPerson(bestName);
//	List<Pair<String,Integer>> names = p.getSecond();
	List<Pair<String,Float>> names = null;
	if (text == null)
		return;
	
	text = text.trim();
	if (text.startsWith("http://"))
		names = NER.namesFromURLs(text);
	else	
		names = NER.namesFromText(text);
	
	if (indexer != null)
	{
		for (Pair<String, Float> pair: names)
		{
	String name = pair.getFirst();
	if (bestName.toLowerCase().contains(name.toLowerCase()))
		continue;
	if (name.length() <= 2)
		continue;
	
	String url = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/browse?term=\"" + name + "\"";
	float times = pair.getSecond();
	name = JSPHelper.convertRequestParamToUTF8(name);
	List<Document> docsForName = new ArrayList<Document>(archive.docsForQuery(name, -1, Indexer.QueryType.FULL));
	if (docsForName.size() > 0)
		// note: using single quotes here because href has double quotes
		out.println ("<a href='" + url +  "'>" + name + "</a> " + docsForName.size() + " messages (Reftext: " + times + " mention" + ((times>1) ?"s":"") + ")<br/>\n");			
		}	
	}
%>
</div>
</body>
</html>
