function copyTermToDiary(elem) {
	var term = $(elem).html();
	// find the description of the term's cloud
	var description = $(elem).closest('.tagcloud-content').attr('description');
	description = description.replace(/&nbsp;/g,''); // remove the darn &nbsp; 's
	description = description.replace(/\(.*/g,''); // remove the count after the interval name

	// get prev diary text.
	// strange $('#diary').html does not work in firefox at least.
	var prevText = $('#diary').val();
	if (prevText)
	{
		if (prevText == 'Write your diary here!')
			prevText = '';
	}
	else
		prevText = '';

    var indexOfDate = prevText.indexOf(description);
    var nextText = "";

    if (indexOfDate < 0) {
	    nextText = description + "\n" + term + "\n\n" + prevText;
    } else {
    	nextText = prevText.substring(0, indexOfDate + description.length) + "\n" + term + "\n" + prevText.substring(indexOfDate + description.length, prevText.length);
    }

    $('#diary').val(nextText);
}