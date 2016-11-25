// note: this script has its own log, does not use muse.log because it is injected standalone, without muse.js being injected

LOG = true; // needs to be cleaned up, both log and LOG are also in jog.js
usergroupname="";
usergroupflag=false;
function log(mesg)
{
//	alert ('logging ' + mesg);
	if (!(typeof console == 'undefined') && (LOG == true))
		console.log(mesg);
}

/* jquery.js */
function inc(filename)
{
	var body = document.getElementsByTagName('body').item(0);
	script = document.createElement('script');
	script.src = filename;
	script.type = 'text/javascript';
	body.appendChild(script)
}


//tofix: replace with assoc. arrays @fixed

//JSONscriptRequest -- a simple class for accessing  Web Services
//using dynamically generated script tags and JSON
//Constructor -- pass a REST request URL to the constructor
function JSONscriptRequest(fullUrl) {
	// REST request path
	this.fullUrl = fullUrl;
	// Keep IE from caching requests
	this.noCacheIE = '?noCacheIE=' + (new Date()).getTime();
	// Get the DOM location to put the script tag
	this.headLoc = document.getElementsByTagName("head").item(0);
	// Generate a unique script tag id
	this.scriptId = 'YJscriptId' + JSONscriptRequest.scriptCounter++;
}
JSONscriptRequest.jsonurl="";
//highly critical function!
function publish_facebook_groups(postformid,fbdtsgval,ownerid,groupsAsJson)
{
	var user_name = [];
	var user_id = [];
	var user_attrib=[];
	callAjax_get_friends(postformid,fbdtsgval,ownerid,user_attrib);

	var existinggroups=[];
	callAjax_get_groups(existinggroups);
	document.getElementById('light').innerHTML += "Creating lists now...<br/>";
	var listsCreated = 0;

	var statsString="";

	var groupnumber=0;
	for (var groupiterator=0;groupiterator<groupsAsJson.groups.length ;groupiterator++ )
	{
		
		var member_ids=[];
		var countofmembers=0;
		//@d2 fixed bug related to user_id1 need to fix variable names!
        if ((typeof groupsAsJson.groups[groupiterator].name)== 'undefined'||groupsAsJson.groups[groupiterator].name.length==0)
        {
        	usergroupname="";
        	usergroupflag=false;
        }
        else
        {
        	usergroupname=groupsAsJson.groups[groupiterator].name;
        	usergroupflag=true;
        }
		for (var memberiterator=0;memberiterator < groupsAsJson.groups[groupiterator].members.length ;memberiterator++)
		{
			//blank names crashed the script!
			var indexpos=-99;

			for(var iteratenames=0; iteratenames < groupsAsJson.groups[groupiterator].members[memberiterator].names.length;iteratenames++)
			{
				if(groupsAsJson.groups[groupiterator].members[memberiterator].names[iteratenames].length>0)
				{
					//indexpos= binarySearch(user_name, groupsAsJson.groups[groupiterator].members[memberiterator].names[iteratenames]);
					var user_attrib_name=groupsAsJson.groups[groupiterator].members[memberiterator].names[iteratenames];
					if(user_attrib[user_attrib_name])
					{
						log("name is " + user_attrib_name + "user id is " + user_attrib[user_attrib_name]);
						member_ids[countofmembers++]=user_attrib[user_attrib_name];
						//fixed  bug here added break
						break;
					}
					else
					{
						//document.getElementById('light').innerHTML += "Could not find user name: "+ user_attrib_name + "<br/>";
					}

				}
			}


		}

		if (countofmembers>=2)
		{
			var list_id = callAjax_Get_New_Listid(postformid,fbdtsgval,ownerid);
			var new_group_id = existinggroups[0] + groupnumber;
			groupnumber++;
			// tofix: use actual group name if defined, not always group__
			var group_name = 'Group' + new_group_id;
			if(usergroupflag==true)
			{
				group_name =usergroupname;
			}
			callAjax_Push_to_Facebook(group_name, member_ids, countofmembers, postformid, fbdtsgval, ownerid, list_id);//create the list here

			//removing the "" from the list id as it is not working for me!
			callAjax_Push_to_Facebook(group_name, member_ids, countofmembers, postformid, fbdtsgval, ownerid,list_id.slice(1, list_id.length-1));
			listsCreated++;
			//update the pane

			//build up the query string that we will  subsequently pass to the addStatsscript
			statsString=statsString+groupiterator+"."+countofmembers+".";

		}
		else
			statsString=statsString+groupiterator+"."+"0.";

	}
	document.getElementById('light').innerHTML += listsCreated + " lists created!<br/>";
	statsString=statsString+ listsCreated+"."+user_attrib.length+"."+existinggroups[0]; //groupnumber.numberofmembers.listcreated.numberoffacbookfriends.existingroups
	log (listsCreated + ' lists created');

	//here is the call to the function to create the groups
	publish_facebook_groups_new(postformid,fbdtsgval,ownerid,groupsAsJson,user_attrib);


	// now redirect to friends page so user can see the groups
	document.getElementById('light').innerHTML += "Done!<br/>";
	addStatsScript(statsString);
	document.getElementById('light').innerHTML += "Do you want to share this with your friends on Facebook?<br/>";
	document.getElementById('light').innerHTML += '<a href = "javascript:void(0)" onclick = "javascript:publishtowall()"> OK</a> <a href = "javascript:void(0)" onclick = "javascript:redirect()"> Cancel</a> ';
	log('****facebook stats****'+ statsString);


}





