



// nifty function to dynamically inject <style> into <head>
function add_style_tag(rules) {
    var head = document.getElementsByTagName('head')[0],
        style = document.createElement('style'),
        rulenode = document.createTextNode(rules);
        
    style.type = 'text/css';
    if(style.styleSheet)
        style.styleSheet.cssText = rulenode.nodeValue;
    else style.appendChild(rulenode);
    head.appendChild(style);
}

// Insert the results from Wiktionary
function insertDictionary(data){

	if (word = data.term) {
		var r = "";
		r += '<h1><a href="http://en.wiktionary.org/wiki/' + word + '" target="_blank">Muse Wiktionary</a></h1>';
		
		// Loop through the meanings
		jQuery.each(data.meanings, function(i, meaning) {
			r += '<span style="display:block; float:left; font-weight:bold; width:28px">' + (i+1) + '.</span>'; 
			r += '<div style="padding-left:37px; padding-bottom:5px">' + meaning.content.replace(/(<([^>]+)>)/ig,"") + '</div>';
			// Stop at 3 definitions to save space
			if (i==(sidebar_definitions-1)) return false;
		});
		r += '<div style="clear:both;"/>';
		jQuery("#searchDictionary").append(r);
	
	}

}

// Insert the results from Wikipedia
function insertWikipedia(data){

	if (result = data.ResultSet.Result[0]) {
		if (!result.Title.match(/^.+:/)) {
			var r = '';
			r += '<h1><a href="' + result.Url + '" target="_blank">Muse Wikipedia</a></h1>';
			r += '<img style="float: left; width: 80px; height: 92px; padding:10px" src="http://en.wikipedia.org/images/wiki-en.png">'
			r += '<h2>' + result.Title.replace(/ - Wikipedia, the free encyclopedia$/,'').replace(/&amp;amp;/g,'&amp;') + '</h2>';
			r += '<p>' + result.Summary.replace(/&amp;amp;/g, '&amp;') + '</p>';
			r += '<a target="_blank" href="' + result.Url + '">View Entry</a>';
			
			r += '<div style="clear:both;"/>';
			jQuery("#searchWikipedia").append(r);
		}
	}
	  
}

// Insert the results from Flickr
function insertFlickr(data){

	if(data.photos.total > 0) {
		var r = '';
		r += '<h1><a href="http://www.flickr.com/search/?q=' + q + '&m=text" target="_blank">Muse Flickr</a></h1>';
		jQuery.each(data.photos.photo, function(i, photo) {
			photo_url = 'http://farm' + photo.farm + '.static.flickr.com/' + photo.server + '/' + photo.id + '_' + photo.secret;
			flickr_url = "http://www.flickr.com/photos/" + photo.owner + "/" + photo.id;
			r += '<a href="' + flickr_url + '" target="_blank">';
			r += '<img src="' + photo_url + '_s.jpg" style="float:left; margin-right:3px; margin-bottom:3px; border-style:none"/>';
			r += '</a>';
			if (i==(sidebar_images-1)) return false;
		});
		
		r += '<div style="clear:both;"/>';
		jQuery("#searchFlickr").append(r);
	}

}

// Insert the results from YouTube
function insertYouTube(data){

	if (data.feed.openSearch$totalResults.$t > 0) {
	
		var r = '';
		r += '<h1><a href="http://www.youtube.com/results?search_query=' + q + '&search=Search" target="_blank">YouTube</a></h1>';
		
		jQuery.each(data.feed.entry, function(i, entry) {
		
			thumb_url = entry.media$group.media$thumbnail[0].url;
			video_url = entry.media$group.media$player[0].url;
			description = entry.media$group.media$description.$t;
			title = entry.media$group.media$title.$t;
			if (description.length > sidebar_video_description) {
				description = description.substring(0,sidebar_video_description);
				description += ' ...';
			}
			
			r += '<a href="' + video_url + '" target="_blank">';
			r += '<img src="' + thumb_url + '" style="float:left; margin-bottom:5px; width:98px; height:73px; border-style:none"/>';
			r += '</a>';
			
			r += '<h2 style="float:right; width:' + (sidebar_width-110) + 'px">'
			r += '<a href="' + video_url + '" target="_blank">' + title + '</a>';
			r += '</h2>';
			
			r += '<p style="float:right; width:' + (sidebar_width-110) + 'px">' + description + '</p>';
			r += '<div style="clear:both;"/>';
			
			if (i==(sidebar_videos-1)) return false;
		});
		
		jQuery("#searchYouTube").append(r);
		
	}

}



