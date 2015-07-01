<%@ page contentType="text/html;charset=UTF-8" language="java"%>
<%@page language="java" import="edu.stanford.muse.slant.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.io.*"%>
<%@page language="java" import="org.apache.log4j.*"%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<script src='http://ajax.googleapis.com/ajax/libs/jquery/1.3.2/jquery.js'></script>
 <script src='jquery.rating.js'></script>

<script src='util.js'></script>
<script src='museutil.js'></script>
<link rel="stylesheet"
	href="http://mobisocial.stanford.edu/musemonkey/jquery.rating.css"
	media="screen" type="text/css" />
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
<title>Untitled Document</title>
<style type="text/css">
body {
	width: 1000px;
	margin: auto;
	font-family: Tahoma;
	font-size: 11px;
}
/*body {
	font: Arial, Helvetica, sans-serif normal 10px;
	background: #eee;
}*/
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


#searchCustomEmail {
	overflow: hidden;
	margin: 20px 0 10px;
	position: absolute;
	left: 20px;
	z-index: 100;
	width: 900px;

}



#searchiframe{
	overflow: hidden;
	margin: 20px 0 10px;
	position: absolute;
	left: 1700px;
	z-index: 100;
	width: 900px;
	display:none;
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

#searchMuse h2,#searchDictionary h2,#searchWikipedia h2,#searchFlickr h2,#searchYouTube h2
	{
	font-size: 0.9em;
	color: #333;
	margin: 0;
	padding: 0;
}