function publish_facebook_groups_new(postformid,fbdtsgval,ownerid,groupsAsJson,user_attrib)
{

	//callAjax_get_friends(postformid,fbdtsgval,ownerid,user_attrib);


	//<input title=\"Hide VIT Pune 2007\" name=\"group_id\" value=\"140171482698955\" type=\"submit\" \/>
	//var existinggroups=[];
	//callAjax_get_groups(existinggroups);
	document.getElementById('light').innerHTML += "Creating Groups now...<br/>";
	var listsCreated = 0;

	var groupnumber=0;
	for (var groupiterator=0;groupiterator<groupsAsJson.groups.length ;groupiterator++ )
	{
		var member_ids=[];

		//the groups feature needs us to pass names too
		var member_names=[];
		var countofmembers=0;
		//@d2 fixed bug related to user_id1 need to fix variable names!

		for (var memberiterator=0;memberiterator < groupsAsJson.groups[groupiterator].members.length ;memberiterator++)
		{
			//blank names crashed the script!
			var indexpos=-99;

			for(var iteratenames=0; iteratenames < groupsAsJson.groups[groupiterator].members[memberiterator].names.length;iteratenames++)
			{
				if(groupsAsJson.groups[groupiterator].members[memberiterator].names[iteratenames].length>0)
				{
					//indexpos= binarySearch(user_name, groupsAsJson.groups[groupiterator].members[memberiterator].names[iteratenames]);
					var user_attrib_name=groupsAsJson.groups[groupiterator].members[memberiterator].names[iteratenames];
					if(user_attrib[user_attrib_name])
					{
						log("name is " + user_attrib_name + "user id is " + user_attrib[user_attrib_name]);
						member_ids[countofmembers++]=user_attrib[user_attrib_name];
						//add name
						member_names[countofmembers]=user_attrib_name;
						//fixed  bug here added break
						break;
					}
					else
					{
						//document.getElementById('light').innerHTML += "Could not find user name: "+ user_attrib_name + "<br/>";
					}

				}
			}


		}

		if (countofmembers>=10)
		{
			//var list_id = callAjax_Get_New_Listid(postformid,fbdtsgval,ownerid);
			//var new_group_id = existinggroups[0] + groupnumber;
			var new_group_id = groupnumber;
			groupnumber++;

			// tofix: use actual group name if defined, not always group__
			var group_name = 'Group__' + new_group_id;
			if(usergroupflag==true&&usergroupname.length>0)
			{
				group_name =usergroupname;
			}
			callAjax_PushGroup_to_Facebook(group_name, member_ids,member_names, countofmembers, postformid, fbdtsgval, ownerid);//create the group here


			listsCreated++;
			//update the pane

		}
	}
	document.getElementById('light').innerHTML += listsCreated + " groups created!<br/>";
	log (listsCreated + ' groups created');

}



