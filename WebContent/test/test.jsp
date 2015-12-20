<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<%@ page import="java.util.List" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="java.util.Collection" %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<title>Diary Info</title>
<script type="text/javascript" src="../js/jquery/jquery.js"></script>
<script type="text/javascript" src="../js/jquery/jquery-ui.js"></script>
<link href="muse.css" rel="stylesheet" type="text/css"/>
<link href="cloud.css" rel="stylesheet" type="text/css"/>
</head>
<%
    Archive archive = JSPHelper.getArchive(session);
    List<EmailDocument> emailDocs = (List)archive.getAllDocs();
    AddressBook addressBook = archive.addressBook;
    GroupAssigner ga = JSPHelper.doGroups(request, session, emailDocs, addressBook);
    archive.setGroupAssigner(ga);
%>
</html>