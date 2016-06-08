<%@ page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="java.awt.image.*"%>
<%@page language="java" import="javax.imageio.*"%>

<%@include file="getArchive.jspf" %>
	
<html lang="en">
<head>
<title>Links</title>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/protovis.js"></script>
<script type="text/javascript" src="js/proto_funcs.js"></script>

<jsp:include page="css/css.jsp"/>
</head>
<body>
<jsp:include page="header.jsp"/>

<div align="center">
<h2> Note: This feature is under construction! </h2>
</div>
<hr/>

<span style="color:red">New!</span> <a href="createEmailLinksCSE.jsp">Create</a> yourself a customized search engine, tuned for these domains.
<br/>

<%
	// Note: the message link from this page uses a docNum index into selectedDocs, which is stored in the session
	// therefore there can be only one link collection active at a time.
	// that's why most pointers to this page should use <a target="_links"...
	// so that only one tab with links is open.

	List<Document> docs = JSPHelper.selectDocsAsList(request, session);
	GroupAssigner groupAssigner = archive.groupAssigner;
	String datasetName = String.format("docset-%08x", EmailUtils.rng.nextInt());
	DataSet dataset = new DataSet (docs, archive, datasetName, null, null, null);
	session.setAttribute(datasetName, dataset);
	List<LinkInfo> links = EmailUtils.getLinksForDocs(docs);
	for (LinkInfo li : links)
		li.tld = Util.getTLD(li.link);

	// sort links by url
	Map<String, List<LinkInfo>> fmap = new LinkedHashMap<String, List<LinkInfo>>();
	int linkCount = 0;
	for (LinkInfo li : links) {
//		if (li.link.indexOf(".yahoo.com") >= 0) // get rid of !#!@$! yahoo because there are too many of them?
//			continue;

		List<LinkInfo> lis = fmap.get(li.link);
		if (lis == null)
		{
	lis = new ArrayList<LinkInfo>();
	fmap.put(li.link, lis);
		}
		lis.add(li);
		linkCount++;
	}

	Pair<Date, Date> p = LinkInfo.getFirstLast(links);
	long firstLastInterval = Long.MAX_VALUE, firstTime = 0;
	if (p != null)
	{
		Date startDate = p.getFirst();
		Date endDate = p.getSecond();
		if (startDate != null && endDate != null)
		{
		 	firstLastInterval = endDate.getTime() - startDate.getTime();
		 	firstTime = startDate.getTime();
		}
	}

	// organize tld -> list<url> map, and sort it so as store most frequent domain first
	List<String> linksList = new ArrayList<String>(fmap.keySet());
	Map<String, List<String>> tldMap = new LinkedHashMap<String, List<String>>();
	for (LinkInfo li: links)
	{
		String tld = Util.getTLD(li.link);
		List<String> list = tldMap.get(tld);
		if (list == null)
		{
	list = new ArrayList<String>();
	tldMap.put (tld, list);
		}
		list.add(li.link);
	}
	tldMap = Util.sortMapByListSize((Map) tldMap); // not sure why this cast is required, but eclipse keeps complaining
	
	for (String tld: tldMap.keySet())
		Collections.sort (tldMap.get(tld)); // sort in alphabetical order within a tld
	
	// set up numbering for these docs internally. Document.docNum may not be reliable
	Map<Document,Integer> docNumMap = new LinkedHashMap<Document,Integer>();
	if (docs != null)
	{
		for (int i = 0; i < docs.size(); i++)
	docNumMap.put(docs.get(i), i);
	}
