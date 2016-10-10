// "use strict";
// warning: some functions depend on jquery
var muse = {};
muse.LOG = true; 

muse.ASSERT = function(p)
{
	if (!p)
	{
		try {
			var trace = printStackTrace();
			alert ('Assertion failed: stack trace is\n' +  trace.join('\n'));
		} catch (e) { alert ("printStackTrace not found; need to include stacktrace.js");}
		throw "ASSERTION FAILED";
	}
};

muse.log = function(mesg, log_on_server)
{
//	alert ('logging ' + mesg);
	if (typeof console != 'undefined' && muse.LOG)
		console.log(mesg);
	// log on server only if log_on_server is undefined or true
	if (typeof log_on_server == 'undefined' || log_on_server)
		muse.post_message(mesg); // post JS message to server
};

// print all members directly in o (and not its supertypes)
// note: does not actually print anything, just returns a string
// print_supertype_fields is off by default
muse.dump_obj = function (o, print_supertype_fields)
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
				s += f + "=" + (f.match(/.*password.*/) ? '***' : o[f]) + ' '; // otherwise write out the value					
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

muse.ellipsize = function(s, maxChars)
{
	if (s == null)
		return null;

	if (maxChars < 0)
		return '';
	
	if (maxChars < 4)
		return (s.substring(0, maxChars));

	if (s.length > maxChars)
		return s.substring(0, maxChars-3) + "...";
	else
		return s;
};

muse.pluralize = function(count, description)
{
	if (count == 1)
		return count + ' ' + description;
	else
		return count + ' ' + description + 's';
};

muse.clearCache = function()
{
	$('#clearCacheSpinner').show();
	$.ajax({url: "clearCache", 
		   dataType: "json",
		   success: function(data) {
			$('#clearCacheSpinner').hide('slow');
			if (data.status == 0)
				$.jGrowl('Archive cleared.');
			else
				$.jGrowl('Problems clearing archive.');			
		   }
	});
};

muse.doLogout = function()
{
//	muse.log ('running on localhost = ' + running_on_localhost);
//	if (running_on_localhost)
	{
		alert ('Muse stores the archive in <HOME>/.muse for fast access the next time you run it. Please clear this cache from the settings page if you are not going to run Muse again.');
		window.location = 'logout.jsp';
	}
//	else
//		window.location = 'logout.jsp?clearCache=true';		
};

/** message is shown as an alert box if the setting is successful */
muse.setConfigParam = function (key, value, message) {
	var page = '/muse/ajax/setConfigParam.jsp?key=' + key + '&value=' + value;
	$.get(page, function() {
		if (typeof message != 'undefined')
			alert (message);
		else {
//			alert (key + ' set to ' + value);
		}
	});
};

// should only be called from settings page, because it refreshes the page to set the logging level
muse.changeLoggingLevel = function(event) { 
	var level = event.target.value;
	var logger = event.target.getAttribute('class');
	$.get('/muse/ajax/changeLoggingLevel.jsp?logger=' + logger + '&level=' + level, function() { window.location='settings';});
};

/** collects all input fields on the page, and makes an object out of them with the field names as property names */
muse.collect_input_fields = function() {
	var result = {};
	$('input,select').each (function() {  // select field is needed for accounttype
		if ($(this).attr('type') == 'button') { return; } // ignore buttons (usually #gobutton)
		if ($(this).attr('type') == 'checkbox') {
			if ($(this).is(':checked'))
			{
				result[this.name] = 'on';
				muse.log ('checkbox ' + this.name + ' is on');
			}
			else
				muse.log ('checkbox ignored');
		}
		else {
			result[this.name] = this.value;
		}
	});
	
	return result;
};