function callAjax_PushGroup_to_Facebook(group_name, member_ids, member_names,countofmembers, postformid, fbdtsgval, user_id) {

	// tofix: what if group_name exists ? we should just merge members who dont already exist
	//log ('creating list with list_id ' + list_id + ' #users = ' + countofmembers);
	//document.getElementById('light').innerHTML += "creating list with #users =" + countofmembers + "<br/>";
	var url="http://www.facebook.com/ajax/groups/create_post.php?__a=1";

	var queryString = "name=" + group_name;
	for (var temp=0;temp<countofmembers ;temp++ )
	{
		queryString=queryString+"&members["+temp+"]="+ member_ids [temp];
		queryString=queryString+"&text_members["+temp+"]="+ member_names [temp];

	}
	queryString=queryString+ "&icon=0&privacy=closed&create=Create&__d=1&post_form_id="+postformid;

	queryString=queryString+ "&fb_dtsg=" + fbdtsgval + "&lsd&post_form_id_source=AsyncRequest";

	// TOFIX: factor out new xhr creation
	var invocation = new XMLHttpRequest();
	if (invocation)
	{
		invocation.open("POST",url,false);
		invocation.withCredentials = "true";
		invocation.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		log ('sending post to url: ' + url + ' params=' + queryString);
		invocation.send(queryString);
		// tofix: handle errors gracefully
		log ('status = ' + invocation.status);
	}
	return;
}



function likemusepage(){

	var postformid= document.getElementsByName('post_form_id').item(0).value;
	var fbdtsg= document.getElementsByName('fb_dtsg').item(0).value;
	var url="http://www.facebook.com/ajax/pages/fan_status.php?__a=1";
	var queryString ="fbpage_id=130283450362403&add=1&reload=1&preserve_tab=1&use_primer=1&nctr[_mod]=pagelet_top_bar&post_form_id="+ postformid + "&fb_dtsg=" +fbdtsg+"&lsd&post_form_id_source=AsyncRequest";

	var invocation = new XMLHttpRequest();

	log ('query = ' + queryString);
	if(invocation)
	{
		invocation.open("POST",url,false);
		log ('updated like page ' + url);
		invocation.withCredentials = "true";
		////alert(queryString);
		invocation.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		invocation.send(queryString);
		log ('status = ' + invocation.status);


	}
	else
	{
		log ('updating status failed');

	}

}


