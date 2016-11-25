// terminology: a section has documents, documents may consist of multiple pages
// pdf's: conf. sections are sections, each paper/PDF is one
// emails: sections are emails a multi-doc, i.e. all emails in a month (or year). each email is a doc with just 1 page
// only "selected pages" are shown

// global vars: currentPage, totalPages
// page contents are currently read with $(page_selector)[selectedPageNum[page]].innerHTML;
// selectedPageNum[] is set up by ajax call to narrowSelectedDocs.jsp after a search

// "use strict";

var PDF_ICON_WIDTH = 30; // in pixels

document.write('<div id="jog_div" style="position:absolute;display:none;top:0px;left:0px;opacity:0.9;z-index:101">	<table><tr><td>	<img id="jog_img" src="images/reel21.png" width="250"></img>	</td>	<td valign="middle">&nbsp;&nbsp 	<span id="jog_text" class="jog_text rounded">&nbsp;</span><br/>	</td></tr></table>	</div>');

// start_jog is the top level function
// paging_info is optional, if present, it has the form {url: ..., window_size_fwd: ..., window_size_back}
function start_jog(page_selector, jog_content_frame_selector, paging_info, post_comments_url)
{		
//	page_selector = '.page';
//	jog_content_frame_selector = '#jog_contents';
	var currentPage = -1;
	muse.log ('starting jog with page_selector ' + page_selector + " jog_contents_selector "+ jog_content_frame_selector + ' paging_info ' + paging_info);
	var pages_jq = $(page_selector);
	totalPages = $(page_selector).length;
	if (totalPages == 0)
	{
		$(jog_content_frame_selector).html('Zero documents. Try relaxing the filter.');
		return;
	}
	muse.log (totalPages + ' pages found');
	
	var editing_comment = false;
	pages = new Array();
	comments = new Array();
	pageImagesFetched = new Array();
	for (var i = 0; i < totalPages; i++)
	{
		pages[i] = null;
		comments[i] = pages_jq[i].getAttribute('comment');
		pageImagesFetched[i] = false;
	}

	selectedPageNum = new Array(); // array of indices of selected pages into main page array
	for (var i = 0; i < totalPages; i++)
		selectedPageNum[i] = i;

	setupSections();
	setupDocuments();
	muse.log ('pages and docs set up');

	liked = new Array();
	$(document).ajaxError(handle_ajax_error);
	setup_paging();
	jog = new Jog(jog_content_frame_selector, 250, 250, 20, doPageForward, doPageBackward);
	$(document).keydown(keypress_handle);
	// TOFIX: entryPage is a global
	if (typeof(entryPage) != 'undefined')
		setCurrentPage(entryPage);
	else
		setCurrentPage(0);

	showCurrentPage();
	// end of this function... the rest are private helper functions

	function keypress_handle(event) {
		var code;
		var handled = false;
		if (event.keyCode)
		  code = event.keyCode; // keyCode is apparently IE specific
		else
		  code = event.charCode;

		if (editing_comment)
			return true; // pass all keys to textarea including arrow keys etc
		
		if (code == 37) // left arrow
		{
		  doPageBackward();
		  handled = true;
		}
		else if (code == 39) // right arrow
		{
		  doPageForward();
		  handled = true;
		}
		else if (code == 9) // tab
		{
		  handled = true;
		  if (!event.shiftKey)
			  scrollSection(1);
		  else
			  scrollSection(-1);
		}
		else
		{
			// muse.log ('in keystroke, code = ' + code);
			if (!event.ctrlKey && !event.metaKey && !event.altKey)
				comment_keystroke_handler(code);
		}

		if (handled)
		    return false; // we handled the key, do not propagate it further
		else 
			return true;
	} // end of keypress

function comment_keystroke_handler(code)
{
	// check if its special keys like escape, ctrl, command etc.
	// based on http://unixpapa.com/js/key.html
	if (code == 16 || code == 17 || code == 18 || code == 224 || code == 27 || code == 91 || code == 92 || code == 93 || code == 219 || code == 220)
		return; 
	muse.log ('in keystroke code' + code);
	if ($('.comment:visible').length == 0)
	{
		$('.comment').fadeIn();
		$('.comment-note').fadeIn();
	}
	var comment_jq = $('.comment')[0];
	comment_jq.focus();
	comment_jq.setSelectionRange($(comment_jq).val().length, $(comment_jq).val().length); // set cursor to end of field - TODO check, doesn't seem to work
}
	
// currently not being used
function narrowSearch()
{
//	term = $('#searchbox').val();
//	muse.log ('term = ' + term);
//	if (term.length === 0)
//		return;
//
//	$('#search_spinner').fadeIn('slow');
//
//	// server side narrowing
//	/*
//	 narrowedPageNums =
//		 $.ajax({
//		   type: "GET",
//		   url: "ajax/narrowSelectedDocs.jsp?term=" + term,
//		   dataType: "json"
//		 });
//	*/
//
//	 $.getJSON("ajax/narrowSelectedDocs.jsp?term=" + term, function(json){
//		   narrowedPageNums = json.indices;
////		   muse.log ('json returns = ' + json);
//
//		   if (narrowedPageNums !== null)
//		   {
//			   selectedPageNum = narrowedPageNums;
//			   currentPage = 0;
//			   showCurrentPage();
//		   }
//		   $('#search_spinner').fadeOut('slow');
//		 });
//
//	/*
//	 // client side narrowing
//	var selected = 0;
//	selectedPageNum = new Array();
//	for (var i = 0; i < totalPages; i++)
//		if ($(page_selector)[i].innerHTML.indexOf(term) > 0)
//		{
//			selectedPageNum[selected] = i;
//			selected++;
//			$('.search_status').html(i + "/" + totalPages);
//		}
//	console.log ('#pages selected: ' + selectedPageNum.length);
//	*/
//	$('#search_spinner').fadeOut('slow');
//	currentPage = 0;
//	showCurrentPage();
}

// returns which section/doc the given page belongs to, given the ending page nums of each section/doc
function whichRange(page, endPageNums)
{
	if (endPageNums == null || endPageNums.length == 0)
		return -1;

	var startPage = 0;
	var endPage = endPageNums[0];
	for (var i = 0; i < endPageNums.length; i++)
	{
		endPage = endPageNums[i];
		if (page >= startPage && page <= endPage)
			return i;
		startPage = endPage+1; // for next iteration
	}
	muse.log ('unable to find ' + page + ' in range [0..' + endPage + ']');
	return -1;
}

// update section name for the current page
function updateSectionName(page)
{
	currentSectionName = "Unnamed section";
	var section = whichRange(page, sectionEndPages);
	if (section >= 0)
		currentSectionName = sectionNames[section];
	muse.log (' updating to section ' + section + ' name: ' + currentSectionName);
	$('#sectionName').html('&nbsp;' + currentSectionName + '&nbsp;');
}

// sections have names and pages under them
// computers sectionEndPages and sectionNames and nSections
function setupSections()
{
	muse.log ('setting up sections');

	sectionEndPages = new Array(); // end page # of sections, inclusive.
	sectionNames = new Array();
	nSections = 0;
	N_COLS_IN_FRAME = (window.innerWidth > 1600) ? 2 : 1;
	N_ROWS_IN_FRAME = 1;
	N_PAGES_IN_FRAME = N_COLS_IN_FRAME * N_ROWS_IN_FRAME;

	// OVERRIDE
	N_COLS_IN_FRAME = N_PAGES_IN_FRAME = N_ROWS_IN_FRAME = 1;

	// note: we don't support the case where there are no sections, but some pages are not in any section
	var sections = $('.section');
	var pagesSoFar = 0;
	if (sections != null)
	{
		sections.each(function (idx) {
			sectionNames[idx] = $(this).attr('name');
			var sectionPages = $(this).find(page_selector);
			if (sectionPages != null)
				pagesSoFar += sectionPages.length;
//			muse.log ('section ' + idx + ': #pages = ' + sectionPages.length + ' total pages so far ' + pagesSoFar);
//			muse.log ('Pages: ' + sectionPages.length + ' pages in section ' + idx);
			sectionEndPages[idx] = pagesSoFar-1;
		});
	}

	nSections = sectionEndPages.length;
	muse.log (nSections + ' sections found with a total of ' + pagesSoFar + ' pages');
}

function setupDocuments()
{
	muse.log ('setting up documents');
	docEndPages = new Array(); // end page # of documents, inclusive.
	nDocs = 0;

	var docs = $('.document');
	var pagesSoFar = 0;
	if (docs != null)
	{
		for (var i = 0; i < docs.length; i++)
		{
			var t = docs[i];
			var docPages = $(t).find (page_selector);
			if (docPages != null)
				pagesSoFar += docPages.length;
			docEndPages[i] = pagesSoFar-1;
		}
		// .each seems to hit "script stack space quota is exhausted */
		/*
		docs.each(function (idx) {
			var docPages = $(this).find(page_selector);
			if (docPages != null)
			{
				pagesSoFar += docPages.length;
//				muse.log ('doc ' + idx + ': #pages = ' + docPages.length + ' total pages so far ' + pagesSoFar);
			}
			docEndPages[idx] = pagesSoFar-1;
		});
		*/
	}
	else
	{
		// no document class, so just assign 1 doc per page
		muse.log ('no doc tags, assigning 1 doc per page');
		var pages = $(page_selector);
		if (pages != null)
		{
			for (var i = 0; i < pages.length; i++)
				docEndPages[i] = i;
			/*
			pages.each(function (idx) {
				docEndPages[idx] = idx;
			});
			*/
			pagesSoFar = pages.length;
		}
	}

	nDocs = docEndPages.length;
	muse.log (nDocs + ' documents found with a total of ' + pagesSoFar + ' pages');
}

function scrollSection(increment)
{
	if (sectionEndPages == null || sectionEndPages.length == 0)
		return;
	var section = whichRange(currentPage, sectionEndPages);
	var nextSection = section + increment;
	if (nextSection >= 0 && nextSection < nSections)
	{
		muse.log ('scrolling to section ' + nextSection);
		if (nextSection == 0)
			setCurrentPage(0);
		else
			setCurrentPage(sectionEndPages[nextSection-1]+1); // first page of this section is last page of prev. section + 1
	}
	else
		muse.log ('not possible to scroll to section ' + nextSection);

	showCurrentPage();
}

/* currently not used.
function scrollDocument(increment)
{
	if (docEndPages == null || docEndPages.length == 0)
		return;
	var doc = whichRange(currentPage, docEndPages);
	var nextDoc = doc + increment;
	if (nextDoc >= 0 && nextDoc < nDocs)
	{
		muse.log ('scrolling to document ' + nextDoc);
		if (nextDoc == 0)
			setCurrentPage(0);
		else
			setCurrentPage(docEndPages[nextDoc-1]+1); // first page of this doc is last page of prev. doc + 1
	}
	else
		muse.log ('not possible to scroll to section ' + nextSection);

	showCurrentPage();
}
*/

function disposePage(num)
{
	var comment = $('.comment').val();
	if (comment != null && comment.length > 0 && typeof settings.post_comments_url != 'undefined')
	{
		muse.log ('posting comment for page ' + num + ' at url ' + post_comments_url + ': ' + comment);
		$.get(post_comments_url + '&page=' + num + '&comment=' + escape(comment)); // & and not ? because we always expect postCommentURL will have a ? param
		// if needed, can check for successful get....
		comments[num] = comment;
	}
}

function setCurrentPage(num)
{
	if (currentPage >= 0) // currentPage < 0 only at init
		disposePage(currentPage);
	currentPage = num;	
}

function showCurrentPage()
{
	muse.log ('showing current page = ' + currentPage);

	// normally we'll update page window after displaying the page, but if we dont have the current page,
	// we need to update page window right now
	var page_window_updated_in_this_call = false;

	if (selectedPageNum.length == 0)
		return;

	$('#jog_text').html('&nbsp;' + (currentPage+1) + '/' + (selectedPageNum.length) + '&nbsp;');
	$('#jog_status1').html('&nbsp;' + (currentPage+1) + '/' + (selectedPageNum.length) + '&nbsp;');

	// muse.log ('absolute page num = ' + selectedPageNum[currentPage]);

	var docNum = whichRange(currentPage, docEndPages); // kinda inefficient to find range for every page in frame
	
	// table does not span 100% of div_jog_contents if in acrobatics mode
	var style = 'position:relative;left:0px;'; // 'style="width:100%"';
	if (acrobatics)
		style += 'min-width:' + 820*N_COLS_IN_FRAME + 'px;';

	var frameContents = '\n<table class="browse_contents rounded shadow" style="' + style + '">\n<tr>';
	frameContents += '<td class="pageHeader" colspan="' + N_COLS_IN_FRAME + '">' + 	computePageHeader(docNum);
	frameContents += '</td>\n';
	frameContents += '</tr>\n<tr>';

	for (var i = 0; i < N_PAGES_IN_FRAME; i++)
	{
		var page = currentPage + i;
		if (page < totalPages)
		{
			if (pages[currentPage] == null)
			{
				muse.log ('page ' + currentPage + " not available");
				update_page_window();
				page_window_updated_in_this_call = true;

				// should show a quick status message here indicating that the page is loading
//				for (var i = 0; i < N_PAGES_IN_FRAME; i++)
//				{
//					var page = currentPage + i;
//					frameContents += "Loading page " + i;
//				}
				// we'll check again after 200 ms
				window.setTimeout(showCurrentPage, 200);
				return;
			}

			var bStyle = '';
			if (page < totalPages-1 && i < N_PAGES_IN_FRAME-1)
				bStyle = 'border-right: solid #888 2px;';
			frameContents += '<td class="pageFrame" style="vertical-align:top;min-width:800px; max-width:800px; min-height:1000px;overflow:hidden;' + bStyle + '">';
			frameContents += pages[page];
			frameContents += '</td>\n';
			
			if ((i+1) % N_COLS_IN_FRAME == 0)
				frameContents += "</tr>\n<tr>\n";
		}
		// muse.log ('page ' + page + ' is ' + pages[page]);
	}
	frameContents += '</tr></table>';
//	muse.log ('frame contents: ' + frameContents);
	frameContents += '<span class="comment-note" style="display:none">Note</span>';
	frameContents += '<textarea class="rounded comment" style="display:none"></textarea>';
	$(jog_content_frame_selector).html(frameContents);
	$('.comment').click (function() { muse.log ('text area click'); return false;});
	$('.comment').focus (function() { editing_comment = true; });
	$('.comment').blur (function() { editing_comment = false; }); 
	if (comments[currentPage] != null && comments[currentPage].length > 0)
	{
		$('.comment').val(comments[currentPage]);
		$('.comment-note').show(); // make sure comment box is visible if it does have comments
		$('.comment').show(); // make sure comment box is visible if it does have comments
	}
	
	// muse.log ('setting up contents for page ' + currentPage);

	var img = "images/" + (((currentPage/N_PAGES_IN_FRAME) % 2 == 0) ? "reel21.png" : "reel22.png");
	$('#jog_img').attr('src', img);

	if (!page_window_updated_in_this_call)
		update_page_window();

	// trying out lightbox, remove it if it becomes too much of a hassle
	// downside is that it only works for images
	// re-investigate if we have attachments with image previews that link to download of actual file (like for PDFs)
	var temp = $(".attachments img");
	if (temp.length > 0)
		temp.lightBox();
	/*
	$(".attachments img").fancybox({
		'transitionIn'	:	'elastic',
		'transitionOut'	:	'elastic',
		'speedIn'		:	300, 
	//	'modal'			: true,
		'speedOut'		:	200, 
		'overlayShow'	:	true,
		'showCloseButton' : true,
		'hideOnOverlayClick': true
	});
	*/
	// updatePageHeader();
}

function computePageHeader(docNum)
{
	var docElement =  $(".document")[docNum];
	starSrc = (liked[docNum]) ? 'images/star-gold256.png' : 'images/star-white256.png';
	var star = ''; // '<img id="star" width="30" title="Click to like/unlike" src="' + starSrc + '" onclick="javascript:toggleDocumentStar(event, ' + docNum + ')"/>';

	var pdfLink = $(docElement).attr('pdfLink');
	var header = '<table style="width:100%;vertical-align:top"><tr class="browse-header">\n';
	if (pdfLink)
	{
		// muse.log ('pdflink for page ' + currentPage + ' = ' + pdfLink);
		updateSectionName(currentPage);

		// a little div + table inside the header so the pdf logo and section name align properly
		header += '<td align="right" width="10%">&nbsp;<a href="' + pdfLink + '"><img width="' + PDF_ICON_WIDTH + '" src="images/pdf.png"/></a></td>\n';
		header += '<td class="sectionName">' + currentSectionName + '</td>\n';
		header += '<td width="10%">' + star + '</td>'; // only last 10% for star, otherwise it appears too wide

		header += '</tr>\n<tr><td colspan="3"><hr color="red" width="95%"/></td>\n'; // just an empty line
	}
	else
	{
		header += '<td width="100%" align="right" >' + star + '&nbsp;&nbsp;&nbsp;&nbsp;</td>';
	}

	header += '\n</tr></table>\n';
	return header;
}

/*
function toggleDocumentStar(e, docNum)
{
	if (liked[docNum])
	{
		muse.log ('user unlikes document ' + docNum);
		$('#star').attr('src', 'images/star-white256.png');
		liked[docNum] = false;
	}
	else
	{
		muse.log ('user likes document ' + docNum);
//		e.target.src = 'images/star-gold256.png';
		$('#star').attr('src', 'images/star-gold256.png');
		liked[docNum] = true;
	}

	// prevent event from bubbling up to outer container (would turn on jog dial)
	// see http://www.quirksmode.org/js/events_order.html
//	muse.log (dump_obj(e));
	if (e)
	{
		e.cancelBubble = true;
		if (e.stopPropagation) e.stopPropagation();
	}
	return false;
}
*/

function doPageForward()
{
	if (currentPage + N_PAGES_IN_FRAME < selectedPageNum.length)
	{
		setCurrentPage(currentPage + N_PAGES_IN_FRAME);
	    showCurrentPage();
	}
}

function doPageBackward()
{
	if ((currentPage-N_PAGES_IN_FRAME) >= 0)
	{
		setCurrentPage(currentPage - N_PAGES_IN_FRAME);
	    showCurrentPage();
	}
}

////////////////  paging ////////////////////
function handle_ajax_error(event, XMLHttpRequest, ajaxOptions, thrownError)
{
	alert ('ajax error');
	muse.log ("Sorry: Ajax error: event = " + event + " ajaxOptions = " + ajaxOptions + " thrownError = " + thrownError);
}

function setup_paging()
{
	muse.log ('setting up paging');
	currentWindow = new Object();
	windowSize = new Object();
	currentWindow.start = currentWindow.end = -1;
	// these many pages will be stored in browser (not including current page)
	if (typeof paging_info != 'undefined')
	{
		windowSize.back = paging_info.window_size_back;
		windowSize.fwd = paging_info.window_size_fwd;
	}
	else
	{
		// no windows
		windowSize.back = totalPages;
		windowSize.fwd = totalPages;
	}
	timeOfLastWindowMove = 0; // we last moved the window in 1970...
}

// fetch startPage to endPage, both inclusive
function page_in(startPage, endPage)
{
	muse.log ("Paging in [" + startPage + ".." + endPage + "]");

	if (startPage > endPage)
	{
		muse.log ("ERROR: attempting to page in [" + startPage + ".." + endPage + "]");
		return;
	}

//	ajax request to get lowPage to highPage
// response pages must be in sequence with pageId attribute set
	$.get(paging_info.url + "&startPage=" + startPage + "&endPage=" + endPage,
			function(response, t, xhr)
			{
				// using $(page_selector, response) leads to stack overflow in ffox :-(, so working around by creating a div and stashing the response in it and using DOM query methods
			    var p = document.createElement("div");
			    p.innerHTML = response;
			    var recvdPages = p.getElementsByClassName('page');
				muse.log ("received response for pages [" + startPage + ".." + endPage + "], response length is " + response.length + " has " + recvdPages.length + " pages");
				for (var i = 0; i < recvdPages.length; i++)
				{
					var recvdPageId = recvdPages[i].getAttribute("pageId");
					var expectedPageId = startPage+i;
					if (recvdPageId != expectedPageId)
						muse.log ("Warning: pageId expected " + expectedPageId + " recvd " + recvdPageId);
					pages[startPage+i] = recvdPages[i].innerHTML;
					// comments[startPage+i] = recvdPages[i].getAttribute('comment'); // we disable this currently because jogPageIn doesn't do comments
				}
			}
	);
}

/*
function fetchIMGs(startPage, endPage)
{
	for (var i = startPage; i < endPage; i++)
	{
		if (pages[i] == null)
			continue;
		var imgURLs = $(pages[i]).getAttribute("src");
		for (var j = 0; j < imgURLs.length; j++)
		{
			var imgURL = imgURLs[j];
			$.get(imgURL, function f(response) {
				pageImagesFetched[i] = true;
			});
		}
	}
}
*/

// start, end could be < 0, in which case the page is ignored
function page_out(start, end)
{
	// sometimes start, end could be < 0
	for (var i = start; i < end; i++)
		if (i > 0)
		{
			pages[i] = null;
			// comments[i] = null; // we'll keep comments always loaded up
		}
}

// initial window: [-1,-1]
function update_page_window() {
	var currentTime = new Date().getTime();

	// don't do anything if we moved window < 300 ms ago
	if ((currentTime - timeOfLastWindowMove) < 500)
		return;
	timeOfLastWindowMove = currentTime;

	var totalPages = pages.length;

	// compute position of new window, based on current page
	var newWindow = new Object();
	newWindow.start = currentPage - windowSize.back;
	if (newWindow.start <= 0)
		newWindow.start = 0;

	newWindow.end = currentPage + windowSize.fwd;
	if (newWindow.end >= totalPages)
		newWindow.end = totalPages-1;

	// if window didn't change, return, can happen e.g. if user moved fwd a page and then back a page
	if (currentWindow.start == newWindow.start && currentWindow.end == newWindow.end)
		return;

	muse.log ('current page: ' + currentPage + " of " + totalPages + " current window: [" + currentWindow.start + ".." + currentWindow.end + "] moving to new window: [" + newWindow.start + ".." + newWindow.end + "]");

	if (newWindow.start < currentWindow.start)
		page_in (newWindow.start, currentWindow.start-1);
	else if (newWindow.start > currentWindow.start)
		page_out (currentWindow.start, newWindow.start-1);

	if (newWindow.end > currentWindow.end)
		page_in (currentWindow.end+1, newWindow.end);
	else if (newWindow.end < currentWindow.end)
		page_out (newWindow.end+1, currentWindow.end);

	currentWindow.start = newWindow.start;
	currentWindow.end = newWindow.end;
}

} // end of setup_jog