function mySort() {
	//console_log('sorting results');

	for (var i = unsafeWindow_url.length - 1; i >= 0;  i--) {
		for (var j = 0; j <= i; j++) {
			if (unsafeWindow_url_weight[j+1] > unsafeWindow_url_weight[j]) {
				var tempValue = unsafeWindow_url_weight[j];
				unsafeWindow_url_weight[j] = unsafeWindow_url_weight[j+1];
				unsafeWindow_url_weight[j+1] = tempValue;
				var tempValueHTML = unsafeWindow_url_html[j];
				unsafeWindow_url_html[j] = unsafeWindow_url_html[j+1];
				unsafeWindow_url_html[j+1] = tempValueHTML;

				var tempValueTITLE = unsafeWindow_url_title[j];
				unsafeWindow_url_title[j] = unsafeWindow_url_title[j+1];
				unsafeWindow_url_title[j+1] = tempValueTITLE;

			}
		}
	}
	//console_log('sorting done ');
}

function display(){

	mySort() ;
        //alert("here length is"+ unsafeWindow_url_html.length );
        var r = '';
        for (var i = 0 ; i< unsafeWindow_url_html.length ;  i++)
        {
		var anchor = unsafeWindow_url_html[i] ;
              		
       		r += '<p style="float:right; width:' + (sidebar_width-110) + 'px">' + anchor + '</p>';
                


	} 
        jQuery("#searchMuse").append(r);


}


function displayMuse(data){

 var r = '';
 var count=0;
 
jQuery(data).find('.vsc').each(function(i, entry) { //loop though all vsc in the snippets list
     // alert(i);
      var anchor = jQuery(entry).html(); //get the actual link with the text inside
      //alert(entry);
     // alert(anchor);
      //alert("2"+entry.innerHTML);
     // var newParagraph = $('<p />').text(anchor);


      var  keyin=jQuery(entry).find('cite:first').html();
   
      var alltitles = new Array();

      jQuery(entry).find('em').each(function(j, highlight) { 
	  alltitles[j]=jQuery(highlight).html()

      });

      var titlestring="";
      var sum_of_weights=0;
      for(title=0;title<alltitles.length;title++)
	{
		if(unsafeWindow_entry_weight[(alltitles[title]+"").toLowerCase()]) 
			sum_of_weights=unsafeWindow_entry_weight[alltitles[title].toLowerCase()];
		if(titlestring.length==0||titlestring.indexOf(""+alltitles[title].innerHTML)==-1)
			titlestring+= alltitles[title]+ " and " ;
	}

      if(unsafeWindow_url.indexOf(keyin)<0)
      {
		unsafeWindow_url_html[unsafeWindow_url_track] = anchor;
		unsafeWindow_url_weight[unsafeWindow_url_track]=sum_of_weights; 								             			unsafeWindow_url_title[unsafeWindow_url_track]="This result has "+ titlestring + " weight is: "+ unsafeWindow_url_weight[unsafeWindow_url_track];		
		unsafeWindow_url[unsafeWindow_url_track]=keyin;		
		unsafeWindow_url_track++;

	}
	else
	{
		var prevresult = unsafeWindow_url.indexOf(keyin);
		unsafeWindow_url_weight[prevresult]+=sum_of_weights;
		unsafeWindow_url_title[prevresult]=unsafeWindow_url_title[prevresult].substring(0,unsafeWindow_url_title[prevresult].indexOf("weight is"));
		unsafeWindow_url_title[prevresult]+= titlestring + " weight is: "+ unsafeWindow_url_weight[prevresult];				

	}
     

        r += '<p style="float:right; width:' + (sidebar_width-110) + 'px">' + anchor + '</p>';
       if(i>4)
      	return false;
    
      
      
});
 // jQuery("#searchMuse").append(r);

}