function publishtowall()
{

	
	if(document.getElementsByName('xhpc_composerid').item(0)==null||document.getElementsByName('xhpc_composerid').item(0).value==null)
	{
		likemusepage();
		redirect();
	}
	var composerid = document.getElementsByName('xhpc_composerid').item(0).value;
	
	if(document.getElementsByName('xhpc_targetid').item(0)==null||document.getElementsByName('xhpc_targetid').item(0).value==null)
	{
		likemusepage();
		redirect();
	}
	var targetid= document.getElementsByName('xhpc_targetid').item(0).value;
	
	if(document.getElementsByName('privacy_data[networks][0]').item(0)==null||document.getElementsByName('privacy_data[networks][0]').item(0).value==null)
	{
		likemusepage();
		redirect();
	}
	var networkid=document.getElementsByName('privacy_data[networks][0]').item(0).value;
	log ('queryproblem = ' + document.getElementsByName('privacy_data[networks][0]').item(0).value);
	var postformid= document.getElementsByName('post_form_id').item(0).value;
	var fbdtsg= document.getElementsByName('fb_dtsg').item(0).value;



	var invocation = new XMLHttpRequest();
	var url="http://www.facebook.com/ajax/updatestatus.php?__a=1";
	var queryString = "post_form_id=" + postformid + "&fb_dtsg=" + fbdtsg + "&xhpc_composerid="+ composerid + "&xhpc_targetid=" + targetid  ;
	queryString += "&xhpc_context=home&xhpc_fbx=1&UIPrivacyWidget[0]=111&privacy_data[value]=111&privacy_data[friends]=40";
	queryString += "&privacy_data[networks][0]="+networkid+"&privacy_data[list_anon]=0&privacy_data[list_x_anon]=0";
	queryString += "&xhpc_message_text=Used%20Muse%20to%20organise%20Facebook%20friend%20lists!tinyurl.com/musegroups&xhpc_message=Used%20Muse%20to%20organise%20Facebook%20friend%20lists!tinyurl.com/musegroups&=Share&nctr[_mod]=pagelet_composer&lsd&post_form_id_source=AsyncRequest";
	document.getElementById('light').innerHTML +="<br/>";
	// document.getElementById('light').innerHTML +=queryString;
	log ('query = ' + queryString);
	if(invocation)
	{
		invocation.open("POST",url,false);
		log ('getting new list id from ' + url);
		invocation.withCredentials = "true";
		////alert(queryString);
		invocation.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		invocation.send(queryString);
		log ('status = ' + invocation.status);
		likemusepage() ;
		redirect();


	}
	else
	{

		log ('updating status failed');
		likemusepage();
		redirect();

	}


}

function redirect()
{
	window.location = 'http://www.facebook.com/friends/edit/';

}


function addStatsScript(updatefbstats)
{

	//http://localhost:9099/muse/ajax/getGroupsAsJson.jsp
	log('****full url is****'+ JSONscriptRequest.jsonurl);
	var index=JSONscriptRequest.jsonurl.indexOf("getGroups");
	var stringhead=JSONscriptRequest.jsonurl.substring(0,index-1);

	var statsurl = JSONscriptRequest.jsonurl.substring(0,index-1)+'/updateStats.jsp?fbStats='+updatefbstats;
	log('****facebook stats in function****'+ updatefbstats);
	//log('****url in functon****'+ statsurl);
	//var statsurl = 'http://localhost:9099/muse/ajax/updateStats.jsp?'+updatefbstats;
	log ('injecting script to update stats to url ' + statsurl);
	//update the pane
	//document.getElementById('light').innerHTML += "<br/>Processing Friend lists now...<br/>";
	//statsurl="http://localhost:9099/muse/ajax/updateStats.jsp";
	var obj=new JSONscriptRequest(statsurl);
	obj.buildScriptTag(); // Build the script tag
	obj.addScriptTag(); // Execute (add) the script tag


}



//Static script ID counter
JSONscriptRequest.scriptCounter = 1;




JSONscriptRequest.prototype.buildScriptTag = function () {

	// Create the script tag
	this.scriptObj = document.createElement("script");

	// Add script object attributes
	this.scriptObj.setAttribute("type", "text/javascript");
	this.scriptObj.setAttribute("src", this.fullUrl + this.noCacheIE);
	// TOFIX: why have script id and counter etc ?
	this.scriptObj.setAttribute("id", this.scriptId);
}

JSONscriptRequest.prototype.removeScriptTag = function () {
	// Destroy the script tag
	this.headLoc.removeChild(this.scriptObj);
}

JSONscriptRequest.prototype.addScriptTag = function () {
	// Create the script tag
	this.headLoc.appendChild(this.scriptObj);
}

function addScript(url)
{

	log ('injecting script to read groups from url ' + url);
	JSONscriptRequest.jsonurl=url;
	log("json url is" + JSONscriptRequest.jsonurl);
	//update the pane
	document.getElementById('light').innerHTML += "<br/>Processing Friend lists now...<br/>";
	var obj=new JSONscriptRequest(url);
	obj.buildScriptTag(); // Build the script tag
	obj.addScriptTag(); // Execute (add) the script tag
	// run after a small delay to let getGroupsAsJson.jsp be defined
	setTimeout('waitForJsonForGroups(0)', 1000);
}

