
function get_account_div(accountName, accountIdx)
{
	// if querying by name, necessary to escape the accountIdx as it may have periods.
	// see http://docs.jquery.com/Frequently_Asked_Questions#How_do_I_select_an_item_using_class_or_ID.3F
	var accounts = $('.account');
	if (accounts.length == 0)
		throw("ERROR");
	var accountDiv = $(accounts[accountIdx]);
	return accountDiv;
}

function render_folders_box(accountName, accountIdx, accountStatus)
{
	var accountDiv = get_account_div(accountName, accountIdx);
	var accountHeader = $('.accountHeader', accountDiv);
	var accountBody = $('.foldersTable', accountDiv);

	var nFoldersAlreadyShown = 0;
	var prev_folders = $('.folder', accountDiv);
	if (prev_folders)
		nFoldersAlreadyShown = prev_folders.length;

	var folderInfos = accountStatus.folderInfos;
	if (!folderInfos || !folderInfos.length)
		return; // we're not ready yet, just return quietly
	if (folderInfos.length > 1)
		$('#select_all_folders').show();

	// store the status of checked folders in the existing doc, and check them again later
	var checked = new Array();
	for (var i = 0; i < prev_folders.length; i++)
		checked[i] = prev_folders[i].checked;

	if (!accountStatus.doneReadingFolderCounts)
		accountHeader.html('Scanning ' + accountName + '...');
	else
		accountHeader.html(accountName + ' (' + folderInfos.length + ((folderInfos.length > 1) ? ' folders' : ' folder') + ')');

	// $('.nFolders', accountDiv).html(folderInfos.length);

	muse.log ('we already have ' + nFoldersAlreadyShown + ' folders, new status has ' + folderInfos.length);
	muse.log (accountStatus);

	var html = '';
//	for (var i = nFoldersAlreadyShown; i < folderInfos.length; i++)
	for (var i = 0; i < folderInfos.length; i++)
	{
		if (i == 0)
			html += '<tr>';

		var folderInfo = folderInfos[i];
		// if the folder was already selected (checked[i], give it a selected-folder class so it'll be highlighted
		var classStr = (checked[i]) ? 'folderEntry selected-folder' : 'folderEntry';
	    html += '<td title="' + folderInfo.longName + '" class=\"' + classStr + '\">\n'; // .folderEntry does the same as min-width:225.

	    var checkedStr = (checked[i]) ? 'CHECKED' : '';
	    html += '<INPUT class="folder" onclick=\"updateFolderSelection(this)" TYPE=CHECKBOX STORE="' + accountName + '" NAME="' + folderInfo.longName + '"' + checkedStr + '/>';
	    html += '&nbsp;' + muse.ellipsize(folderInfo.shortName, 15);
	    if (folderInfo.messageCount >= 0)
		    html += ' (' + folderInfo.messageCount + ')';
		else
		    html += ' (<img width="15" style="position:relative; top:3px" src="images/spinner.gif"/>)'; // top:3px because image appears slightly too high

	    html += '</td>\n';

	    if ((i+1)%numFoldersPerRow === 0) // numFoldersPerRow defined in /folders
		    html += '\n</tr>\n<tr>';
	}

	if ('' !== html)
	{
	//	console.muse.log ('html being added is ' + html);
	//	$(accountBody).append(html);
		accountBody.html(html);
	}
}

// displays folders and counts for the given account
// first is true only when called from the caller, not when this fn resched's itself
function display_folders(accountName, accountIdx, first)
{
	if (first)
	{
		var accountDiv = get_account_div(accountName, accountIdx);
		var accountHeader = $('.accountHeader', accountDiv);
		accountHeader.html("Scanning " + accountName);
		muse.log ('Starting to read account ' + accountIdx + ' ' + accountName);
	}

	muse.log ('Starting to getFolderInfos... for acct#' + accountIdx + ' ' + accountName);

	// note: .ajax instead of .get because it allows us to disable caching
	// refreshing folders while reading them wasn't working earlier inspite if cache control header from getFolderInfos.jsp
	// TODO: maybe should detect if any folders have already been loaded into archive and have them initially checked.
	//       but should also give some indicator that a sync is needed, i.e., if the (server) folder has been updated since it was previously loaded.
	$.ajax({
		type:'GET',
		url: 'ajax/getFolderInfos.jsp?account=' + accountIdx, 
		cache: false,
		success: function (response, textStatus) {
				// response is a json object
				var accountStatus =  response;
				muse.log ('updating folders box for accountIdx ' + accountIdx + ': '  + accountName + ' http req. status = ' + textStatus);
				render_folders_box(accountName, accountIdx, accountStatus);
				// if we're not done, schedule refresh_folders again after some time
				if (typeof accountStatus.doneReadingFolderCounts == 'undefined' || !accountStatus.doneReadingFolderCounts)
					setTimeout(function() { display_folders(accountName, accountIdx, false); }, 1000);
			}
	});
}

function toggle_select_all_folders(elem) { 
	var text = $(elem).text();
	muse.log('current text = ' + text);
	muse.log($("input.folder").length);
	if (text.indexOf('Unselect') >= 0) 
	{
		$(elem).text('Select all folders'); 
		$("input.folder").attr("checked", false);
		$("input.folder").closest('.folderEntry').removeClass('selected-folder');
	}
	else
	{
		$(elem).text('Unselect all folders'); 
		$("input.folder").attr("checked", true);
		$("input.folder").closest('.folderEntry').addClass('selected-folder');
	}
}

function updateFolderSelection(o)
{
	if (o.checked == true)
		$(o).closest('.folderEntry').addClass('selected-folder');
	else
		$(o).closest('.folderEntry').removeClass('selected-folder');
}

function updateAllFolderSelections(o)
{
	// warning: this code not really tested
	// repaint selected folder indication for all folders
    var unchecked = $('input.folder:not(:checked)');
    unchecked.each(function (index, elem) {
    	$(elem).closest('.folderEntry').removeClass('selected-folder');
    });
    var checked = $('input.folder:checked');
    checked.each(function (index, elem) {
    	$(elem).closest('td.folderEntry').addClass('selected-folder');
    });
}

