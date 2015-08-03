<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="java.text.SimpleDateFormat"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.lens.*"%>

<%
JSPHelper.logRequest(request); 
JSPHelper.setPageUncacheable(response);

JSONObject result = new JSONObject();
JSONArray resultItemsOnPage=null;
try {	
	response.setContentType("application/x-javascript; charset=utf-8");

	String[] alltext = request.getParameterValues("refText");

	
	double [] scores= new double[alltext.length];
	
	for(int i = 0; i < alltext.length; ++i) {
	
	    System.out.println(alltext[i]);
	
		String text=alltext[i];
		String url = request.getParameter("refURL");
		
		String baseURL = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
		Archive archive = JSPHelper.getArchive(session);
		if (archive == null)
		{
			// display error is a message that can be displayed to the end user, i.e. something he can take action upon.
			result.put("displayError", "No archive loaded in <a href=\"" + baseURL + "\">Muse</a>.");
			out.println (result.toString(4));
			JSPHelper.log.warn ("No archive loaded");
			return;
		}
		
		Collection<EmailDocument> allDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
		Indexer indexer = archive.indexer;
		AddressBook ab = archive.addressBook;

		if (Util.nullOrEmpty(text))
		{
			result.put("error", "null or empty ref-text");
			out.println (result.toString());
			return;
		}
	
		List<Pair<String,Float>> names = null;
		List<Pair<String,Integer>> namesFromArchive = null;
		Map<String, Integer> termFreqMap = new LinkedHashMap<String, Integer>();
		List<JSONObject> list=null;
	
		names = NER.namesFromText(text, true);
		String DATE_FORMAT = "yyyyMMdd";
		
		System.out.println("names size"+names.size());
	
		for (Pair<String, Float> pair: names)
		{
	String name = pair.getFirst();
	Float count = pair.getSecond();
	termFreqMap.put(name, 1);
		}
	
	//System.out.println("referrer is"+ refererPage);
		System.out.println("Calling getHits"+termFreqMap.size());
	//		list = JSPHelper.getHits (names, indexer, ab, baseURL, allDocs,termFreqMap);
	
		LensPrefs lensPrefs = (LensPrefs) JSPHelper.getSessionAttribute(session, "lensPrefs");
		if (lensPrefs == null)
		{
			String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
			if (cacheDir != null)
			{
				lensPrefs = new LensPrefs(cacheDir);
				session.setAttribute("lensPrefs", lensPrefs);
			}
		}
		list = Lens.getHits (names, lensPrefs, archive, ab, baseURL, allDocs);
		JSPHelper.log.info (list.size() + " hits after sorting");
	}
	JSONArray jsonArray = new JSONArray();
	int index = 0;
	for (int i=0;i<scores.length;i++)
		jsonArray.put(index++, scores[i]);
	
	result.put("results", jsonArray);
	//JSPHelper.log.info (result.toString(4));
	out.println (jsonArray);
	
} catch (Exception e) {
	result.put("error", "Exception: " + e);
	e.printStackTrace();
	out.println (result.toString(4));
	JSPHelper.log.warn ("Exception: " + e);
	return;
} catch (Error e) {
	// stupid abstract method problem on jetty shows up as an error not as exception
	result.put("error", "Error: " + e);
	out.println (result.toString(4));
	JSPHelper.log.warn ("Error: " + e);
	return;	
}
finally { JSPHelper.logRequestComplete(request); }
%>