.horizontal_dotted_line {
	border-bottom: 2px solid #80c080;
	width: 900px;
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
	width: 850px;
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

/* Styles for my star rating , NOTE: the jquery one does not want to work
*/

.rate_widget {
            border:     1px solid #CCC;
            overflow:   visible;
            padding:    10px;
            position:   relative;
            width:      180px;
            height:     32px;
        }
        .ratings_stars {
            background: url('star_empty.png') no-repeat;
            float:      left;
            height:     28px;
            padding:    2px;
            width:      32px;
        }
        .ratings_vote {
            background: url('star_full.png') no-repeat;
        }
        .ratings_over {
            background: url('star_highlight.png') no-repeat;
        }
        .total_votes {
            background: #eaeaea;
            top: 58px;
            left: 0;
            padding: 5px;
            position:   absolute;  
        } 
        .movie_choice {
            font: 10px verdana, sans-serif;
            margin: 0 auto 40px auto;
            width: 180px;
        }
        h1 {
            text-align: center;
            width: 900px;
            margin: 20px auto;
        }
</style>
<!--[if lte IE 7]>
<link rel="stylesheet" type="text/css" href="ie.css" />
<![endif]-->
</head>

<body>
<%
String str=null;
            try{
            String userHome = System.getProperty("user.home"); 
			String file = userHome + File.separator + "tmp" + File.separator + "cseconfig.txt";
		    BufferedReader in_user = new BufferedReader(new FileReader(file));
		    
		    str = in_user.readLine();
            }
            catch(Exception e)
            {
            
            }
		    if(str==null)
		    	str="vsearchlogin1";
%>
		    
		    
	<h1
		style="text-align: center; color: #fff; font-family: Arial, Helvetica, sans-serif;">Go
		ahead and search away!</h1>

	<fieldset class="search">
		<input type="text" name="q" class="box" />
		<button class="btn" title="Submit Search">Search</button>
	</fieldset>

	
	<div class="right" id="searchCustomEmail"></div>
	<div class="right" id="searchiframe"></div>

	<script type="text/javascript">
	
	function callIframe(url, callback) {
	    $(document.body).append('<IFRAME id="myId" ...>');
	    $('iframe#myId').attr('src', url);

	    $('iframe#myId').load(function() 
	    {
	        callback(this);
	    });
	}
	    var cxidLogin="<%=str %>";
		var musejson;
		var query;
		var myA;
		var cxidArray = new Array();

		cxidArray [ "vsearchlogin1"]=011877436534446649362;
		cxidArray [ "vsearchlogin2"]=011102526199543163634;
		cxidArray [ "vsearchlogin3"]=016414092392085377230;
		cxidArray [ "vsearchlogin4"]=015412874178567528850;
		cxidArray [ "vsearchlogin5"]=012274386799538405616;
		cxidArray [ "vsearchlogin6"]=013299019507641768210;
		cxidArray [ "vsearchlogin7"]=017895617867299565084;
		cxidArray [ "vsearchlogin8"]=007909703992518424922;
		cxidArray [ "vsearchlogin9"]=012641203354281655660;
		cxidArray [ "vsearchlogin10"]=017749479736713225790;
		cxidArray [ "vsearchlogin10"]=017749479736713225790;
		cxidArray [ "vsearchlogintoptweet"]=005771262623611307196;
		cxidArray [ "musetestlogin2"]=011302047062929031823;
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
		var sidebar_width = 900;
		
		function initialize_rating_display()
		{
			
			 $('.ratings_stars').hover(
			            // Handles the mouseover
			            function() {
			                $(this).prevAll().andSelf().addClass('ratings_over');
			                $(this).nextAll().removeClass('ratings_vote'); 
			            },
			            // Handles the mouseout
			            function() {
			                $(this).prevAll().andSelf().removeClass('ratings_over');
			                // can't use 'this' because it wont contain the updated data
			                //set_votes($(this).parent());
			            }
			        );
			        
			        
			        // This actually records the vote
			        $('.ratings_stars').bind('click', function() {
			            var star = this;
			            var widget = $(this).parent();
			             $(this).prevAll().andSelf().addClass('ratings_vote');
			             $(this).nextAll().removeClass('ratings_vote'); 
			            var clicked_data = {
			                clicked_on : $(star).attr('class'),
			                widget_id : $(star).parent().attr('id')
			            };
			            ratingurl='uploadrating.jsp?searchengineid='+clicked_data.widget_id+'&rating='+clicked_data.clicked_on+'&query='+query;
			            $.get( ratingurl, function(result) {
							//  $("#search").html(result);
							
						});
			        });
		}
		function testing()
		{
			//alert( $("#postframe").contents().find('body').html());
			var r = '';
			 r += '<h1><a href="#" target="_blank">Custom search Twitter</a></h1>';
			 r += '<select name="myRating" class="rating" id="serialStar2">  <option value="1">Very Poor</option><option value="2">Poor</option> <option value="3">Ok</option>  <option value="4">Good</option> <option value="5">Awesome</option> </select> ';
			$("#postframe").contents().find('.gsc-table-result').each(function(j, result) { 
	        	//alert("found");
		  //customresults[j]=jQuery(highlight).html()
	          r += '<br/><div style="margin-top:25px; margin-bottom-15px;float:right; width:900px">'+ jQuery(result).html() + '</div>';

	     	 });
			jQuery("#searchCustomTwitter").append(r);
		}
		
		//Custom search Twitter,searchTwitter
		function appendresults(title , engineId)
		{
			//alert( $("#postframe").contents().find('body').html());
			var r = '';
			 r += '<h1><a href="#" target="_blank">'+ '</a></h1>';
			 r += '<div id="r_'+ engineId+'" class="rate_widget"><div class="star_1 ratings_stars"></div><div class="star_2 ratings_stars"></div><div class="star_3 ratings_stars"></div><div class="star_4 ratings_stars"></div><div class="star_5 ratings_stars"></div></div>';
		     
			 //r += '<select name="myRating" class="rating" id="serialStar2">  <option value="1">Very Poor</option><option value="2">Poor</option> <option value="3">Ok</option>  <option value="4">Good</option> <option value="5">Awesome</option> </select> ';
			$("#postframe"+engineId).contents().find('.gsc-table-result').each(function(j, result) { 
	        	//alert("found");
		  //customresults[j]=jQuery(highlight).html()
	         // r += '<br/><div style="float:right; width:' + (300) + 'px >'+ jQuery(result).html() + '</div>';
				 r += '<br/><div style="margin-top:25px; margin-bottom-15px;float:right; width:900px">'+ jQuery(result).html() + '</div>';

	     	 });
			jQuery("#"+engineId).append(r);
		}
		$(document)
				.ready(
						function() {

							//http://localhost:9099/muse/ajax/getGroupsAndAddressBookJson.jsp

							//Turn all the select boxes into rating controls
							$("#tmp").attr("disabled", "disabled");
							$(".rating").rating();
							
							var callurl = "http://localhost:8080/testproject/callsearch.jsp?url=";
							var url;
							initialize_rating_display();
							
							
							
						});
		$(".btn")
				.click(
						function() {
							query = document.getElementsByName('q')[0].value
									.split(' ').join('+');
							
														
							//clean up previous results if any
							jQuery("#searchCustomEmail").html("");
							jQuery("#searchTwitter").html("");
							jQuery("#search").html("");
							jQuery("#searchMuse").html("");
							
							
							var callurl = "http://localhost:8080/testproject/callsearch.jsp?url=";
							var url;
							/*$.get("getjson.jsp", function(result) {
								//  $("#search").html(result);
								musejson=result;
								//insertMuse(musejson);
								//alert(result);
								if(musejson!=null)
								 insertMuse(musejson);
							});*/
							//if(musejson!=null)
							  //insertMuse(musejson);
							//get google results
							
							q = "http://www.google.com/search?q=" + query;
							/*q = encodeURIComponent(q);
							url = callurl + q;*/
							url=q;
							$.get(url, function(result) {
								//  $("#search").html(result);
								insertGoogle(result);
							});

							//fetch email links
							
        				
							
							q = "http://www.google.com/cse?cx=001156696886027950495%3Amailengine&ie=UTF-8&q="
									+ query;
							
							var iframe = $( '<iframe name="postframesearchCustomEmail" id="postframesearchCustomEmail" class="hidden" src="'+q+'" />' );
							//$('#searchiframe').html(" ");
							$('#searchiframe').append( iframe );
							var closure = function() {  appendresults("Custom search Email" , "searchCustomEmail"); };
        					$('#postframesearchCustomEmail').load(function(){
        						setTimeout(closure,2000);
        				    });
							/*q = "http://www.google.com/cse?cx="+cxidArray[cxidLogin]+"%3Amailengine&ie=UTF-8&q="
									+ query;*/
							
							/*q = encodeURIComponent(q);
							url = callurl + q;
							url=q;
							$.get(url, function(result) {
								//  $("#search").html(result);
								insertCSE_email(result);
							});*/

							//fetch twitter links

							q = "http://www.google.com/cse?cx=001156696886027950495%3Atestengine&ie=UTF-8&q="
									+ query;
							
							iframe = $( '<iframe name="postframesearchCustomTwitter" id="postframesearchCustomTwitter" class="hidden" src="'+q+'" />' );
							//$('#searchiframe').html(" ");
							$('#searchiframe').append( iframe );
							closuretwitter = function() { appendresults("Custom search Twitter" , "searchCustomTwitter"); };
        					$('#postframesearchCustomTwitter').load(function(){
        						setTimeout(closuretwitter,2000);
        				    });
							

							//fetch top twitter - dont know where did this code go? some versioning ghost ate it
									
									

							q = "http://www.google.com/cse?cx=001156696886027950495%3Azwodf9ugsau&ie=UTF-8&q="
									+ query;
							
							iframe = $( '<iframe name="postframesearchCustomTopTwitter" id="postframesearchCustomTopTwitter" class="hidden" src="'+q+'" />' );
							//$('#searchiframe').html(" ");
							$('#searchiframe').append( iframe );
							closuretoptwitter = function() { appendresults("Custom search Top Twitter" , "searchCustomTopTwitter"); };
        					$('#postframesearchCustomTopTwitter').load(function(){
        						setTimeout(closuretoptwitter,2000);
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
							r += '<h1><a href="#" &search=Search target="_blank"></a></h1>';
						                //r += '<select name="myRating" class="rating" id="serialStar2">  <option value="1">Very Poor</option><option value="2">Poor</option> <option value="3">Ok</option>  <option value="4">Good</option> <option value="5">Awesome</option> </select> ';
						     r += '<div id="r_searchMuse" class="rate_widget"><div class="star_1 ratings_stars"></div><div class="star_2 ratings_stars"></div><div class="star_3 ratings_stars"></div><div class="star_4 ratings_stars"></div><div class="star_5 ratings_stars"></div></div>';
		     
						    $("#searchMuse").append(r);
							//setTimeout(display,4000); 
							setTimeout(initialize_rating_display,4000);
							//display();
						});
	</script>

</body>
</html>