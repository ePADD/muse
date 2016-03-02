<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="edu.stanford.muse.email.Contact" %>
<%@ page import="edu.stanford.muse.ner.NER" %>

<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="javax.mail.Address" %>
<%@ page import="com.google.common.collect.Multimap" %>
<%@ page import="com.google.common.collect.LinkedHashMultimap" %>
<%@ page import="edu.stanford.muse.util.DictUtils" %>
<%@ page import="org.w3c.tidy.Dict" %>
<%@ page import="edu.stanford.muse.xword.ArchiveCluer" %>
<%@ page import="edu.stanford.muse.xword.Clue" %>
<%@ page import="java.io.File" %>
<%@ page import="edu.stanford.muse.ner.model.NERModel" %>
<%@ page import="edu.stanford.muse.util.EmailUtils" %>
<%@ page import="edu.stanford.muse.xword.SentenceTokenizer" %>
<%@include file="../getArchive.jspf" %>
<%@include file="../getNERModel.jspf" %>

<html>
<head>
    <link rel = "stylesheet" type ="text/css" href="memorystudy/css/screen.css">
    <link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>
    <meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
    <link rel="icon" href="images/ashoka-favicon.gif">
    <jsp:include page="../css/css.jsp"/>
    <script type="text/javascript" src="js/jquery/jquery.js"></script>
    <script type="text/javascript" src="js/jquery.safeEnter.1.0.js"></script>
    <script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
    <script type="text/javascript" src="js/statusUpdate.js"></script>
    <script type="text/javascript" src="js/muse.js"></script>
    <script type="text/javascript" src="js/ops.js"></script>
    <title>All sentences</title>
    <style> td { padding: 2px 25px 2px 10px} </style>
</head>
<body>
<b>All sentences</b><br/>
    <%
        archive.assignThreadIds();
        Lexicon lex = archive.getLexicon("default");

        for (Document d: archive.getAllDocs()) {
            EmailDocument ed = (EmailDocument) d;

            String contents = archive.getContents(ed, true);
    		String cleanedContents = EmailUtils.cleanupEmailMessage(contents);
				SentenceTokenizer st = new SentenceTokenizer(cleanedContents);
				int sentenceNum = 0;

				while (st.hasMoreSentences())
                    out.println (Util.escapeHTML(st.nextSentence()) + "<br/>");
//            out.println (++sentenceNum + ". " + Util.escapeHTML(st.nextSentence()) + "<br/>");

                out.println ("<br/>");
        }
    %>
</body>
</html>