// code for groups editor

muse.CURRENT_GROUPMENU_TIMEOUT = null, muse.CURRENT_PERSONMENU_TIMEOUT = null;

$(document).ready(function() {
	var persons = $(".person");

	for (var i = 0; i < persons.length; i++)
	{
//		console.log (typeof(persons[i].draggable));
//		console.log (typeof($('#diaryarea').draggable));
	//	persons[i].draggable();
	//	$(persons[i]).draggable();
	}

//	$('.group').each(function() { $(this).droppable({drop: f})});

	// make groups themselves movable
	$('#allGroups').sortable({items:'div.group'});

	var getMouseEnterHandler = function($menu) { return function() { $menu.fadeIn('fast');};};
	$('div.group').live('mouseenter', function() { var h = getMouseEnterHandler($('.groupsmenu', this)); muse.CURRENT_GROUPMENU_TIMEOUT = setTimeout (h, 500); });
	$('div.group').live('mouseleave', function() { 
			if (muse.CURRENT_GROUPMENU_TIMEOUT) { clearTimeout(muse.CURRENT_GROUPMENU_TIMEOUT); muse.CURRENT_GROUPMENU_TIMEOUT = null;} 
			$('.groupsmenu', this).fadeOut('fast'); 
		});

	var getMouseEnterHandlerPerson = function($menu) {
		return function() { 
			$menu.css('visibility', 'visible'); 
			$menu.css('background-color', 'yellow'); 
			$menu.closest('.person').addClass('hilited-person');
		};
	};

	// smaller timeout (300 instead of 500) seems better for person
	$('.person').live('mouseenter', function() { var h = getMouseEnterHandlerPerson($('.personmenu', this)); muse.CURRENT_PERSONMENU_TIMEOUT = setTimeout (h, 300);});
	$('.person').live('mouseleave', function() { 
		if (muse.CURRENT_PERSONMENU_TIMEOUT) { clearTimeout(muse.CURRENT_PERSONMENU_TIMEOUT); muse.CURRENT_PERSONMENU_TIMEOUT = null;} 
		$('.personMenu', this).css('visibility', 'hidden'); 
		$('.personMenu', this).css('background-color', 'white');
		$('.personMenu', this).closest('.person').removeClass('hilited-person');
	});
//	$('span.nameAndImage').live('mouseenter', function() {$('.personMenu', this).css('visibility', 'visible'); $('.personMenu', this).closest('.person').css('background-color', 'yellow');});
//	$('span.nameAndImage').live('mouseleave', function() {$('.personMenu', this).css('visibility', 'hidden'); $('.personMenu', this).closest('.person').css('background-color', 'white');});

	enableDND();

});

var dndLogStr = '';
function dndLog(str)
{
	muse.log(str);
	dndLogStr += str + '\n';
}

function enableDND()
{
$('ul.personList').each (function() {
		var that = $(this);
		$(this).sortable(
		{
			start: function(event, ui) {
			},
			stop: function(event, ui) {
			},
			receive: function(event, ui) {
				// need to hide the menu of the sender
				$('.groupsmenu', ui.sender).hide();

				// process incoming item.

				// 1. unhighlight if its hilited.
				$(ui.item).removeClass('hilited-person'); // unhighlight person if he has hilited
				// 2. style for incoming li should be defined only if its coming from the ALL group.
				// everything in the style (inline, float, width) is needed only in ALL group
				// warning: dependency on the ALL group
				$(ui.item).removeAttr('style');
				var fromGroup_persons = $('li.person', ui.sender);
				var toGroup_persons = $('li.person', that);
				dndLog('moved a person. new group sizes: from group: ' + fromGroup_persons.size() + ' to group: ' + toGroup_persons.size());

				// delete sender if its length is 0
				if (fromGroup_persons.length == 0)
					deleteGroup(ui.sender[0]);

			//	alert (ui.item.attr("personId") + " dragged to group " + that.attr("groupId") + " from group " +  ui.sender.attr("groupId"));
				saveGroups();
//				event.originalEvent.preventDefault();
		//		event.stopPropagation();
			//	event.stopImmediatePropagation();
			//	event.originalEvent.stopPropagation();
		//		event.originalEvent.stopImmediatePropagation();
		/*		var e = event.originalEvent;
				if (e)
				{
					e.cancelBubble = true;
					if (e.stopPropagation) e.stopPropagation();
				}
				var e = event.originalEvent.originalEvent;
				if (e)
				{
					e.cancelBubble = true;
					if (e.stopPropagation) e.stopPropagation();
				}
				*/
				return false;
			}, connectWith: 'ul.personList',
			items: 'li.person'
		});
		$(this).disableSelection(); // disables text selection within the group
});
$('li>input.groupname').blur(saveGroups);
}