/** takes the input fields and does logins, showing spinners while logging is on. returns false if an obvious error occurred. */
var n_login_attempts = 0;
muse.do_logins = function() {	
	muse.log ('doing login (go button pressed) for ' + $('#loginName0').val());
	n_login_attempts++;
	if (n_login_attempts % 3 == 0) {
			alert('Please ensure that popups are enabled in your browser.');
	}
	var post_params = muse.collect_input_fields();
	// do not print post_params as it may have a passwd
		var $accounts = $('.accountType');
		muse.log ('#accounts = ' + $accounts.length);
		var accountIdx = 0;
		var n_valid_accounts = 0;
		for (var i = 0; i < $accounts.length; i++)
		{
			// its a valid a/c if its got a login and passwd
			var $login = $('#loginName' + i);
			if ($login.length > 0)
			{
				var login = $login.val();
				var pw = $('#password' + i).val();
				// ignore if has login name and password fields, but not filled in
				if (login == null || login.length == 0 || pw == null || pw.length == 0)
					continue;
			}
			n_valid_accounts++;
		}
		
		muse.log ('n_valid_accounts = ' + n_valid_accounts + ' out of ' + $accounts.length);

		if (n_valid_accounts === 0)
		{
			$.jGrowl('Please enter some login information');
			return;
		}
		
		for (var i = 0; i < $accounts.length; i++)
		{
			post_params.accountIdx = accountIdx;
			post_params.incremental = 'true';
			$('#spinner' + i).css('visibility', 'visible');
			var n_errors = 0, resp_received = 0;
			muse.log ('logging into account # ' + accountIdx);
			var sent_folder_found = true; // will be set to false for any a/c for which we don't find a valid sent folder
			// do login for this account, all these lookups will be fired in parallel
			// note that all post_params are posted for each a/c... somewhat ineffecient, but not enough to matter
			$.post ('/muse/ajax/doLogin.jsp', post_params, function(i, idx) { return function(data) {
				muse.log ('received resp for login ' + idx + ':' + muse.dump_obj(data));
				$('#spinner' + i).css('visibility', 'hidden');
				resp_received++;
				var j = data;
				if (j.status != 0)
				{
					if(j.status == 2) {
						muse.log('openserverinputbox');
						$('option[value=imap]').attr('selected', 'selected');
						$('#server' + i).css('display', 'block').focus();
						$('#message' + i).text('');
					}
					
					muse.log ('error message: ' + j.errorMessage);
					$.jGrowl(j.errorMessage);
					n_errors++;
				}
				else if ($('#sent-messages-only').attr('checked') && (typeof j.defaultFolderCount == 'undefined' || j.defaultFolderCount < 0))
				{
					sent_folder_found = false;
					var message = 'Unable to find default sent folder: ' + j.defaultFolder + ' for account#' + idx + '. You will have to choose folders manually.';
					muse.log(message);
					// don't like alerts, but can't help it here. growl is not sufficient, because we're going to move off the login page to the folders page soon.
					alert(message);
				}
				else if (typeof j.defaultFolderCount != 'undefined')
					muse.log('Default folder ' + j.defaultFolder + ' has ' + j.defaultFolderCount + ' messages');
				
				// will be nice to give some indicator of login status here
				muse.log ('login responses received for account #' + i + ' ' + n_errors + ' error(s)');

				// if any errors, we'd have growled, we just don't move to the next page
				if (resp_received == n_valid_accounts && n_errors == 0)
					muse.all_logins_complete(sent_folder_found);
			};}(i, accountIdx), 'json');
			accountIdx++;
		};
	return true;
};

