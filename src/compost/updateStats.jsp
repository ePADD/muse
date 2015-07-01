<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.io.*"%>
<%
response.setHeader("Cache-Control","no-cache");
response.setHeader("Pragma","no-cache");
response.setHeader("Expires","-1");
response.setContentType("application/x-javascript; charset=utf-8");

Grouper grouper = (Grouper) JSPHelper.getSessionAttribute(session, "grouper");
String grouperStats = grouper.getGrouperStats();
String groupEditorStats = (String) JSPHelper.getSessionAttribute(session, "groupEditorStats");
String fbStats = request.getParameter("fbStats");
String data = "&to=socialflows@gmail.com"
	+ "&grouperStats=" + ((grouperStats == null) ? "null" : URLEncoder.encode(grouperStats, "UTF-8"))
	+ "&groupEditorStats=" + ((groupEditorStats == null) ? "null" : URLEncoder.encode(groupEditorStats, "UTF-8"))
	+ "&fbStats=" + ((fbStats == null) ? "null" : URLEncoder.encode(fbStats, "UTF-8"));
byte[] dataBytes = data.getBytes();

try {
	URL url = new URL("http://prpl.stanford.edu/report/field_report.php");
	HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	conn.setDoOutput(true);
	conn.setRequestMethod ("POST");
	conn.setRequestProperty("User-Agent", "Whatever");
	conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
	conn.setRequestProperty("Content-Length", Integer.toString(dataBytes.length));

	OutputStream os = conn.getOutputStream();
	os.write (dataBytes);
	os.close();

	int rc = conn.getResponseCode();
	JSPHelper.log.info ("Stats posted, response code is " + rc);
} catch (Exception e) {
	JSPHelper.log.error ("Exception while posting stats: " + e.getMessage());
}
%>
