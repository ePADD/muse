<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>

<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>

<%
JSPHelper.logRequest(request);
String archiveId = request.getParameter("aId");
String archiveName = Sessions.getArchiveInfoMap(archiveId).get("name");
String archiveDescription = Sessions.getArchiveInfoMap(archiveId).get("description");
String archiveFindingAids = Sessions.getArchiveInfoMap(archiveId).get("findingaids");
String archiveSearchWorks = Sessions.getArchiveInfoMap(archiveId).get("searchworks");

Archive archive = Sessions.loadSharedArchiveAndPrepareSession(session, archiveId);

AddressBook ab = archive.addressBook;
AddressBookStats abs = ab.getStats();
Collection<EmailDocument> allDocs = (Collection) archive.getAllDocs();
assert(ab != null && allDocs != null);
session.setAttribute("emailDocs", allDocs);

int nAttachments = EmailUtils.countAttachmentsInDocs(allDocs);
int nImageAttachments = EmailUtils.countImageAttachmentsInDocs(allDocs);

Archive driver = JSPHelper.getArchive(session);
Indexer indexer = null;
if (driver != null)
	indexer = driver.indexer;

int n_outgoing = ab.getOutMessageCount(allDocs);
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>

<head>
<link rel="shortcut icon" href="http://library.stanford.edu/sites/all/themes/sulair_framework/favicon.ico" type="image/vnd.microsoft.icon" />
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link rel=StyleSheet href="infopage.css" type="text/css">
<link rel=StyleSheet href="general.css" type="text/css">
<link rel=StyleSheet href="bootstrap/css/bootstrap.css" type="text/css">

<!-- JavaScript Sources -->
<script type="text/javascript" src="/muse/js/jquery/jquery.js"></script>
<script src="bootstrap/js/bootstrap-tab.js"></script>
<title><%=archiveName%></title>
</head>

<script>
function browse(e) {
	var url = e.target.value;
	window.location = url;
}

search_entities = true; // default search type
function checkSearchType() {
	if (search_entities) {
		$("input[name=term]").val($("input[name=text]").val());
		$("input[name=person]").val('');
	} else {
		$("input[name=person]").val($("input[name=text]").val());
		$("input[name=term]").val('');
	}
}
</script>

<body style="margin: 0px;">
	<div class="site-wrapper">
		<div id="wrapper" class="clearfix">
			<div id="wrapper-inner">
				<%@include file="header.html"%> 
				<%@include file="title.html"%> 

				<nav class="breadscrumb">
					<a href="/muse/archives">Home</a>
					<span>&gt;
					<%=Util.escapeHTML(archiveName)%>
					</span>
				</nav>
				<br>

				<div id="info-column">

					<table id="info-table">
					<tr>
					<td id="left-column" valign="top">

					<div class="info-box" id="left-pane">

						<table id="collection-info-table">
						<col style="width: 150px"/>
						<col style="width: 500px"/>

						<tr>
							<td class="collection-heading">Collection Title</td>
							<td class="collection-description"><%=Util.escapeHTML(archiveName)%></td>
						</tr>

						<tr>
							<td class="collection-heading">Collection Number</td>
							<td class="collection-description"><%=Util.escapeHTML(archiveId)%></td>
						</tr>

						<tr>
							<td class="collection-heading">Extent</td>
							<td class="collection-description">
							<%=IndexUtils.getDateRangeAsString((List) allDocs)%><br>
							<%=Util.commatize(allDocs.size())%> messages (<%=Util.commatize(n_outgoing)%> outgoing and <%=Util.commatize(allDocs.size()-n_outgoing)%> incoming).<br>
							<%=Util.commatize(nAttachments)%> attachment<%=nAttachments != 1 ? "s" : ""%> <%=Util.commatize(nImageAttachments)%> images.<br>
							<%=Util.commatize(abs.nContacts)%> people.<br>
							<!-- // for privacy concern, group info should come from static session attributes since we shall not include any data which would permit computation on them.
								 // hmm.. but if we already expose email headers to public view then ones can compute/extract group info by themselves.
							500 Groups (Covering 2000 people and 20,000 messages).<br>
							13,000 Attachments (5000 images, 3000 doc, 500 pdf ...) with 1GB.<br>
							 -->
							</td>
						</div>
						</tr>

						<tr>
							<td class="collection-heading">Links</td>
							<td class="collection-description">
								<% if (!Util.nullOrEmpty(archiveFindingAids)) { %>
								<a href="<%=archiveFindingAids%>">Finding Aids</a><br>
								<% } %>
								<% if (!Util.nullOrEmpty(archiveSearchWorks)) { %>
								<a href="<%=archiveSearchWorks%>">SearchWorks</a>
								<% } %>
							</td>
						</tr>

						</table>

						<br>
						<div id="word-cloud-wrapper">
							<span class="collection-heading">Word Cloud of Entities</span><br>
							<div id="word-cloud" class="white-box">
								<% for (Map.Entry<String, Integer> e : archive.getTopNames(10, 100, /*sort by name*/ true)) { %>
								<%   double v = e.getValue().doubleValue(); %>
									 <div class='cloud' style='font-size:<%=(int)(v*2.5)%>%;opacity:<%=v/100%>'><%=e.getKey()%></div>
								<% } %>
								<div style="clear:both"></div>
							</div>
						</div>

					</div>