muse.all_logins_complete = function(sent_folder_found) {
	muse.log ('all login responses received');
	var params = muse.collect_input_fields();

	/*
	if ($('#downloadAttachments').attr('checked')) {
		muse.log ('download attachments is on');
		params = 'downloadAttachments=true';
	} else
		params = '';
	*/
	if (sent_folder_found && $('#sent-messages-only').attr('checked'))
	{
		// sent messages only.. directly go to doFetch...
		if (muse.mode && 'memorytest' == muse.mode) 
			muse.do_memory_test();
		else {
			var page = "ajax/doFetchAndIndex.jsp?simple=true";

			// add dateRange to page if present
			try {
				var $dateRange = $('#dateRange');
				if ($dateRange.length > 0)
					if ($dateRange.val() && $dateRange.val().length > 0)
						page += ('&dateRange=' + $dateRange.val());
			} catch (e) { muse.log ("exception trying to get date range: " + e); }

			// add downloadAttachments=false to page if present
			try {
				var $downloadAttachments = $('#downloadAttachments');
				if ($downloadAttachments.length > 0)
					if ($downloadAttachments.val() && $downloadAttachments.val().length > 0)
						page += ('&downloadAttachments=' + $downloadAttachments.val());
			} catch (e) { muse.log ("exception trying to get downloadAttachments: " + e); }
			
			muse.log ('sent messages only: kicking off fetch+index to page: ' + page);
			fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text'), params);
		}
	}
	else						
	{
		muse.log ('redirecting to folders');
		window.location = 'folders';
	}
};
	
muse.do_memory_test = function()
{
	var params = muse.collect_input_fields();
	muse.log ('sent messages only: kicking off memorytest fetch+index');
	var page = 'ajax/prepareMemoryTest.jsp';
	fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text'), params);
};

muse.submitFolders = function()
{
	try {
		var post_params = getSelectedFolderParams() + getAdvancedParams() + '&period=Monthly';
		// need to check muse.mode here for page to redirect to actually!
		var page = "ajax/doFetchAndIndex.jsp";
		fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text'), post_params);
	} catch(err) { }
}

muse.highlightButton = function(evt)
{
	/*
	var targ;
	var e = evt;
	if (!e) e = window.event;  // needed for IE
	if (e.target) targ = e.target;
	else if (e.srcElement) targ = e.srcElement; // needed for IE

	targ.setAttribute("class", 'hilited-button');
	targ.setAttribute("className", 'hilited-button'); // needed for IE
	*/
};

muse.unhighlightButton = function(evt)
{
	/*
	var targ;
	var e = evt;
	if (!e) var e = window.event; // needed for IE
	if (e.target) targ = e.target;
	else if (e.srcElement) targ = e.srcElement; // needed for IE
	targ.setAttribute("class", 'tools-button');
	targ.setAttribute("className", 'tools-button'); // needed for IE
	*/
};


muse.toggle_advanced_panel = function(elem)
{
	if ($('#div_advanced_text').html().indexOf('Hide') < 0)
	{
		$('#div_advanced').slideDown('fast');
		$('#div_advanced_text').html("Hide advanced controls");
	}
	else
	{
		$('#div_advanced').slideUp('fast');
		$('#div_advanced_text').html("Advanced controls");
	}
};

function toggleVisibility(id)
{
    var elem = document.getElementById(id);
    if (elem.style.display == 'none') {
        elem.style.display = 'block';
    }
    else
        elem.style.display = 'none';
}

//toggle the words "More" and "Less" inside an <a href="...">More/Less</a> situation
function toggleMoreAndLess(elem) { $(elem).text('More' == $(elem).text() ? 'Less':'More'); }

muse.post_message = function(mesg)
{
	$.post('/muse/ajax/muselog.jsp', {'message': mesg.toString()});
};

// trim for JS
muse.trim = function(s) {
	if (typeof (s) !== 'string')
		return s;

	// trim leading
	while (true) {
		if (s.length == 0)
			break;
		var c = s.charAt(0);
		if (c !== '\n' && c !== '\t' && c !== ' ')
			break;
		s = s.substring(1);
	}
	
	// trim trailing
	while (true) {
		if (s.length == 0)
			break;
		var c = s.charAt(s.length-1);
		if (c !== '\n' && c !== '\t' && c !== ' ')
			break;
		s = s.substring(0,s.length-1);
	}
	return s;
};

