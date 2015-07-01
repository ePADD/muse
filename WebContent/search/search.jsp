<%@ page contentType="text/html;charset=UTF-8" language="java"%>
<%@page language="java" import="edu.stanford.muse.slant.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.apache.log4j.*"%>
<%@include file="getArchive.jspf" %>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<!--  <script src='http://mobisocial.stanford.edu/musemonkey/jquery.js'></script>
<script src='http://mobisocial.stanford.edu/musemonkey/jquery.rating.js'></script>
<link rel="stylesheet"
	href="http://mobisocial.stanford.edu/musemonkey/jquery.rating.css"
	media="screen" type="text/css" />
<script src='http://mobisocial.stanford.edu/musemonkey/util.js'></script>
<script src='http://mobisocial.stanford.edu/musemonkey/museutil.js'></script>

-->
<script src='jquery.js' ></script>
<script src='jquery.rating.js' ></script>
<link rel="stylesheet" href="jquery.rating.css" media="screen"
	type="text/css" />

<script src='util.js' ></script>
<script src='museutil.js' ></script>

<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
<title>Untitled Document</title>
<style type="text/css">
body {
	width: 1400px;
	margin: auto;
	font-family: Tahoma;
	font-size: 11px;
}

fieldset.search {
	border: none;
	width: 243px;
	margin: 0 auto;
	background: #222;
}

.search input,.search button {
	border: none;
	float: left;
}

.search input.box {
	color: #fff;
	font-size: 1.2em;
	width: 190px;
	height: 30px;
	padding: 8px 5px 0;
	background: #616161 url(search_bg.gif) no-repeat left top;
	margin-right: 5px;
}

.search input.box:focus {
	background: #616161 url(search_bg.gif) no-repeat left -38px;
	outline: none;
}

.search button.btn {
	width: 38px;
	height: 38px;
	cursor: pointer;
	text-indent: -9999px;
	background: #fbc900 url(search_bg.gif) no-repeat top right;
}

.search button.btn:hover {
	background: #fbc900 url(search_bg.gif) no-repeat bottom right;
}

/*#search {
	overflow: hidden;
	margin: 20px 0 10px;
	position: absolute;
	left: 660px;
	z-index: 100;
	width: 300px;
}*/
#google {
	overflow: hidden;
	margin: 20px 0 10px;
	position: absolute;
	left: 660px;
	z-index: 100;
	width: 300px;
}

/*#searchCustomTwitter {
	overflow: hidden;
	margin: 20px 0 10px;
	position: absolute;
	left: 340px;
	z-index: 100;
	width: 300px;
}*/

#twitter {
	overflow: hidden;
	margin: 20px 0 10px;
	position: absolute;
	left: 340px;
	z-index: 100;
	width: 300px;
}

/*#searchCustomEmail {
	overflow: hidden;
	margin: 20px 0 10px;
	position: absolute;
	left: 20px;
	z-index: 100;
	width: 300px;
}*/

#email {
	overflow: hidden;
	margin: 20px 0 10px;
	position: absolute;
	left: 20px;
	z-index: 100;
	width: 300px;
}

#searchSidebar {
	overflow: hidden;
	margin: 20px 0 10px;
	position: absolute;
	left: 980px;
	z-index: 100;
	width: 300px;
}

#toptweets {
	overflow: hidden;
	margin: 20px 0 10px;
	position: absolute;
	left: 1300px;
	z-index: 100;
	width: 300px;
}

#searchMuse,#searchCustomEmail,#searchCustomTwitter,#searchCustomTopTwitter,#searchDictionary,#searchWikipedia,#searchFlickr,#searchYouTube
	{
	margin-bottom: 20px
}

#searchMuse p,#search p,#searchCustomEmail p,#searchCustomTwitter p,#searchCustomTopTwitter p,#searchDictionary p,#searchWikipedia p,#searchFlickr p,#searchYoutTube p
	{
	margin-top: 5px;
	font-size: 0.9em;
	line-height: 1.3em;
	padding: 0;
}

#searchMuse a,#searchCustomEmail a,#search a,#searchCustomTwitter a,#searchCustomTopTwitter a,#searchDictionary a,#searchWikipedia a,#searchFlickr a,#searchYouTube a
	{
	font-size: 0.9em;
}

#res h2,#searchMuse h1,#search h1,#searchCustomEmail h1,#searchCustomTwitter h1,#searchCustomTopTwitter h1,#searchDictionary h1,#searchWikipedia h1,#searchFlickr h1,#searchYouTube h1
	{
	font-size: 1em;
	margin: 0 0 20px;
	background-color: #F0F7F9;
	border-top: 1px solid #6b90da;
	padding: 5px
}