<!-- 					<h3 class="info-title">Collection Summary:</h3> -->
<!-- 					<div class="info-box"> -->
						
<!-- 						<div class="collection-item"> -->
<!-- 							<div class="collection-heading">Description</div> -->
<!-- 							<div class="collection-description"> -->
<%-- 							<%=archiveDescription%> --%>
<!-- 							</div> -->
<!-- 						</div> -->
<!-- 					</div> -->

					</td>

					<td id="right-column" valign="top">

					<div class="info-box" id="right-pane">

						<div id="browse" class="action-section">
							<span class="collection-heading">Browse</span><br>
							<select width="100%" onchange='browse(event);' onfocus='this.selectedIndex = -1;'>
							<option value="#"></option>
							<option value="cards?aId=<%=Util.URLEncode(archiveId)%>">Monthly summaries</option>
							<option value="correspondents?aId=<%=Util.URLEncode(archiveId)%>&view=people&maxCount=200">Correspondents</option>
							<option value="category?aId=<%=Util.URLEncode(archiveId)%>">Categories</option>
							</select>
						</div>
					 
						<div id="search" class="action-section">
							<form class="form-search" method="post" action="search" onsubmit="checkSearchType()">
								<input type="hidden" name="aId" value="<%=Util.URLEncode(archiveId)%>">
								<div id="search-box">
								<i class="icon-search" style="margin-left: 8px; margin-top: 2px;"></i>
								<input id="search-text" type="text" name="text" class="input-large search-query" onblur="this.value = $.trim(this.value)" onkeypress="if (event.keyCode==13) this.value = $.trim(this.value)" placeholder="search">
								<input type="hidden" name="person" value="">
								<input type="hidden" name="term" value="">
								</div>
								<button type="submit" name="type" value="Entities" class="btn-primary" onclick="search_entities = true">Entities</button>
								<button type="submit" name="type" value="Correspondents" class="btn-primary" onclick="search_entities = false">Correspondents</button>
							</form>
						</div>

						<div id="bulk-search">
							<form method="post" action="../reflectText?aId=<%=Util.URLEncode(archiveId)%>">
								<span class="collection-heading">Bulk Search Entities</span><br>
								<textarea name="refText" cols="25" rows="16" style="width: 330px"></textarea><br>
								<button type="submit" class="btn-primary">Search</button>
							</form>
						</div>

					</div>

					</td>
					</tr>
					</table>
				</div>


			</div>
		</div>
	</div>
	
	
</body>
<%@include file="footer.html"%>
<% JSPHelper.logRequestComplete(request); %>
</html> 
