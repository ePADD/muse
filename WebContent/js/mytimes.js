//==UserScript==
//@name news
//@include  http://www.nytimes.com
//==/UserScript==

(function() {
function GM_log (x) { LOG(x); }
function LOG(x) { console.log (x); }

if (typeof window.MUSE_URL == 'undefined')
	window.MUSE_URL = 'http://localhost:9099/muse';

//Add jQuery
var inject_jquery = function() {
	function wait_for_jquery_and_call_main(backoff) {
		if (typeof backoff == 'undefined') {
			backoff = 100;
		}
		if (typeof window.jQuery == 'undefined') {
			LOG ('waiting for jq');
			window.setTimeout(function() { return wait_for_jquery_and_call_main(backoff*2);}, backoff); 
		} else {
			// $ = window.jQuery.noConflict(true);
			GM_log ('jquery loaded');
			$_ = window.jQuery;// .noConflict(false);
			LOG ('window.jq = ' + window.jQuery);
			
			var head = document.getElementsByTagName('head')[0];
	        if (head == null)
	        	head = document.documentElement;
	        if (head == null)
	        	head = document.body;
	        // alert (GM_Head);

	        var GM_JQ = document.createElement('script');
//			GM_JQ.src = 'http://ajax.googleapis.com/ajax/libs/jquery/1/jquery.min.js';
			GM_JQ.src = window.MUSE_URL + '/js/jquery.quicksand.js';
			GM_JQ.type = 'text/javascript';
			GM_JQ.async = false;
			//head.insertBefore(GM_JQ, head.firstChild);
			//LOG ('injected quicksand');
			window.setTimeout(main,300);
		}
	}

	GM_log ("checking for jq...");

	if (typeof window.jQuery == 'undefined') {
		// we are injecting, so noConflict needed
		no_conflict_needed = true;
		LOG ("injecting jq");
		var GM_Head = document.getElementsByTagName('head')[0];
        if (GM_Head == null)
            GM_Head = document.documentElement;
        if (GM_Head == null)
            GM_Head = document.body;
        // alert (GM_Head);

        var jq = document.createElement('script');
        jq.src = window.MUSE_URL + '/js/jquery/jquery.js';
        jq.type = 'text/javascript';
        jq.async = true;
		GM_Head.insertBefore(jq, GM_Head.firstChild);
	}
	GM_log ("Waiting for jquery in URL:" + document.URL);
	wait_for_jquery_and_call_main(100);
};

function inject_html(html) {
	var div = document.createElement('div');
	div.innerHTML = html;
	document.body.insertBefore(div, document.body.firstChild);
}

var URLS = new Array();
var PAGETEXT = new Array();
var SCORES = new Array();
var maxURLS = 100;
var HITS = new Array();
var TITLES = new Array();
var DESC = new Array();

function uniquify(arr)
{
	var result = new Array();
	var hash = {};
	for (var i = 0; i < arr.length; i++)
		hash[arr[i]] = arr[i];
	
	for (var x in hash)
	{
		if (!hash.hasOwnProperty(x))
			continue;
		result.push(x);
	}
	return result;
}

function filterMainArticle(nodes)
{
	var result = new Array();
	
	for (var i = 0; i < nodes.length; i++)
	{
		$article = $_('#article', nodes[i]);
		$_('.articleInline', $article).html(''); // jam out inline part of article, which is not really the article at all
		if ($article.length > 0)
			result.push($article[0]);
	}
//	LOG ('returning filtered ' + result.length + ' out of ' + nodes.length);
	return result;
}

function main()
{
	var content = 'Reading articles <img src=' + window.MUSE_URL + '/images/spinner.gif/>';
	inject_html ('<div id="muse-status" style="position:fixed; opacity:0.8;padding:5px; top:10px;left:10px; width:150px; height:50px; font-size:12px; color: #ffffff; background: none repeat scroll 0 0 #0C3569; box-shadow: 2px 2px 3px #000000; border-radius:4px; background-color: border: 1px solid white; z-index:100000002">' + content + '</div>');
	if ($_('.muse-rearranged').length > 0)
	{
		$_('#muse-status').html('Already rearranged!');
		window.setTimeout(function() { $_('#muse-status').fadeOut('slow');}, 3000);
		return;
	}
	
	$stories = $_('.baseLayoutBelowFold .abColumn .headlinesOnly a');	
	
	if ($stories.length == 0) {
		$_('#muse-status').html('Run this on the NYTimes front page');
		window.setTimeout(function() { $_('#muse-status').fadeOut('slow');}, 3000);
		return;
	}
	
	// get candidate urls
	LOG ('#stories = ' + $stories.length);
	$stories.each (function(i, elem) {
		var url = $_(this).attr('href');
		URLS.push(url);
		TITLES[url] = $_(this).html();
	});
	
	URLS = uniquify(URLS);
	LOG ("starting with " + URLS.length + " unique URLs");
	
	// strip out junk urls
	var newURLS = new Array();
	for (var i = 0; i < URLS.length; i++) {
		var url = URLS[i];
		if (typeof url == 'undefined')
			continue;
		if (url.indexOf('http') != 0)
			continue;
		if (url.indexOf('.js') == url.length-3)
			continue;
		if (url.indexOf('.css') == url.length-4)
			continue;
		if (url.indexOf('adx_click') > 0)
			continue;
		newURLS.push(url);
	}
	URLS = newURLS;
	$_('muse-status').html('Reading ' + URLS.length + ' articles');
	var NODES = new Array();
	var nCompleted = 0;
	if (maxURLS > URLS.length)
		maxURLS = URLS.length;
	
	for (var i = 0; i < URLS.length; i++) {
		var url = URLS[i];
		if (i < maxURLS) {
			LOG ('Issuing request for url#' + i + ':' + url);
			$_.ajax({
				url: url, 
				success: (function(url, i) { return function(responseHTML) {
					nCompleted++;
					try { 
						NODES[i] = $html = $_(responseHTML);
						NODES[i] = filterMainArticle(NODES[i]);
						
						LOG ('got response for ' + url + ' ' + responseHTML.length + ' chars' + ' ' + NODES[i].length + ' nodes');
					} catch (e) { LOG ('error parsing response from ' + url + ':' + e);}
					LOG ('nCompleted = ' + nCompleted + ' maxURLS = ' + maxURLS);
					$_('#muse-status').html('Read ' + nCompleted + '/' + maxURLS + ' articles');
					if (nCompleted == maxURLS) 
						extractTextFromPages(NODES); 
				};
			})(url, i),
				error: (function(url, i) { return function() { 
					nCompleted++; 
					LOG ('error response for url ' + url);
					LOG ('nCompleted = ' + nCompleted + ' maxURLS = ' + maxURLS);
					NODES[i] = null;
					$_('#muse-status').html('Read ' + nCompleted + '/' + maxURLS + ' articles');
					if (nCompleted == maxURLS) 
						extractTextFromPages(NODES); 
					}; })(url, i)
			});
	  }
	};
}

function extractTextFromPages(NODES) {
	LOG ("all pages read, starting to extract page text!");
	// read text from each page's nodes
	for (var i = 0; i < NODES.length; i++)
	{
		var nodes = NODES[i];
		// nodes is an array of nodes
		if (nodes == null)
			continue; // some error, we didn't get the page
		var url = URLS[i];
		LOG ('# top level nodes for ' + url + ' = ' + nodes.length);
				
		try {
			// extract text from all nodes
			var arr = new Array();
			for (var j = 0; j < nodes.length; j++)
				addTextNodes(nodes[j], arr, true);
			
			// arr is a bunch of text nodes. string them together to form pagetext
			PAGETEXT[i] = '';
			for (var j = 0; j < arr.length; j++)
			{
				if (arr[j] == null)
					PAGETEXT[i] += '. '; 
				else
					PAGETEXT[i] += ' ' + arr[j].data;
			}
			// get rid of ampersands, they might interfere with the http req params
			PAGETEXT[i] = PAGETEXT[i].replace(/&/g, ' ');
			
			LOG ('# text nodes for ' + url + ' = ' + arr.length + ' page text length = ' + PAGETEXT[i].length);
		} catch (e) { LOG ('exception trying to read text from page ' + url + ': ' + e); }
	}
	
	// get leads from each pagetext.
	var nCompleted = 0;	
	for (var i = 0; i < PAGETEXT.length; i++)
	{
		if (PAGETEXT[i] == null) {
			nCompleted++;
			continue;
		}
		
		// look up muse for leads in this text. 
		// must send credentials to hook into the current Muse session
		$_.ajax(
			{url: window.MUSE_URL + '/ajax/leadsAsJson.jsp', 
			type: 'POST',
			data:  {refText: PAGETEXT[i]}, 
			beforeSend: function(xhr) { xhr.withCredentials = true;  },
			xhrFields: { withCredentials: true},
			crossDomain: true,
			success: (function(url) { return function(data) {
				nCompleted++;
				try {
					LOG ('received lookup response for ' + url + ' response length = ' + data.length + ' chars');
					 // strip out newlines at the beginning
	                if (data)
	                    while (data.indexOf("\n") == 0)
	                    	data = data.substring(1);
	                
					var hits = eval('(' + data + ')');
					var score  = 0.0;
					if (typeof hits.results != 'undefined' && hits.results) {
						HITS[url] = hits;
						LOG ('There are ' +  hits.results.length + ' hits for url ' + url);
						for (var j = 0; j < hits.results.length; j++) {
							score += hits.results[j].score;
						}
					}
					SCORES[url] = score;
				} catch (e) { LOG ('Exception reading ' + url + ': ' + e); }
				LOG ('final score for ' + url + " is " + SCORES[url] + ' completed = ' + nCompleted + ' urls = ' + maxURLS);
				$_('#muse-status').html('Scoring ' + nCompleted + '/' + maxURLS + ' articles');

				if (nCompleted == maxURLS)
					completedScoring();
			  }; })(URLS[i]),
			  error: function() { 
				  nCompleted++; 
				  LOG ("error in looking up leads!");
					$_('#muse-status').html('Scoring ' + nCompleted + '/' + maxURLS + ' articles');

				  if (nCompleted == maxURLS) 
					  completedScoring(); 
				} 
			}
		);
	}
}

function completedScoring()
{
	var DEBUG_INFO = false;
	
	$_('#muse-status').html('Rearranging articles...');
	LOG ("Completed scoring, now sorting pages that hit...");
	// retain only the URLS that have some score
	var newURLS = new Array();
	for (var i = 0; i < URLS.length; i++) {
		var url = URLS[i];
		
		if (!SCORES[url])
		{
		//	LOG ('continuing 1 for url ' + url);
			continue;
		}
		if (SCORES[url] === 0.0) {
		//	LOG ('continuing 2 for url ' + url);
			continue;
		}
		//LOG ('pushing URL ' + url);
		newURLS.push(url);
	}
	URLS = newURLS;
	LOG ("Starting sorting of " + URLS.length + " urls");

	// now sort
	URLS.sort (function(a, b) { return SCORES[b] - SCORES[a];});
	LOG ("Completed sorting of " + URLS.length + " urls");

	for (var i = 0; i < URLS.length; i++)
	{
		var url = URLS[i];
		var hit_results = HITS[url].results;
		if (!hit_results)
			continue;
		var desc = '';
		for (var j = 0; j < hit_results.length; j++) {
			if (hit_results[j].score == 0)
				continue;
			desc += hit_results[j].text + ' ';
			if (DEBUG_INFO)
				desc += '(P' + hit_results[j].timesOnPage + ' I' + hit_results[j].indexScore + ') ';
		}
		DESC[url] = desc;
		LOG (url + " score " + SCORES[url] + ' desc: ' + desc);
	}
	
	// get candidate urls
	$_('.baseLayoutBelowFold .moduleHeaderLg').fadeOut('slow');
	
	$stories = $_('.baseLayoutBelowFold .abColumn .headlinesOnly a');
	LOG ('#stories = ' + $stories.length);
	var numArticle = 0;
	$sorted_clones = new Array();
	$new_div = $_('div');
	$stories.each (function(i, elem) {

		if ($_('img', this).length > 0) // nuke the image
		{
			$_(this).fadeOut('fast');
//			$_(this).attr('href', '');
			return;
		}
		
		if (numArticle++ < URLS.length) {
			LOG ('replacing article ' + (numArticle-1));
			var url = URLS[numArticle-1];
			/*
			$clone = $_(this).clone(true);
			$clone.attr('href', url);
			$clone.html(numArticle + '. ' + TITLES[url]);
			$clone.attr('title', DESC[url]);
			$clone.attr('data-id', 'dest-slot' + (numArticle-1));
			$sorted_clones.push($clone);
			$new_div.append($clone);
			LOG ('clone: ' + $clone.html());
			*/
			$_(this).attr('href', url);
			$_(this).addClass('muse-rearranged');
			$_(this).html(TITLES[url]);
			$_(this).attr('title', DESC[url]);
			$_(this).after('<br/><span style="font-size:80%;font-weight:normal;opacity:0.8">[' + DESC[url] + ']</span>');
		}
	});
	$_('#muse-status').html('Done');

//	$new_div.css('display', 'none');
//	$_('body').append($new_div);
	
//	$_('.baseLayoutBelowFold .abColumn .headlinesOnly a').each(function (i, v) { $_(this).attr('data-id', 'slot' + i);});
//	LOG ("added data-id's, running quicksand with " + $sorted_clones.length + ' clones');
//	$_('.baseLayoutBelowFold .abColumn .headlinesOnly a').quicksand($sorted_clones);
	$_('#muse-status').fadeOut('slow');
}

var textNodesParsedWithoutBreak = 0;
//fills in 'arr', an array of text nodes, arr can be passed in as null by external caller.
//also fills in a null for div, header, li endings if insert_delimiters is true 
//caller should watch out for nulls in arr
function addTextNodes(node, arr, insert_delimiters) {
//	LOG ('node type is ' + node.nodeType);
	
     // do not use jquery inside this method, it may not have been loaded!
     if ((document.URL.indexOf('google') || document.URL.indexOf('gmail') >= 0) && node.id == 'gb')
             return null; // ignore google bar because it causes spurious hits w/the name of the user.
     if (document.URL.indexOf('facebook') >= 0 && node.id == 'pageNav')
             return null; // ignore google bar because it causes spurious hits w/the name of the user.

     if (textNodesParsedWithoutBreak > 10)
     {
             GM_log ('taking a break from finding text nodes');
             textNodesParsedWithoutBreak = 0;
             setTimeout (function() { addTextNodes(node, arr, insert_delimiters); }, 1); 
             return;
     }
     //   textNodesParsedWithoutBreak++;

     if (!arr)
         arr = new Array();
     if (node.nodeType == 7 || node.nodeType == 2) // comment node, attribute node
         return;

     /*
     try { 
             if (!$_(node).is(':visible'))
                     return;
     } catch(e) { }
*/
     var whitespace = /^\s*$/;
     if (node.className == 'muse-details')
             return; // we don't want to highlight our own nodes!
     if (typeof node.tagName !== 'undefined')
             if (node.tagName.toUpperCase() == 'SCRIPT' || node.tagName.toUpperCase() == 'NOSCRIPT')
                     return; // don't look inside scripts, and even noscripts

     if (node.nodeType == 3 && !whitespace.test(node.nodeValue))
     {
   // 	 LOG ('pushing');
             arr.push(node);
             if (arr.length % 100 == 0)
                     GM_log ("# text nodes: " + arr.length);
     }
     else 
     {
             for (var i = 0, len = node.childNodes.length; i < len; ++i)
                     addTextNodes(node.childNodes[i], arr, insert_delimiters);

             if (insert_delimiters && typeof node.tagName !== 'undefined')
             {
                     if (arr && arr.length > 0 && arr[arr.length-1] != null)
                     {
                             var lastText = arr[arr.length-1].data;

                             // see if the lastText ends with a sentence delimiter
                             if ('!?.'.indexOf(lastText[lastText.length-1]) < 0)
                             {
                                     var tag = node.tagName.toUpperCase();
                                     if (tag == 'H1' || tag == 'H2' || tag == 'H3' || tag == 'H4' || tag == 'H5' || tag == 'H6' || tag == 'DIV' || tag == 'P' || tag == 'LI' || tag == 'TD' || tag == 'B' || tag == 'I') // perhaps we should just check all tags that are not span.
                                     {
                                             // push an artificial stop after these tags.
                                             // GM_log ('Pushing a stop after tag ' + tag);
                                             arr.push(null);
                                             if (arr.length % 100 == 0)
                                                     GM_log ("# text nodes: " + arr.length);
                                     }
                             }
                     }
             }
     }
     return arr;
}

inject_jquery();
})();