#res h2 a,#searchMuse h1 a,#search h1 a,#searchCustomEmail h1 a,#searchCustomTwitter h1 a,#searchCustomTopTwitter h1 a,#searchDictionary h1 a,#searchWikipedia h1 a,#searchFlickr h1 a,#searchYouTube h1 a
	{
	text-decoration: none;
	font-weight: bold;
	color: #000;
}

/*#searchMuse h2,#searchDictionary h2,#searchWikipedia h2,#searchFlickr h2,#searchYouTube h2
	{
	font-size: 0.9em;
	color: #333;
	margin: 0;
	padding: 0;
}*/

.horizontal_dotted_line {
	border-bottom: 2px solid #80c080;
	width: 300px;
}

';
//
new rules for css hover 
.classic {
	padding: 0.8em 1em;
}

.custom {
	padding: 0.5em 0.8em 0.8em 2em;
}

* html a:hover {
	background: transparent;
}

.classic {
	background: #FFFFAA;
	border: 1px solid #FFAD33;
}

.tooltip {
	border-bottom: 1px dotted #000000;
	color: #000000;
	outline: none;
	cursor: help;
	text-decoration: none;
	position: relative;
}

.tooltip span {
	margin-left: -999em;
	position: absolute;
}

.tooltip:hover span {
	border-radius: 5px 5px;
	-moz-border-radius: 5px;
	-webkit-border-radius: 5px;
	box-shadow: 5px 5px 5px rgba(0, 0, 0, 0.1);
	-webkit-box-shadow: 5px 5px rgba(0, 0, 0, 0.1);
	-moz-box-shadow: 5px 5px rgba(0, 0, 0, 0.1);
	font-family: Calibri, Tahoma, Geneva, sans-serif;
	position: absolute;
	left: 1em;
	top: 2em;
	z-index: 99;
	margin-left: 0;
	width: 250px;
}

.tooltip:hover img {
	border: 0;
	margin: -10px 0 0 -55px;
	float: left;
	position: absolute;
}

.tooltip:hover em {
	font-family: Candara, Tahoma, Geneva, sans-serif;
	font-size: 1.2em;
	font-weight: bold;
	display: block;
	padding: 0.2em 0 0.6em 0;
}
</style>
<!--[if lte IE 7]>
<link rel="stylesheet" type="text/css" href="ie.css" />
<![endif]-->
</head>