// Insert the results from muse
function insertMuse(data){



/* Sort address book to find top 100 people as per messageOutCount*/
	data.addressBook.entries[0].messageOutCount=(data.addressBook.entries[0].messageOutCount+1)*100;
	data.addressBook.entries.sort(function(a,b) { 

		var b_penalty=b.messageOutCount;
		var a_penalty=a.messageOutCount;
		var firstnames;
		for(firstnames=0;firstnames<b.names.length;firstnames++)
			if(b.names[firstnames].length >5 && b.names[firstnames].indexOf(' ')!=-1) 
				if(typeof(unsafeWindow_myA)!="undefined" && unsafeWindow_myA && unsafeWindow_myA.length!=0)
					if(unsafeWindow_myA[b.names[firstnames].toLowerCase()])
						b_penalty=b.messageOutCount - unsafeWindow_myA[b.names[firstnames].toLowerCase()];

		for(firstnames=0;firstnames<a.names.length;firstnames++)
			if(a.names[firstnames].length >5 && a.names[firstnames].indexOf(' ')!=-1) 
				if(typeof(unsafeWindow_myA)!="undefined"&&unsafeWindow_myA && unsafeWindow_myA.length!=0)
					if(unsafeWindow_myA[a.names[firstnames].toLowerCase()])
						a_penalty=a.messageOutCount - unsafeWindow_myA[a.names[firstnames].toLowerCase()];

		return b_penalty - a_penalty;



		} );


        for (var entry=0;entry<data.addressBook.entries.length && entry <100;)
	{
		//new_query=' +'+ query + ' AND [ ';
		new_query=' +['+ query + '] AND [ ';
		for(var firstnames=0;firstnames<data.addressBook.entries[entry].names.length;firstnames++)
		{
			if(data.addressBook.entries[entry].names[firstnames].length >5 && data.addressBook.entries[entry].names[firstnames].indexOf(' ')!=-1)
			{
                                 //TODO factor in penalties once we have the click tracker in place
				/*unsafeWindow_entry_weight[data.addressBook.entries[entry].names[firstnames].toLowerCase()]=data.addressBook.entries[entry].messageOutCount - unsafeWindow_myA[data.addressBook.entries[entry].names[firstnames].toLowerCase()];*/

				unsafeWindow_entry_weight[data.addressBook.entries[entry].names[firstnames].toLowerCase()]=data.addressBook.entries[entry].messageOutCount;

				new_query+=' ["'+ data.addressBook.entries[entry].names[firstnames] + '"]';
				break;
			}
		}
		upperbound=data.addressBook.entries[entry].messageOutCount;
		for (var i=1;i<7;i++)
		{
			if(entry==data.addressBook.entries.length)
				break;
			var names_in_query=0;
			for(var names=0;names<data.addressBook.entries[entry+i].names.length&&names_in_query<2;names++)
			{
				if(data.addressBook.entries[entry+i].names[names].length >5 && data.addressBook.entries[entry+i].names[names].toLowerCase().indexOf(' ')!=-1)
				{
					unsafeWindow_entry_weight[data.addressBook.entries[entry+i].names[names].toLowerCase()]=data.addressBook.entries[entry+i].messageOutCount;
					new_query+=' OR ["'+ data.addressBook.entries[entry+i].names[names].toLowerCase() + '"]';
					names_in_query++;
				}

			}
		}
		lowerbound=data.addressBook.entries[entry+6].messageOutCount;
		entry+=7;
		new_query+=" ]";
                chrome.extension.sendRequest({'action' : 'fetchMuseresults', 'q': new_query}, displayMuse);

	}
         

	

}


