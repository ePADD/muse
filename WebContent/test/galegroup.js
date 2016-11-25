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
var TITLES = new Array();

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
		if ($article.length > 0)
			result.push($article[0]);
	}
//	LOG ('returning filtered ' + result.length + ' out of ' + nodes.length);
	return result;
}

function appendToPage(html)
{
	var d = document.createElement('div');
	d.innerHTML = html;
	document.body.appendChild(d);	
}

var dump_obj = function (o, print_supertype_fields)
{
	if (typeof(o) === 'undefined')
		return 'undefined';
	if (typeof(print_supertype_fields) === 'undefined')
		print_supertype_fields = false;

	var functions = new Array();

	var s = 'typeof=' + typeof(o) + ' ';
	if (o.constructor.name) // often the constructor name is empty because it is an anonymous function; print it only if non-empty
		s += ' constructor=' + o.constructor.name + ' ';
	for (var f in o)
	{
		try {
			if (!print_supertype_fields && !o.hasOwnProperty(f)) // only print properties directly attached this object, not fields in its ancestors
				continue;
			if (typeof(o[f]) === 'function')
				functions.push (f); // just write out "function" for functions
			else
			{
				s += f + "=" + o[f] + ' '; // otherwise write out the value
			}
		} catch (e) {
			muse.log ('exception trying to dump object field ' + f + ':' + e);
		}
	}
	
	if (functions.length > 0)
		s += functions.length + ' function(s): {';
	for (var i = 0; i < functions.length; i++)
	{
		s += functions[i];
		if (i < functions.length-1)
			s += ' ';
	}
	if (functions.length > 0)
		s += '}';
	
	return s;
};
function performRelevantNavigation(base_url, page){
		// get the query params, after the first ?
		var idx = base_url.indexOf("?");
		var q = (idx >= 0) ? base_url.substring(idx+1, base_url.length-idx-1) : base_url;
			
		var urlParams = {};
		(function (q) {
		    var e,
		        a = /\+/g,  // Regex for replacing addition symbol with a space
		        r = /([^&=]+)=?([^&]*)/g,
		        d = function (s) { return decodeURIComponent(s.replace(a, " ")); };
		
		    while (e = r.exec(q))
		       urlParams[d(e[1])] = d(e[2]);
		})(q);
		LOG ('q = ' + q);
		LOG ('url params read is ' + dump_obj(urlParams));
				
		delete urlParams.action; 
	//	delete urlParams.searchType; delete urlParams.searchId; delete urlParams.currentPosition;
	//	delete urlParams.qrySerId; // delete urlParams.sort; delete urlParams.searchType;
	//	LOG ('page = ' + page);
	//	LOG ('clean page = ' + page);
		urlParams.pageIndex = page; // urlParams.pageNumber = page; 
		urlParams.now = new Date().getTime();
		urlParams.scale='0.33';
		urlParams.sort='Relevance';
		urlParams.docLevel='FASCIMILE';
		urlParams.sgHitCountType = 'None';
		urlParams.prodId = 'ECCO';
		urlParams.retrieveFormat='MULTIPAGE_DOCUMENT';
		urlParams.contentSet='ECCOArticles';
		return urlParams;
}