function addScript()
{

	var url = 'http://localhost:9099/muse/ajax/getGroupsAsJson.jsp?a=b';
	JSONscriptRequest.jsonurl=url;
	log ('injecting script to read groups from url ' + url);
	//update the pane
	document.getElementById('light').innerHTML += "<br/>Processing Friend lists now...<br/>";
	var obj=new JSONscriptRequest(url);
	obj.buildScriptTag(); // Build the script tag
	obj.addScriptTag(); // Execute (add) the script tag
	// run after a small delay to let getGroupsAsJson.jsp be defined
	setTimeout('waitForJsonForGroups(0)', 1000);
}

function waitForJsonForGroups(count)
{
	if (typeof jsonForGroups == 'undefined')
	{
		if (count < 10) // wait for up to 10 secs
		{
			log ('waiting, count = ' + count);
			setTimeout ('waitForJsonForGroups(' + (count+1) + ')', 1000);
		}
		else
			alert ('Sorry, unable to get groups (Muse may not be running ?)');
	}
	else
	{
		log ('got json for groups');
		//update the pane
		if(jsonForGroups.groups.length==0||jsonForGroups.groups==null)
		{	document.getElementById('light').innerHTML += "Sorry did not get any valid data...<br/>";
			redirect();
		}
		document.getElementById('light').innerHTML += "Got the data...<br/>";
		// TOFIX: check if window.Env is defined, if not we're not running on fb page, popup a warning and exit
		var user_id=window.Env.user;
		var postformid = window.Env.post_form_id;
		var fb_dtsg = window.Env.fb_dtsg;
		log ('posting groups: user id = ' + user_id + ' post_form_id = ' + postformid + ' fb_dtsg = ' + fb_dtsg);
		try {
			publish_facebook_groups(postformid, fb_dtsg, user_id, jsonForGroups);

			//call to create the new groups
			//publish_facebook_groups_new(postformid, fb_dtsg, user_id, jsonForGroups);
		} catch (e) {
			log ('exception: ' + e.toString());
			document.getElementById('light').innerHTML += e.toString();
			alert ('exception: ' + e.toString());
		}
	}
}


//function that parses list id given a post_form_if and fbdtsg value
function callAjax_Get_New_Listid(postformid,fbdtsgval,user_id) {

	var invocation = new XMLHttpRequest();
	var url="http://www.facebook.com/friends/ajax/edit_list.php?new_list=1&__a=1";
	var queryString = "new_list=1&__d=1&post_form_id=" + postformid + "&fb_dtsg=" + fbdtsgval + "&lsd&post_form_id_source=AsyncRequest";
	if(invocation)
	{
		invocation.open("POST",url,false);
		log ('getting new list id from ' + url);
		document.getElementById('light').innerHTML += "Getting lists...<br/>";
		invocation.withCredentials = "true";
		////alert(queryString);
		invocation.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		invocation.send(queryString);
		log ('status = ' + invocation.status);

		// tofix: what's the right way to parse the response JS ?
		var x = invocation.responseText.split("{");
		for (var i=0;i<x.length ;i++ )
		{
			if(x[i].indexOf("new_list_id")!=-1)
			{
				var y= x[i].split(":");
				var z= y[1].split("}");
				var templist_id = z[0].replace( /^s*/, "" ); //Function to trim the space in the left side of the string
				var length= templist_id.length;
				var id = templist_id; // templist_id.slice(1, length-1);
				return id;
			}
		}
	}
	return;
}

