<%@ page contentType="text/html; charset=UTF-8"%>
<% JSPHelper.checkContainer(request); // do this early on so we are set up
  request.setCharacterEncoding("UTF-8"); %>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.util.Pair"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.util.zip.*"%>
<%@page language="java" import="java.io.*"%>
String title = "Advanced Search";
%>


<p>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
  "http://www.w3.org/TR/html4/loose.dtd">
<html lang="en">
<head>
<title>Advanced Search</title>
<META http-equiv="Content-Type" content="text/html; charset=UTF-8">
<jsp:include page="../css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
</head>
<body>
<jsp:include page="../header.jsp"/>

<script src="js/jquery/jquery.js" type="text/javascript"></script>
<script src="js/muse.js" type="text/javascript"></script>
<script src="js/protovis.js" type="text/javascript"></script>

<p>
Sample searches:

<p>
Explore holidays and vacations <input type="text" size="40"/><br/>
examples:
	<a href="browse?term=christmas">Christmas</a>,
	<a href="browse?term=new year">New year</a>,
	<a href="browse?term=thanksgiving">Thanksgiving</a>,
	<a href="browse?term=halloween">Halloween</a>,
	<a href="browse?term=diwali">Diwali</a>,
	<a href="browse?term=carnival">Carnival</a>,
	<a href="browse?term=hannukah">Hannukah</a>,
	<a href="browse?term=oktoberfest">Oktoberfest</a>,
	<a href="browse?term=easter">Easter</a>,
	<a href="browse?term=holiday|holidays">holidays</a>
	<a href="browse?term=vacation|vacations">vacations</a>

<p>
Explore your hometown or a place you've lived in <input type="text" size="40"/><br/>
examples:
<a href="browse?term=san francisco">San Francisco</a>,
<a href="browse?term=new york">London</a>,
<a href="browse?term=Delhi">Delhi</a>

<% // wikipedia: emotion is a good source of emotions
%>
<p>
Explore feelings: <input type="text" size="40"/><br/>
examples:
	<a href="browse?term=love">love</a>,
	<a href="browse?term=happy|joy|happiness">happy</a>,
	<a href="browse?term=cry|crying|sad|saddened|sadness|pain|pained|sorrow">sad</a>,
	<a href="browse?term=worry|worried|anxiety|anxious|panic">worry</a>,
	<a href="browse?term=angry|anger|irritated|irritating|annoyed|annoying|hostile|hostility|hostilities">angry</a>,
	<a href="browse?term=hope|hopeful">hope</a>,
	<a href="browse?term=disappoint|disappointed|disappointment|disgust|disgusted|misery|miserable">disappointed</a>,
	<a href="browse?term=sorry|remorse|regret|regrets|apology|apologize|apologies">sorry</a>,
	<a href="browse?term=surprise|surprised|surprising">surprise</a>,
	<a href="browse?term=afraid">fear</a>,
	<a href="browse?term=anxious|anxiety">anxious</a>,
	<a href="browse?term=hate|loathe">hate</a>,
	<a href="browse?term=curious|curiosity">curious</a>,
	<a href="browse?term=grief|grieving|grieved|aggrieved|miserable|suffer|suffering">grief</a>,
	<a href="browse?term=desperate|frustrate|frustrated|frustrating|horror|horrified">despair</a>,
	<a href="browse?term=jealous">jealous</a>,
	<a href="browse?term=interest">interest</a>,
	<a href="browse?term=pity">pity</a>,
	<a href="browse?term=grateful|gratitude">grateful</a>,
	<a href="browse?term=embarass|embarrasment|embarassed">embarassed</a>,
	<a href="browse?term=guilt|guilty">guilt</a>,
	<a href="browse?term=sick">sick</a>,
	<a href="browse?term=pride|proud">pride</a>,
	<a href="browse?term=shame|ashamed|shameful">shame</a>,
	<a href="browse?term=wonder">wonder</a>,
	<a href="browse?term=wish|wish for">wish</a>,
	<a href="browse?term=congratulations">congratulations</a>,
	<a href="browse?term=memory|memories">memories</a>,
	<a href="browse?term=condolences">condolences</a>. if you want to be adventurous, look for <a href="browse?term=sex|sexy|lust|lusty|bawdy|raunchy">sex</a>, or <a href="browse?term=fuck|fucking|fucked">expletives</a>.

<p>
Explore leisure activities, such as a favorite sport or hobby: <input type="text" size="40"/><br/>
examples:
	<a href="browse?term=soccer">Soccer</a>,
	<a href="browse?term=football">Football</a>,
	<a href="browse?term=cricket">Cricket</a>,
	<a href="browse?term=giants">Giants</a>,
	<a href="browse?term=book|books">Books</a>,
	<a href="browse?term=movies">Movies</a>,
	<a href="browse?term=music">Music</a>

<p>
Explore events: <input type="text" size="40"/><br/>
examples:
	<a href="browse?term=party|parties">Parties</a>,
	<a href="browse?term=birthday|birthdays">birthdays</a>,
	<a href="browse?term=farewell|goodbye">farewells</a>,
	<a href="browse?term=concert|concerts">concerts</a>,
	<a href="browse?term=reunion">reunions</a>,
	<a href="browse?term=weekend|weekends">weekends</a>

<p>
Type in terms related to religion or politics: <input type="text" size="40"/><br/>
	<a href="browse?term=religion|god|sin|sinful|sinned">religion</a>,
	<a href="browse?term=democrat|democrats|democratic">democrats</a>,

<p>
Type in the name of a family member, a child or a close friend. <input type="text" size="40"/><br/>
example:	<a href="browse?term=rishabh">Rishabh</a>

<p>
Check the first message exchanged with person, or to mention a term <input type="text" size="40"/><br/>
example:
<a href="browse?term=Google">google</a>,
<a href="browse?term=facebook">facebook</a>,
<a href="browse?term=obama">obama</a>,

<p>
Type in a significant date: <input type="text" size="40"/><br/>
example: sep 11 2001, your anniversary or birthday.

<% // other ideas: proverbs.
%>
<jsp:include page="../footer.jsp"/>

</body>
</html>
