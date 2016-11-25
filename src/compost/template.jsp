<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
    <meta http-equiv="content-type" content="text/html; charset=utf-8">
    <link href="../css/muse.css" rel="stylesheet" type="text/css"/> <!--  TOFIX for alt styles -->
<title>Weighted Social Graph</title>

<style type="text/css">
/*margin and padding on body element
  can introduce errors in determining
  element position and are not recommended;
  we turn them off as a foundation for YUI
  CSS treatments. */
body {
	margin:5%;
	padding:0;
}
</style>

<link rel="stylesheet" type="text/css" href="../yui/build/fonts/fonts-min.css" />
<link rel="stylesheet" type="text/css" href="../yui/build/datatable/assets/skins/sam/datatable.css" />
<script type="text/javascript" src="../yui/build/yahoo-dom-event/yahoo-dom-event.js"></script>
<script type="text/javascript" src="../yui/build/dragdrop/dragdrop-min.js"></script>
<script type="text/javascript" src="../yui/build/element/element-beta-min.js"></script>
<script type="text/javascript" src="../yui/build/datasource/datasource-min.js"></script>
<script type="text/javascript" src="../yui/build/datatable/datatable-min.js"></script>


<!--begin custom header content for this example-->
<style type="text/css">
/* custom styles for this example */
.yui-skin-sam .yui-dt-liner { white-space:nowrap; }
.yui-skin-sam .body { font-style: normal; white-space:nowrap; }
</style>

<!--end custom header content for this example-->

</head>

<body class=" yui-skin-sam">
<jsp:include page="../header.jsp"/>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>

<p>
<%
JSPHelper.logRequest(request);
String xmlDataFile = (String) JSPHelper.getSessionAttribute(session, "xmlDataFile");
out.println ("<a href=\"" + xmlDataFile + "\">");
out.println ("<img src=\"http://www.google.com/calendar/images/xml.gif\"/>");
out.println ("</a>");
out.println ("&nbsp;&nbsp;&nbsp;&nbsp;");
String prefuseDataFile = (String) JSPHelper.getSessionAttribute(session, "prefuseDataFile");
out.println ("<a href=\"" + prefuseDataFile + "\">");
out.println ("<img src=\"http://prefuse.org/images/prefuse-logo.gif\" height=\"15\"/>");
out.println ("</a>");
out.println ("&nbsp;&nbsp;&nbsp;&nbsp;");
out.println ("<a href=\"MailVoyager.jsp\">Mail Voyager</a> (Experimental)");
out.println ("&nbsp;&nbsp;&nbsp;&nbsp;");
out.println ("<a href=\"protovis.html\">Protoviz</a> (Experimental)");

%>
<div class="exampleIntro">
</div>

<!--BEGIN SOURCE CODE FOR EXAMPLE =============================== -->

<div id="basic"></div>

<% String jsDataFile = (String) JSPHelper.getSessionAttribute(session, "jsDataFile");
out.println ("<script type=\"text/javascript\" src=\"" + jsDataFile + "\"></script>");
%>
<script type="text/javascript">
YAHOO.util.Event.addListener(window, "load", function() {
    YAHOO.example.Basic = new function() {
        var myColumnDefs = [
            {key:"emailAddress", label: "Email", sortable:true, resizeable:true},
            {key:"inCount", label: "In count", formatter:YAHOO.widget.DataTable.formatNumber, sortable:true, resizeable:true},
            {key:"outCount", label: "Out count", formatter:YAHOO.widget.DataTable.formatNumber, sortable:true, resizeable:true},
            {key:"timeline", sortable:true, resizeable:true},
            {key:"names", label: "Name(s)", sortable:true, resizeable:true},
        ];

        this.myDataSource = new YAHOO.util.DataSource(CONTACTS_DATA.contacts);
        this.myDataSource.responseType = YAHOO.util.DataSource.TYPE_JSARRAY;
        this.myDataSource.responseSchema = {
            fields: ["emailAddress","inCount","outCount","timeline", "names"]
        };

        this.myDataTable = new YAHOO.widget.DataTable("basic",
                myColumnDefs, this.myDataSource, {caption:CONTACTS_DATA.title});
    };
});

</script>

<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<p/>&nbsp;
<jsp:include page="../footer.jsp"/>
</body>
</html>
