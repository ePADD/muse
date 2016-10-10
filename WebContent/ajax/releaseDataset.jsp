<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<% 
JSPHelper.setPageUncacheable(response);
response.setContentType("application/json; charset=utf-8");

String datasetId = request.getParameter("datasetId");
DataSet dataset = (DataSet) JSPHelper.getSessionAttribute(session, datasetId);
boolean error = (dataset == null);
if (error)
	out.println("{status: 'error'}");
else
{
	dataset.clear();
	session.removeAttribute(datasetId);
	out.println("{status: 'ok'}");
}

%>
