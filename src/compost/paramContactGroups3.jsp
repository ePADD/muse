<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<% 	JSPHelper.logRequest(request); %>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page language="java" import="org.json.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<title>Contact Groups</title>
<jsp:include page="css/css.jsp"/>
</head>
<body>

<%@include file="header.html"%>
<p/> <p/> <p/> <p/>

    <%
    AddressBook addressBook = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
    Collection<EmailDocument> allEmailDocs = (Collection<EmailDocument>) JSPHelper.getSessionAttribute(session, "emailDocs");
	if (addressBook == null || allEmailDocs == null)
	{
		if (!session.isNew())
			session.invalidate();
		session.setAttribute("loginErrorMessage", "Your session has timed out -- please click login again.");
%>
        <script type="text/javascript">window.location="index.jsp";</script>
<%
        System.err.println ("Error: session has timed out, addressBook = " + addressBook);
		if (allEmailDocs != null)
			System.err.println ("allEmailDocs size = " + allEmailDocs);
		else
			System.err.println ("allEmailDocs = null");
		return;
	}
%>
<br/>
<b>We have <%= allEmailDocs.size() %> messages, <%=addressBook.size() %> contacts</b>
<% if (allEmailDocs.size() == 0) {  %>
<br/>(Er... sorry, we can't work with 0 messages. Please choose a folder that has some messages. Or your date filter may be too strict).
<% return;
}
%>
<br/>

<br/>
<b>Advanced Controls</b>

<%
    String contactGroupsResultPage = "getContactGroups3.jsp";
    String callbackSocialFlowsURL  = (String)JSPHelper.getSessionAttribute(session, "socialflow");
    String additionalOptionsCSS = "";
    String submitButtonText = "Go";
    if ("socialflow".equals(JSPHelper.getSessionAttribute(session, "mode"))
    	&& callbackSocialFlowsURL != null && callbackSocialFlowsURL.trim().length() > 0)
    {
    	contactGroupsResultPage = "getContactGroupsToSocialFlows.jsp";
    	additionalOptionsCSS = "display: none";
    	submitButtonText = "Continue with SocialFlows";
    }
%>
<form method="get" action="<%=contactGroupsResultPage%>">
 <table>
    <tr>
        <td align="right">Min. Frequency</td>
        <td> <input class="input-field" type="text" name="minCount" id="minCount" size="3" value="<%=AlgoStats.DEFAULT_MIN_FREQUENCY%>"/> messages</td>
    </tr>
    <tr>
        <td align="right">Max. error<br/></td>
        <td> <input class="input-field" type="text" name="maxError" id="maxError" size="3" value="<%=AlgoStats.DEFAULT_MAX_ERROR%>" /> for subsumed subsets</td>
    </tr>
    <tr>
        <td align="right">Min. group size<br/></td>
        <td> <input class="input-field" type="text" name="minGroupSize" id="minGroupSize" size="3" value="<%=AlgoStats.DEFAULT_MIN_GROUP_SIZE%>"/></td>
    </tr>
    <tr>
        <td align="right">Min. group similarity<br/></td>
        <td> <input class="input-field" type="text" name="minMergeGroupSim" id="minMergeGroupSim" size="3" value="<%=AlgoStats.DEFAULT_MIN_GROUP_SIMILARITY%>"/> for merging groups </td>
    </tr>
    <tr style="<%=additionalOptionsCSS%>">
        <td align="right">Threads only<br/></td>
        <td> <input class="input-field" type="checkbox" name="threadsOnly" id="threadsOnly" />  (experimental)</td>
    </tr>
    <tr style="<%=additionalOptionsCSS%>"><td>&nbsp;</td></tr>
    <% /*
        <tr> <td colspan="2"><b>Group evolution:</b></td></tr>
		<tr> <td align="right">Window size</td><td> <input class="input-field" type="text" name="windowSize" id="windowSize" value="12" size="3"/> months</td></tr>
    		<tr><td align="right">Slide by </td><td><input class="input-field" type="text" name="windowScroll" id="windowScroll" value="3" size="3"/> months</td></tr>
    		<tr><td align="right">Min. similarity</td><td> <input class="input-field" type="text" name="minGroupSimEvolution" id="minGroupSimEvolution" value="0.1" size="3"/> Jaccard similarity for mapping groups across time windows</td></tr>

    <tr><td>&nbsp;</td></tr>
    */ %>

        <tr style="<%=additionalOptionsCSS%>">
        <td align="left" colspan="2"><b>Group utility </b> (used for display ordering only)</td>
        </tr>
        <tr style="<%=additionalOptionsCSS%>">
        <td align="right">w.r.t. size</td>
        <td>
        <input type="radio" name="utilityType" value="linear" checked />linear</input>
        <input type="radio" name="utilityType" value="square" />square</input>
        <input type="radio" name="utilityType" value="exponential" />exponential</input>&nbsp;
        <input class="input-field" type="text" name="multiplier" id="multiplier" value="1.4" size="3"/>^group size
		</td>
	    </tr>
    <tr>
    <td></td>
    <td>
    <br/>
    <button name="submit" value="submit" type="submit"><%=submitButtonText%></button>
    </td>
    </tr>
  </table>
</form>
<%

%>
<br/>

<jsp:include page="footer.jsp"/>
</body>
</html>