////////////////////jog stuff //////////////////////

function Jog(jog_content_frame_selector, height, width, trackWidth, fn_forward, fn_backward)
{
	muse.log ('creating jog');
	this.jogTrackWidth = trackWidth;
	this.x = 0;
	this.y = 0;
	this.enabled = false;
	this.height = height;
	this.width = width;
	this.fn_forward = fn_forward;
	this.fn_backward = fn_backward;
	var x = this.createClickHandler();
	$(jog_content_frame_selector).click(x);
	$('#jog_img').mousemove(this.createMouseMoveHandler());
	$('#jog_img').click(x); // same click handler on jog itself needed also to dismiss
	muse.log ('mouse move setup');
}

Jog.prototype.showJog = function (x, y, fn_forward, fn_backward)
{
	$('#jog_div').css('top', y-(this.height/2)-50); // -50 to make the mouse start at the ready-to-go point on the jog
	$('#jog_div').css('left', x-(this.width/2));

	$('#jog_div').fadeIn('fast');
	this.x = x;
	this.y = y;
	this.enabled = true;
	this.prevCompartment = -1;
	this.prevX = x;
	this.prevY = y;
};

Jog.prototype.hideJog = function ()
{
	$('#jog_div').fadeOut('fast');
	this.enabled = false;
};