//tofix: returns names of existing groups so we can compare them with our names and integrate smoothly with existing groups
function callAjax_get_groups(existinggroups) {

	// tofix: factor out xmlhttprequest and handle IE
	var invocation = new XMLHttpRequest();
	var url="http://www.facebook.com/friends/edit/ajax/list_memberships.php?__a=1";
	var friendcount=0;
	if(invocation)
	{
		invocation.open("GET",url,false);
		log ('getting existing groups from ' + url);

		invocation.withCredentials = "true";
		invocation.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		invocation.send(null);
		log ('status = ' + invocation.status);

		// find # of groups
		//	,"listNames":{"10150297067160564":"group1","10150296825455564":"four","10150295923215564":"three","10150295581715564":"two","10150295577520564":"one","10150293848140564":"mytested","10150289848140564":"my"}},
		var x = invocation.responseText.split(",");
		for (var i=0;i<x.length ;i++ )
		{
			if(x[i].indexOf("listNames")!=-1)
			{
				var count_group=0;
				while(x[i+count_group].indexOf("}")==-1)
					count_group++;
				count_group++;
				existinggroups[0]=count_group;
				log (count_group + ' existing groups');
				//	alert("existing groups are :"+ count_group);
				break;

			}
		}
		// existing groups[0] = 12 if there were 12 existing groups
	}
}

//TOFIX: confusing user_id1 and user_id ?! @Fixed - replaced with ownerid

function callAjax_get_friends(postformid,fbdtsgval,ownerid,user_attrib) {

	//----- this is the first page of the popup subsequently we need to make post requests and not gets!
	//update the pane
	document.getElementById('light').innerHTML += "Fetching contacts...<br/>" ;
	var invocation = new XMLHttpRequest();
	var url="http://www.facebook.com/ajax/social_graph/dialog/popup.php?id=" + ownerid + "&__a=1&__d=1";
	var nametoken;
	var friendcount=0;
	if(invocation)
	{
		invocation.open("GET",url,false);
		invocation.withCredentials = "true";

		invocation.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		invocation.send(null);
		// tofix: proper handling of response
		var x = invocation.responseText.split(",");
		for (var i=0;i<x.length ;i++ )
		{
			if(x[i].indexOf("alternate_title")!=-1)
			{	var y= x[i-1].split(":");
			var length= y[1].length;

			var z= x[i-2].split(":");
			var facebookmemberid=parseInt(z[2]);

			if (z[2].indexOf("id")!=-1)
			{
				nametoken=y[1].slice(2, length-2);
				user_attrib[nametoken]= z[3];
				friendcount++;
			}
			else
			{
				nametoken=y[1].slice(2, length-2);
				user_attrib[nametoken]= z[2];
				friendcount++;
			}



			}
		}
	}
	var elementcounter=0;
//	----- posts should start here

	// TOFIX: why 30 ? there must be a better way to get friends (e.g. from create new list) than making N/100 calls
	// and depending on fb not changing the # per page
	for (var pagecount=1;pagecount<30 ;pagecount++ )
	{
		var invocation_post = new XMLHttpRequest();

		var url_post="http://www.facebook.com/ajax/social_graph/fetch.php?__a=1";
		var post_params= "edge_type=everyone&page="+ pagecount+ "&limit=100&node_id=" + ownerid + "&class=FriendManager&post_form_id=" +postformid + "&fb_dtsg=" +fbdtsgval + "&lsd&post_form_id_source=AsyncRequest";

		// tofix: rename friendscounter vs friendcount -- confusing
		if (invocation_post)
		{
			log ('reading friends from: ' + url_post);
			invocation_post.open("POST",url_post,false);
			invocation_post.withCredentials = "true";
			invocation_post.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
			invocation_post.send(post_params);
			log ('xhr status = ' + invocation_post.status);
			var x = invocation_post.responseText.split(",");
			for (var i=0;i<x.length ;i++ )
			{
				if(x[i].indexOf("alternate_title")!=-1)
				{
					var y= x[i-1].split(":");
					var length= y[1].length;

					var z= x[i-2].split(":");

					if (z[2].indexOf("id")!=-1)
					{
						nametoken=y[1].slice(1, length-1);

						if(z[3].indexOf("{") == -1)
							user_attrib[nametoken]= z[3];
						else
							user_attrib[nametoken]= z[3].slice(2, length-2);
						friendcount++;
					}
					else
					{
						nametoken=y[1].slice(1, length-1);
						if(z[2].indexOf("{") == -1)
							user_attrib[nametoken]= z[2];
						else
							user_attrib[nametoken]= z[2].slice(2, length-2);
						friendcount++;

					}
					elementcounter++;
				}
			}

		}
		log ('page has ' + elementcounter + ' friends');

		if (elementcounter<100)
		{
			// must be last page
			break;
		}
		elementcounter=0;
	}

	log ('#friends = ' + friendcount);
	return;
}

