
$(function() {
	$('.hover-star').rating({
		focus : function(value, link) {
			// 'this' is the hidden form element holding the current value
			// 'value' is the value selected
			// 'element' points to the link element that received the click.
			//alert($(this).parent().attr('id'));
			var tip = $('#hover-test-'+$(this).parent().attr('id'));
			tip[0].data = tip[0].data || tip.html();
			tip.html(link.title || 'value: ' + value);
		},
		blur : function(value, link) {
			var tip = $('#hover-test-'+$(this).parent().attr('id'));
			$('#hover-test-'+$(this).parent().attr('id')).html(tip[0].data || '');
		}
	});
});

function insertCSE_email(data) {

	//alert(data);
	var r = '';
	r += '<h1><a href="#" target="_blank">Custom search Email</a></h1>';

	jQuery(data).find('.g').each(
			function(j, result) {
				r += "<input type='checkbox' id='check1' name='email' onclick='checkemail()' />Do you like this result?";
				r += '<br/><p style="float:right; width:' + (300)
						+ 'px" title="' + jQuery(result).html() + '">'
						+ '</p> </div>';
			});
	jQuery("#searchCustomEmail").append(r);

}

function insertCSE_twitter(data) {

	//alert(data);
	var r = '';
	r += '<h1><a href="#" target="_blank">Custom search Twitter</a></h1>';

	jQuery(data).find('.g').each(
			function(j, result) {
				//customresults[j]=jQuery(highlight).html()
				//r += '<br/><p style="float:right; width:' + (300) + 'px" title="'+ jQuery(result).html() + '">' + '</p> ';
				//r += '<br/><p style="float:right; width:' + (300) + 'px" title="">' + jQuery(result).html() + '</p> ';
				r += "<input type='checkbox' id='check1' name='twitter'onclick='checktwitter()' />Do you like this result?";
				r += '<br/><p style="float:right; width:' + (300)
						+ 'px" title="' + jQuery(result).html() + '">'
						+ '</p> </div>';
			});
	jQuery("#searchCustomTwitter").append(r);

}

function insertCSE_toptwitter(data) {

	//alert(data);
	var r = '';
	r += '<h1><a href="#" target="_blank">Custom search Top Twitter</a></h1>';

	jQuery(data).find('.g').each(
			function(j, result) {
				//customresults[j]=jQuery(highlight).html()
				//r += '<br/><p style="float:right; width:' + (300) + 'px" title="'+ jQuery(result).html() + '">' + '</p> ';
				//r += '<br/><p style="float:right; width:' + (300) + 'px" title="">' + jQuery(result).html() + '</p> ';
				r += "<input type='checkbox' id='check1' name='toptwitter'onclick='checktop()' />Do you like this result?";
				r += '<br/><p style="float:right; width:' + (300)
						+ 'px" title="' + jQuery(result).html() + '">'
						+ '</p> </div>';
			});
	jQuery("#searchCustomTopTwitter").append(r);

}


function insertGoogle(data) {

	//alert(data);
	var r = '';
	r += '<h1><a href="#" target="_blank">Google Search</a></h1>';

	jQuery(data).find('.g').each(
			function(j, result) {
				console.log("parenthtml is "+jQuery(result).parent().html());
				console.log("html is "+jQuery(result).html());
				console.log("id is "+jQuery(result).attr("id"));
				if(jQuery(result).attr("id")=="newsbox")
					return;
				//customresults[j]=jQuery(highlight).html()
				// r += '<br/><p style="float:right; width:' + (300) + 'px" title="">' + jQuery(result).html() + '</p> ';
				r += "<input type='checkbox' id='check1' name='google' onclick='checkgoogle()'/>Do you like this result?";
				r += '<br/><p style="float:right; width:' + (300)
						+ 'px" title="' + jQuery(result).html() + '">'
						+ '</p> </div>';
			});
	jQuery("#search").append(r);

}
