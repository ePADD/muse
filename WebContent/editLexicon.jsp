<%@ page language="java" contentType="text/html; charset=UTF-8"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>    
<!DOCTYPE html>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.lang.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<% 

String cacheDir = (String) JSPHelper.getSessionAttribute(session, "cacheDir");

String name = request.getParameter("lexicon");
Lexicon lex = (Lexicon) JSPHelper.getSessionAttribute(session, "lexicon");
JSPHelper.log.info ("req lex name = " + name + " session lex name = " + ((lex == null) ? "(lex is null)" : lex.name));
// resolve lexicon based on name in request and existing lex in session.
// name overrides lex
if (!Util.nullOrEmpty(name))
{
	if (lex == null || !lex.name.equals(name))
	{
		lex = new Lexicon(cacheDir, name);
		session.setAttribute("lexicon", lex);	
	}
	// else do nothing, the right lex is already loaded
}
else
{
	if (lex == null)
	{
		// nothing in session, no request param... probably shouldn't happen
		name = "general";
		lex = new Lexicon(cacheDir, name);
		session.setAttribute("lexicon", lex);	
	}
	else
		name = lex.name;
}

// now name and lex are both set correctly.

boolean createLexicon = request.getParameter("create") != null;
String language = request.getParameter("language");
if (language == null)
	language = "english"; // default

if (request.getParameter("updateLexicon") != null)
{
	Map<String, String[]> map = new LinkedHashMap<String, String[]>((Map<String, String[]>) request.getParameterMap());
	map.remove("to");
	map.remove("lexicon");
	map.remove("language");
	String lexiconParam = "";
	
	if (name != null)
		lexiconParam = "&lexicon=" + Util.escapeHTML(name);
	
	if (name != null && language != null)
	{
		// convert string->string[] to string -> string
		Map<String, String> newMap = new LinkedHashMap<String, String>();
		for (String key: map.keySet())
		{
			String val = map.get(key)[0];
			if (Util.nullOrEmpty(val))
				continue;
			newMap.put(key, val);
		}
		JSPHelper.log.info ("updating lexicon for " + name + " in language " + language + " with " + newMap.size() + " entries");
		lex.update(language, newMap);
		lex.save(cacheDir, language);
	}
	response.sendRedirect("stackedGraph?view=sentiments" + lexiconParam);
}

%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<jsp:include page="css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>
<title>Muse Lexicons</title>
<script type="text/javascript" src="js/jquery/jquery.js"></script>
<script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
<script type="text/javascript" src="js/muse.js"></script>
<script type="text/javascript">
function addCategory()
{
	var name = prompt ("Category name");
	if (name == null || name.length == 0)
		return;
	
	var html = '<p><b>' + name + '</b> <br/> <textarea cols="120" rows="2" name="' + name + '" placeholder="Enter some words or phrases, separated by |">';
	$('#categories').append(html);
	var areas = $('textarea');
	areas[areas.length-1].focus();
}

function sendLexInfo() {
	document.getElementById('lexForm').action = "http://prpl.stanford.edu/report/field_report.php";
	// we'll send back everything inside the main div
	// add on the current value of the submitter field since its not in the inner html
	var data = 'Submitter is ' + document.getElementById('submitter').value + escape(document.getElementById('main').innerHTML);
	// jam in the current page into the submit form as another hidden var
	document.getElementById('lexForm').innerHTML = document.getElementById('lexForm').innerHTML + '<input type="hidden" name="debugpage" value="' + data + '"></input>';
	document.getElementById('lexForm').submit();
//	document.getElementById('submitFormDiv').innerHTML = "Thank you for submitting a problem report.";
	alert ('Thanks!');
}

</script>
</head>
<body class="fixed-width">
<jsp:include page="header.jsp"/>
<div id="main" class="panel rounded shadow">
<div align="center">
<h2>Muse Lexicons</h2>

