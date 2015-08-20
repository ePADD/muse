<%@ page contentType="text/html; charset=UTF-8"%>
<%
	JSPHelper.checkContainer(request); // do this early on so we are set up
	request.setCharacterEncoding("UTF-8");
%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.lang.*"%>
<%@page language="java" import="java.net.*"%>
<!DOCTYPE HTML>
<html lang="en">
<head>
<head>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script src="js/jquery/jquery.tools.min.js" type="text/javascript"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
<script type="text/javascript" src="js/jquery-lightbox/js/jquery.lightbox-0.5.min.js"></script>
<script type="text/javascript" src="js/jquery-lightbox/js/jquery.lightbox-0.5.pack.js"></script>
</head>  
<body>
<jsp:include page="header.jsp"/>
<%
	JSPHelper.logRequest(request);
	String userKey = "user";
	String rootDir = JSPHelper.getRootDir(request);
	String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
	JSPHelper.log.info("Will read attachments from blobs subdirectory off cache dir " + cacheDir);
	
	Collection<Document> docs = JSPHelper.selectDocs(request, session, true /* only apply to filtered docs */, false);

	if (docs != null && docs.size() > 0) {
		String extra_mesg = null;
		if ("localhost".equals(request.getServerName()))
			extra_mesg = "Want to copy these attachment as files? They are stored in the folder "
					+ cacheDir + File.separator + "blobs\n"; // rootDir already has trailing /

		// attachmentsForDocs
		String attachmentsStoreDir = cacheDir + File.separator
				+ "blobs" + File.separator;
		BlobStore store = null;
		try {
			store = new BlobStore(attachmentsStoreDir);
		} catch (IOException ioe) {
			JSPHelper.log.error("Unable to initialize attachments store in directory: "
					+ attachmentsStoreDir + " :" + ioe);
			Util.print_exception(ioe, JSPHelper.log);
			String url = null;
		}

		Map<Blob, String> blobToSubject = new LinkedHashMap<Blob, String>();
		List<Blob> allAttachments = new ArrayList<Blob>();
		Collection<EmailDocument> eDocs = (Collection) docs;
		for (EmailDocument doc : eDocs) {
			List<Blob> a = doc.attachments;
			if (a != null)
				allAttachments.addAll(a);
			if (!(Util.nullOrEmpty(doc.description)))
				for (Blob b: a)
					blobToSubject.put(b, doc.description);
		}
		
		// create a dataset object to view
		int i = new Random().nextInt();
		String randomPrefix = String.format("%08x", i);
		JSPHelper.log.info("Root dir for blobset top level page is " + rootDir);
		BlobSet bs = new BlobSet(rootDir, allAttachments, store);

		int nEntriesForPiclens = bs.generate_top_level_page(
				randomPrefix, extra_mesg);
		// attachmentsForDocs
		
		String piclensRSSFilename = userKey + "/" + randomPrefix + ".photos.rss";
		String faviconPlusCSS = "<link rel=\"icon\" type=\"image/png\" href=\"images/muse-favicon.png\">\n<link href=\"css/muse.css\" rel=\"stylesheet\" type=\"text/css\"/>"; 

		// This generates the html page
		// don't include the wall if no attachments on

		out.println("<html><head><title>Attachments</title>"
				+ faviconPlusCSS
				+ "</head><body style=\"margin:auto;max-width:1300px\">");
		if (nEntriesForPiclens > 0) {
			out.println("<span class=\"db-hint\">"
					+ Util.commatize(bs.getStats().n_total_pics)
					+ " attachments, "
					+ Util.commatize(bs.getStats().n_unique_pics) + " unique, "
					+ Util.commatize(bs.getStats().unique_data_size / 1024)
					+ " KB.");
			out.println("</span><br/>");
			out.println("<span class=\"db-hint\"><span class=\"helpcue\">SCROLL</span> to zoom <span class=\"helpcue\">DRAG</span> to pan <span class=\"helpcue\">CLICK</span> to select. <img height=\"15px\" src=\"images/piclens-breakout.png\"></img> links to containing message(s). <br/>\n");
		}	
%>

		
<object id="o" classid="clsid:D27CDB6E-AE6D-11cf-96B8-444553540000" width="1200" height="720">
<param name="movie" value="//apps.cooliris.com/embed/cooliris.swf" />
<param name="flashvars" value="feed=<%= piclensRSSFilename %>" />
<param name="allowFullScreen" value="true" />
<param name="allowScriptAccess" value="always" />
<embed type="application/x-shockwave-flash" src="//apps.cooliris.com/embed/cooliris.swf" width="1200" height="720" flashvars="feed=<%= piclensRSSFilename %>" allowFullScreen="true" allowScriptAccess="always"></embed>
</object>
<br/>
			
<%
		if (!Util.nullOrEmpty(extra_mesg))
			out.println(extra_mesg);
		int n = allAttachments.size();
		if (n > 0) {
			boolean plural = (n > 1);
%>

<p><b>&nbsp;&nbsp; <%= Util.commatize(n) %> attachment <%= (plural ? "s have" : " has")%> no thumbnail and <%=(plural ? "are" : "is")%> listed below.</b><br/>

	
<table><th>Name</td><th>Size</th><th>Date</th><th>Subject</th><th>Shared with</th></th><tr><td colspan=\"5\"><hr/></td></tr><tr>
			
<%
			//Check for OS and qvpDir
			String osName = System.getProperty("os.name");
			boolean isWindows = osName.contains("Windows");
			boolean qvpExist = false;
			String quickDir = (String) JSPHelper.getSessionAttribute(session, "qvpDir");
			if(isWindows)
			{
				if(quickDir == null)
				{
					quickDir = "C:\\Program Files\\Quick View Plus\\Program\\qvp64.exe";
					session.setAttribute("qvpDir", quickDir);
				}
				File f = new File(quickDir);
				if(f.exists()) qvpExist = true;

			}
			
			for (Blob b : allAttachments) {
				StringBuilder sb = new StringBuilder();

				sb.append("<tr>");
				EmailAttachmentBlob eb = (EmailAttachmentBlob) b;
				String contentFileDataStoreURL = bs.getURL(b);
				String linkURL = "/muse/browse?attachment="
						+ Util.URLtail(contentFileDataStoreURL);
				String fileID = Util.URLtail(contentFileDataStoreURL);
				// String contentURL = "../serveAttachment.jsp?file=" + Util.URLtail(contentFileDataStoreURL);
				sb.append("<td class=\"noTNattachment\">");
				sb.append("<a onclick=\"muse.openQVP(event, " +isWindows+ ", " + qvpExist+ ")\" target=\"_blank\" href=\""
						+ linkURL
						+ "\"> <span class=\"noTNattachmentText\" id=\""
						+ fileID
						+ "\">"
						+ Util.escapeHTML(Util
								.ellipsizeKeepingExtension(b.filename,
										25)) + "</span></a>");
				sb.append("</td>");

				String size_str = Long.toString(b.size / 1024) + "KB";
				sb.append("<td align=\"right\">" + size_str + "</td>");

				String full_date = (eb.modifiedDate != null) ? CalendarUtil
						.formatDateForDisplay(eb.modifiedDate) : "?";
				sb.append("<td>" + full_date + "</td>");

				String sub = blobToSubject.get(eb);
				if (sub != null)
					sub = Util.ellipsize(sub, 30);
				else
					sub = "";
				sb.append("<td>" + sub + "</td>");

				sb.append("</tr>\n");
				out.println(sb);
			}
			out.println("</table>\n");
			//	out.println ("<p><a href=\"" + prefix + ".index.html\">An alternate non-Flash view (under construction)</a><br/>");
		}
		out.println("</body></html>");
	} else {
%>
		No attachments in this set of messages.
<%
	}
%>
</body>
</html>
