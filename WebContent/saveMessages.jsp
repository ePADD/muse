<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<% 	JSPHelper.logRequest(request); %>

<html>
<head>
<title>Save messages</title>
<jsp:include page="css/css.jsp"/>
</head>
<body>
<%	if (Util.nullOrEmpty(request.getParameter("noheader"))) { %>
	<jsp:include page="header.jsp"/>
<% } %>

<div class="panel" style="padding-left:10%">
<%
//	Set<Document> selectedDocs = (Set) JSPHelper.selectDocs(request, session);
	Archive archive = JSPHelper.getArchive(session);
	Collection<Document> allDocs = (Collection<Document>) JSPHelper.getSessionAttribute(session, "emailDocs");
	String docset = request.getParameter("docset");
	List<Document> selectedDocs = null;
	if (Util.nullOrEmpty(docset)) {
		selectedDocs = (List<Document>) JSPHelper.getSessionAttribute(session, "emailDocs");
	}
	else
	{
		DataSet browseSet = (DataSet) JSPHelper.getSessionAttribute(session, docset);
		if (browseSet == null) {
			%>
			<br/>Sorry, that message view was just freed up in order to conserve memory. Please try loading the messages view again. (<%=docset%>)
			<br/>
			</div>
			<jsp:include page="footer.jsp"/>
			</body>
			</html>
			<%
			return;
		}	
		selectedDocs = browseSet.getDocs();
	}
	
	JSPHelper.log.info ("original browse set has " + selectedDocs.size() + " docs");

	String num = request.getParameter("num");
	if (!Util.nullOrEmpty(num))
	{
		List<String> tokens = Util.tokenize(num);		
		// hack! we keep the nums as strings instead of converting to numbers
		Set<String> selectedNums = new LinkedHashSet<String>(tokens);
		
		// filter selected nums
		List<Document> filteredDocs = new ArrayList<Document>();
		for (int i = 0; i < selectedDocs.size(); i++)
			if (selectedNums.contains(Integer.toString(i)))
				filteredDocs.add(selectedDocs.get(i));
		selectedDocs = filteredDocs;
	}
	
	JSPHelper.log.info ("doc set after num filters has " + selectedDocs.size() + " docs");
	if (selectedDocs.size() == 0)
	{
		%>
		<br/>Sorry, no messages match that filter.<br/><br>
		</div>
		<jsp:include page="footer.jsp"/>
		</body>
		</html>
		<%
		return;
	}
	
	// either we do tags (+ or -) from selectedTags
	// or we do all docs from allDocs
	String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");
	String attachmentsStoreDir = cacheDir + File.separator + "blobs" + File.separator;
	BlobStore bs = null;
	try {
		bs = new FileBlobStore(attachmentsStoreDir);
		JSPHelper.log.info ("Good, found attachments store in dir " + attachmentsStoreDir);
	} catch (IOException ioe) {
		JSPHelper.log.error("Unable to initialize attachments store in directory: " + attachmentsStoreDir + " :" + ioe);
	}

    String rootDir = JSPHelper.getRootDir(request);
	new File(rootDir).mkdirs();
	String userKey = JSPHelper.getUserKey(session);
	String name = request.getParameter("name");
	if (Util.nullOrEmpty(name))
		name = String.format("%08x", EmailUtils.rng.nextInt());
	String filename = name + ".mbox.txt";
	String path = rootDir + File.separator + filename;

	String tag = request.getParameter("tag");
	String notTag = request.getParameter("nottag");
	
	// can opt to save docs with or without a certain tag (req param tag or nontag)
	if (!Util.nullOrEmpty(tag))
	{
		Set<Document> taggedDocs = new LinkedHashSet<Document>();
		for (Document d: selectedDocs)
		{
			if (d.comment == null)
				continue;
			if (tag.toLowerCase().equals(d.comment.toLowerCase()))
				taggedDocs.add(d);				
		}
		selectedDocs = new ArrayList<Document>(taggedDocs);
	}
	else if (!Util.nullOrEmpty(notTag))
	{
		Set<Document> nonTaggedDocs = new LinkedHashSet<Document>();
		for (Document d: selectedDocs)
		{
			if (d.comment != null && notTag.toLowerCase().equals(d.comment.toLowerCase()))
				continue;
			nonTaggedDocs.add(d);				
		}
		selectedDocs = new ArrayList<Document>(nonTaggedDocs);
	}
	
	JSPHelper.log.info ("After tag filters, saving " + selectedDocs.size() + " messages to " + path);
	PrintWriter pw = new PrintWriter (path);

	String noAttach = request.getParameter("noattach");
	boolean noAttachments = "on".equals(noAttach); 
	boolean stripQuoted = "on".equals(request.getParameter("stripQuoted"));
	for (Document ed: selectedDocs)
		EmailUtils.printToMbox(archive, (EmailDocument) ed, pw, noAttachments ? null: bs, stripQuoted);
	pw.close();
%>

<br/>

A file with <%=Util.pluralize(selectedDocs.size(), "message")%> is ready. <br/>
To save it, right click <a href="<%=userKey%>/<%=filename%>">this link</a> and select "Save Link As...". 
<% String message;
if (selectedDocs.size() > 1000) 
	message = "";
else if (selectedDocs.size() > 50)
	message = "(Left click to view the file. It can be very large!)<br/>";
else
	message = "(Left click to view the file in the browser.)<br/>";		
%>
<%=message%>

<p></p>
This file is in mbox format, and can be accessed with many email clients (e.g. <a href="http://www.mozillamessaging.com/">Thunderbird</a>.)
It can also be viewed with a text editor.<br/>
On Mac OS X, Linux, and other flavors of Unix, you can usually open a terminal window and type the command: <br/>
<i>mail -f &lt;saved file&gt;</i>
<br/>
<br/>
</div>
<jsp:include page="footer.jsp"/>
</body>
</html>