
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="org.json.*"%>

<%
	JSPHelper.logRequest(request);
	// This jsp fetches the headers and populates the contactGroups and emailDocs session vars
	// and passes on the baton to getContactGroups2.jsp
    boolean success = JSPHelper.fetchEmails(request, session, false);

	if (!success)
	{
        response.setContentType("text/xml");
        response.setHeader("Cache-Control", "no-cache");
	    out.println("<result><div resultPage=\"error.jsp\"></div></result>");
		return;
	}

    response.setContentType("text/xml");
    response.setHeader("Cache-Control", "no-cache");

    // Let original page handle error situations
    AddressBook addressBook = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
    Collection<EmailDocument> allEmailDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
    if (addressBook == null || allEmailDocs == null || allEmailDocs.size() == 0)
    {
        out.println("<result><div resultPage=\"paramContactGroups3.jsp\"></div></result>");
        return;
    }

    String callbackSocialFlowsURL  = (String)JSPHelper.getSessionAttribute(session, "socialflow");
    if ("socialflow".equals(JSPHelper.getSessionAttribute(session, "mode"))
        && callbackSocialFlowsURL != null && callbackSocialFlowsURL.trim().length() > 0)
    {
    	// Redirect to algo page that is well-integrated with SocialFlows
    	String useNewGroupsAlgo = (String)request.getParameter("useNewGroupsAlgo");
    	if (useNewGroupsAlgo != null && useNewGroupsAlgo.equals("true"))
    	{
    		System.out.println("doContactGroups3.jsp: Should be displaying new algo of getContactGroups5.jsp\n");

            out.println("<result><div resultPage=\"getContactGroupsToSocialFlows.jsp\"></div></result>");
    	}
    	else
    	{
            System.out.println("doContactGroups3.jsp: Should be displaying\n");

            out.println("<result><div resultPage=\"getContactGroupsToSocialFlows.jsp?useOldGroupsAlgo=true\"></div></result>");
        }


    }
    else {
   //     out.println("<result><div resultPage=\"getContactGroups5.jsp\"></div></result>");
        out.println("<result><div resultPage=\"paramContactGroups3.jsp\"></div></result>");
    }
	JSPHelper.logRequestComplete(request);
%>