muse.reveal = function(elem, show_less) 
{ 
	if (typeof(show_less) == 'undefined')
		show_less = 'true'; // true by default
	if ($(elem).text() == 'More')
	{
		// sometimes we want to only do the "more" without an option for "less"
		if (show_less)
			$(elem).text('Less'); 
		else
			$(elem).text('');
		$(elem).prev().show();
	}
	else
	{
		$(elem).text('More'); 
		$(elem).prev().hide();
	}
	return false; //  usually we don't want the click to ripple through
};

//toggle the words "Show" and "Hide" inside an <a href="...">More/Less</a> situation
//warning: depends on jquery
function toggleShowAndHide(elem) { $(elem).text(('Show' == $(elem).text()) ? 'Hide':'Show'); }

//necessary to escape the accountName as it may have periods.
//see http://docs.jquery.com/Frequently_Asked_Questions#How_do_I_select_an_item_using_class_or_ID.3F
function escapeIdForJquery(myid) { return '#' + myid.replace(/(:|\.)/g,'\\$1'); }
muse.pluralize = function(count, descr) { return count + " " + descr + ((count > 1) ? "s":""); }

muse.approximateTimeLeft = function(sec)
{
	var h = parseInt(sec/3600);
	var m = parseInt((sec%3600)/60);
	
	if (sec > 2 * 3600)
		return "About " + h + " hours left";
	if (h == 1 && m > 5)
		return "About an hour and " + muse.pluralize(m, 'minute');
	if (h == 1)
		return "About an hour";
	if (sec > 120)
		return "About " + m + " minutes left";
	if (sec > 90)
		return "A minute and a bit...";
	if (sec > 60)
		return "About a minute...";
	if (sec > 30)
		return "Less than a minute...";
	if (sec > 10)
		return "Less than half a minute...";	
	return "About 10 seconds...";
};

muse.refresh_page = function() { window.location.reload();};

/* kinda private function used to save/delete/load a session */
muse.do_session = function(params, spinner_selector, redirect_url, status_handler)
{
	// make sure session name is valid
	if (!params.title)
		return;
	params.title = muse.trim(params.title);
	if (!params.title)
		return;
		
	$(spinner_selector).css('visibility', 'visible');

	var err_func = function() { 
		$(spinner_selector).css('visibility', 'hidden');
		alert ('Sorry! Muse is unable to ' + params.verb + ' the session ' + params.title + '. Please tell us about this error by clicking the \"Report Error\" link at the bottom of the page'); 
	};

	var url = '/muse/ajax/doSessions.jsp';
	// $(document).ajaxError(err_func);
	var jq_xhr = $.post(url, params, function(stat) {
		$(spinner_selector).css('visibility', 'hidden');
		if (typeof stat.status !== 'undefined' || stat.status == 0) {
			muse.log (params.verb + ' session for ' + params.title + ' returned success ' + muse.dump_obj(stat));
			if (status_handler)
				status_handler(stat);
			if (redirect_url)
				window.location = redirect_url;
		}
		else {
			muse.log ('session ' + params.verb + ' for ' + params.title + ' returned failure ' + muse.dump_obj(stat)); // consider reflecting stat.errorMessage to the user
			if (status_handler)
				status_handler(stat);
			else
				err_func(); // default failure handler
		};
	}, 'json');

	// immediate assign an err handler
	jq_xhr.error(err_func);

};

muse.load_archive = function(base_dir, spinner_selector, redirect_url) { 	muse.do_session({verb: 'load_archive', title: base_dir}, spinner_selector, redirect_url); };
muse.load_session = function(session, spinner_selector, redirect_url) { 	muse.do_session({verb: 'load', title: session}, spinner_selector, redirect_url); };
muse.delete_session = function(session, spinner_selector, redirect_url) { 	muse.do_session({verb: 'delete', title: session}, spinner_selector, redirect_url); };
muse.save_session = function(session, spinner_selector, redirect_url) { 	muse.do_session({verb: 'save', title: session}, spinner_selector, redirect_url); };

