<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.ner.NER" %>

<%@include file="../getArchive.jspf" %>
<%@include file="../getNERModel.jspf" %>

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
    <title>All entities</title>
    <style> td { padding: 2px 25px 2px 10px} </style>
</head>
<body>
<b>All entities</b><br/>
    <%
        archive.assignThreadIds();

        List<String> entities = new ArrayList<String>(), personEntities = new ArrayList<String>();
        for (Document doc : archive.getAllDocs()) {
            if (Util.nullOrEmpty(request.getParameter("locations")))
                entities.addAll(archive.getEntitiesInDoc(doc, NER.EORG, true, true));
            if (Util.nullOrEmpty(request.getParameter("orgs")))
                entities.addAll(archive.getEntitiesInDoc(doc, NER.ELOC, true, true));
        }

        for (String e: entities) {
            out.println (e + "<br/>");
        }

    %>
</body>
</html>