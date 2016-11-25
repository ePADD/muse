"use strict";
// used to get photos from firefox contacts
$(document).ready(renderPersonsOnPage);

function renderPersonsOnPage()
{
	var persons = $("li.person");
	// alert (persons.length + ' people on this page');
	// try the w3c spec and then the old moz format
	/*
	try {
		if (typeof(navigator) != 'undefined' && typeof(navigator.service) != 'undefined' && typeof(navigator.service.contacts) != 'undefined')
			navigator.service.contacts.find(['name', 'displayName', 'photos'], function (x) { return foundContacts(x, persons);}, null, {});
	} catch (e) {
		try { navigator.people.find({}, ["name", "displayName", "photos"], function(x) { return foundContacts(x, persons);}, reportError);
			alert ('please upgrade your mozilla contacts addon here: http://people.mozilla.com/~mhanson/contacts/contacts-0.4-relbundle-rel.xpi')
		}
		catch (e) {
			muse.log ("not running with firefox contacts");
		}
	}
	*/
}

function foundContacts(contacts, persons)
{
	muse.log (persons.length + " persons and " + contacts.length + " contacts");
	profilePhotos = new Array();
	for (var j = 0; j < contacts.length; j++)
	{
		var profilePhoto = profilePhotoURL(contacts[j]);

		var n = canonicalizeName(contacts[j].displayName);
		profilePhotos[n] = profilePhoto;

		if (contacts[j].name)
		{
			n = canonicalizeName(contacts[j].name.givenName + " " + contacts[j].name.familyName);
			profilePhotos[n] = profilePhotoURL(contacts[j]);
		}
	}

	for (var i = 0; i < persons.length; i++)
	{
		var found = false;
		var attr = persons[i].getAttribute("personDescription");

		// the looking for us currently a --- separated list of emails and names
		// see Contact.toMozContactsDescriptionStr()
		var lookingFors = " ?? ";
		if (attr == null)
			muse.log ("WHAT !?" + persons[i].innerHTML);
		else
		    lookingFors = attr.split("---");

		var foundURL = null;
		for (var j = 0; j < lookingFors.length; j++)
		{
			var lookingFor = $.trim(lookingFors[j]);
			var url = profilePhotos[canonicalizeName(lookingFor)];
			if (typeof(url) != 'undefined')
			{
				foundURL = url;
				break;
			}
		}

//		muse.log ('found url for ' + attr + ' is ' + url);
		if (foundURL == null)
			foundURL = 'http://static.ak.fbcdn.net/rsrc.php/z5HB7/hash/ecyu2wwn.gif';
		var newHTML = '<img style="margin:1px" src="' + foundURL + '"/>&nbsp;' + persons[i].innerHTML;
		$(".nameAndImage", persons[i]).html(newHTML);
	}
}

function profilePhotoURL(contact)
{
	if (contact.photos)
		for (var i = 0; i < contact.photos.length; i++)
			if (contact.photos[i].type == 'thumbnail' || contact.photos[i].type == 'profile')
				return contact.photos[i].value;
	muse.log ('Moz contacts does not have thumbnail photo for ' + contact.displayName);
	return null;
}

function canonicalizeName(name)
{
	if (name)
		return name.toLowerCase();
	return '';
}

function reportError(e)
{
  muse.log('Error reading contacts: ' + e);
}