function main()
{
	// remove leading whitespace
	var trim = function(s) {
		if (typeof (s) !== 'string')
			return s;

		while (true) {
			if (s.length == 0)
				break;
			var c = s.charAt(0);
			if (c !== '\n' && c !== '\t' && c !== ' ')
				break;
			s = s.substring(1);
		}
		return s;
	};
	
	var content = 'Reading search results...';
	inject_html ('<div id="muse-status" style="position:fixed; opacity:0.8;padding:5px; top:10px;left:10px; width:150px; height:50px; font-size:12px; color: #ffffff; background: none repeat scroll 0 0 #0C3569; box-shadow: 2px 2px 3px #000000; border-radius:4px; background-color: border: 1px solid white; z-index:100000002">' + content + '</div>');
	if ($_('.muse-rearranged').length > 0)
	{
		$_('#muse-status').html('Already rearranged!');
		window.setTimeout(function() { $_('#muse-status').fadeOut('slow');}, 3000);
		return;
	}
	
	// get candidate urls
	var $books = $_('.pub_details .pic_Title a');
	LOG ('#hits = ' + $books.length);
	$books.each (function(i, elem) {
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
//		if (url.indexOf('http') != 0)
//			continue;
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
		
	function fetchURLs(URLS, maxURLS, callback) {
		if (typeof URLS == 'undefined' || typeof maxURLS == 'undefined') {
			callback.call (this, '', '', false, -1, 0, 0);
			return;
		}
		
		if (maxURLS > URLS.length)
			maxURLS = URLS.length;
		LOG ('fetching ' + maxURLS + ' URLs');
		var status = new Object();
		status.nCompleted = 0; status.maxURLS = maxURLS;
		
		for (var i = 0; i < maxURLS && i < URLS.length; i++) {
			var url = URLS[i];
			
			LOG ('Issuing request for url#' + i + ':' + url);
			$_.ajax({
				url: url, 
				success: (function(url, i, status) { return function(responseHTML) {
					status.nCompleted++;
					LOG ('Received success reponse for url#' + i + ':' + url + ' = ' + responseHTML.length + ' chars');
					callback.call(this, url, responseHTML, true, i, status.nCompleted, status.maxURLS);
				    };
			      })(url, i, status),
				error: (function(url, i, status) { return function() { 
					status.nCompleted++; 
					LOG ('Received error reponse for url#' + i + ':' + url);
					callback.call(this, url, responseHTML, false, i, status.nCompleted, status.maxURLS);
				    }; 
				  })(url,i, status)
			 });
		} // for
	} // fetchURLs

	var PAGE_URLS = [];
	var SEARCH_RESULT_PAGE = [];
	
	function callback(url, responseHTML, success, idx, nCompleted, maxURLS) {
		if (typeof PAGE_URLS[idx] == 'undefined')
			PAGE_URLS[idx] = [];
		
		if (success) {
			$page = $_('<div>' + responseHTML + '</div');
			var $content = $_('#contentcontainer', $page);
			$('#muse-' + idx).append ('<div style="float:left">' + $content.html() + '</div>');
			SEARCH_RESULT_PAGE[idx] = $content.html();
			var $pages = $_('#stwTraverser-0 .imgN ul li', $content);
			LOG ('content len = ' + $content.length + ' ' + $pages.length + ' hits ');
			
			$pages.each(function(i, elem) { 
				var pagenum = trim($_(this).text());
				pagenum = pagenum.replace(/\n.*/,''); // delete everything after %
				var page_url_params = performRelevantNavigation(url, pagenum);
				var page_url = 'retrieve.do?' + $_.param(page_url_params);
				LOG ('pushing url ' + page_url + ' to idx ' + idx);
				PAGE_URLS[idx].push(page_url);
				$('#muse-' + idx).append ('<div id="muse-' + idx + '-' + i + '" style="float:left;"><div style="font-size:24px;text-align:center;width:1000px;">LOADING PAGE ' + pagenum + '</div></div>'); // prepared divs for the pages
			});
		} else {
			// failure
			LOG ('error response for url ' + url);
		}

		LOG ('nCompleted = ' + nCompleted + ' maxURLS = ' + maxURLS);	
		$_('#muse-status').html('Read ' + nCompleted + '/' + maxURLS + ' search hits');
		if (nCompleted == maxURLS) {
			$_('#muse-status').html('Fetching individual pages');
			// fetch indiv. pages now for each of the books
			LOG (PAGE_URLS.length + " books");
			for (var i = 0; i < PAGE_URLS.length; i++)
				fetchURLs(PAGE_URLS[i], 100, get_callback_page_fn(i));
		}
	}
	
	function get_callback_page_fn (search_result_idx)
	{
		return function (url, responseHTML, success, idx, nCompleted, maxURLS) {
			$_('#muse-status').html('Read ' + nCompleted + '/' + maxURLS + ' pages for search result ' + search_result_idx);

			if (success) {
				$page = $_('<div>' + responseHTML + '</div');
				$wanted = $_('#contentcontainer', $page);
				LOG ($page.html().length);
				$('#muse-' + search_result_idx + '-' + idx).html ($wanted.html());
			}
			else 
				LOG ("url " + url + " failed!");
		};
	}

	LOG (URLS.length + ' pages to fetch');
	for (i = 0; i < URLS.length; i++) {
		appendToPage('<hr style="color: green;"/>');
		appendToPage('<div style="margin:100px; width:20000px" id="muse-' + i + '"> <span style="padding-left:30px;text-transform:uppercase;font-size:20px;font-family:Gill Sans, Arial">Search Result ' + i + ' (<a style="text-transform:uppercase;font-size:20px;font-family:Gill Sans, Arial" href="' + URLS[i] + '">ORIGINAL</a>)<br/></div><div style="clear:both"></div></div>');
	}
	fetchURLs(URLS, 20, callback);
}

inject_jquery();
})();