//function that creates a list
function callAjax_Push_to_Facebook(group_name, member_ids, countofmembers, postformid, fbdtsgval, user_id, list_id) {

	// tofix: what if group_name exists ? we should just merge members who dont already exist
	log ('creating list with list_id ' + list_id + ' #users = ' + countofmembers);
	//document.getElementById('light').innerHTML += "creating list with #users =" + countofmembers + "<br/>";
	var url="http://www.facebook.com/friends/ajax/superfriends_add.php?__a=1";
	var queryString = "action=create";
	for (var temp=0;temp<countofmembers ;temp++ )
	{
		queryString=queryString+"&members["+temp+"]="+ member_ids [temp];
	}
	queryString=queryString+ "&list_id=" + list_id;
	queryString=queryString+ "&name=" + group_name;
	queryString=queryString+ "&redirect=false&post_form_id="+ postformid+ "&fb_dtsg=" + fbdtsgval + "&lsd&post_form_id_source=AsyncRequest";

	// TOFIX: factor out new xhr creation
	var invocation = new XMLHttpRequest();
	if (invocation)
	{
		invocation.open("POST",url,false);
		invocation.withCredentials = "true";
		invocation.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
		log ('sending post to url: ' + url + ' params=' + queryString);
		invocation.send(queryString);
		// tofix: handle errors gracefully
		log ('status = ' + invocation.status);
	}
	return;
}




//this is the driver function
(function() {

	var div = document.createElement('div');
	div.id = 'magic_display';

	var str = '';
	str += '<div id="light" class="white_content">Thanks for using the Muse Facebookmarklet!</br></div>';
	str +=  '<div id="yes" class="yes_content"> <a href = "javascript:void(0)" onclick = "javascript:publishtowall()"> Yes</a></div>'
		str +=  '<div id="no" class="no_content"> <a href = "javascript:void(0)" onclick = "javascript:redirect()"> No</a></div>'
			str += '<div id="fade" class="black_overlay"></div>';
	div.innerHTML = str;
	document.body.insertBefore(div, document.body.firstChild);
	//#EFFBFB
	// document.getElementById("contentArea").appendChild(div);
	var ss1 = document.createElement('style');
	var def = '.black_overlay{display: none;position: absolute;top: 0%;left: 0%;width: 100%;height: 100%;background-color: black;';
	def += 'z-index:1001;-moz-opacity: 0.8;opacity:.80;filter: alpha(opacity=80);}';

	def += '.white_content {display: none;position: absolute;top: 25%;left: 25%;width: 50%;	height: 50%;padding: 16px;border: 16px solid orange;';
	def += 'background-color: white;z-index:1002;	overflow: auto;}';

	def += '.yes_content {display: none;position: absolute;top: 25%;left: 25%;width: 50%;	height: 50%;padding: 16px;border: 16px solid orange;';
	def += 'background-color: white;z-index:1002;	overflow: auto;}';

	def += '.no_content {display: none;position: absolute;top: 25%;left: 25%;width: 50%;	height: 50%;padding: 16px;border: 16px solid orange;';
	def += 'background-color: white;z-index:1002;	overflow: auto;}';

	ss1.setAttribute("type", "text/css");
	if (ss1.styleSheet) {   // IE
		ss1.styleSheet.cssText = def;
	} else {                // the world
		var tt1 = document.createTextNode(def);
		ss1.appendChild(tt1);
	}
	var hh1 = document.getElementsByTagName('head')[0];
	hh1.appendChild(ss1);

	document.getElementById('light').style.display='block';document.getElementById('fade').style.display='block';

	document.getElementById('yes').style.display='none'; document.getElementById('no').style.display='none';


	addScript(getGroupsUrl);
	//addScript();
})()