muse.export_archive = function(name, trim_archive, for_public, spinner_selector, redirect_url, status_handler) {
	params = { verb: 'export', title: name };
	if (trim_archive)
		params.trimArchive = 1;
	if (for_public)
		params.forPublicMode = 1;
	muse.do_session(params, spinner_selector, redirect_url, status_handler);
};

var waitForFinalEvent = (function () {
	  var timers = {};
	  return function (callback, ms, uniqueId) {
	    if (!uniqueId) {
	      uniqueId = "Don't call this twice without a uniqueId";
	    }
	    if (timers[uniqueId]) {
	      clearTimeout (timers[uniqueId]);
	    }
	    timers[uniqueId] = setTimeout(callback, ms);
	  };
	})();


// note cancel has to be defined before show, because it uses the name cancel_filter
muse.cancel_filter = function() { 
	$('#filter-div').hide();
	$('.muse-overlay').hide();
};

muse.show_filter = function() {
	// don't explicitly change height. better to set it to css height 100%. this will update the height if the div height changes.
//	$('.muse-overlay').height($(document).height());		
//   $('.muse-overlay').width($(document).width());		
    $('.muse-overlay').show();		
    $('#filter-div').css ('left', (window.innerWidth/2)- 250+"px");
    $('#filter-div').css ('top', (window.innerHeight/2)- 185+"px"); // the total width is 200px
    $('#filter-div').css ('width', '500px');
    $('#filter-div').css ('height', '370px');
    // this code is duplicated from filter.jsp
    $('#filter-div').html('<div style="padding:7px;margin:7px;">'
    		+ '<div style="position:relative">'
    		+ '<div style="float:left">'
    		+ '<h2><img style="width:20px;position:relative;top:2px"src="images/filter-icon.png">Message Filters'
    		+ '&nbsp;<img width="15" src="images/spinner.gif"/>'
    		+ '</h2></div>'
    		+ '<div style="clear:both"></div>'
    		+ '</div>'
    		+ '<hr style="width:95%;color:rgba(0,0,0,0.2)"/>');
	$('#filter-div').show();

	$('#filter-div').load('/muse/filter.jsp', function(responseText, textStatus) {
		if (textStatus == "error") {
			$('#filter-div').hide();
		    $('.muse-overlay').hide();		
			alert ('Unable to compute filter statistics. Muse may be down?');
			return;
		};
		$('.cancel-filter').click(muse.cancel_filter);
		$(document).keyup(function(e) {
			  if (e.keyCode == 27) { muse.cancel_filter(); }   // esc
		});
	});
};

muse.load_grouping = function(grouping_name, spinner_selector, redirect_url)
{
	if (typeof(grouping_name) == 'undefined')
		return;
	grouping_name = muse.trim(grouping_name);
	if (grouping_name.length == 0)
		return;
	var url = '/muse/ajax/doGroupConfigs.jsp';
	$(spinner_selector).css('visibility', 'visible');
	$.ajax({
		url: url, 
		data: {'load': grouping_name}, 
		dataType: 'json',
		success: function(data) {
		$(spinner_selector).css('visibility', 'hidden');
		muse.log ('group load returned ' + data);
		if (redirect_url)
			window.location = redirect_url;
		},
		error: function(jqxhr, textStatus, errorThrown) {
			$(spinner_selector).css('visibility', 'hidden');
			alert ("Sorry, this grouping cannot be loaded because it may have been created with a different version of Muse. Please delete this group, and try again. If the problem persists, please clear your cache from the settings page, and then retry.");
			muse.log (textStatus + " Exception: " + errorThrown);
		}
	});
};

