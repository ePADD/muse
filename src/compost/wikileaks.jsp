<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.text.*"%>
<%@page language="java" import="java.io.*" %>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Wikileaks cables</title>
<script type="text/javascript" src="../js/jquery/jquery.js"></script>
<script type="text/javascript" src="../js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="../js/jquery.safeEnter.1.0.js"></script>
<script src="../js/jog_plugin.js" type="text/javascript"></script>
<script src="../js/muse.js" type="text/javascript"></script>

</head>
<body style="font-family:'Myriad Pro',Calibri,Arial; margin: 0% 5%;">

<script>
function doSearch()
{
	window.location = 'wikileaks.jsp?q=' + $('#searchbox').val();	
}

$(document).ready(function() {
	$('#searchbox').clickOnEnter($('#submitsearch')[0]);
});
</script>
<%
	JSPHelper.logRequest(request);
String baseURL = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
AddressBook ab = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
Archive driver = (Archive) JSPHelper.getSessionAttribute(session, "indexDriver");
Collection<EmailDocument> wlDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "wikileaks");

String RESULT_MAP_FILE = "/Users/hangal/wikileaks/cables.500.results";
Indexer indexer = null;
if (driver != null)
	indexer = driver.indexer;

	String DATE_FORMAT = "EEE MMM d, yyyy";
	SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
	Map<String, List<JSONObject>> resultMap = (Map<String, List<JSONObject>>) JSPHelper.getSessionAttribute(session, "resultMap");
	Collection<EmailDocument> docs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "wikileaks");

	if (docs == null)
	{
		resultMap = new LinkedHashMap<String, List<JSONObject>>();
		try { resultMap = (Map<String, List<JSONObject>>) ImportWikileaks.readFromFile(RESULT_MAP_FILE); }
		catch (Exception e) { Util.print_exception (e); };
		
		session.setAttribute("resultMap", resultMap); // score map will cache doc -> score

		ObjectInputStream ois = new ObjectInputStream (new FileInputStream("/Users/hangal/wikileaks/cables.500.docs"));
		docs = (Collection<EmailDocument>) ois.readObject();
		session.setAttribute("wikileaks", docs);
		ois.close();

	}

	LuceneIndexer lindexer = (LuceneIndexer) JSPHelper.getSessionAttribute(session, "luceneIndexer");
	if (lindexer == null)
	{
		String LINDEX_FILE = "/Users/hangal/wikileaks/cables.500.lindex";
		try { 
	lindexer = (LuceneIndexer) ImportWikileaks.readFromFile(LINDEX_FILE); 
	JSPHelper.log.info ("Read existing index file");
		}
		catch (Exception e) {
	JSPHelper.log.info ("no existing index file, creating new");
	lindexer = new LuceneIndexer(wlDocs);
	session.setAttribute("luceneIndexer", lindexer);
	ImportWikileaks.writeToFile("/Users/hangal/wikileaks/cables.500.lindex", lindexer);
		}
	}

	String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
	lindexer.setupQueries(cacheDir);
	Collection<EmailDocument> selectedDocs = docs;
	String q = request.getParameter("q");
	if (q != null && q.length() > 0)
		selectedDocs = new ArrayList<EmailDocument>((Set) lindexer.docsForQuery(q, -1));
	else
	{
		String docnum = request.getParameter("doc");
		if (docnum != null && docnum.length() > 0)
		{
	selectedDocs = new ArrayList<EmailDocument>();
	selectedDocs.add(lindexer.getDocMap().get(docnum));
		}
	}
	
	if (selectedDocs.size() == 1 || request.getParameter("jog") != null)
	{
		int count = 0;
		StringBuilder h = new StringBuilder();
		h.append("<div class=\"section\" name=\"ALL\"> <div class=\"document\">\n");
		for (EmailDocument ed: selectedDocs)
		{ 
	h.append("<div style=\"display:none\" class=\"page\" pageId=\"" + count++ + "\">");
	h.append("<div style=\"position:relative;max-width:400px;margin-top:10px;\"><span style=\"font-size:16pt;font-weight:bold\">Wikileaks cable #" + ed.docNum + "</span></div>");
	h.append("<hr/>\n");
	h.append("<div style=\"padding:10px; background-color: #CCCCCC\">");
	String contents = ed.getContents();
	contents = Util.escapeHTML(contents);
	String s = lindexer.getHTMLAnnotatedDocumentContents(ed);
	s = s.replaceAll("\n", "<br/>");
	h.append(s);
	h.append("</div>\n"); // page
		}
		h.append("</div></div>\n");
%>
		<div id="jog_contents" style="position:relative">
		<br/><br/><br/><br/><br/><br/><div style="text-align:center"><img src="images/spinner.gif"/><h2>Loading <%=Util.commatize(selectedDocs.size())%> messages...</h2></div><br/><br/><br/><br/><br/>
		</div>
		<script type="text/javascript">
		$('body').ready(function() { 
			$(document).jog({paging_info: {url: null}, logger: muse.log, width: 180, reel_prefix: '../images/movieReel', reel1: '../images/reel21.png', reel2: '../images/reel22.png', post_comments_url: null});
		});
		</script>
		<%
		out.println(h);
	}
	else
	{
		%>
		<div style="position:relative;max-width:400px;margin-top:10px;"><span style="font-size:16pt;font-weight:bold">Wikileaks cables</span></div>
		<div style="position:absolute;right:50px;top:-10px">
		<img src="../images/movieReel1.png" width="30px" onclick="withJog();" style="position:relative;top:10px"/>
		<script>function withJog() { window.location = document.URL + "&jog=1"; } </script>
		&nbsp;&nbsp;
		<input id="searchbox" type="text" placeholder="search" size="20"/>
		<img id="submitsearch" src="../images/search.png" onclick="doSearch()"/>
		</div>
		<div style="clear:both"></div>
		<hr/>
	<%
	out.println ("<table>");
	out.println ("<tr>"); 
	out.println ("<td><b>ID</b></td>");
	out.println ("<td><b>Date</b></td>");
	out.println ("<td><b>From</b></td>");
//	out.println ("<td><b>Score</b></td>");
	out.println ("<td><b>Subject</b></td>");
//	out.println ("<td><b>Terms</b></td>");
	out.println ("</tr><tr><td>&nbsp;</td></tr>");

	int count = 0;
	for (EmailDocument ed: selectedDocs)
	{
//		if (count++ > 500)
//			break;
		if (count % 10 == 0)
			out.flush();
		
		out.println ("<tr>"); 
		out.println ("<td style=\"min-width:5em\">" + ed.docNum + "</td>");
		out.println ("<td style=\"min-width:8em\">" + sdf.format(ed.date) + "</td>");
		out.println ("<td style=\"min-width:10em\">" + ed.from[0] + " </td>");
		
		LensPrefs lensPrefs = (LensPrefs) JSPHelper.getSessionAttribute(session, "lensPrefs");
		if (lensPrefs == null)
		{
			if (cacheDir != null)
			{
				lensPrefs = new LensPrefs(cacheDir);
				session.setAttribute("lensPrefs", lensPrefs);
			}
		}
		List<JSONObject> list = (List<JSONObject>) resultMap.get(ed.docNum);
		if (list == null)
		{
			String contents = "";
			try { 
				contents = ed.getContents();
				List<Pair<String,Float>> names = NER.namesFromText(contents, true);
				list = Lens.getHits (names, lensPrefs, indexer, ab, baseURL, wlDocs); 
			} catch (Exception e) { Util.print_exception(e); }
			resultMap.put(Integer.toString(ed.docNum), list);
		}
		
		double score = 0.0f;
		List<String> teaser = new ArrayList<String>();
		if (list != null)
			for (JSONObject o: list)
			{
				double d = o.getDouble("score");
				score += d;
				if (d > 0)
					teaser.add(o.getString("text"));
			}
			
	//	out.println ("<td style=\"min-width:7em\">" + String.format ("%.3f", (score*10000.0)) + "</td>");
		out.println ("<td><a href=\"wikileaks.jsp?doc=" + ed.docNum + "\">" + ed.description + "</a></td>");
	//	out.println ("<td>" + teaser + "</td>");
		out.println ("</tr>");
	}
	// ImportWikileaks.writeToFile(RESULT_MAP_FILE, resultMap);
	out.println ("</table>");
	}
%>

</body>
</html>