function saveGroups()
{
	var groups = new Array();
	var groupId = 0;
	$('#allGroups ul.personList').each (function() { // be careful only to pick up the ones under allgroups, not the everyone else group
		var groupname = $('li>input.groupname', $(this)).val();
		if (groupname && groupname != 'Label')
		{
			muse.log('name = ' + groupname);
		}
		else
			groupname = '';
		var persons = $('li.person', $(this));
		if (persons.length > 0)
		{
			groups[groupId] = {'name': groupname, 'members':[]};
			$('li.person', $(this)).each (function() { groups[groupId].members.push($(this).attr('personId')); });
			groupId++;
		}
	});
	var json = JSON.stringify(groups, null);
	// dndLog ('Current status: ' + groupId + ' groups');
	$.post("ajax/updateGroups.jsp", {'groups': json, 'groupEditorLog': dndLogStr}, function(response) { dndLog(groupId + ' groups posted successfully'); });
}

var groupToMerge, groupToMergeDiv;

function mergeGroup(groupDiv)
{
	var thisGroup = $(groupDiv).closest('div.group');
	dndLog('mergeGroup called');
	// groupToMerge will store the (first) group selected for merging
	if (groupToMerge)
	{
		if (groupToMerge[0] === thisGroup[0]) // check if underlying div is the same, if so just toggle the merge selection
		{
			groupToMerge.removeClass('hilited-group'); // ('hilited-group'); // lemon chiffon
			groupToMerge.removeClass('hilited-group'); // ('hilited-group'); // lemon chiffon
//			groupToMerge.removeClass('hilited-group');
			groupToMerge = null;
			dndLog('dehighlighted group');
			return;
		}

		var thisGroup_persons = $('li.person', thisGroup);
		var groupToMerge_persons = $('li.person', groupToMerge);
		dndLog('merging ' + thisGroup_persons.size() + ' people with ' + groupToMerge_persons.size());
		for (var i = 0; i < groupToMerge_persons.size(); i++)
		{
			var personid = groupToMerge_persons[i].getAttribute("personid");
			// dndLog(' checking ' + personid);
			var x = thisGroup_persons.filter(function(index) { return thisGroup_persons[index].getAttribute("personId") === personid; });
			// if x.size() = 0 it means the person is not already present in this group
			if (x.size() == 0)
			{
				var personList_jq = $('ul.personList', thisGroup);
				// add html for this person into the new group
				personList_jq.append(groupToMerge_persons[i]);
				// dndLog('added ' + personid);
			}
		}
		deleteGroup($(groupToMerge)[0]); // delete group needs the actual div, not the jq
		groupToMerge = null;
		saveGroups();
	}
	else
	{
		// no prev. group selected for merge, so select this group and change its bg color
		var thisGroupPerson = $('li.person', thisGroup);
		var thisGroupColor = (thisGroupPerson.length > 0) ? $(thisGroupPerson[0]).css('color') : 'yellow';
		
		groupToMerge = thisGroup;
		groupToMerge.addClass('hilited-group'); // css('background-color', '#fffacd'); // ('hilited-group'); // lemon chiffon
		groupToMerge.css('border-color', thisGroupColor); // css('background-color', '#fffacd'); // ('hilited-group'); // lemon chiffon
		
		dndLog('group of size ' + $('li.person', groupToMerge).length + ' selected for merging');
	}
}

function deletePerson(person)
{
	var person_jq = $(person).closest('li.person');
	var group_jq = $(person).closest('ul.personList'); // containing group

	$(person_jq).fadeOut();
	$(person_jq).remove();
	// if group is now empty, delete it
	if ($('li.person', group_jq).length == 0)
		deleteGroup(group_jq);
	saveGroups();
}

function clonePerson(person)
{
	var person_jq = $(person).closest('li.person');
	var clone = person_jq.clone();
	person_jq.after(clone);
}

function deleteGroup(groupdiv)
{
	var groupdiv = $(groupdiv).closest('div.group');
	groupdiv.fadeOut('slow');
	groupdiv.remove();
	var deletedGroupLength = $('li.person', groupdiv).length;
	dndLog('deleted a group of size ' + deletedGroupLength);
	saveGroups();
}

function cloneGroup(groupdiv)
{
	var groupdiv = $(groupdiv).closest('div.group');
	var clone = groupdiv.clone();
	groupdiv.after(clone);
	var clonedGroupLength = $('li.person', groupdiv).length;
	enableDND();
	dndLog('cloned a group of size ' + clonedGroupLength);
	$('.groupsmenu', clone).hide();

//	clone.mouseenter(function() {$('.groupsmenu', this).show();}).mouseleave(function() {$('.groupsmenu', this).hide();}); // add this action to the clone
	saveGroups();
}

function groupNameEditStart(elem, v)
{
	if ($(elem).val() == v)
	{
		$(elem).val(''); // clear if the value of the input field is the default value
		$(elem).removeClass('unhilited-group-name');
		$(elem).addClass('hilited-group-name');
	}
	return true; // let element ripple
}

function groupNameEditEnd(elem)
{
	saveGroups();
}