<span class="db-hint">
Words in each category are separated with the | character. Multi-word phrases can be specified with double quotes. <br/>
You can refer to other categories as {category-name}.
To remove a category, simply remove all words in it.
</span>
<form id="lexForm" action="editLexicon" method="post"> <!-- post back to the same page -->
<input type="hidden" name="updateLexicon"></input> <!--  this param present only when we are updating/saving a lex -->
<input type="hidden" name="language" value="<%=language%>"></input>
<input type="hidden" name="lexicon" value="<%=name%>"></input>

<% 
%>
<p>

<p>
Editing lexicon <%=name %> in 

<select id="language" onchange='editLexicon()'>
<%
Set<String> languages = Languages.allLanguages;
for (String lang: languages)
{
%>
	<option <%=language.equalsIgnoreCase(lang) ? "selected":"" %> value="<%=lang%>"><%=Util.capitalizeFirstLetter(lang)%></option>
<%
}
// note: no option to create a new language, because we don't yet have the ability to detect if, if its not special to a script.
%>

</select>
&nbsp;&nbsp;
(<a href="#" onclick="createLexicon()">Create new lexicon</a>)
<script type="text/javascript">
function editLexicon()
{
	window.location = 'editLexicon?lexicon=<%=name%>&language=' + $('#language').val();
}
function createLexicon()
{
	var name = prompt ('Enter the name of the new lexicon:');
	if (!name)
		return;
	window.location = 'editLexicon?lexicon=' + name + '&create=1';
}
</script>

<div id="categories">
<%

// can show expandedMap here, but then disable update/query option so that the expanded map doesn't get saved
Map<String, String> captionToQueryMap = lex.getRawMapFor(language);
if (captionToQueryMap != null)
{
	for (String sentiment: captionToQueryMap.keySet())
	{
		String query = captionToQueryMap.get(sentiment);
		int nRows = query.length()/120 + 1;
	%>
	<p>
	<b><%=sentiment%></b><br/>
	<textarea style="padding:5px" cols="120" rows="<%=nRows%>" name="<%=sentiment%>" ><%=query%></textarea>
	<% 
	
	}
}
else 
{
	%>
	No categories available for <%=language%>. Please create some.
	<%
}
%>
</div> <!--  categories -->
</form>
<p>
<button class="tools-pushbutton" onclick="javascript:addCategory();" >Add a category</button>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
<button class="tools-pushbutton" onclick="$('#lexForm').submit();" >Save Lexicon</button>
<p/>
<input type="hidden" name="to" value="hangal@cs.stanford.edu"></input>

</div>
<br/>
<h3>New!</h3> 
Help improve Muse by <a href="#" onclick="javascript:sendLexInfo(); return false;">clicking here</a> to send this list back to Stanford.<br/>
No personal information is submitted.<br/>
Optionally, please provide your email address so we can send you a thank you note: <input id="submitter" type="text" size="30"></input><br/>
<br/>
<jsp:include page="footer.jsp"/>
</div>
<%
javax.servlet.http.Cookie[] cookies = request.getCookies();
int lexiconPrompts = 0;
for (javax.servlet.http.Cookie cookie: cookies)
	if ("lexiconPrompts".equals(cookie.getName()))
	{
		try { lexiconPrompts = Integer.parseInt(cookie.getValue()); } catch (NumberFormatException nfe) { }
		break;
	}

	javax.servlet.http.Cookie cookie1 = new javax.servlet.http.Cookie("lexiconPrompts", Integer.toString(lexiconPrompts+1));
    cookie1.setMaxAge(24*60*60*7); // 7 days
    response.addCookie(cookie1); 

if (lexiconPrompts < 5) { %>
	<script type="text/javascript">
		$(document).ready(function() { $.jGrowl('<span class="growl"><br/>We would like to include lexicons for other languages in Muse. If you know another language, please consider submitting a lexicon using this form. Thanks!</span>'); }); 
	</script>
	<% 	
} 	%>

</body>
</html>