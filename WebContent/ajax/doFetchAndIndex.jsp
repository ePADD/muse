<%@page language="java" contentType="text/javascript; charset=UTF-8"%>
<%@page trimDirectiveWhitespaces="true"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.exceptions.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.memory.*"%>
<%
	// core JSP that does fetch, grouping and indexing
	// sets up archive in the session at the end

	JSPHelper.logRequest(request);
	session.setAttribute("statusProvider", new StaticStatusProvider("Starting up..."));

	boolean cancelled = false;
	JSONObject result = new JSONObject();
	String errorMessage = null;
	String resultPage = null;
	// simple flow means we're running off the login page and that we'll just use sent folders
	boolean simpleFlow = request.getParameter("simple") != null;
	boolean searchMode = "search".equals(JSPHelper.getSessionAttribute(session, "mode"));
	boolean memoryTestMode = "memorytest".equals(JSPHelper.getSessionAttribute(session, "mode"));
	MuseEmailFetcher m = (MuseEmailFetcher) JSPHelper.getSessionAttribute(session, "museEmailFetcher");
	// m can't be null here, the stores should already have been set up inside it

	try {
		Archive archive = (Archive) session.getAttribute("archive");
		if (archive == null) {
			SimpleSessions.prepareAndLoadArchive(m, request);
			archive = (Archive) session.getAttribute("archive");
		}

		// step 1: fetch		
		Collection<EmailDocument> emailDocs = null;

		// note: folder=<server>^-^<sent folder> is already in the request
		String str = request.getParameter("downloadAttachments");
		// a little confusing here... some frontends omit downloadAttachments and some frontends send downloadAttachments=false
		boolean downloadAttachments = !(str == null || "false".equals(str));
		// now fetch and index... can take a while
		// we'll index messages even in slant mode... not strictly needed, but does not hurt.
		// rewrite for efficiency if slant becomes important.
		m.setupFetchers(-1);

		// set up the owner email addrs from the email addrs saved in the fetcher's stores
		String altEmailAddrs = (String) JSPHelper.getSessionAttribute(session, "alternateEmailAddrs");
		for (EmailStore store : m.emailStores)
			if (!(Util.nullOrEmpty(store.emailAddress)))
				archive.addOwnerEmailAddr(store.emailAddress);
		if (!Util.nullOrEmpty(altEmailAddrs))
			archive.addOwnerEmailAddrs(EmailUtils.parseAlternateEmailAddrs(altEmailAddrs));

		// the emailStores session var has done its job (check login info and hold on to stores till MEF can take over) and can be removed		

		// this is a special flag used during screening time to read only headers without the message bodies
		boolean downloadMessageText = !"false".equals(request.getParameter("downloadMessages"));

		JSPHelper.fetchAndIndexEmails(archive, m, request, session, downloadMessageText, downloadAttachments, simpleFlow); // download message text, maybe attachments, use default folders
		archive.postProcess();

		emailDocs = (List) archive.getAllDocs();
		AddressBook addressBook = archive.getAddressBook();
		//Set<String> ownNames = IndexUtils.readCanonicalOwnNames(addressBook); // TODO: to be executed somewhere? used to be passed to doIndexing which in turn passed it to recomputeCards.

		if (emailDocs == null) {
			// if we run out of memory parsing mbox files etc, emailDocs == null is usually the manifestation
			errorMessage = "You may not be running with enough memory. Please try again with more memory, or on a folder with fewer messages.";
		} else {
			session.setAttribute("emailDocs", emailDocs);

			if (searchMode) {
				// exp. infused search. only extract links. no need of groups or indexing either.
				List<LinkInfo> links = JSPHelper.extractLinks(archive, session, (Collection) emailDocs, addressBook);
				session.setAttribute("statusProvider", new StaticStatusProvider("Expanding short URLs"));
				// CustomSearchHelper.expandShortenedURLs(links); // this may already have been done, but doesn't hurt to do it again
				resultPage = "createEmailLinksCSE.jsp";
			} else if (memoryTestMode) {
				/** this is for screening only. the real memory test uses prepareMemoryTest */
				try {					
					resultPage = "screen-result";
					Pair<List<Integer>, Boolean> screenResult = Screener.screen(emailDocs, MemoryStudy.N_INTERVALS /* months */, MemoryStudy.MIN_MESSAGES_PER_INTERVAL /* messages/month */, MemoryStudy.INTERVAL_MILLIS);
					if (screenResult.getSecond())
					{
						String loginName = request.getParameter("loginName0");
						if (!Util.nullOrEmpty(loginName))
							session.setAttribute("screenPassEmail", loginName);
						resultPage= "/muse/memorystudy/screenpass";
						session.setAttribute("activityHistogram", screenResult.getFirst());
					}
					else
						resultPage= "/muse/memorystudy/faileligibility.html";
					// clear the archive when done with screening
				} finally {
					try {
						archive.clear();
						archive.close();
						String cacheDir = (String) session.getAttribute("cacheDir");
					    String rootDir = JSPHelper.getRootDir(request);
						Archive.clearCache(cacheDir, rootDir);
						// avoid trouble later if the same session is used
						session.removeAttribute("emailDocs");
						session.removeAttribute("archive");
					} catch (Exception e) {
						JSPHelper.log.info("Error clearing cache");
						Util.print_exception (e, JSPHelper.log);
					}
				}
			} else {
				if (request.getParameter("nogroups") == null) {
					// normal muse flow.
					GroupAssigner ga = JSPHelper.doGroups(request, session, emailDocs, addressBook);
					archive.setGroupAssigner(ga);
				}

				resultPage = "info";
				SimpleSessions.saveArchive(session);
			}
			try {
				String aStats = archive.getStats();
				JSPHelper.log.info("ARCHIVESTATS-1: " + aStats);
				Pair<String, String> p = Util.fieldsToCSV(archive.addressBook.getStats(), false);
				JSPHelper.log.info("ADDRESSBOOKSTATS-1: " + p.getFirst());
				JSPHelper.log.info("ADDRESSBOOKSTATS-2: " + p.getSecond());
				p = Util.fieldsToCSV(archive.getStats(), false);
				JSPHelper.log.info("INDEXERSTATS-1: " + p.getFirst());
				JSPHelper.log.info("INDEXERSTATS-2: " + p.getSecond());
			} catch (Exception e) { }
		}
	} catch (CancelledException ce) {
		// op was cancelled, so just go back to where we must have been
		JSPHelper.log.warn("Fetch groups and indexing cancelled by user");
		// need to be careful with incremental indexing here...
		// may need to archive.clear();
		cancelled = true;
	} catch (Exception e) {
		JSPHelper.log.warn("Exception fetching/indexing emails");
		Util.print_exception(e, JSPHelper.log);
		errorMessage = "Exception fetching/indexing emails";
		// we'll leave archive in this
	}

	if (cancelled) {
		result.put("status", 0);
		result.put("cancelled", true);
	} else if (errorMessage == null) {
		result.put("status", 0);
		result.put("resultPage", resultPage);
	} else {
		result.put("status", 1);
		result.put("resultPage", "error");
		result.put("error", errorMessage);
	}
	out.println(result);

	// resultPage is set up to where we want to go next
	session.removeAttribute("statusProvider");
	JSPHelper.logRequestComplete(request);
%>