muse.save_grouping = function(grouping_name, spinner_selector, redirect_url) {
	var url = '/muse/ajax/doGroupConfigs.jsp'; 
	$(spinner_selector).css('visibility', 'visible');
	$(document).ajaxError();
	$.ajax({
			type:'POST',
			url: url,
			dataType: 'json',
            data: {'save': grouping_name}, 
            success: function(data) {
				$(spinner_selector).css('visibility', 'hidden');
				muse.log ('group save returned ' + data);
				if (redirect_url)
					window.location = redirect_url;
            },
            error: function(jqxhr, textStatus, errorThrown) {
    			$(spinner_selector).css('visibility', 'hidden');
    			alert ("Sorry, this grouping could not be saved. If the problem persists, please report the error using the link at the bottom of this page");
    			muse.log (textStatus + " Exception: " + errorThrown);
    		}
	});
};


muse.delete_grouping = function(grouping_name, spinner_selector, redirect_url)
{
	if (typeof (grouping_name) == 'undefined')
		return;
	
	grouping_name = muse.trim(grouping_name);
	if (grouping_name.length == 0)
		return;
	
	var url = '/muse/ajax/doGroupConfigs.jsp';
	$(spinner_selector).css('visibility', 'visible');
//	$(document).ajaxError(function() { 
//		alert ('Sorry! Muse is unable to delete the grouping. Please tell us about this error by clicking the \"Report Error\" link at the bottom of the page.');
//	});
	$.ajax({
		type: 'POST',
		url: url,
//		dataType: 'json', // we're not really using the return value
 		data: {'delete': grouping_name}, 
		success: function(data) {
		$(spinner_selector).css('visibility', 'hidden');
		muse.log ('grouping delete returned ' + data);
		if (redirect_url)
			window.location = redirect_url;
		}, 
		error: function(jqxhr, textStatus, errorThrown) {
			$(spinner_selector).css('visibility', 'hidden');
			alert ("Sorry, this grouping could not be saved. If the problem persists, please report the error using the link at the bottom of this page");
			muse.log (textStatus + " Exception: " + errorThrown);
		}
	});
};

muse.openQVP = function(e, isWindows, qvpExist)
{
	if(isWindows)
	{
		var targ = null;
		if (!e) var e = window.event;
		if (e.target) targ = e.target;
		else if (e.srcElement) targ = e.srcElement;
		if (targ.nodeType == 3)
		targ = targ.parentNode;
		
		var name = targ.getAttribute("id");
		var r = confirm("Would you like to preview the file with Quick View Plus?");
		if(r == true)
		{
			if(qvpExist){
				$.get(
						"/muse/ajax/attachmentQVP.jsp",
						{attachmentName:name}
					);	
			} else {
				alert("You Quick View Plus directory doesn't seem to be correct, please check in setting");
			}
				
		}
	}
		
			
};

muse.getURLParameter = function(name, url)
{
    if (!url) url = location.search;
    return decodeURI(
        (RegExp(name + '=' + '(.+?)(&|$)').exec(url)||[,null])[1]
    );
};

// catchall ajax error function
$(document).ajaxError(function(e, jqxhr, settings, exception) {
	// don't log this error on server, if the url itself was muselog.jsp  -- leads to nasty endless inovacations
	var log_on_server = (settings.url.indexOf('/muse/ajax/muselog.jsp')) < 0;
	muse.log ('AJAX ERROR! event = ' + muse.dump_obj (e), log_on_server);
	muse.log ('jqxhr = ' + muse.dump_obj (jqxhr), log_on_server);
	muse.log ('settings = ' + muse.dump_obj ( settings), log_on_server);
	muse.log ('exception = ' + muse.dump_obj (exception), log_on_server);
	// don't pop up alerts for silent things like muselog
	if (jqxhr.status === 503 && settings.url.indexOf('/muse/ajax/muselog.jsp') < 0)
		alert ("The Muse server does not appear to be running. Please relaunch Muse.");
});
