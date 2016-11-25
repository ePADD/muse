<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="javax.mail.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>

<%
	// SocialFlows mode
String socialflowsURL = request.getParameter("socialflow");
if (socialflowsURL != null) {
	socialflowsURL = URLDecoder.decode(socialflowsURL, "UTF-8");
    session.setAttribute("mode", "socialflow");
    session.setAttribute("socialflow", socialflowsURL);
    JSPHelper.log.info ("running in socialflow mode with socialflow=" + socialflowsURL);
}

String jsonpCallback = null;
String mode = (String)JSPHelper.getSessionAttribute(session, "mode");
if ("socialflow".equals(mode)) {
    // JSONP processing
    jsonpCallback = (String)request.getParameter("jsonp_callback");
    if (jsonpCallback == null) {
        System.out.println(request.getServletPath()+": jsonp_callback is empty!");
        return;
    }
}


JSPHelper.logRequest(request);

try
{
	GroupAssigner ca = (GroupAssigner) JSPHelper.getSessionAttribute(session, "groupAssigner");
//	if (ca == null) // disabling this check, we'll compute each time
	{
		boolean success = JSPHelper.fetchEmails(request, session, false);
		if (!success)
		{ // ! success could be due to cancellation also... in that case we should go to folders page ??
	List<MTEmailFetcher> fetchers = (List<MTEmailFetcher>) JSPHelper.getSessionAttribute(session, "fetchers");
            if (fetchers != null) {
            	for (MTEmailFetcher f: fetchers) {
            		if (f.mayHaveRunOutOfMemory()) {
                        session.setAttribute("errorMessage", "May not be running with enough memory.");
                        session.setAttribute("exception", new OutOfMemoryError());
                        break;
                    }
            	}
            }

	response.setContentType("text/xml");
            response.setHeader("Cache-Control", "no-cache");
            out.println("<result><div resultPage=\"error.jsp\"></div></result>");
            return;
		}
			session.setAttribute("statusProvider",
					new StaticStatusProvider(
							"Computing top groups...<br/>&nbsp;"));

			JSPHelper.doGroups(request, session);

			session.setAttribute("statusProvider",
					new StaticStatusProvider(
							"Finishing up...<br/>&nbsp;<br/>"));

			// Generate the JSON for groups output
			// Process the hierarchy
			AddressBook addressBook = (AddressBook) JSPHelper
					.getSessionAttribute(session, "addressBook");
			List<EmailDocument> allDocs = (List<EmailDocument>) JSPHelper
					.getSessionAttribute(session, "emailDocs");
			GroupHierarchy<String> hierarchy = (GroupHierarchy<String>) JSPHelper
					.getSessionAttribute(session, "groupHierarchy");
			JSPHelper.log
					.info("computeGroups.jsp: Generated social topology!!!");
		}

		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		//			out.println("<result><div resultPage=\"facebookGroups.jsp\"></div></result>"); // used to be diaryInfo.jsp
		out.println("<result><div resultPage=\"groups\"></div></result>"); // used to be diaryInfo.jsp

	} catch (Exception e) {
		// for easier debugging, report exception to javascript
		session.setAttribute("errorMessage",
				e.toString() + "\n" + Util.stackTrace(e));
		e.printStackTrace(System.err);

		response.setContentType("text/xml");
		response.setHeader("Cache-Control", "no-cache");
		out.println("<result><div resultPage=\"error.jsp\"></div></result>");
	}
	JSPHelper.logRequestComplete(request);
%>
