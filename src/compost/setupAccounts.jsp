<%@ page language="java" contentType="application/x-javascript; charset=UTF-8"
    pageEncoding="ISO-8859-1"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%
JSPHelper.logRequest(request);
Thread.sleep(100); // wait for killsession in case one was issued

response.setHeader("Cache-Control","no-cache");
response.setHeader("Pragma","no-cache");
response.setHeader("Expires","-1");

// set up email stores in the session (no logins etc)
String documentRootPath = application.getRealPath("/").toString();
List<Accounts.Status> statuses = Accounts.setup(request, session, documentRootPath);
List<EmailStore> stores = new ArrayList<EmailStore>();

boolean anyErrors = false;
int i = 0;
JSONArray arr = new JSONArray();
for (Accounts.Status status: statuses)
{
	JSONObject obj = new JSONObject();

	if (!Util.nullOrEmpty(status.errorMessage))
	{
		obj.put ("status", 1);
		obj.put ("errorMessage", status);
		anyErrors = true;
	}
	else
	{
		obj.put("status", 0);
		stores.add(status.emailStore);
	}
	
	arr.put(i, obj);
	i++;
}

// if setup was ok, we save the stores in the session 
if (!anyErrors)
	session.setAttribute("emailStores", stores);

out.println (arr);
JSPHelper.logRequestComplete(request);
%>