%>
	<script type="text/javascript">
	<%int tldsToShow = 0;
	for (String tld: tldMap.keySet())
		if (tldMap.get(tld).size() >= 10)
			tldsToShow++;

	out.print ("draw_bar_graph(new Array(");
	int cc = 0;
	for (String tld: tldMap.keySet())		
	{
		out.print (tldMap.get(tld).size());
		if (++cc < tldsToShow)
			out.print (", ");
		else 
			break;
	}
	out.print ("),\n new Array(");
	cc = 0;
	for (String tld: tldMap.keySet())
	{
		out.print ("'" + tld + "'");
		if (++cc < tldsToShow)
			out.print (", ");
		else
			break;
	}
	out.println (")\n);\n");%>
	</script>
	<p/>
	
	<p> <%=fmap.keySet().size()%> unique links (<%=linkCount%> total).
	Links are sorted by frequency of top-level domain (TLD).

	<%
 	int i = 0;
  	for (String tld: tldMap.keySet())
  	{
  		Set<String> uniqueURLsForThisTLD = new LinkedHashSet<String>(tldMap.get(tld));
  		out.print("<hr/><p> Domain: " + tld + " (" + tldMap.get(tld).size() + " urls, " + uniqueURLsForThisTLD.size() + " unique)<br/>");
  	// go in the sorted order instead of fmap.keySet()
  //	for (String url : linksList)
  	;
  	
  	for (String url : uniqueURLsForThisTLD)
  	{
  		i++;
  		out.print(i + ". <a href=\"" + url + "\">" + url
  	+ "</a>");

  		// 2 fixed links, for message and now

  		List<LinkInfo> lis = fmap.get(url);
  		String messagesURL = "browse?datasetId=" + datasetName;
  		List<Integer> seen = new ArrayList<Integer>();
  		List<LinkInfo> uniqueDocsLinkInfo = new ArrayList<LinkInfo>();
  		boolean show_youtube = request.getParameter("youtube") != null;
  		
  		for (LinkInfo li: lis)
  		{
  	Integer docIdx = docNumMap.get(li.doc);
  	if (docIdx == null)
  		continue;
  	if (!seen.contains(docIdx))
  	{
  		messagesURL += "&docNum=" + docIdx;
  		seen.add(docIdx);
  		uniqueDocsLinkInfo.add(li);
  	}
  		}
  		Collections.reverse(uniqueDocsLinkInfo); // docs are sorted in increasing time, we prefer decreasing time instead...
  		// uniqueDocs better have dated docs

  		int count = 0;
  		int bg = count % GroupAssigner.COLORS.length;
  		String bgcolor = '#' + String.format ("%06x", GroupAssigner.COLORS[bg]);
  		out.println ("&nbsp;<a href=\"" + messagesURL + "\"><span class=\"weblink-date rounded\" style=\"background-color:" + bgcolor + "\">" + "Message" + ((lis.size() != 1) ? "s" : "") + "</span></a>&nbsp;");
  		count++;

  		// now NOW
  //		bg = count % GroupAssigner.COLORS.length;
  //		bgcolor = '#' + String.format ("%06x", GroupAssigner.COLORS[bg]);
  //		out.println ("&nbsp;<a href=\"" + url + "\"><span class=\"weblink-date rounded\" style=\"background-color:" + bgcolor + "\">" + "Now" + "</span></a>&nbsp;");
  //		count++;
  		
 			if (show_youtube && "youtube.com".equals(Util.getTLD(url))) {
 				String videoId = url;
 				// look for this signature, ignore what came before, e.g. www.youtube.com/watch or just youtube.com/watch etc.
 				int idx = videoId.indexOf("youtube.com/watch?v=");
 				if (idx >= 0)
 					videoId = videoId.substring(idx + "youtube.com/watch?v=".length());
 				// strip out extra params
 				idx = videoId.indexOf("&");
 				if (idx >= 0)
 					videoId = videoId.substring(0, idx);
 				out.println("<br/><iframe title=\"YouTube video player\" width=\"480\" height=\"297\" src=\"http://www.youtube.com/embed/" + videoId
 						+ "\" frameborder=\"0\" allowfullscreen></iframe>\n<br/>");
 			}

 			for (LinkInfo li : uniqueDocsLinkInfo) {
 				if (li.doc instanceof DatedDocument) {
 					Date d = ((DatedDocument) li.doc).date;
 					Calendar c = new GregorianCalendar();
 					c.setTime(d);
 					String archiveDate = c.get(Calendar.YEAR) + String.format("%02d", c.get(Calendar.MONTH))
 							+ String.format("%02d", c.get(Calendar.DATE)) + "120000";
 					// color dates
 					bg = count % GroupAssigner.COLORS.length;
 					//bgcolor = '#' + String.format ("%06x", GroupAssigner.COLORS[bg]);

 					double pct_age = 1.0;
 					if (firstLastInterval > 0)
 						pct_age = ((double) d.getTime() - firstTime) / firstLastInterval; /* percent age of the doc. heh heh */
 					if (pct_age >= 1)
 						pct_age = 1.0; // shouldnt' happen, just being defensive
 					float alpha = (float) (0.1 + (0.8 * pct_age));
 					int saturation = 127 + (int) (128 * pct_age);
 					bgcolor = '#' + String.format("0000%02x", saturation);
 					bgcolor = ("rgba(64,64,192," + alpha + ")");
 					out.println(" &nbsp;<a href=\"http://web.archive.org/web/" + archiveDate + "/" + url
 							+ "\"><span class=\"weblink-date rounded\" style=\"background-color:" + bgcolor + "\">" + Util.formatDate(c)
 							+ "</span></a>&nbsp; ");
 					count++;
 				}
 			}

 			/*
 			//			if (!address.endsWith(".htm") && !address.endsWith(".html"))
 			//				continue;

 			try {
 			//	BufferedImage buff = null;
 			//	buff = Graphics2DRenderer.renderToImageAutoSize(address, 1024);
 			//	ImageIO.write(buff, "png", new File("/tmp/new" + i + ".png"));
 			FileOutputStream fos = new FileOutputStream("/tmp/new" + i + ".png");
 			boolean success = HTMLToImage.image(link, fos, 1024, 768);
 			System.out.println ("downloading link: " + link + " success: " + success);
 			} catch (Exception e) {
 				e.printStackTrace();
 			}
 			 */

 			out.println(" <p/>");
 		}
 	}

 	//	out.println ("<a href=\"webPiclens.jsp\">Link wall</a> (Under construction!)<p>");
 %>

<%@include file="footer.jsp"%>
</body>
</html>
