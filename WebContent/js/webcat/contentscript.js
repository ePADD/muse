var $;



var head = document.getElementsByTagName('head')[0],
    style = document.createElement('style'),
    rules = document.createTextNode('.muse-navbar {padding-top: 3px; position: fixed; top: 0pt; right:10px;z-index:10000; text-transform:uppercase;font-family:"Gill Sans",Calibri,Helvetica,Arial,Times;font-size:10pt;font-weight:normal} \
				   .muse-navbar a span{-moz-border-radius: 4px; background-color: #0C3569; opacity: 0.9;} \
				 	.muse-navbar a span {color:white;font-size:14pt; font-weight:normal; padding: 5px 5px 5px 5px; text-decoration:none;} \
					.muse-navbar a span:hover {color:yellow; text-decoration:none;}');

style.type = 'text/css';
if(style.styleSheet)
    style.styleSheet.cssText = rules.nodeValue;
else style.appendChild(rules);
head.appendChild(style);


$("body").prepend('<div class="muse-navbar">  <a id="refresh_button" href="#"><span>Jog</span></a> ');


var b = document.getElementById('refresh_button');
        if (b != null)
    	    b.addEventListener("click", init, true);

function init() {
function GM_log (x) { LOG(x); }
function LOG(x) { console.log (x); }

if (typeof window.MUSE_URL == 'undefined')
	window.MUSE_URL = 'http://suif.stanford.edu/~hangal/jog';
if (typeof unsafeWindow === 'undefined') 
	unsafeWindow = window;



//inject button here




function inject_html(where_to_insert, html) {
	var div = document.createElement('div');
	div.innerHTML = html;
	$(where_to_insert).append($(div));
}

var URL_SPECS = new Array();
var maxURL_SPECS = 200;

// uniquify an array of url's
function uniquify(arr)
{
	var result = new Array();
	var hash = {};
	for (var i = 0; i < arr.length; i++)
		hash[arr[i].url] = arr[i];
	
	for (var x in hash)
	{
		if (!hash.hasOwnProperty(x))
			continue;
		result.push(hash[x]);
	}
	return result;
}

function main($, spec)
{
    LOG ('main called with spec' + spec.selector_for_links);

    var muse_status_style = 'position:fixed; width:100%;text-align:center;opacity:0.9;padding:10px; top:0px;left:0px; font-size:20px; color: #ffffff; background: none repeat scroll 0 0 rgb(204,101,134); z-index:100000002';
    // reads links from selected text
    function $getLinksFromSelection() {
        var getNextNode = function(node, skipChildren, endNode){
          //if there are child nodes and we didn't come from a child node
            if (endNode == node) { return null; }
            if (node.firstChild && !skipChildren) { return node.firstChild; }
            if (!node.parentNode){ return null; }
            return node.nextSibling || getNextNode(node.parentNode, true, endNode); 
        };

        var $stories = null;
        try {
        if (window.getSelection) {
            var sel = document.getSelection();
            LOG ('selected text = ' + sel);
            var range = sel.getRangeAt(0);
            var node = range.startContainer, endNode = range.endContainer;
            LOG ('start node = ' + node + ' end node = ' + endNode);
            while (node = getNextNode(node, false, endNode)) {
                // LOG ('node = ' + $(node).html()); 
                $x = $('a', $(node));
                if ($stories) {
                    $stories = $stories.add($x);
                } else {
                    $stories = $x;
                }
            }
            LOG ('#sel stories = ' + $stories.length);
            return $stories;
        } else {
            LOG ('selecting all links on page');
            return $('a');
        }
    
        LOG ('#sel stories = ' + $stories.length);
        } catch (e) { LOG ('exception trying to read selected text'); }
        return $stories;
    }

    function $filterMainArticle(spec, $response) {
        if (typeof spec.selector_for_article === 'undefined')
            return $response;

        $article = $(spec.selector_for_article, $response);
        if ($article.length == 0) {
            return $response; // throw ('No article');
        }

        if (spec.selectors_to_nuke)
            for (var i = 0; i < spec.selectors_to_nuke.length;i++)
                $(spec.selectors_to_nuke[i], $article).html('');

        var $div_scripts = $('script', $article);
        LOG ('removing ' + $div_scripts.length + ' scripts');
        $('script', $article).remove();
        return $article;
    }

    var $stories = $getLinksFromSelection();
    if ($stories == null && typeof spec.selector_for_links !== 'undefined')
	    $stories = $(spec.selector_for_links); // use default selector for site
	
	if ($stories == null || $stories.length == 0) {
        LOG ('no links selected with selector: ' + spec.selector_for_links);
		var message = 'Select some text on this page first.';
	    inject_html (document.body, '<div id="muse-status" style="' + muse_status_style + '">' + message + '</div>');
		window.setTimeout(function() { $('#muse-status').fadeOut('slow');}, 3000);
		return;
	}
	
	// get candidate urls
	LOG ('#stories = ' + $stories.length);
	$stories.each (function(i, elem) {
		var rel = $(this).attr('rel');
        if ( rel === 'tag' || rel === 'category' || rel == 'category tag') // ignore these, because they are usually not real articles
            return;
		var url = $(this).attr('href');
        var spec = {url:url, text: $(this).text()};
		URL_SPECS.push(spec);
        LOG ('url from page = ' + spec.url + ' text = ' + spec.text);
	});

    if (typeof spec.selector_for_placing_content === 'undefined')
        spec.selector_for_placing_content = 'body';

	var top_level_page = $(spec.selector_for_placing_content).html(); // save the original content first

    // inject muse-status After nuking prev. content in that space.
    $(spec.selector_for_placing_content).html('<br/>'); // nuke the prev. content
	var message = 'Reading articles ...';
	inject_html (document.body, '<div id="muse-status" style="' + muse_status_style + '">' + message + '</div>');
    inject_html (document.body, '<audio id="click_sound"> <source src="' + window.MUSE_URL + '/click.ogg" type="audio/ogg" /> <source src="' + window.MUSE_URL + '/click.mp3" type="audio/mpeg" /></audio>'); // no audio controls needed

	var where_to_insert = $(spec.selector_for_placing_content)[0];

	LOG ('Starting with ' + URL_SPECS.length + ' URL specs on page');

    // check the urls and massage a bit of necessary
	var newURL_SPECS = new Array();
    var banned_suffixes = ['.js', '.css', '.png', '.jpg', '.gif', '.jpeg', '.pdf', '.ps', '.doc', '.ppt', '.pptx', '.docx', '.xls', '.xlsx'];
	for (var i = 0; i < URL_SPECS.length; i++) {
		var url = URL_SPECS[i].url;
        // LOG ('url = ' + url);
		if (typeof url == 'undefined')
			continue;
		if (url.indexOf('http') == 0 || url.indexOf('//') == 0) { // if starts with http or https make same origin check
            if (url.indexOf(location.hostname) <= 0) { // simplified check for same origin. see http://stackoverflow.com/questions/9404793/check-if-same-origin-policy-applies for options.
                LOG ('dropping url ' + url + ' because of different origin from current hostname ' + location.hostname);
               // continue; // violates same origin
            }
        } else { // ok, same origin not an issue, but expand the url anyway
            //if (url.indexOf('/') != 0)
			   // continue;
            url = location.protocol + '//' + document.domain + url; // TOFIX: needs the path of the request too!!!
        }

	    // remove junk
        var banned = false;
        for (var k = 0; k < banned_suffixes.length; k++) {
            if (url.indexOf(banned_suffixes[k]) == url.length-banned_suffixes[k].length) { // endsWith banned suffix
                banned = true;
                break;
            }
        }

        if (banned) { 
            LOG ('dropping banned suffix ' + url);
			continue;
        }

		if (url.indexOf('adx_click') > 0)
			continue;
        if (url.indexOf('#') >= 0) {
            var idx = url.indexOf('#');
            url = url.substring (0, idx); // strip anchors
        }

        // nytimes special, but could also apply to other sites.
        url = url.replace(/\?hp.*/, '');
        url = url.replace(/\?ref=[A-Za-z]*&/, '?'); // strip out from ?ref=<section name>&...
        url = url.replace(/\?ref=[A-Za-z]*/, ''); // strip out from ?ref=<section name>
        url = url.replace(/\&ref=[A-Za-z]*/, ''); // strip out from &ref=<section name>...

        // add append_to_link if needed. currently used only to add pagewanted=all for nyt. 
        if (typeof spec.append_to_link !== 'undefined') {
            if (url.indexOf('?') >= 0) {
                url += '&' + spec.append_to_link; 
            } else {
                url += '?' + spec.append_to_link; 
            }
        }
		newURL_SPECS.push({url: url, text: URL_SPECS[i].text});
	}
	URL_SPECS = newURL_SPECS;
	URL_SPECS = uniquify(URL_SPECS);

	LOG ("after filtering, we have " + URL_SPECS.length + " unique URL specs");
    if (URL_SPECS.length == 0) {
	    $('#muse-status').html('No valid links...'); 
		window.setTimeout(function() { $('#muse-status').fadeOut('slow');}, 3000);
		return;
    }

	$('#muse-status').html('Reading ' + URL_SPECS.length + ' articles');
	var PAGES = new Array();
	var ARTICLES = new Array();
	var nCompleted = 0;
	if (maxURL_SPECS > URL_SPECS.length)
		maxURL_SPECS = URL_SPECS.length;

	inject_html (where_to_insert, '<div id="jog_contents" style="min-height:600px;width:800px;padding:0px 30px;margin:0px 40px; font-size:12px;"></div>  <div id="muse-ZZZ_pages"></div> ');

//	$('body').append(insert);
    function all_links_fetched() { 
        // inject section and document beginning (sections and documents go together)
		var all_pages_html = '<div class="muse-ZZZ_section" name="section1"> <div class="muse-ZZZ_document">';
        var count = 0; // count of #pages we actually have
       /* {
            var help = '<div>Click to display or dismiss the jog dial. Rotate it to move between pages. <a style="color:white;text-decoration:underline;" target="_new" href="http://suif.stanford.edu/~hangal/jog#help">More Help</a>';
		    $('#muse-status').html(help); 
   		    var div = '<div><div class="muse-ZZZ_page" pageId="' + count + '" style="display:none" ><div style="margin-left:-60px">' + top_level_page + '</div></div></div>';
   		    var $div = $(div);
		    all_pages_html += $div.html();
            LOG ('after appending top page ' + count + ' ' + all_pages_html.length + ' chars');
            count++;
        }*/
        for (var k = 0; k < ARTICLES.length; k++)  {
            if (typeof ARTICLES[k] !== 'undefined' && ARTICLES[k] && ARTICLES[k].html()) {      
                var original_url = URL_SPECS[k].url;
                var original_text = URL_SPECS[k].text;
                var html = '<div style="color:#777;margin-top:50px;"><a href="' + original_url + '">Original link</a> ' + original_text + '<div><br/>';
                html += ARTICLES[k].html();
   			    var div = '<div><div class="muse-ZZZ_page" pageId="' + count + '" style="font-size:12px;display:none" >' + html + '</div></div>';
   			    var $div = $(div);
		        all_pages_html += $div.html();
                if (count % 10 == 9) {
		            all_pages_html += '</div></div><div class="muse-ZZZ_section"> <div class="muse-ZZZ_document">';
                    LOG ('#pages = ' + count + ' introducing a new section');
                }
                LOG ('logging page ' + count + ' k = ' + k + ' ' + html.length + ' chars');
                count++;
            } else {
                LOG ('missing article #' + k);
            }
        }
        all_pages_html += '</div></div>';// close the section and document div's
        $('#muse-ZZZ_pages').append(all_pages_html);
        LOG ('#pages injected = ' + count);
		LOG ('Completed fetching, starting jog');
		$(document).jog({
							 page_selector: '.muse-ZZZ_page',
						     document_selector: '.muse-ZZZ_document',
						     section_selector: '.muse-ZZZ_section',
						     sound_selector: '#click_sound',
						     jog_content_frame_selector: '#jog_contents',
							 logger: LOG,
							 width: 180,
							 reel_prefix  : window.MUSE_URL + '/images/sleekknob_line_',
						});
    }

	for (var i = 0; i < URL_SPECS.length; i++) {
		var url_spec = URL_SPECS[i];
		if (i < maxURL_SPECS) {
			LOG ('Issuing request for url#' + i + ':' + url_spec.url + ': ' + url_spec.text);
			$.ajax({
				url: url_spec.url, 
				success: (function(url, i) { return function(responseHTML) {
					nCompleted++;
					try { 
                        if (responseHTML.length > 0) {
						    LOG (responseHTML.length + ' chars in response for url#' + i + ':' + url);
    						$response = $(responseHTML);
                            var $article;
                            try { $article = ARTICLES[i] = $filterMainArticle(spec, $response); } 
                            catch (e) { $article = ARTICLES[i] = null; LOG ('WARNING: no article in response for url#' + i + ':' + url); }

                            // for craigslist etc, we need to put the response inside a div first
                            if (!$article || !$article.html() || $article.html().length == 0)
                            {
                                LOG ('funny, response.html().length straight from ajax response is 0');
    						    $response = $('<div>' + responseHTML + '</div>'); // don't do this directly, breaks nytimes site.
                                if ($response && $response.html())
                                    LOG ('funny, now response.html().length is ' + $response.html().length);
                                try { ARTICLES[i] = $filterMainArticle(spec, $response); } 
                                catch (e) { ARTICLES[i] = null; LOG ('WARNING: no article in response for url#' + i + ':' + url); }
                            }
                        } else {
                            LOG ('Empty response for url#' + i + ' ' + url);
                            ARTICLES[i] = null;
                        }
					} catch (e) { LOG ('error parsing response from url#' + i + ' ' + url + ':' + e);}
					LOG ('nCompleted = ' + nCompleted + ' maxURL_SPECS = ' + maxURL_SPECS);
					$('#muse-status').html('Read ' + nCompleted + '/' + maxURL_SPECS + ' articles');
					if (nCompleted == maxURL_SPECS) {
                        all_links_fetched();
					}
				};
			})(url_spec.url, i),
				error: (function(url, i) { return function() { 
					nCompleted++; 
					LOG ('Error response for url#' + i + ': ' + url);
					LOG ('nCompleted = ' + nCompleted + ' maxURL_SPECS = ' + maxURL_SPECS);
					ARTICLES[i] = null;
					$('#muse-status').html('Read ' + nCompleted + '/' + maxURL_SPECS + ' articles');
					if (nCompleted == maxURL_SPECS) {
                        all_links_fetched();
					}
					}; })(url_spec.url, i)
			});
	  }
	};
}

var nyt_spec = {
selector_for_links: '.story a, .storyHeader a, .storyFollowsLede a, .headlinesOnly a',
append_to_link: 'pagewanted=all',
selector_for_article: '#article',
selector_for_placing_content: '#main',
selectors_to_nuke: ['#articleToolsTop', '.articleInline .doubleRule']
};

var nytq_spec = {
selector_for_links: 'li.summary a',
append_to_link: 'pagewanted=all',
selector_for_article: '#article',
selector_for_placing_content: '#main',
selectors_to_nuke: ['#articleToolsTop', '.articleInline .doubleRule']
};


var twitter_spec = {
selector_for_links: '.js-tweet-text a',

};


var google_reader_spec = {
selector_for_links: '.entry-title a',
}

var cs_spec = { selector_for_links: '.inner td a'};
var cl_spec = { selector_for_links: '.row a, p a'};
var slashdot_spec = { selector_for_links: '.story a', selector_for_article: '#content', selectors_to_nuke: ['#slashboxes'] };
var galegroup_spec = { selector_for_links: '.imgN a', selector_for_article: '#docViewer', selector_for_placing_content: '#docViewer'};
var engadget_spec = { selector_for_links: '.post_title a'};
var searchworks_spec = { selector_for_links: '.index_title a'};
var deccanherald_spec = { selector_for_links: '.topBlock a', selector_for_article: '#main'};
var yelp_spec = { selector_for_links: '.itemheading a'};
var histdept_spec = { selector_for_links: '.view-people .views-field a'};
var wp_spec = { selector_for_links: '.wikitable a :not(.reference)'}; // anything in a table, but not references
wp_spec = { selector_for_links: '.wikitable a'}; // anything in a table, but not references
var baking_spec = { selector_for_links: '.wikitable a :not(.reference)'}; // anything in a table, but not references
baking_spec = { selector_for_links: '.post a', selector_for_article: '.post', selector_for_placing_content: '#content'}; // anything in a table, but not references
baking_spec = { selector_for_links: '.post a'};

if (document.URL.indexOf('query.nytimes.com') >= 0)
    main($,nytq_spec);
else if (document.URL.indexOf('nytimes.com') >= 0)
    main($,nyt_spec);
else if (document.URL.indexOf('craigslist.org') >= 0)
    main($,cl_spec);
else if (document.URL.indexOf('cs.stanford.edu') >= 0)
    main($,cs_spec);
else if (document.URL.indexOf('slashdot.org') >= 0) 
    main($,slashdot_spec);
else if (document.URL.indexOf('engadget.com') >= 0) 
    main($,engadget_spec);
else if (document.URL.indexOf('searchworks.com') >= 0) 
    main($,searchworks_spec);
else if (document.URL.indexOf('galegroup.com') >= 0) 
    main($,galegroup_spec);
else if (document.URL.indexOf('deccanherald.com') >= 0) 
    main($,deccanherald_spec);
else if (document.URL.indexOf('yelp.com') >= 0) 
    main($,yelp_spec);
else if (document.URL.indexOf('history.stanford.edu') >= 0) 
    main($,histdept_spec);
//else if (document.URL.indexOf('wikipedia.org') >= 0) 
//    main($,wp_spec);
else if (document.URL.indexOf('17andbaking.com') >= 0) 
    main($,baking_spec);
else if (document.URL.indexOf('twitter.com') >= 0) 
    main($,twitter_spec);
else if (document.URL.indexOf('google.com/reader') >= 0) 
    main($,google_reader_spec);
else if (document.URL.indexOf('everywhereist.com') >= 0) 
    main($,baking_spec); // same general blog spec
else 
    main($,{}); // same general blog spec
};