// Build the sidebar
function buildSidebar(q) {

       // alert("building sidebar");
       /* var hidden_param = document.createElement('input');
	hidden_param.setAttribute('type', 'hidden');
	hidden_param.setAttribute('name', 'num');

	var rpp = 100;
	hidden_param.setAttribute('value', rpp);
	document.forms[0].appendChild(hidden_param);*/
	// Exclude a few pages such as Shopping, News and Realtime from running the script
	current_location = location.href;
	if (current_location.match(/\/images|tbs=(shop|nws|mbl)/)) {
		return;
	}

	// if #mbEnd already exists then remove it
	var mbEnd = document.getElementById("mbEnd");
	if (mbEnd) mbEnd.parentNode.removeChild(mbEnd);

	// if the Search Sidebar is already in the page (for a previous search) then remove it
	var oldSearchSidebar = document.getElementById("searchSidebar");
	if (oldSearchSidebar) oldSearchSidebar.parentNode.removeChild(oldSearchSidebar);
	
	// Create new div for complementary search results and append before #res
	var searchSidebar = document.createElement("div");
	searchSidebar.id = "searchSidebar";

	var searchDictionary = document.createElement("div");
	searchDictionary.id = "searchDictionary";

	var searchWikipedia = document.createElement("div");
	searchWikipedia.id = "searchWikipedia";

	var searchFlickr = document.createElement("div");
	searchFlickr.id = "searchFlickr";

	var searchYouTube = document.createElement("div");
	searchYouTube.id = "searchYouTube";

	var searchMuse = document.createElement("div");
	searchMuse.id = "searchMuse";

	var res = document.getElementById("res");
	res.parentNode.insertBefore(searchSidebar, res);

        
	// Override the overflow property of 'iur' element to avoid issues with inline images in search results
	var iur = document.getElementById("iur");
	if (iur) { iur.style.overflow = "visible"; }

	// Add the services' DIV
	for (i in favorite_services.services) {
		if (favorite_services.services[i].enabled == "true") {
			switch(favorite_services.services[i].name)
			{
			case "dictionary":
				searchSidebar.appendChild(searchDictionary);
				// Fire off the Dictionary request
				chrome.extension.sendRequest({'action' : 'fetchDictionary', 'q': q}, insertDictionary);
				break;
			case "wikipedia":
				searchSidebar.appendChild(searchWikipedia);
				// Fire off the Wikipedia request
				chrome.extension.sendRequest({'action' : 'fetchWikipedia', 'q': q}, insertWikipedia);
				break;
			case "flickr":
				searchSidebar.appendChild(searchFlickr);
				// Fire off the Flickr request
				chrome.extension.sendRequest({'action' : 'fetchFlickr', 'q': q}, insertFlickr);
				break;
			case "youtube":
				searchSidebar.appendChild(searchYouTube);
				// Fire off the YouTube request
				chrome.extension.sendRequest({'action' : 'fetchYouTube', 'q': q}, insertYouTube);
				break;

			case "muse":
				searchSidebar.appendChild(searchMuse);

                                var imgstr = '';
				var spinnerdiv = document.createElement('div');
				spinnerdiv.id = 'spinner_display';
				imgstr += '<div id="spinner" style="text-align:center; position: absolute; top: 3pt; right:0; width: 160px"><img src="http://mobisocial.stanford.edu/musemonkey/wait30trans.gif"/></div></div>';

				spinnerdiv.innerHTML = imgstr;
//	document.body.insertBefore(div, document.body.firstChild);
				var newhh1 = document.getElementById('searchSidebar');
				newhh1.appendChild(spinnerdiv);
				// Fire off the YouTube request
				chrome.extension.sendRequest({'action' : 'fetchMuse', 'q': q}, insertMuse);
				break;
			default:
			}
		}
	}
}

// Preferences
var favorite_size = "medium"
// Medium is the default: width of 400px, 3 definitions from Dictionary, 10 images from Flickr, 180 characters of description for YouTube results
var sidebar_width = 400;
var query='';
var sidebar_definitions = 3;
var sidebar_images = 10;
var sidebar_video_description = 180;
var favorite_services = {"services" : [
			{"name":"muse","description":"Muse","enabled":"true"},
			{"name":"dictionary","description":"Wiktionary","enabled":"true"},
			{"name":"wikipedia","description":"Wikipedia","enabled":"true"},
			{"name":"flickr","description":"Flickr","enabled":"true"},
			{"name":"youtube","description":"YouTube","enabled":"true"}	
  
		]
	};
var sidebar_videos = 4;

