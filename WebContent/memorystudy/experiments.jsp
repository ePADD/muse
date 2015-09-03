<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="edu.stanford.muse.email.Contact" %>
<%@ page import="edu.stanford.muse.ner.NER" %>
<%@ page import="edu.stanford.muse.util.Triple" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@include file="../getArchive.jspf" %>
<html>
<head>
    <link rel = "stylesheet" type ="text/css" href="memorystudy/css/screen.css">
    <link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>
    <meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
    <link rel="icon" href="memorystudy/images/stanford-favicon.gif">
    <jsp:include page="../css/css.jsp"/>
    <script type="text/javascript" src="js/jquery/jquery.js"></script>
    <script type="text/javascript" src="js/jquery.safeEnter.1.0.js"></script>
    <script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
    <script type="text/javascript" src="js/statusUpdate.js"></script>
    <script type="text/javascript" src="js/muse.js"></script>
    <script type="text/javascript" src="js/ops.js"></script>
    <title>Experiments</title>
    <style> td { padding: 2px 10px 2px 10px} </style>
</head>
<body>
<p>

<b>Entity experiments</b>

<p>
    <a target="_blank" href="entities-by-last-month.jsp">Browse non-person entities by month of last occurrence</a>
    <p>
    <a target="_blank" href="contacts-by-last-month.jsp">Browse contacts by month of last occurrence</a>
    <p>
    <a target="_blank" href="solo-contacts-by-last-month.jsp">Browse solo contacts by month of last occurrence</a>

</body>
</html>