<body>
	<h1
		style="text-align: center; color: #fff; font-family: Arial, Helvetica, sans-serif;">Go
		ahead and search away!</h1>

	<fieldset class="search">
		<input type="text" name="q" class="box" />
		<button class="btn" title="Submit Search">Search</button>
	</fieldset>
	<div id="ratingbutton" align="center">
		<input type="button" id="myButton" value="submit ratings"
			style="text-align: center; color: #fff; font-family: Arial, Helvetica, sans-serif;" />
	</div>
	
		
			
	<div id="google">
			Rate this Engine
				<input class="hover-star" type="radio" name="rating-1" value="1"
					title="Very poor" /> <input class="hover-star" type="radio"
					name="rating-1" value="2" title="Poor" /> <input class="hover-star"
					type="radio" name="rating-1" value="3" checked="checked" title="OK" />
				<input class="hover-star" type="radio" name="rating-1" value="4"
					title="Good" /> <input class="hover-star" type="radio"
					name="rating-1" value="5" title="Very Good" /> <span
					id="hover-test-google" style="margin: 0 0 0 20px;"></span>
					<br/>
					<br/>
			<div class="right" id="search">
			</div>
		

	</div>

	
	<div id="email">
	
			Rate this Engine
				<input class="hover-star" type="radio" name="rating-2" value="1"
					title="Very poor" /> <input class="hover-star" type="radio"
					name="rating-2" value="2" title="Poor" /> <input class="hover-star"
					type="radio" name="rating-2" value="3" checked="checked" title="OK" />
				<input class="hover-star" type="radio" name="rating-2" value="4"
					title="Good" /> <input class="hover-star" type="radio"
					name="rating-2" value="5" title="Very Good" /> <span
					id="hover-test-email" style="margin: 0 0 0 20px;"></span>
		    <br/>
					<br/>
			<div class="right" id="searchCustomEmail">
		   </div>

	</div>

	<div id="twitter">
	
			Rate this Engine
				<input class="hover-star" type="radio" name="rating-3" value="1"
					title="Very poor" /> <input class="hover-star" type="radio"
					name="rating-3" value="2" title="Poor" /> <input class="hover-star"
					type="radio" name="rating-3" value="3" checked="checked" title="OK" />
				<input class="hover-star" type="radio" name="rating-3" value="4"
					title="Good" /> <input class="hover-star" type="radio"
					name="rating-3" value="5" title="Very Good" /> <span
					id="hover-test-twitter" style="margin: 0 0 0 20px;"></span>
					<br/>
					<br/>
			<div class="right" id="searchCustomTwitter"></div>
		</div>

	<div class="right" id="searchSidebar">
		
			<div id="muse">
			Rate this Engine
				<input class="hover-star" type="radio" name="rating-4" value="1"
					title="Very poor" /> <input class="hover-star" type="radio"
					name="rating-4" value="2" title="Poor" /> <input class="hover-star"
					type="radio" name="rating-4" value="3" checked="checked" title="OK" />
				<input class="hover-star" type="radio" name="rating-4" value="4"
					title="Good" /> <input class="hover-star" type="radio"
					name="rating-4" value="5" title="Very Good" /> <span
					id="hover-test-muse" style="margin: 0 0 0 20px;"></span>
					<br/>
					<br/>
			</div>
		

		<div class="right" id="searchMuse"></div>
	</div>


    <div id="toptweets">
	
			Rate this Engine
				<input class="hover-star" type="radio" name="rating-5" value="1"
					title="Very poor" /> <input class="hover-star" type="radio"
					name="rating-5" value="2" title="Poor" /> <input class="hover-star"
					type="radio" name="rating-5" value="3" checked="checked" title="OK" />
				<input class="hover-star" type="radio" name="rating-5" value="4"
					title="Good" /> <input class="hover-star" type="radio"
					name="rating-5" value="5" title="Very Good" /> <span
					id="hover-test-email" style="margin: 0 0 0 20px;"></span>
		    <br/>
					<br/>
			<div class="right" id="searchCustomTopTwitter">
		   </div>

	</div>
	
	<script type="text/javascript">
		var musejson;
		var query;
		var myA;
		var userids = new Array();
		userids["vsearchlogin1"]= "011877436534446649362";
		userids["vsearchlogin2"]= "011102526199543163634";
		userids["vsearchlogin3"]= "016414092392085377230";
		userids["vsearchlogin4"]= "015412874178567528850";
		userids["vsearchlogin5"]= "012274386799538405616";
		userids["vsearchlogin6"]= "013299019507641768210";
		userids["vsearchlogin7"]= "017895617867299565084";
		userids["vsearchlogin8"]= "007909703992518424922";
		userids["vsearchlogin9"]= "012641203354281655660";
		userids["vsearchlogin10"]= "017749479736713225790";
		userids["musetestlogin1"]="011658128557668009249";
		userids["vsearchlogintoptweet"] = "005771262623611307196:zwodf9ugsau";
		<%
		String str;
		try{
		String userHome = System.getProperty("user.home"); 
		String file = userHome + File.separator + "tmp" + File.separator + "cseconfig.txt";
	    BufferedReader in_user = new BufferedReader(new FileReader(file));
	    
	    str = in_user.readLine();
		}
		catch(Exception e)
		{
			str="musetestlogin1";
		}
		%>
		var username = "<%=str%>";
		//alert(username);
		var engineid=userids[username];
		unsafeWindow_url = new Array();
		unsafeWindow_url_weight = new Array();
		unsafeWindow_url_html = new Array();
		unsafeWindow_url_track = 0;
		unsafeWindow_url_title = new Array();
		unsafeWindow_entry_weight = new Array();
		var unsafeWindow_myA;
		unsafeWindow_muse_running = 0;
		var weight_of_hundred = 1;
		var upperbound = 1;
		var lowerbound = 1;
		var sidebar_width = 400;
		
		var googlelikes=0;
		var twitterlikes=0;
		var toptwitterlikes=0;
		var peoplelikes=0;
		var emaillikes=0;
		function hideperson(divid)  
		{ 	
			//alert(jQuery(divid).html());
		    console.log("dismisssss" + $(divid).html());
		}
		
		function checkemail()
		{
			//alert("email");
			emaillikes++;
		}
		
		function checktop()
		{
			//alert("top");
			toptwitterlikes++
		}
		
		
		
		function checkgoogle()
		{
			googlelikes++;
		}
		
		function checkpeople()
		{
			peoplelikes++;
		}
		
		function checktwitter()
		{
			twitterlikes++;
		}
		$(document)
				.ready(
						function() {

							$('#myButton').click(function(){ 
		                        //location.href='/Tag/ List?page=1'; 
		                        //alert("clicked");
		                       
								 $('input').each(function(){
									   if(this.checked)
										   {
										     //alert(this.name+': '+this.value+ ":"+ $(this).parent().attr("id"));
										   	var url = "uploadrating.jsp?query="+query+"&rating="+this.value+"&searchengineid="+$(this).parent().attr("id");
											$.get(url, function(result) {
												//  $("#search").html(result);
												
											});
										    
										   }
											});
									
								
								 var url = "uploadlinkrating.jsp?query="+query+"&googlelikes="+googlelikes+"&twitterlikes="+twitterlikes+"&peoplelikes="+peoplelikes+"&toptwitterlikes="+toptwitterlikes+"&emaillikes="+emaillikes;
									$.get(url, function(result) {
										//  $("#search").html(result);
										
									});
		                        
		                	}); 
						   $('#ratingbutton').attr("style", "display:none");
							$('#google').attr("style", "display:none");
							$('#muse').attr("style", "display:none");
							$('#email').attr("style", "display:none");
							$('#twitter').attr("style", "display:none");
							$('#toptweets').attr("style", "display:none");
							
							
							var callurl = "http://localhost:8080/muse/search/callsearch.jsp?url=";
							var url;
							$("input[@name='google']").click(function(){

								if ($(this).is(":checked"))
								{
								   alert("google clicked");

								}

								

							});
							
							
						});
		$(".btn")
				.click(
						function() {
							query = document.getElementsByName('q')[0].value
									.split(' ').join('+');
							
							document.getElementById('ratingbutton').style.display='block';
							document.getElementById('google').style.display='block';
							document.getElementById('muse').style.display='block';
							document.getElementById('email').style.display='block';
							document.getElementById('twitter').style.display='block';
							document.getElementById('toptweets').style.display='block';
							
							//clean up previous results if any
							jQuery("#searchCustomEmail").html(" ");
							jQuery("#searchCustomTwitter").html(" ");
							jQuery("#searchCustomTopTwitter").html(" ");
							jQuery("#search").html(" ");
							jQuery("#searchMuse").html(" ");
							
							
							var callurl = "http://localhost:8080/muse/search/callsearch.jsp?url=";
							var url;
							$.get("getjson.jsp", function(result) {
								//  $("#search").html(result);
								musejson=result;
							    insertMuse(musejson);
								
							});
							
							//get google results
							
							q = "http://www.google.com/search?q=" + query;
							q = encodeURIComponent(q);
							url = callurl + q;
							$.get(url, function(result) {
								//  $("#search").html(result);
								//alert(result);
								insertGoogle(result);
							});

							//fetch email links
							q = "http://www.google.com/cse?cx="+ engineid + "%3Amailengine&ie=UTF-8&q="
									+ query;
							q = encodeURIComponent(q);
							url = callurl + q;
							$.get(url, function(result) {
								//  $("#search").html(result);
								//alert(result);
								insertCSE_email(result);
							});

							//fetch twitter links

							q = "http://www.google.com/cse?cx="+ engineid + "%3Atestengine&ie=UTF-8&q="
									+ query;
							q = encodeURIComponent(q);
							url = callurl + q;
							$.get(url, function(result) {
								//  $("#search").html(result);
								//alert(result);
								insertCSE_twitter(result);
							});

							//fetch top tweet engine
							
							//fetch twitter links

							
							q = "http://www.google.com/cse?cx=005771262623611307196%3Atoptweetengine&ie=UTF-8&q="
									+ query;
							q = encodeURIComponent(q);
							url = callurl + q;
							$.get(url, function(result) {
								//  $("#search").html(result);
								insertCSE_toptwitter(result);
							});
							
							//display people search
							var imgstr = '';
							if(typeof(document.getElementById("spinner_display"))=="undefined"||document.getElementById("spinner_display")==null )
							{
								var spinnerdiv = document.createElement('div');
								spinnerdiv.id = 'spinner_display';
								imgstr += '<div id="spinner" style="text-align:center; position: absolute; top: 3pt; right:0; width: 160px"><img src="http://mobisocial.stanford.edu/musemonkey/wait30trans.gif"/></div></div>';

								spinnerdiv.innerHTML = imgstr;
							//	document.body.insertBefore(div, document.body.firstChild);
								var newhh1 = document
									.getElementById('searchSidebar');
								newhh1.appendChild(spinnerdiv);
							}
							var r = '';
							r += '<h1><a href="#" &search=Search target="_blank">Muse</a></h1>';
						                //r += '<select name="myRating" class="rating" id="serialStar2">  <option value="1">Very Poor</option><option value="2">Poor</option> <option value="3">Ok</option>  <option value="4">Good</option> <option value="5">Awesome</option> </select> ';
						    $("#searchMuse").append(r);
							setTimeout(display,4000); 
						     
							
							
							//display();
						});
	</script>

</body>
</html>