Jog.prototype.createMouseMoveHandler = function () {
	var myJog = this;
	return function(event) {
	//    jogInactivityTimer.reset();
	//	moveJogDynamic();
		
	// this code tries to track the jog to the cursor location
	function moveJogDynamic()
	{
	    function initEpoch(event) {
	    	var epoch = myJog.epoch;
	    	if (epoch == null)
	    		myJog.epoch = epoch = new Object();
	    	epoch.timeStamp = event.timeStamp;
	    	epoch.startX = event.pageX;
	    	epoch.startY = event.pageY;
	    	epoch.lastX = event.pageX;
	    	epoch.lastY = event.pageY;
	    	epoch.distanceFromStart = epoch.distanceTravelled = 0;
	    	epoch.minX = epoch.minY = 10000;
	    	epoch.maxX = epoch.maxY = -1;
	    	
	 //   	muse.log ('init new epoch ' + muse.dump_obj(epoch) + '\ncurrent pagex = ' + event.pageX + ' pagey = ' + event.pageY);
	    }

    	var epoch = myJog.epoch;
	    if (typeof epoch == 'undefined' || (event.timeStamp - epoch.timeStamp > 1000))
	    {
	    	initEpoch(event);
	    } else {
	    	// muse.log ('timestamp delta = ' + (event.timeStamp - epoch.timeStamp));
	    	// muse.log (muse.dump_obj(epoch) + '\ncurrent pagex = ' + event.pageX + ' pagey = ' + event.pageY);
		    var deltaX = Math.abs(event.pageX - epoch.startX);
		    var deltaY = Math.abs(event.pageY - epoch.startY);
	    //	muse.log (muse.dump_obj(epoch) + '\ncurrent pagex = ' + event.pageX + ' pagey = ' + event.pageY);
		    epoch.distanceFromStart = Math.sqrt (deltaX * deltaX + deltaY * deltaY);

		    var deltaX = Math.abs(event.pageX - epoch.lastX);
		    var deltaY = Math.abs(event.pageY - epoch.lastY);
		    var distanceTravelled = Math.sqrt (deltaX * deltaX + deltaY * deltaY);
		    epoch.distanceTravelled += distanceTravelled;

		    if (epoch.minX > event.pageX)
		    	epoch.minX = event.pageX;
		    if (epoch.minY > event.pageY)
		    	epoch.minY = event.pageY;
		    if (epoch.maxX < event.pageX)
		    	epoch.maxX = event.pageX;
		    if (epoch.maxY < event.pageY)
		    	epoch.maxY = event.pageY;
		    epoch.lastX = event.pageX;
		    epoch.lastY = event.pageY;
		    if (epoch.distanceFromStart < 40 && epoch.distanceTravelled > 100 && epoch.distanceTravelled > 5 * epoch.distanceFromStart)
		    {
		    	// need to readjust
		    	newCenterX = (epoch.maxX + epoch.minX)/2;
		    	newCenterY = (epoch.maxY + epoch.minY)/2;
		    	muse.log ("OK... RECENTERING! + " + newCenterX + "," + newCenterY);
		    	initEpoch(event);
		    	$('#jog_div').animate({'top': newCenterY-(myJog.height/2)}, {duration:100}); // -50 to make the mouse start at the ready-to-go point on the jog
		    	$('#jog_div').animate({'left': newCenterX-(myJog.width/2)}, {duration:100});

		    	// alert ('adjusting center to ' + newCenterX + ", " + newCenterY);
		    }
	    }
	}
	
	    var compartment = myJog.getCompartment(event.pageX, event.pageY);
	    if (compartment == myJog.prevCompartment)
	    	return;
	   // muse.log('new compartment: ' + compartment);

	    if (myJog.prevCompartment == -1)
	    {
	    	myJog.prevCompartment = compartment;
	    	return;
	    }

	    if ((compartment == ((myJog.prevCompartment+1) % 8)) || (compartment == ((myJog.prevCompartment+2) % 8)))
	    	myJog.fn_forward();
	    if ((compartment == ((myJog.prevCompartment+7) % 8)) || (compartment == ((myJog.prevCompartment+6) % 8)))
	    	myJog.fn_backward();

	    myJog.prevCompartment = compartment;	
	};
};

