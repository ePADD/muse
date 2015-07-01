(function() {
var RUNNING_IN_PLUGIN = false;
var BG_COLOR = '#69AA35'; // '#0C3569'; // // 'rgb(6,217,255)';
function GM_log (x) { LOG(x); }
function LOG(x) { console.log (x); }

if (typeof window.BASE_URL == 'undefined')
	window.BASE_URL = 'http://suif.stanford.edu/~hangal/jog';
if (typeof window.MUSE_URL == 'undefined')
	window.MUSE_URL = 'http://localhost:9099/muse';
if (typeof unsafeWindow === 'undefined') 
	unsafeWindow = window;

var UPPERCASE = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';

//Add jQuery
var inject_jquery_and_call_main = function(spec) {
    if (RUNNING_IN_PLUGIN) { // no need to inject jq
        var $_ = $.noConflict(true);
        main($_, spec);
        return;
    }

    function wait_for_jquery_and_call_main(backoff) {

    var saved$ = null; // will save the original jq if page already has it

    function wait_for_jog_and_call_main() {
        if (typeof unsafeWindow.jQuery.fn.jog == 'undefined') {
            window.setTimeout(wait_for_jog_and_call_main, 100); // jq not loaded, try again in 100ms
        } else {
            GM_log ('ok, jog loaded up ');
            
            // now, make our version of jq invisible and restore the original version if it exists
            $_ = unsafeWindow.jQuery.noConflict(true); // $_ is our version of jq, regardless of whether page had its own.  
            if (saved$ != null) {
                GM_log ('restoring original version of jquery on page: ' + saved$().jquery);
                GM_log ('restoring original version of jquery on page');
                unsafeWindow.$ = unsafeWindow.jQuery = saved$; // restore saved
            } else {
                GM_log ('cool, no previous version of jquery on page');
            }
            unsafeWindow.setTimeout(function() { main($_, spec); },1000);
        }
    }

		if (typeof backoff == 'undefined') {
			backoff = 100;
		}
		if (typeof window.jQuery == 'undefined') {
            backoff *= 2;
			LOG ('waiting for jq with backoff ' + backoff);
			window.setTimeout(function() { return wait_for_jquery_and_call_main(backoff);}, backoff); 
		} else {
            // we have to install jog_plugin also now
            GM_log ('new jquery loaded, version is ' + unsafeWindow.jQuery().jquery + '. now injecting jog');
            function inject_jog() {
                var GM_Head = document.getElementsByTagName('head')[0] || document.documentElement || document.body;
                var GM_JQPP = document.createElement('script');
                GM_JQPP.src = window.BASE_URL + '/js/jog_plugin.js';
                GM_JQPP.type = 'text/javascript';
                GM_JQPP.async = true;
                GM_Head.insertBefore(GM_JQPP, GM_Head.lastChild);
            }

            // we have to wait now for jog also, because we want it to be associated with our version of jq
            $ = jQuery = unsafeWindow.jQuery;
            inject_jog();
            wait_for_jog_and_call_main();
		}
	}
    
	GM_log ("checking for jq...");

    function inject_jq_script() {
        GM_log ("injecting jq");
        var jq_scr = document.createElement('script');
        jq_scr.src = 'http://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js'; 
        jq_scr.type = 'text/javascript';
        jq_scr.async = true;
        var heads = document.getElementsByTagName('head');
        if (heads.length > 0)
            head = heads[0];
        else
            head = document.documentElement || document.body;
        head.insertBefore(jq_scr, head.firstChild);
    }

    inject_jq_script();
    
    if (typeof unsafeWindow.jQuery == 'undefined') {
        wait_for_jquery_and_call_main(100);
    } else {
        // see http://blog.nemikor.com/2009/10/03/using-multiple-versions-of-jquery/ for noconflict explanation
        saved$ = unsafeWindow.jQuery.noConflict(true); // first save away the original jq if page already has itself
        GM_log ('saving away original version of jquery on page: ' + saved$().jquery);
        wait_for_jquery_and_call_main(100);
    }
}; // end of inject_jquery_and_call_main

function inject_html($_, where_to_insert, html) {
	var div = document.createElement('div');
	div.innerHTML = html;
	$_(where_to_insert).append($_(div));
}

var LINKS = new Array();
var maxLINKS = 200;

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

/** selected jq links starting from given root */
function $getSelectedElements($_, root) {

LOG ('root is ' + root);
    var getNextNode = function(node, skipChildren, endNode){
      //if there are child nodes and we didn't come from a child node
        if (endNode == node) { return null; }
        if (node.firstChild && !skipChildren) { return node.firstChild; }
        if (!node.parentNode){ return null; }
        return node.nextSibling || getNextNode(node.parentNode, true, endNode); 
    };

    var $stories = null;
    try {
        var sel = root.getSelection();
        LOG ('selected text = ' + sel);
        var range = sel.getRangeAt(0);
        var node = range.startContainer, endNode = range.endContainer;
        // if these are text nodes, move them to the parent, cos we can't select any anchors with text nodes
        if (node.nodeType == 3) 
            node = node.parentNode;
//            if (endNode.nodeType == 3)
//                endNode = endNode.parentNode; 
        LOG ('selection start node = ' + node + ' end node = ' + endNode);
        while (node = getNextNode(node, false, endNode)) {
            var $x;
            // check if the node is itself an 'a' node, if so add $_(node) to $stories directly.. 
            // found this problem on xkcd, where the $_('a', $_(node)) doesn't work.
            if (node.nodeName.toLowerCase() == 'a') {
                $x = $_(node);
            } else {
                LOG ('node = ' + node + ' type = ' + node.nodeType + ' html: ' + $_(node).html()); 
                $x = $_('a', $_(node));
                LOG('# links found = ' + $x.length);
            }
            if ($stories) {
                $stories = $stories.add($x);
            } else {
                $stories = $x;
            }
        }
        LOG ('#sel stories = ' + $stories.length);
    } catch (e) { LOG ('exception trying to read selected text ' + e); }
    return $stories;
} 

function main($_, spec)
{
    LOG ('main called with spec selector_for_links' + spec.selector_for_links);
    window.b$ = $_; // just for debug
    var DISABLE_XDOMAIN = !RUNNING_IN_PLUGIN;
    var DROP_VIDEO_LINKS = true;
    var $stories = null;
    var textNodesParsedWithoutBreak = 0;
    
    var muse_status_container_style = 'position:fixed; left:0px; top:0px;width:100%; z-index:100000002;text-align:center;'; // -30 so we can animate it downward
    var muse_status_style = 'display:inline;padding:10px;background-color:' + BG_COLOR + ';color: #ffffff;';
    muse_status_style += 'font-family: \'Gill Sans\',\'Gill Sans MT\',Arial,sans-serif; font-size:15px; ';
    muse_status_style += 'border-radius:3px; border-bottom:solid 1px black; border-left: solid 1px black; border-right:solid 1px black;';
    muse_status_style += 'box-shadow: 1px 1px 1px #000; -webkit-box-shadow: 1px 1px 1px #000; -moz-box-shadow: 1px 1px 1px #000;';
    // reads links from selected text
    function $getLinksFromSelection() {
        if (window.getSelection) {
            $stories = $getSelectedElements($_, document);
            if (window.frames) {
                for (var f = 0; f < window.frames.length; f++)
                { 
                    try {
                        $frame_stories = $getSelectedElements($_, window.frames[f].document);
                        if ($stories == null)
                            $stories = $frame_stories;
                        else if ($frame_stories)
                            $stories.add($frame_stories);
                    } catch (e) { LOG ('exception trying to read frame ' + f + ': ' + e); }
                }
            }

            if (!$stories) {
                LOG ('selecting all links on page');
                return $_('a');
            }
        }
        return $stories;
    }

    unsafeWindow.HIT_CLICKED = function(e, hit_details) {
        if (e.shiftKey)
            return true;
        
        LOG ("showing messages in popup, hit_details = " + hit_details);
        // if hit_details.messages are available, just go with that
        if (typeof hit_details.messages != 'undefined' && hit_details.messages.length > 0)
        	open_popup(hit_details);
        else
        {
        	$_.ajax({
    			url: window.MUSE_URL + '/ajax/getHitTermDetails.jsp', 
    			type: 'POST',
    			data: 'term=' + hit_details.text +"&pageScore="+hit_details.pageScore, 
    			dataType: 'text',
    			beforeSend: function(xhr) { xhr.withCredentials = true;  },
    			xhrFields: { withCredentials: true},
    			crossDomain: true,
    			success: function(response) {
    			    var muse_response = trim(response);
    			    var new_details = null;
    				try { new_details = eval('(' + muse_response + ')'); } 
    				catch (e) { LOG('error in evaluating muse response for term details'); return; }
    				hit_details.messages = new_details.result.messages;
    				hit_details.nMessages = new_details.result.nMessages;
    		    	open_popup(hit_details);
    			},
    			error: function(jqXHR, textStatus, errorThrown) {
    				alert ('getting term details failed : '  + textStatus);
    				LOG(dump_obj(jqXHR));
    				LOG(errorThrown);
    			}
    		});
        }
        
        if (e.stopPropagation)
        {
        	LOG ('stopping event propagation');
        	e.stopPropagation();
        }
        if (e.preventDefault)
        {
    		LOG ('preventing event default');
        	e.preventDefault();
        }
        
        return false;
    };
    
    function decoratePage(hits, text_nodes) {
    	
    	if (typeof GM_addStyle === "undefined") {
    		function GM_addStyle(/* String */ styles) {
    			var oStyle = document.createElement("style");
    			oStyle.setAttribute("type", "text\/css");
    			oStyle.appendChild(document.createTextNode(styles));
    			document.getElementsByTagName("head")[0].appendChild(oStyle);
    		};
    	}

    	// highlight contents
    	// decoratedWithoutBreak is an internal counter counting how many nodes we've decorated since the last break ("timeout")
    	// after some number of calls, we take a break, to allow the browser to become more responsive.
    	var decoratedWithoutBreak = 0;

    	var inject_styles = function() {
    		styles =  '.muse-navbar {padding-top: 3px; position: fixed; top: 0pt; right:10px;z-index:10000; text-transform:uppercase;font-family:"Gill Sans",Calibri,Helvetica,Arial,Times;font-size:10pt;font-weight:normal} \
    				   .muse-navbar a span{-moz-border-radius: 4px; background-color: #0C3569; opacity: 0.9;} \
    				 	.muse-navbar a span {color:white;font-size:14pt; font-weight:normal; padding: 5px 5px 5px 5px; text-decoration:none;} \
    					.muse-navbar a span:hover {color:yellow; text-decoration:none;}';

    		styles += '#calloutparent {max-height:60px; line-height:20px; background: none repeat scroll 0 0 #0C3569; opacity: 0.8; color: #fff; box-shadow: 2px 2px 3px #000; padding-top: 5px; text-align:left;margin-left:2%;margin-right:2%;-moz-border-radius: 6px 6px 0px 0px;position: fixed; bottom: 0;left: 0;right: 0;z-index: 100000;width: 96%;border-top: 1px solid #fff;}';
    		styles += '.termMenu { background-color: black; -moz-border-radius: 6px 6px 0px 0px;z-index: 100000;border-top: 1px solid #fff; position:absolute;top:-22px;min-width:70px; border-top: 1px solid white; border-left: 1px solid white; border-right: 1px solid white;}';
    		styles += '#callout { float: left;text-align: left; padding: 0px 5px; height: width:100%; margin-bottom: 4px} \
    				  #callout li {background:transparent; display: inline;padding: 0 3px;} \
    				  #callout, #callout a, .term {color: white; text-transform:uppercase;font-family:"Gill Sans",Calibri,Helvetica,Arial,Times;font-size:10pt;font-weight:normal;} \
    				  .term:hover {text-decoration:underline}';

    		styles += '.muse-highlight { background-color: yellow; color: black; cursor:hand; cursor:pointer;} \
    				   .muse-soft-highlight {  background-color: lightyellow; color: black; cursor: hand;cursor:pointer;} \
    				   .muse-NER-name { border-bottom: 1px red dotted; }';
    		
    		styles+= '.musified {margin: 5px;padding: 5px;background: #D8D5D2; font-size: 11px;line-height: 1.4em;float: left;-webkit-border-radius: 5px;-moz-border-radius: 5px;border-radius: 5px; max-width:40%;} \
    				.musified_story {margin: 5px;padding: 5px;background: #D8D5D2; font-size: 11px;line-height: 1.4em;float: left;-webkit-border-radius: 5px;-moz-border-radius: 5px;border-radius: 5px; max-width:40%;}';
    		GM_addStyle(styles);
    	};
    	
    	inject_styles();
    	LOG ('inject styles');
    	
    	function decorateTextNode(node, hits, anchors_entered) {
    	    if (decoratedWithoutBreak > 100)
    	    {
    	         GM_log ('taking a break from decorating nodes');
    	         decoratedWithoutBreak = 0;
    	         window.setTimeout (function() { decorateTextNode(node, hits, anchors_entered); }, 1);
    	         return;
    	    }
    	    decoratedWithoutBreak++;

    		// node has to be a text node
    	    try {
    		// ignore whitespace
    		if (/^\s*$/.test(node.nodeValue))
    			return;

    		var newNodes = new Array(); // nodes we might create when we split this node
    	    var nodeText = node.data.toUpperCase();
    	    
    	    // sort results so that longer phrases are before shorter ones. 
    	    // ensures that superstrings are hilited in preference to substrings
    	    // e.g. Texas Rangers should be before Texas, so the whole phrase gets hilited
    	    // var hit_results = hits.results;
    	    //  hit_results.sort (function(a, b) { return b.text.length - a.text.length;});
    	    // check if this node contains any hits
    	    for (var hit = 0; hit < hits.results.length; hit++)
    	    {
    	        var pat = hits.results[hit].text.toUpperCase();
    	        if (hit > 160)
    	            continue; // FLAG DEBUG, ideally could bail out if its a long doc and score reaches 0. (hits are sorted by score)

    	        if (!node.parentNode)
    	            continue;
    	        if (node.parentNode.className == 'muse-NER-name' || node.parentNode.className == 'muse-highlight')
    	        	continue;
    	        if (document.URL.toUpperCase().indexOf(pat) >= 0) // if the term appears in the document's url, kill it... e.g. stanford on stanforddaily.com
    	            continue;

    	        var pos = nodeText.indexOf(pat);
    	        if (pos < 0)
    	            continue; // not found

    	        // skip if prev or next letters are alpha's, we want only complete words
    	        var prev_letter = '.', next_letter = '.'; // any non-alpha
    	        if (pos > 0)
    	            prev_letter = nodeText.charAt(pos-1);
    	        if (pos + pat.length < nodeText.length)
    	            next_letter = nodeText.charAt(pos + pat.length);
    	        if (UPPERCASE.indexOf(prev_letter) >= 0 || UPPERCASE.indexOf(next_letter) >= 0)
    	            continue;
    	        
    		    // ok, we have a proper name in this node
    		//    n_hilights++;
    		    
    		    // create a <span> decorator node for it -- preferable to a nodes 'cos it's less likely to have existing page styles associated with it.
    	        var anchor = null;
    	        var decorator = document.createElement('span');
    	        
    	        // var hilite_class = sketchy_hit(hits.results[hit]) ? "muse-soft-highlight":"muse-highlight";
    	        var hilite_class = "muse-highlight";
    	        
    	        // assign it muse-highlight or NER-highlight based on whether it was a real hit or not
    	        if (hits.results[hit].nMessages > 0)
    	        {
    	            if (hilite_class == 'muse-highlight')
    	            {
    	                var name = pat.replace (/ /g, '_') + "_anchor";
    	                name = name.toLowerCase(); // canonicalize
    	                // only enter an anchor for the first occurrence on a page
    	                if (typeof anchors_entered[name] == 'undefined' || !anchors_entered[name]) {
    		            	anchor = document.createElement('a');
    		            	anchor.setAttribute('name', name);
    		            	anchor.setAttribute('href', '#');
    		            	anchors_entered[name] = true;
    	                }
    	            }

    	            var people_str = '';
    	            var people = hits.results[hit].people;
    	            if (typeof people !== 'undefined')
    	            	for (var i = 0; i < people.length; i++)
    	            		people_str += people[i].person + ' ';
    		        decorator.setAttribute('title', 'Page score: ' + hits.results[hit].timesOnPage + ' Index score: ' + hits.results[hit].nMessages + ' for term: ' + hits.results[hit].text + '. People: ' + people_str); // set it otherwise "undefined" shows up in bottom left corner
    		        decorator.setAttribute('alt', 'ALT');
//    		        decorator.setAttribute('onclick', 'return SHOW_MESSAGES(event, ' + hit + ');');
    		        decorator.className = hilite_class;
    		        decorator.classList.add('nojog');
    	            decorator.addEventListener("click", function(h) { return function(e) { return HIT_CLICKED(e, h); }; }(hits.results[hit]));
    	        }
    	        else
    	        {
    		        decorator.className = 'muse-NER-name'; // not a real hit, for debug only
    	        }
    	        // make sure existing css rules on span's don't cause extra margins, padding, etc.
    	        // e.g. of site that causes trouble without this: the timeswire div on nytimes.com
    	        $_(decorator).css({display: 'inline', padding: '0px', margin: '0px', 'float': 'none'});

    	        if (pos > 0)
    	        {
    	        	// hit not at the beginning. 
    	        	 // update nodeText to the portion before the hit
    	        	var originalNodeLength = nodeText.length;
    	        	middlebit = node.splitText(pos);
    	        	endNode = middlebit.splitText(pat.length);
    		        var middleclone = middlebit.cloneNode(true);
    		        decorator.appendChild(middleclone);
    		        nodeText = node.data.toUpperCase();
    		        middlebit.parentNode.replaceChild(decorator, middlebit);
    	        	// add endNode to newnodes if it has any content, we'll process it later
    		        if (pos + pat.length < originalNodeLength)
    		        {
    		        	// GM_log ('pushing end node ' + endNode.nodeValue);
    		        	newNodes.push(endNode);
    		        }
    		        
		          	jparent=$_(node);
				    while(jparent[0].nodeType!=1)
				    	jparent=jparent.parent();
				    if(! jparent.is(":visible"))
				    {
				    	jparent=jparent.clone();
				    	$_("#hidden_content").append(jparent);
				    	jparent.show();
				    }
    	        }
    	        else
    	        {
    	        	// hit at the beginning. nodeText = remaining portion after this hit.
    	        	middlebit = node;
    	        	node = middlebit.splitText(pat.length);
    		        var middleclone = middlebit.cloneNode(true);
    		        decorator.appendChild(middleclone);
    		        middlebit.parentNode.replaceChild(decorator, middlebit);
    		        nodeText = node.data.toUpperCase();
    		       // var parentel= middlebit.parentNode;
		          	jparent=$_(node);
				    while(jparent[0].nodeType!=1)
				    	jparent=jparent.parent();
				    if(! jparent.is(":visible"))
				    {
				    	jparent=jparent.clone();
				    	$_("#hidden_content").append(jparent);
				    	jparent.show();
				    }
    	        }
    	        if (anchor != null)
    	        	decorator.parentNode.insertBefore(anchor, decorator);
    	      //  GM_log ('nodes twiddled, Node text is now: ' + node.data);
    		} // end for
    		    
//    		    if (newNodes.length > 0)
//    		    	GM_log (newNodes.length + " new nodes after decoration done");
    		    // this node done, handle any news nodes created on the way
    		    for (var x = 0; x < newNodes.length; x++)
    		    	decorateTextNode(newNodes[x], hits, decoratedWithoutBreak, anchors_entered); 
    	    } catch(e) { GM_log("Exception decorating text node: " + e); }
    	}
    	
    	GM_log ('decorating pages: ' + hits);
    	if (typeof hits == 'undefined' || typeof hits.results  == 'undefined' || hits.results == null) {
//            if (typeof hits.displayError !== 'undefined' && hits.displayError && hits.displayError.length > 0) {
//    			$_("#muse-status").fadeOut('slow');
 //           }
    		GM_log('No hits available!');
    		if (typeof hits != 'undefined' && typeof hits.error != 'undefined')
    			GM_log('hits error message: ' + hits.error);
    		return; // do nothing
    	}

    	GM_log (hits.results.length + ' names on page');
    	var n_hits = hits.results.length;
    	
        // populate the callout_content and insert messages into the page
        if (n_hits > 0) {
        	var startTime = new Date().getTime();
	    	var textNodes = text_nodes; // as an optimization, we'll reuse the nodes we found while extracting the text
	    	GM_log ('decorating ' + text_nodes.length + ' text nodes on page with ' + hits.results.length + ' hits');
	    	
	    	var anchors_entered = new Array();
	    	for (var x = 0; x < textNodes.length; x++)
	    		if (text_nodes[x] != null)
	    		{
	    			try {
	    				decorateTextNode(text_nodes[x], hits, true, anchors_entered);
	    			} catch(err) { alert(err); };
	    		}
	    //	GM_log (n_hilights + " names hilited");
	    	GM_log ('finished decorating page, elapsed time on page = ' + (new Date().getTime()-startTime) + 'ms');
	//    	start_prettyphoto();	    	
	    };
    } // end of decorate page
       

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
} // end of addTextNodes
    // returns effective page text
    function compute_text_nodes(ARTICLES)
    {
	    for (var i = 0; i < ARTICLES.length; i++)
	    {
	        var nodes = ARTICLES[i];
	        // nodes is an array of nodes
	        if (nodes == null)
	            continue; // some error, we didn't get the page
	        var url = LINKS[i].url;
	        LOG ('# top level nodes for url #' + i + ':' + url + ' = ' + nodes.length);
	
	        try {
	            // extract text from all nodes
	            var arr = new Array();
	            for (var j = 0; j < nodes.length; j++)
	                addTextNodes(nodes[j], arr, true);
	            // arr is a bunch of text nodes. string them together to form pagetext
	            LINKS[i].text_nodes = arr;	
	            LOG ('# text nodes for ' + url + ' = ' + arr.length); // + ' page text length = ' + LINKS[i].page_text.length);
	        } catch (e) { 
	        	LOG ('exception trying to read text from page ' + url + ': ' + e); 
	        	LINKS[i].text_nodes = new Array();
	        };
	    };
    }

    function $processPage(spec, $response) {
        $article = (typeof spec.selector_for_article === 'undefined') ? $response : $_(spec.selector_for_article, $response);
        if ($article.length == 0) {
            return {$article: $article, $stylesheets: null} // throw ('No article');
        }
        LOG ('#articles = ' + $article.length);
        if ($article.length > 1)
            $article = $_($article[0]); // just pick the first article

        if (typeof spec.selectors_to_nuke != 'undefined')
            for (var i = 0; i < spec.selectors_to_nuke.length;i++)
                $_(spec.selectors_to_nuke[i], $article).html('');

        var $stylesheets = $_('link[rel="stylesheet"]');
        var $div_scripts = $_('script', $article);
        var mesg = $div_scripts.length + ' scripts (removed), ' + $stylesheets.length + ' stylesheets, #chars ' + $article.html().length;
        $_('script', $article).remove();
        mesg += ' -> ' + $article.html().length;
        LOG (mesg);
        return {$article: $article, $stylesheets: $stylesheets};
    }

    var $stories = $getLinksFromSelection();
    if (typeof spec.selector_for_links !== 'undefined') {
        LOG ('# links from user selection = ' + $stories.length + ' applying filter spec: ' + spec.selector_for_links);
        $stories = $stories.filter(spec.selector_for_links);
    } 
	
	if ($stories == null || $stories.length == 0) {
        LOG ('no links selected with selector: ' + spec.selector_for_links);
		var message = 'Select some text with links first.';
		inject_html ($_, document.body, '<div id="muse-status-container" style="' + muse_status_container_style + '"><div id="muse-status" style="' + muse_status_style + '">' + message + '</div></div>');
		window.setTimeout(function() { $_('#muse-status').fadeOut('slow');}, 3000);
		return;
	}
    LOG ('# links after intersecting user selection with selector for links = ' + $stories.length);
	
	// get candidate urls
	LOG ('#stories = ' + $stories.length);
	$stories.each (function(i, elem) {
		var rel = $_(this).attr('rel');
        if (rel === 'nofollow' || rel === 'tag' || rel === 'category' || rel == 'category tag') // ignore these, because they are usually not real articles
            return;
		var url = $_(this).attr('href');
        var spec = {url:url, text: $_(this).text()};
		LINKS.push(spec);
        LOG ('url from page = ' + spec.url + ' text = ' + spec.text);
	});

    if (typeof spec.selector_for_placing_content === 'undefined')
        spec.selector_for_placing_content = 'body';

	var TOP_LEVEL_PAGE = $_(spec.selector_for_placing_content).html(); // save the original content first

    // inject muse-status 
	// nuke prev. status if the whole page is being used. if not, we'll nuke it later. we don't want to nuke later in case of body because we'd nuke our muse_status divs too
//	if (typeof spec.selector_for_placing_content === 'undefined' || spec.selector_for_placing_content === 'body')
		$_(spec.selector_for_placing_content).html('<br/>'); // nuke the prev. content
	var message = '<span>Reading pages ...</span>';
	var top = '<span id="page_message" style="position:relative;top:3px;">' + message + '</span>'; // needs a 3px offset to align with the icons
    top += '<span id="page_count" style="position:relative;top:3px;"></span> <a style="margin-left:10px; color:white;text-decoration:underline;" target="_new" href="http://mobisocial.stanford.edu/bookify/#help"><img style="display:inline;height:18px;width:18px;position:relative;top:5px;" src="http://suif.stanford.edu/~hangal/jog/question-mark-icon.png"></img></a>';
    top += '<a style="margin-left:10px;color:white;text-decoration:underline" href="#" onclick="alert(\'More controls coming soon!\');"><img style="display:inline;height:18px;position:relative;top:5px; " src="http://suif.stanford.edu/~hangal/jog/gears-icon.png"></img></a></span>';

	inject_html ($_, document.body, '<div id="muse-status-container" style="' + muse_status_container_style + '"><div id="muse-status" style="' + muse_status_style + '">' + top + '</div></div>');
    inject_html ($_, document.body, '<audio id="click_sound"> <source src="' + window.BASE_URL + '/click.ogg" type="audio/ogg" /> <source src="' + window.BASE_URL + '/click.mp3" type="audio/mpeg" /></audio>'); // no audio controls needed
//    alert ($_('#muse-status-container').length);
//    $_('#muse-status-container').animate({top:0}, 'slow');
	var where_to_insert = $_(spec.selector_for_placing_content)[0];

	LOG ('Starting with ' + LINKS.length + ' URL specs on page');

    // check the urls and massage a bit of necessary
	var newLINKS = new Array();
    var banned_suffixes = ['.js', '.css', '.png', '.jpg', '.gif', '.jpeg', '.pdf', '.ps', '.doc', '.ppt', '.pptx', '.docx', '.xls', '.xlsx', '.gz', '.zip'];
	for (var i = 0; i < LINKS.length; i++) {
		var url = LINKS[i].url;
        // LOG ('url = ' + url);
		if (typeof url == 'undefined')
			continue;
		if (url == document.location.href)
			continue;
		if (url.indexOf('http') == 0 || url.indexOf('//') == 0) { // full path. 
            // make same origin check
            if (DISABLE_XDOMAIN && url.indexOf(location.hostname) <= 0) { // simplified check for same origin. see http://stackoverflow.com/questions/9404793/check-if-same-origin-policy-applies for options.
                LOG ('dropping url because of different origin from current hostname (' + location.hostname + '): ' + url);
                continue; // violates same origin
            }
            // same origin. no need to modify url
        }  else if (DROP_VIDEO_LINKS && url.indexOf("/video") >= 0) {
            LOG ('dropping url because of video: ' + url);
            continue;
        } else { // ok, not a full path.
            var dir_path = '';  
            var path = window.location.pathname;
            // if path is /a/b/c we want /a/b
            // if path is /a/ we want /a/
            var path_components = path.split('/');
            if (path_components.length > 0) {
                var up_to_idx = (path.charAt(path.length-1) == '/') ?  path_components.length : path_components.length-1;
                for (k = 0; k < up_to_idx; k++) { 
                    if (path_components[k] == '') // skip empty components
                        continue;
                    dir_path += '/';
                    dir_path += path_components[k];
                }
            }

            var base = location.protocol + '//' + location.host;
            if (location.port != '')
                base += ':' + location.port;
            // url is of th eform //
            if (url.indexOf('/') == 0)
			    url = base + url; 
            else
                url = base + dir_path + '/' + url; 
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
            var new_url = url.substring (0, idx); // strip anchors
            LOG ('changing url ' + url + ' to ' + new_url);
            url = new_url;
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
		newLINKS.push({url: url, text: LINKS[i].text});
	}
	LINKS = newLINKS;
	LOG ("after filtering, we have " + LINKS.length + " unique URL specs");
	LINKS = uniquify(LINKS);
	LOG ("after uniquifying, we have " + LINKS.length + " unique URL specs");

    if (LINKS.length == 0) {
	    $_('#muse-status').html('No valid links...'); 
		window.setTimeout(function() { $_('#muse-status').fadeOut('slow');}, 3000);
		return;
    }

	$_('#page-message').html('Reading ' + LINKS.length + ' pages');
	var ARTICLES = new Array();
	var STYLESHEETS = new Array();
	var nCompleted = 0;
	if (maxLINKS > LINKS.length)
		maxLINKS = LINKS.length;

	inject_html ($_, where_to_insert, '<div id="jog_contents" style="min-height:600px;padding:0px;margin:0px;"></div>  <div id="muse-ZZZ_pages"></div> ');

    function all_links_fetched() { 
         LOG ("all pages read, starting to extract page text!");
        // read text from each page's nodes
        compute_text_nodes(ARTICLES);
        score_pages_and_call(all_pages_scored);
    }

    function score_pages_and_call(callback) {
		// get leads from each pagetext.
		var nCompleted = 0;	
        var maxURLS = LINKS.length;

		for (var i = 0; i < LINKS.length; i++)
		{
			LOG ('starting text assembly for ' + LINKS[i].url);
			// compute raw page text for muse
            var page_text = '';
            
            try {
	            var arr = LINKS[i].text_nodes;
	            for (var j = 0; j < arr.length; j++)
	            {
	                if (arr[j] == null)
	                	page_text += '. ';
	                else
	                	page_text += ' ' + arr[j].data;
	            }
	            // get rid of ampersands, they might interfere with the http req params
	            page_text = page_text.replace(/&/g, ' ');
            } catch(e) {
    			LOG ('exception assembling text nodes for url #' + i + ':' + LINKS[i].url + ": " + e);
    			page_text = null;
            }
            
			if (page_text == null || page_text == '') {
				nCompleted++;
				continue;
			}
			
			// look up muse for leads in this text. 
			// must send credentials to hook into the current Muse session
			$_.ajax(
				{url: window.MUSE_URL + '/ajax/leadsAsJson.jsp', 
				type: 'POST',
                dataType: 'json',
				data:  {refText: page_text}, 
				beforeSend: function(xhr) { xhr.withCredentials = true;  },
				xhrFields: { withCredentials: true},
				crossDomain: true,
				success: (function(url, idx) { return function(hits) {
					nCompleted++;
					var score  = 0.0;
					try {
						LOG ('received muse lookup response for ' + url); // + ' response length = ' + data.length + ' chars');
						if (typeof hits.results != 'undefined' && hits.results) {
							LOG ('There are ' +  hits.results.length + ' hits for url #' + idx + ': ' + url);
							for (var j = 0; j < hits.results.length; j++) { score += hits.results[j].score; }
						}
					} catch (e) { LOG ('Exception reading ' + url + ': ' + e); }
					LINKS[idx].score = score;
					LINKS[idx].hits = hits;
					LOG ('final score for url#' + idx + ' ' + url + ' is ' + score + ' completed = ' + nCompleted + ' urls = ' + maxURLS);
					$_('#page_message').html('Scoring ' + nCompleted + '/' + maxURLS + ' pages');
					if (nCompleted == maxURLS) { callback(); };

			    }; })(LINKS[i].url, i),
			    error: function(jqXHR, textStatus, errorThrown) { 
				        nCompleted++; 
				        LOG ("error in looking up leads! " + textStatus + " " + errorThrown);
					    $_('#page_message').html('Scoring ' + nCompleted + '/' + maxURLS + ' pages');
				        if (nCompleted == maxURLS) { callback(); };
				    }
			    }
		    ); // end of ajax call
			LOG ('fired muse lookup for ' + LINKS[i].url);
	    }; // end of for loop
    } // end of score_pages_and_call

    // PAGES is global, we just need the scores array
    function all_pages_scored() {
        LOG ("all pages scored");
	    for (var i = 0; i < LINKS.length; i++) {
	    	LOG ('Decorating page #' + i);
	    	decoratePage(LINKS[i].hits, LINKS[i].text_nodes);
    	}
    
	    LOG ('Starting sorting of ' + LINKS.length + ' urls');
        // url order is initially just 0..N-1, then we sort it based on the score
	    var url_order = new Array();
	    for (var i = 0; i < LINKS.length; i++) { url_order.push(i); }
	    url_order.sort (function(a, b) { 
            if (!typeof LINKS[a].score || !LINKS[a].score)
                LINKS[a].score = 0.0;
            if (!typeof LINKS[b].score || !LINKS[b].score)
                LINKS[b].score = 0.0;
            return LINKS[b].score - LINKS[a].score;
          });
        var NEW_LINKS = new Array();
	    LOG ("Completed sorting of " + LINKS.length + " urls, order is " + url_order);
        for (var x = 0; x < ARTICLES.length; x++)  {
            LOG ('rank ' + x + ' #' + url_order[x] + ' score ' + LINKS[url_order[x]].score);
            NEW_LINKS[x] = LINKS[url_order[x]];
        }
        LINKS = NEW_LINKS;

    	var PAGES = new Array();

        // inject section and document beginning (sections and documents go together)
		var all_pages_html = '<div class="muse-ZZZ_section" name="section1"> <div class="muse-ZZZ_document">';
        var count = 0; // count of #pages we actually have
        {
		    $_('#page_message').html('Click to show or hide the page dial.'); 
   		    var div = '<div><div class="muse-ZZZ_page" pageId="' + count + '" ><div style="' + spec.content_page_style + '">' + TOP_LEVEL_PAGE + '</div></div></div>';
   		    var $div = $_(div);
		    // all_pages_html += $div.html();
            PAGES[count] = $div.html();
            LOG ('after appending top page ' + count + ' ' + all_pages_html.length + ' chars');
            count++;
        }

        // update these, because we need LINKS to be in sync with pages (to update the location bar URLs)
        var NEW_LINKS = [];
        NEW_LINKS[0] = {url: document.location.href, text: document.title};
        var NEW_STYLESHEETS = [];
        NEW_STYLESHEETS[0] = document.styleSheets;
        
        for (var x = 0; x < ARTICLES.length; x++)  {
            // k is the article at position #x
            var k = url_order[x];
            if (typeof ARTICLES[k] !== 'undefined' && ARTICLES[k] && ARTICLES[k].html()) {      
                var original_url = LINKS[x].url; // note x, not k
                var original_text = LINKS[x].text;
                var html = ''; // '<div style="color:#777;margin-top:50px;"><a href="' + original_url + '">Original link</a> ' + original_text + '<div><br/>';
                html += ARTICLES[k].html();
   			    var div = '<div><div class="muse-ZZZ_page" pageId="' + count + '"><div style="' + spec.content_page_style + '">' + html + '</div></div></div>';
   			    var $div = $_(div);
		        // all_pages_html += $div.html();
                PAGES[count] = $div.html();
                NEW_LINKS[count] = LINKS[x];
                NEW_STYLESHEETS[count] = STYLESHEETS[x];
                LOG ('logging page ' + count + ' k = ' + k + ' ' + html.length + ' chars');
                count++;
            } else {
                LOG ('missing article #' + k);
            };
        }
        LINKS = NEW_LINKS; 
        STYLESHEETS = NEW_STYLESHEETS;
        
        all_pages_html += '</div></div>';// close the section and document div's
        $_('#muse-ZZZ_pages').append(all_pages_html);
        LOG ('#pages injected = ' + count);
		LOG ('Completed fetching, starting jog');
        function on_page_change(old_page, new_page) { 
        	
            function adjust_stylesheets() {
                var in_array = function(arr, mxd,strict) {
                	for (i in arr) {
	                	if (this[i] == mxd && !strict) return true;
	                	else if (arr[i] === mxd) return true;
                	}
                	return false;
                };
                
                if (!STYLESHEETS[old_page])
                	STYLESHEETS[old_page] = [];
                if (!STYLESHEETS[new_page])
                	STYLESHEETS[new_page] = [];
                
                LOG ('old page# ' + old_page + ' had ' + STYLESHEETS[old_page].length + ' stylesheet(s), new page#' + new_page + ' has ' + STYLESHEETS[new_page].length);
                
                // update url location bar (http://spoiledmilk.com/blog/html5-changing-the-browser-url-without-refreshing-page/)
                window.history.pushState("object or string", LINKS[new_page].text, LINKS[new_page].url);
                
                // adjust stylesheets for the new page. keep the common ones, remove the ones only in the old, and add the ones only in the new
                try {
                	for (var i = 0; i < STYLESHEETS[old_page].length; i++);
                	    if (!in_array(STYLESHEETS[new_page], STYLESHEETS[old_page][i], false)) {
                	    	LOG ('removing stylesheet #' + i + ': ' + STYLESHEETS[old_page][i]);
                	    	$_('head').remove(STYLESHEETS[old_page][i]);
                	    }
                } catch (e) {  LOG ('error removing stylesheet!' + e); }
                try {
	                for (var i = 0; i < STYLESHEETS[new_page].length; i++);
            	    	if (!in_array(STYLESHEETS[old_page], STYLESHEETS[new_page][i], false)) {
                	    	LOG ('adding stylesheet #' + i + ': ' + STYLESHEETS[new_page][i]);
            	    		$_('head').append(STYLESHEETS[new_page][i]);
            	    	}
	            } catch (e) { LOG ('error adding stylesheet!' + e); }
            }

            if (new_page > 0) { 
                $_('#page_count').html('Page ' + new_page + '/' + (PAGES.length-1));  // -1 because we don't want to count the cover page
            } else {
                $_('#page_count').html('');
            }
            
            if (new_page > 0) { 
                var spec = LINKS[new_page-1];
                var html = ''; // '<a target="_new" style="text-decoration:underline;" href="' + spec.url + '">Original</a>&bull; ';//  + spec.text;
                $_('#page_message').html(html);
                LOG ('text = ' + spec.text);
               adjust_stylesheets();
            } else {
                $_('#page_message').html('Click to show or hide the page dial.');
            };        
        }
        
        // final step: inject jog
		$_(document).jog({
							 pages: PAGES,
						     sound_selector: '#click_sound1', // effectively disabled, its actually '#click_sound',
						     jog_content_frame_selector: '#jog_contents',
							 logger: LOG,
							 width: 180,
							 reel_prefix  : window.BASE_URL + '/images/sleekknob',
							 show_count: false,
							 page_change_callback: on_page_change,
						});
    } // end of all_pages_scored

	for (var i = 0; i < LINKS.length; i++) {
		var url_spec = LINKS[i];
		if (i < maxLINKS) {
			LOG ('Issuing request for url#' + i + ':' + url_spec.url + ': ' + url_spec.text);
			$_.ajax({
				url: url_spec.url, 
				success: (function(url, i) { return function(responseHTML) {
					nCompleted++;
					try { 
                        if (responseHTML.length > 0) {
						    LOG (responseHTML.length + ' chars in response for url#' + i + ':' + url);
    						$response = $_(responseHTML);
                            var $article;
                            try { 
                                var x = $processPage(spec, $response);
                                $article = ARTICLES[i] = x.$article;
                                STYLESHEETS[i] = x.$stylesheets;
                            } catch (e) { $article = ARTICLES[i] = null; LOG ('WARNING: no article in response for url#' + i + ':' + url + ' exception=' + e); }

                            // for craigslist etc, we need to put the response inside a div first
                            if (!$article || !$article.html() || $article.html().length == 0)
                            {
                                LOG ('funny, response.html().length straight from ajax response is 0');
    						    $response = $_('<div>' + responseHTML + '</div>'); // don't do this directly, breaks nytimes site.
                                if ($response && $response.html())
                                    LOG ('funny, now response.html().length is ' + $response.html().length);
                                try { 
                                      var x = $processPage(spec, $response);
                                      $article = ARTICLES[i] = x.$article;
                                      STYLESHEETS[i] = x.$stylesheets;
                                } catch (e) { ARTICLES[i] = null; LOG ('WARNING: no article in response for url#' + i + ':' + url); }
                            }
                        } else {
                            LOG ('Empty response for url#' + i + ' ' + url);
                            ARTICLES[i] = null;
                        }
					} catch (e) { LOG ('error parsing response from url#' + i + ' ' + url + ':' + e);}
					LOG ('nCompleted = ' + nCompleted + ' maxLINKS = ' + maxLINKS);
					$_('#page_message').html('Read ' + nCompleted + '/' + maxLINKS + ' pages');
					
					if (nCompleted == maxLINKS) {
						all_links_fetched();
					}
				};
			})(url_spec.url, i),
				error: (function(url, i) { return function() { 
					nCompleted++; 
					LOG ('Error response for url#' + i + ': ' + url);
					LOG ('nCompleted = ' + nCompleted + ' maxLINKS = ' + maxLINKS);
					ARTICLES[i] = null;
					$_('#page_message').html('Read ' + nCompleted + '/' + maxLINKS + ' pages');
					if (nCompleted == maxLINKS) {
                        all_links_fetched();
					}
					}; })(url_spec.url, i)
			});
	  }
	};
}


function doIt() {

var nyt_spec = {
selector_for_links: '.story a, .storyHeader a, .storyFollowsLede a, .headlinesOnly a',
append_to_link: 'pagewanted=all',
selector_for_article: '#article',
selector_for_placing_content: '#main',
content_page_style: 'margin:5px;font-size:1.5em',
selectors_to_nuke: ['#articleToolsTop', '.articleInline .doubleRule', '.video']
};

var nytq_spec = {
selector_for_links: 'li.summary a',
append_to_link: 'pagewanted=all',
selector_for_article: '#article',
selector_for_placing_content: '#main',
selectors_to_nuke: ['#articleToolsTop', '.articleInline .doubleRule']
};

var cs_spec = { selector_for_links: '.inner td a'};
var cl_spec = { selector_for_links: '.row > a, p > a'}; // only immediate a descendants of .p and row because there is some noise due to categories which are p.row > .itemcg > a
var slashdot_spec = { selector_for_links: '.story a', selector_for_article: '#content', selectors_to_nuke: ['#slashboxes'] };
var galegroup_spec = { selector_for_links: '.imgN a', selector_for_article: '#docViewer', selector_for_placing_content: '#docViewer'};
var engadget_spec = { selector_for_links: '.post_title a'};
var searchworks_spec = { selector_for_links: '.index_title a'};
var deccanherald_spec = { selector_for_links: '#topBlock a', selector_for_article: '#main'};
var yelp_spec = { selector_for_links: '.itemheading a', selector_for_placing_content: '.column-alpha', selector_for_article: '.column-alpha'};
var histdept_spec = { selector_for_links: '.view-people .views-field a'};
var wp_spec = { selector_for_links: '.wikitable a :not(.reference)'}; // anything in a table, but not references
wp_spec = { selector_for_links: '.wikitable a'}; // anything in a table, but not references
var npr_spec = { selector_for_links: '#wrapper ul li a'};
var baking_spec = { selector_for_links: '.wikitable a :not(.reference)'}; // anything in a table, but not references
baking_spec = { selector_for_links: '.post a', selector_for_article: '.post', selector_for_placing_content: '#content'}; // anything in a table, but not references
baking_spec = { selector_for_links: '.post a'};
var medhelp_spec = { selector_for_links: '.subject_title a'}; 
var supost_spec = { selector_for_links: '.one-result a'}; 
var tc_spec = { selector_for_links: '.headline a', selector_for_article: '.left-container', selector_for_placing_content: '.left-container'}; 
tc_spec = { selector_for_links: '.headline a', };
var gigaom_spec = { selector_for_links: '.post a', selector_for_article: '#content'};
var bloomberg_spec = { selector_for_links: '.news_item a, .teaser_item a, .news_block a, ul li div.hl a', selector_for_placing_content: '#primary_content', selector_for_article: '#primary_content'};
var reuters_spec = { selector_for_article: 'div.column2'};
var theatlantic_spec = { selector_for_links: 'a[href*="archive"]', selector_for_article: '#container', selector_for_placing_content: '#container'};

if (document.URL.indexOf('query.nytimes.com') >= 0)
    inject_jquery_and_call_main(nytq_spec);
else if (document.URL.indexOf('supost.com') >= 0)
    inject_jquery_and_call_main(supost_spec);
else if (document.URL.indexOf('nytimes.com') >= 0)
    inject_jquery_and_call_main(nyt_spec);
else if (document.URL.indexOf('craigslist.org') >= 0)
    inject_jquery_and_call_main(cl_spec);
else if (document.URL.indexOf('cs.stanford.edu') >= 0)
    inject_jquery_and_call_main(cs_spec);
else if (document.URL.indexOf('slashdot.org') >= 0) 
    inject_jquery_and_call_main(slashdot_spec);
else if (document.URL.indexOf('engadget.com') >= 0) 
    inject_jquery_and_call_main(engadget_spec);
else if (document.URL.indexOf('searchworks.com') >= 0) 
    inject_jquery_and_call_main(searchworks_spec);
else if (document.URL.indexOf('galegroup.com') >= 0) 
    inject_jquery_and_call_main(galegroup_spec);
else if (document.URL.indexOf('deccanherald.com') >= 0) 
    inject_jquery_and_call_main(deccanherald_spec);
else if (document.URL.indexOf('yelp.com') >= 0) 
    inject_jquery_and_call_main(yelp_spec);
else if (document.URL.indexOf('history.stanford.edu') >= 0) 
    inject_jquery_and_call_main(histdept_spec);
//else if (document.URL.indexOf('wikipedia.org') >= 0) 
//    inject_jquery_and_call_main(wp_spec);
else if (document.URL.indexOf('17andbaking.com') >= 0) 
    inject_jquery_and_call_main(baking_spec);
else if (document.URL.indexOf('everywhereist.com') >= 0) 
    inject_jquery_and_call_main(baking_spec); // same general blog spec
else if (document.URL.indexOf('medhelp.org') >= 0) 
    inject_jquery_and_call_main(medhelp_spec); 
else if (document.URL.indexOf('techcrunch.com') >= 0) 
    inject_jquery_and_call_main(tc_spec); 
else if (document.URL.indexOf('wikipedia.com') >= 0) 
    inject_jquery_and_call_main(wp_spec); 
else if (document.URL.indexOf('npr.org') >= 0)
    inject_jquery_and_call_main(npr_spec); 
else if (document.URL.indexOf('gigaom.com') >= 0)
    inject_jquery_and_call_main(gigaom_spec);
else if (document.URL.indexOf('bloomberg.com') >= 0)
    inject_jquery_and_call_main(bloomberg_spec);
else if (document.URL.indexOf('reuters.com') >= 0)
    inject_jquery_and_call_main(reuters_spec);
else if (document.URL.indexOf('theatlantic.com') >= 0)
    inject_jquery_and_call_main(theatlantic_spec);
else 
    inject_jquery_and_call_main({}); // same general blog spec
}
if (RUNNING_IN_PLUGIN) {
// we don't run it directly, we wait for the button to be clicked.
var head = document.getElementsByTagName('head')[0];
var style = document.createElement('style');
var rules = document.createTextNode('.muse-navbar {padding-top: 3px; position: fixed; top: 0pt; right:10px;z-index:10000; text-transform:uppercase;font-family:"Gill Sans",Calibri,Helvetica,Arial,Times;font-size:10pt;font-weight:normal} \
				   .muse-navbar a span{-moz-border-radius: 4px; background-color: #0C3569; opacity: 0.9;} \
				 	.muse-navbar a span {color:white;font-size:14pt; font-weight:normal; padding: 5px 5px 5px 5px; text-decoration:none;} \
					.muse-navbar a span:hover {color:yellow; text-decoration:none;}');

style.type = 'text/css';
if (style.styleSheet)
    style.styleSheet.cssText = rules.nodeValue;
else 
    style.appendChild(rules);
head.appendChild(style);
$("body").prepend('<div class="muse-navbar">  <a id="refresh_button" href="#"><span>Jog</span></a> ');
var b = document.getElementById('refresh_button');
if (b != null) 
    b.addEventListener("click", doIt, true);
} else {
    doIt();
}

})();
