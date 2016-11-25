
// throws an exception if at least one folder is not selected
function getSelectedFolderParams() {
    var checked = $('input.folder:checked');
    var urlParams = "";
    if (checked.length == 0)
    {
        alert ("Please select at least one folder");
        throw "Error";
    }

    for (var i=0; i < checked.length; i++)
    {
         var store = checked[i].getAttribute("STORE");
         urlParams += encodeURI("folder=" + store + '^-^' + checked[i].name + "&");
    }
    console.log ('urlparams = ' + urlParams);
    return urlParams;
}

function getAdvancedParams()
{
	var s = filterPersonOrEmail() + getSentOnly() + getDateRange() + getKeywords()
          + getIncrementalTFIDF() + getNER() + getAllText() + getLocationsOnly() + getOrgsOnly() + getSubjectWeight() + includeQuotedMessages()
          + downloadMessageText() + downloadAttachments()
          + getTagsForGroups() + getNumGroups() + getErrWeight() + getDisabledMoves();
	// muse.log(s);
	return s;

	function filterPersonOrEmail()
	{
		try {
		    var f = document.getElementById("filterPersonOrEmail").value;
		    return '&filterPersonOrEmail=' + f;
		} catch(err) { return '';}
	}

	function getSentOnly()
	{
	    var f = document.getElementById("sentOnly").checked;
	    return '&sentOnly=' + f;
	}

	function getDateRange()
	{
		var selection = document.getElementById('dateRange');
		if (selection.value != null)
	     	return '&dateRange=' + selection.value;
		else
			return '';
	}

	function getKeywords()
	{
		var selection = document.getElementById('keywords');
		if (selection.value != null)
	     	return '&keywords=' + selection.value;
		else
			return '';
	}

	function getIncrementalTFIDF()
	{
		// advanced params should be inside try-catch because adv. control panel may be missing
		try {
		    var f = document.getElementById("incrementalTFIDF").checked;
		    return '&incrementalTFIDF=' + f;
		} catch(err) { return '';}
	}


	function getNER()
	{
		// advanced params should be inside try-catch because adv. control panel may be missing
		try {
		    var f = document.getElementById("NER").checked;
		    return '&NER=' + f;
		} catch(err) { return '';}
	}


	function getAllText()
	{
		// advanced params should be inside try-catch because adv. control panel may be missing
		try {
		    var f = document.getElementById("allText").checked;
		    return '&allText=' + f;
		} catch(err) { return '';}
	}
	
	function getLocationsOnly()
	{
		// advanced params should be inside try-catch because adv. control panel may be missing
		try {
		    var f = document.getElementById("locationsOnly").checked;
		    return '&locationsOnly=' + f;
		} catch(err) { return '';}
	}

	function getOrgsOnly()
	{
		// advanced params should be inside try-catch because adv. control panel may be missing
		try {
		    var f = document.getElementById("orgsOnly").checked;
		    return '&orgsOnly=' + f;
		} catch(err) { return '';}
	}

	function getSubjectWeight()
	{
		try {
		    var f = document.getElementById("subjectWeight").value;
		    return '&subjectWeight=' + f;
		} catch(err) { return '';}
	}

	function getNumGroups()
	{
		try {
		    var f = document.getElementById("numGroups").value;
		    return '&numGroups=' + f;
		} catch(err) { return '';}
	}

	function getErrWeight()
	{
		try {
		    var f = document.getElementById("errWeight").value;
		    return '&errWeight=' + f;
		} catch(err) { return '';}
	}

	function includeQuotedMessages()
	{
		try {
		    var f = document.getElementById("includeQuotedMessages").checked;
		    return '&includeQuotedMessages=' + f;
		} catch(err) { return '';}
	}

	function downloadMessageText()
	{
		try {
		    var f = document.getElementById("downloadMessageText").checked;
		    return '&downloadMessageText=' + f;
		} catch(err) { return '';}
	}

	function downloadAttachments()
	{
		try {
		    var f = document.getElementById("downloadAttachments").checked;
		    return '&downloadAttachments=' + f;
		} catch(err) { return '';}
	}

	function includeQuotedMessages()
	{
		try {
		    var f = document.getElementById("includeQuotedMessages").checked;
		    return '&includeQuotedMessages=' + f;
		} catch(err) { return '';}
	}

	function getDateSpec()
	{
		try {
			var f = document.getElementById('dateSpec').value;
			return '&dateSpec=' + f;
		} catch (err) { return ''; }
	}

	function getTagsForGroups()
	{
		try {
		    var f = document.getElementById("tagsForGroups").checked;
		    if (!f)
		    	return '';
		    else
		    	return '&tagsForGroups=true';
		} catch (err) { return ''; }
	}

	function getDisabledMoves()
	{
		var result = '';
		try {
		    if (document.getElementById("disableDropMove").checked)
		    	result += '&disableDropMove=true';
		    if (document.getElementById("disableMergeMove").checked)
		    	result += '&disableMergeMove=true';
		    if (document.getElementById("disableIntersectMove").checked)
		    	result += '&disableIntersectMove=true';
		    if (document.getElementById("disableDominateMove").checked)
		    	result += '&disableDominateMove=true';
		    return result;
		} catch (err) { return ''; }
	}
}

function setUnifiedGroupAlg(elem) {
	if (elem.checked)
		muse.setConfigParam('unifiedGroupsAlg', true);
	else
		muse.setConfigParam('unifiedGroupsAlg', false);
}

/*
function doLists()
{
	try {
		var page = "ajax/doLists.jsp?" + getSelectedFolderParams() + getAdvancedParams() ;
	   	fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text'));
	} catch(err) { }
}

function doInterestingDates()
{
	try {
		var page = "ajax/doInterestingDates.jsp?" + getSelectedFolderParams() + getAdvancedParams();
		fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text'));
	} catch(err) { }
}

function doPurchases()
{
	try {
		var page = "ajax/purchases.jsp?" + getSelectedFolderParams() + getAdvancedParams() ;
	   	fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text'));
	} catch(err) { }
}

function doTravel()
{
	try {
		var page = "ajax/travel.jsp?" + getSelectedFolderParams() + getAdvancedParams() ;
	   	fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text'));
	} catch(err) { }
}
*/

m = handleOnBeforeUnload;

function handleOnBeforeUnload()
{
	function operationInProgress()
	{
		var status_div = document.getElementById('status');
		if (status_div != null)
			return (status_div.className.toLowerCase() == 'show');
		else
			return false;
	}

	// see http://www.hunlock.com/blogs/Mastering_The_Back_Button_With_Javascript
	// for how onbeforeunload works
	if (operationInProgress())
	{
		return false;
	}
	// deliberately don't return anything
}