Jog.prototype.getCompartment = function(x, y) {
    var onLeft = false, lower = false, closerToYAxis = false;
    if (x < this.x)
        onLeft = true;
    if (y > this.y)
        lower = true;

    var correctedX = this.x;
    var correctedY = this.y;
    var xdiff = Math.abs(x - correctedX);
    var ydiff = Math.abs(y - correctedY);

  //  muse.log('x = ' + x + ' this.x = ' + this.x + ' y = ' + y + ' this.y = ' + this.y + ' lower = ' + lower + ' onleft = ' + onLeft);
  //  muse.log('xdiff = ' + xdiff + ' ydiff = ' + ydiff);
  //  muse.log('lower = ' + lower + ' onLeft = ' + onLeft + ' closer to y = ' + closerToYAxis);

    if (ydiff > xdiff)
    	closerToYAxis = true;

    // direction encoding: NNE = 0, and hten clockwise till NNW = 7.
    var result = (closerToYAxis) ? 0 : 1;
    if (lower)
    	result = 3 - result;
	if (onLeft)
		result = 7 - result;
	
	/*
	muse.log ('result compartment = ' + result);
	$('#jog_div').css('top', (lower?y+50:y-50)); // -50 to make the mouse start at the ready-to-go point on the jog
	$('#jog_div').css('left', (onLeft?x+50:x-50));

	this.x = x;
	this.y = y;
	 */
	return result;
};