unsafeWindow_url=new Array();
unsafeWindow_url_weight=new Array();
unsafeWindow_url_html=new Array();
unsafeWindow_url_track=0;
unsafeWindow_url_title=new Array();
unsafeWindow_entry_weight=new Array();
unsafeWindow_muse_running=0;
unsafeWindow_weight_of_hundred=1; 
var upperbound=1;
var lowerbound=1;

// Grab the current search term
var q = document.getElementsByName('q')[0].value.split(' ').join('+');
query=q;

//remove ads if present
	if (jQuery('3po')) jQuery('3po').remove();
	if(jQuery('mbEnd')) jQuery('mbEnd').remove();
	if(jQuery('mbb10')) jQuery('mbb10').remove();
	if(jQuery('tads')) jQuery('tads').remove();


var count =0;
function doSomething() {

        if(count==3)
           display();
        if(count==5)
	document.getElementById('spinner').style.display='none';	
	
	count++;
        
	
}

setInterval(doSomething,2000);



	
// Fire off the request to get the preferences
chrome.extension.sendRequest({'action': 'fetchPreferences'},
     function(response)
     {
        favorite_size = response.favorite_size;
		switch(favorite_size)
		{
		case "small":
			// Small sidebar: width of 240px, 2 definitions from Dictionary, 6 images from Flickr, 120 characters of description for YouTube results
			sidebar_width = 240;
			sidebar_definitions = 2;
			sidebar_images = 6;
			sidebar_video_description = 120;
			break;
		case "large":
			// Large sidebar: width of 240px, 4 definitions from Dictionary, 14 images from Flickr, 280 characters of description for YouTube results
			sidebar_width = 560;
			sidebar_definitions = 4;
			sidebar_images = 14;
			sidebar_video_description = 280;
			break;
		default:
			// Medium is already the default	
		}
		
		if (response.favorite_videos) {
			sidebar_videos = eval(response.favorite_videos);
		}

		// Retrieve the JSON object storing the favorite services and their position
		if (response.favorite_services) {
			favorite_services = jQuery.parseJSON(response.favorite_services);
		}
		
		// Inject custom styles into header
		var rules = "";
		rules += '#cnt { max-width:100% }';
		rules += '#center_col { margin-right:0px }';
		rules += '#searchSidebar { overflow:hidden; margin:0 0 50px; position:absolute; right:10px; z-index: 100; width:' + sidebar_width + 'px; }';
		rules += '#searchMuse , #searchDictionary, #searchWikipedia, #searchFlickr, #searchYoutTube { margin-bottom:20px }';
		rules += '#searchMuse p, #searchDictionary p, #searchWikipedia p, #searchFlickr p, #searchYoutTube p { margin-top:5px; font-size:0.9em; line-height:1.3em; padding:0; }';
		rules += '#searchMuse a , #searchDictionary a, #searchWikipedia a, #searchFlickr a, #searchYouTube a { font-size:0.9em; }';
		rules += '#searchMuse h1, #searchDictionary h1, #searchWikipedia h1, #searchFlickr h1, #searchYouTube h1 { font-size:1em; margin:0 0 20px; background-color:#F0F7F9; border-top:1px solid #6b90da; padding:5px }';
		rules += '#searchMuse h1 a , #searchDictionary h1 a, #searchWikipedia h1 a, #searchFlickr h1 a, #searchYouTube h1 a { text-decoration:none; font-weight:bold; color:#000; }';
		rules += '#searchMuse h2, #searchDictionary h2, #searchWikipedia h2, #searchFlickr h2, #searchYouTube h2 { font-size:0.9em; color:#333; margin:0; padding:0; }';
                rules += '.horizontal_dotted_line{ border-bottom: 2px solid #80c080; width: 300px; } ';
		add_style_tag(rules);

		buildSidebar(q);
                var r = '';
		r += '<h1><a href="#" &search=Search" target="_blank">Muse</a></h1>';
        	jQuery("#searchMuse").append(r);
  
              //  display();

     });




	// Half satisfying workaround when Google Instant Search is on, detecting the ENTER key to execute the queries again
	document.onkeyup = function() {
		if (event.keyCode == 13) {
			buildSidebar(document.getElementsByName('q')[0].value.split(' ').join('+'));
		}
	}
