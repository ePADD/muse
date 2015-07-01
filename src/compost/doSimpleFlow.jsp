<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.datacache.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.slant.*"%>
<%@page language="java" import="edu.stanford.muse.exceptions.*"%>
<%
// DO NOT USE ANY MORE!    !!!!!!!!!!!!!!!!!!!!!!

	JSPHelper.logRequest(request, true);
	MuseEmailFetcher m = (MuseEmailFetcher) JSPHelper.getSessionAttribute(session, "museEmailFetcher");

	// note: folder=<server>^-^<sent folder> is already in the request
	boolean downloadAttachments = request.getParameter("downloadAttachments") != null;
	AddressBook addressBook = null;
	BlobStore blobStore = null;
	Collection<EmailDocument> emailDocs = null;
	
	try {
		Triple<Collection<EmailDocument>, AddressBook, BlobStore> triple = JSPHelper.fetchEmails(request, session, true, downloadAttachments, true); // download message text, maybe attachments, use default folders
		emailDocs = triple.getFirst();
		addressBook = triple.getSecond();
		blobStore = triple.getThird();
		if (emailDocs == null)
		{
			session.setAttribute("errorMessage", "You may not be running with enough memory. Please try again with more memory, or on a folder with fewer messages.");
			session.setAttribute("exception", new NotEnoughMemoryException());
			response.setContentType("text/xml");
			response.setHeader("Cache-Control", "no-cache");
			out.println("<result><div resultPage=\"index\"></div></result>");
			return;
		}
		session.setAttribute("emailDocs", emailDocs);		
	} catch (CancelledException ce) {
		JSPHelper.log.warn ("fetch emails cancelled by user"); 
		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		// this doesn't really matter because the status frontend has disappeared and doesn't read the result
		out.println("<result><div resultPage=\"index\"></div></result>");
		return;
	} catch (Exception e) {
		JSPHelper.log.warn ("Exception fetching emails in simple flow");
		Util.print_exception(e, JSPHelper.log);
		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		out.println("<result><div resultPage=\"error\"></div></result>");
		return;
	}

	Archive archive = null;
	try {
		String resultPage = "info";
		// step 3: indexing
		if ("search".equals(JSPHelper.getSessionAttribute(session, "mode"))) 
		{
			List<LinkInfo> links = JSPHelper.extractLinks(request, session, (Collection) emailDocs, addressBook);
			session.setAttribute("statusProvider", new StaticStatusProvider("Expanding short URLs"));
			CustomSearchHelper.expandShortenedURLs(links); // this may already have been done, but doesn't hurt to do it again

			if (links != null)
			{
				response.setContentType("text/xml");
				response.setHeader("Cache-Control", "no-cache");
				out.println("<result><div resultPage=\"" + "createEmailLinksCSE.jsp" + "\"></div></result>"); // ordinary editing interface
			}
		} else {
			// step 2: grouping
			GroupAssigner ga = JSPHelper.doGroups(request, session, emailDocs, addressBook);

			// step 3. indexing
			List<String> extraOptions = new ArrayList<String>();
			extraOptions.add("-NER"); // we always want NER for simple viz
			extraOptions.add("-incrementalTFIDF");
			extraOptions.add("-subjectWeight");
			extraOptions.add("2");

		    Set<String> ownNames = IndexUtils.readCanonicalOwnNames(addressBook);
			archive = JSPHelper.doIndexing(request, session, ownNames, extraOptions, blobStore);
			if (archive != null)
			{
				archive.setAddressBook(addressBook);
				archive.setGroupAssigner(ga);
				// archive.setBlobStore(blobStore); // already set by doIndexing
			}
		}

		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		out.println("<result><div resultPage=\"" + resultPage + "\"></div></result>"); // ordinary editing interface

		// save the session by default
		String sessionName = "";
		List<String> emailAddrs = m.getUserKeys();
		for (int i = 0; i < emailAddrs.size(); i++) {
			sessionName += emailAddrs.get(i);
			if (i == emailAddrs.size() - 2)
				sessionName += " and ";
			else if (i < emailAddrs.size() - 2)
				sessionName += ", ";
		}
		Sessions.saveSession(session, "Sent messages for " + sessionName);

	} catch (Exception e) {
		// for easier debugging, report exception to javascript
		session.setAttribute("errorMessage", e.toString() + "\n" + Util.stackTrace(e));
		JSPHelper.log.warn(e.toString() + "\n" + Util.stackTrace(e));
		e.printStackTrace(System.err);
		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		out.println("<result><div resultPage=\"error.jsp\"></div></result>");
	}
%>