// not used currently: boundary condition adjustments
Jog.prototype.adjustPos = function(o)
{
    // TOFIX:
    var W = 1000; // topPage.tb.width;
    var H = 600; // topPage.tb.height;
    //Boundary condition adjustments
    if(o.x + (o.width / 2) > W)
    {
        o.x  = W - (o.width / 2) - 10;
    }
    if(o.x - (o.width / 2) < 0)
    {
        o.x  = (o.width / 2) + 10;
    }
    if(o.y + (o.height / 2) > H)
    {
        o.y  = H - (o.height / 2) - 10;
    }
    if(o.y - (o.height / 2) < 0)
    {
        o.y  = (o.height / 2) + 10;
    }
};

Jog.prototype.enableJog = function(mouseX, mouseY)
{
    this.x = mouseX;
    this.y = mouseY + (this.width / 2) - (this.jogTrackWidth / 2);
    this.enabled = true;
    this.showJog(this.x,this.y);
};

Jog.prototype.createClickHandler = function() {
    function getEventTarget(e) {  
        e = e || window.event;  
        return e.target || e.srcElement;  
      }  
	muse.log ('click for jog contents');
	var myJog = this;
	return function (evt)
	{
		var target = getEventTarget(evt);
		//alert (target.tagName.toLowerCase());
		if (target.tagName.toLowerCase() == 'a')
			return; // do nothing if the original click was on an 'a'
		// note, sometimes we have spans for search terms or sentiments, that also act as links. should suppress jogs for those also
		// could disable when editing_comment - currently comment's textarea returns false onclick to stop event propagation
		muse.log ('handleclick called');
		if (myJog.enabled) 
		{
			muse.log ('Jog dismissed');
			myJog.hideJog();	
		} else {
			muse.log ('Jog summoned');
			myJog.enableJog(evt.pageX, evt.pageY);
		};
		return true;
	};
};
