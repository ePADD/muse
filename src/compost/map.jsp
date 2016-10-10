<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@ page contentType="text/html; charset=UTF-8"%>

<%
	JSPHelper.logRequest(request);
	Archive driver = (Archive) JSPHelper.getSessionAttribute(session, "indexDriver");
	AddressBook addressBook = (AddressBook) JSPHelper.getSessionAttribute(session, "addressBook");
	if (driver == null)
	{
		if (!session.isNew())
	session.invalidate();
//			session.setAttribute("loginErrorMessage", "Your session has timed out -- please click login again.");
%>
	    <script type="text/javascript">window.location="index.jsp";</script>
	<%
		System.err.println ("Error: session has timed out");
			return;
		}
		int nResults = HTMLUtils.getIntParam(request, "n", -1);
		if (nResults == -1)
			nResults = 240;
		IndexUtils.populateTopTermsForCards(addressBook, driver, nResults);
	%>
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="initial-scale=1.0, user-scalable=no" />
<style type="text/css">
  html { height: 100% }
  body { height: 100%; margin: 0px; padding: 0px }
  #map_canvas { height: 100% }
</style>
<script type="text/javascript"
    src="http://maps.google.com/maps/api/js?sensor=false">
</script>
<script type="text/javascript"> var locations = new Array(); </script>

<script type="text/javascript">
  function initialize() {
    var latlng = new google.maps.LatLng(0, 30); // 39.50, -98.35); // approx geo center of the us. for ref: stanford is 37.4241667, -122.165
    var myOptions = {
      zoom: 3,
      center: latlng,
      mapTypeId: google.maps.MapTypeId.HYBRID
    };
    map = new google.maps.Map(document.getElementById("map_canvas"),
        myOptions);

    for (var i = 0; i < locations.length; i++)
    {
    	var location = locations[i];
    	muse.log('setting marker for ' + location.title + ' latlong = ' + location.latlng);
	      marker = new google.maps.Marker({
	      position: location.latlng,
	      map: map,
	      title:location.title
   	    });

	   google.maps.event.addListener(marker, 'click', function(x) { return function(event) {
		   window.open('browse?&term=' + x, '_blank'); //  WARNING: watch out for ' in place name
	   };}(location.title));
    }
  }

</script>
</head>
<Title>Maps Page</Title>
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery/jquery-ui.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript" src="js/statusUpdate.js"></script>
<script type="text/javascript" src="js/json2.js"></script>

</head>
<body>

<%@include file="header.html"%>
  <div id="map_canvas" style="width:100%; height:100%"></div>

<div id="div_main" class="page">
<br/>

<%
	out.flush();

List<Card> cards = null;
// JSPHelper.setupIndexerTopTermCount(session, driver, 150);

if (driver != null && driver.indexer != null)
	cards = driver.indexer.summarizer.cards; 

if (cards == null)
{
	out.println ("<br/><br/>Sorry, no locations found. The session may have timed out or run out of memory. Please try again.");
	return;
}

int cloudNum = -1, nonZeroCloudNum = -1, displayedCloudNum = 0;
// cloudNum = absolute cloudNum based on intervals
// nonZeroCloudNum is clouds with non-zero documents (used by indexer methods)
// displayedCloudNum = cloud # actually displayed on screen
// sone nonZeroClouds may not be displayed
// but because all terms in the cloud don't have any docsForTerm
List<Integer> enabledTagNums = new ArrayList<Integer>();

Set<String> shownLocations = new LinkedHashSet<String>();

StringBuilder sb = new StringBuilder();
sb.append ("<div style=\"font-size:small\"><b>Location list</b><br/>\n");

List<String> addressBookNames = addressBook.getAllWordsInNames();
Set<String> addressBookNamesSet = new LinkedHashSet<String>();
for (String s: addressBookNames)
	addressBookNamesSet.add(s.toLowerCase());

/*
List<String> result = new ArrayList<String>();
for (Contact c: addressBook.allContacts())
{
	for (String name: c.names)
	{
		if (name.indexOf("@") >= 0)  // email addr, not a name
	continue;
	}
}
*/

Archive d = (Archive) JSPHelper.getSessionAttribute(session, "indexDriver");
Indexer indexer = null;
if (d != null)
	indexer = d.indexer;

for (Card t: cards)
{
	cloudNum++;
//	if (t == null)
//		continue;

	List<CardTerm> tagList = t.terms;

	if (tagList == null || tagList.size() == 0)
		continue;

	if (tagList.size() > 0)
	{
		for (CardTerm tag : tagList)
		{
	if (tag.lookupTerm == null)
		continue;
	String loc = tag.lookupTerm.toLowerCase();
	Long L = NER.populations.get(loc);
	if (L == null || L < 10000) // ignores cities < 2000 people
		continue;

	if (addressBookNamesSet.contains(tag.lookupTerm.toLowerCase()))
	{
		JSPHelper.log.info ("Dropping name " + tag.lookupTerm);
		continue;
	}

	LocationInfo li = NER.locations.get(loc);
	if (li != null && !shownLocations.contains(loc))
	{
		int total = (indexer != null) ? 1 : 0;
        //location counts is not available anymore.
		Integer I = indexer.locationCounts.get(loc);
		int identifiedAslocation = (I == null) ? 0 : I;
		int vote = (identifiedAslocation*2-total);
		String s= "Vote: " + vote + " Loc: " + identifiedAslocation + " total:" + total + " ";
		s += Util.escapeHTML(loc);
		if (vote < 0)
		{
	sb.append ("<span style=\"color:red\">" + s + "</span><br/>\n");
	continue;
		}
		sb.append(s + "<br/>\n");
		shownLocations.add(loc);
%>
				<script type="text/javascript">
				locations.push ({title: '<%=tag.originalTerm%>', latlng: new google.maps.LatLng(<%=li.lat%>, <%=li.longi%>) })
				</script>
				  <%
			}
		}
	}
} // end of t: cards
%>
<span class="db-hint">
<%= shownLocations.size() %> locations shown. Compute <a href="map.jsp?n=<%=nResults*2%>">more</a> locations, or <a href="map.jsp?n=<%=nResults/2%>">fewer</a>
</span>
<br/>

<%=sb.toString()%>
</div>


<script type="text/javascript">
muse.log("locations " + locations.length);
initialize();
</script>

<!--
<script>
$('.tagcloud-content').mouseenter(function() { $('.controls', $(this)).show(); }).mouseleave(function() { $('.controls', $(this)).hide(); });
</script>
 -->

<br/>
</div> <!-- div_main -->
<jsp:include page="footer.jsp"/>
</body>
</html>
