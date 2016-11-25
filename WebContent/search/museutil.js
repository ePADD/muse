function mySort() {
	// alert('sorting results');

	// alert("mysort" + unsafeWindow_url_html.length);
	for ( var i = unsafeWindow_url.length - 1; i >= 0; i--) {
		for ( var j = 0; j <= i; j++) {
			if (unsafeWindow_url_weight[j + 1] > unsafeWindow_url_weight[j]) {
				var tempValue = unsafeWindow_url_weight[j];
				unsafeWindow_url_weight[j] = unsafeWindow_url_weight[j + 1];
				unsafeWindow_url_weight[j + 1] = tempValue;
				var tempValueHTML = unsafeWindow_url_html[j];
				unsafeWindow_url_html[j] = unsafeWindow_url_html[j + 1];
				unsafeWindow_url_html[j + 1] = tempValueHTML;

				var tempValueTITLE = unsafeWindow_url_title[j];
				unsafeWindow_url_title[j] = unsafeWindow_url_title[j + 1];
				unsafeWindow_url_title[j + 1] = tempValueTITLE;

			}
		}
	}

}


var count =0;
function doSomething() {

       // if(count==3)
         //  display();
        if(count==5)
	document.getElementById('spinner').style.display='none';	
	
	count++;
        if((count%10)==0)
                //if(myA)
        	//	chrome.extension.sendRequest({'action' : 'synchpenalty', 'q': myA}, doSomething);
        
        var clickeddiv = document.getElementById("museclickdiv1");
        if(clickeddiv)
	{
                var ImgTag= clickeddiv.innerHTML; 
               // alert(ImgTag);
                var id = ImgTag.substring(8,ImgTag.length);
		var lineTag="lineDiv"+id;
                var title = jQuery('div#'+lineTag).find('p:first').attr("title");
                var names =title.split("and");
		for (var nameiterator=0;nameiterator<names.length-1;nameiterator++)
		{
			if(myA&&myA[names[nameiterator].toLowerCase()])
				myA[names[nameiterator]]+=100;
			else
                            if(myA==null)
                               myA=new Array();
			    myA[names[nameiterator]]=100;

                        if(unsafeWindow_myA&&unsafeWindow_myA[names[nameiterator].toLowerCase()])
				unsafeWindow_myA[names[nameiterator]]+=100;
			else
                            if(unsafeWindow_myA==null)
                               unsafeWindow_myA=new Array();
			    unsafeWindow_myA[names[nameiterator]]=100;

                        
		}
        	var lineEle = document.getElementById(lineTag);

                
		//lineEle.innerHTML ="";// '<img src="http://www.randomsnippets.com/wp-includes/images/plus.png">';
		
		lineEle.parentNode.removeChild(lineEle);
                clickeddiv.parentNode.removeChild(clickeddiv);
                $("#museclickdiv1").remove();
        }
        
	
}

setInterval(doSomething,2000);


function display() {

	mySort();

	var r = '';

	var script = 'function createDiv(divid)  { var divTag = document.createElement("div");        divTag.id = "museclickdiv1";  divTag.className ="dynamicDiv"; divid=divid+"";  divTag.innerHTML = divid.substring(21,divid.length-2);  divTag.style.display = "none"; document.body.appendChild(divTag);  }';

	var divScript = document.createElement("script");
	divScript.type = "text/javascript";
	divScript.text = script;
	document.getElementsByTagName("head")[0].appendChild(divScript);

	// alert(unsafeWindow_url_html.length);
	for ( var i = 0; i < unsafeWindow_url_html.length; i++) {
		var anchor = unsafeWindow_url_html[i];

		var switchImgTag = "imageDiv" + i;
		var switchLineTag = "lineDiv" + i;
		r += "<input type='checkbox' id='check1' name='people' onclick='checkpeople()'/>Do you like this result?";
		r += '<div id="' + switchLineTag + '">';
		r += '<a id="'
				+ switchImgTag
				+ '" href="javascript:createDiv('
				+ switchImgTag
				+ ');">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<img src="http://mobisocial.stanford.edu/musemonkey/icon_cancel.gif"/></a>';
		r += '<br/><p style="float:right; width:' + (sidebar_width - 110)
				+ 'px" title="' + unsafeWindow_url_title[i] + '">' + anchor
				+ '</p> </div>';

		/*
		 * r += '<a class="tooltip" href="#"><p style="float:right; width:' + (sidebar_width-110) + 'px" title="'+ unsafeWindow_url_title[i] + '">' +
		 * anchor + '</p><span class="classic">' + unsafeWindow_url_title[i] +'</span></a>';
		 */

	}
	$("#searchMuse").append(r);
	// alert("out of here");

}

function displayMuse(data) {

	// alert("in display mus");
	var r = '';
	var count = 0;
	console.log("data is:" + data);
	$(data)
			.find('.g')
			.each(
					function(i, entry) { // loop though all vsc in the
						// snippets list

						// if(i==0)
						// alert("getting results now");
						console.log("entry is:" + $(entry).html());
						var anchor = $(entry).html(); // get the actual
						// link with the
						// text inside

						var keyin = $(entry).find('cite:first').html();
						console.log("keyin is:" + keyin);

						var alltitles = new Array();

						$(entry).find('b').each(function(j, highlight) {
							alltitles[j] = $(highlight).html()
							console.log("alltitles[j] is:" + alltitles[j]);

						});

						var titlestring = "";
						var sum_of_weights = 0;
						for (title = 0; title < alltitles.length; title++) {
							if (unsafeWindow_entry_weight[(alltitles[title] + "")
									.toLowerCase()])
								sum_of_weights = unsafeWindow_entry_weight[alltitles[title]
										.toLowerCase()];
							if (titlestring.length == 0
									|| titlestring.indexOf(""
											+ alltitles[title].innerHTML) == -1)
								titlestring += alltitles[title] + " and ";
						}

						if (unsafeWindow_url.indexOf(keyin) < 0) {
							unsafeWindow_url_html[unsafeWindow_url_track] = anchor;
							unsafeWindow_url_weight[unsafeWindow_url_track] = sum_of_weights;
							unsafeWindow_url_title[unsafeWindow_url_track] = "This result has "
									+ titlestring
									+ " weight is: "
									+ unsafeWindow_url_weight[unsafeWindow_url_track];
							unsafeWindow_url[unsafeWindow_url_track] = keyin;
							unsafeWindow_url_track++;

						} else {
							var prevresult = unsafeWindow_url.indexOf(keyin);
							unsafeWindow_url_weight[prevresult] += sum_of_weights;
							unsafeWindow_url_title[prevresult] = unsafeWindow_url_title[prevresult]
									.substring(0,
											unsafeWindow_url_title[prevresult]
													.indexOf("weight is"));
							unsafeWindow_url_title[prevresult] += titlestring
									+ " weight is: "
									+ unsafeWindow_url_weight[prevresult];

						}

						r += '<p style="float:right; width:' + (200) + 'px">'
								+ anchor + '</p>';
						if (i > 4)
							return false;

					});
	// alert("out display mus");

}

// Insert the results from muse
function insertMuse(data) {

	data = eval("(" + data + ')');

	if (typeof (data) == undefined || data == null) {
		new_definition = "<h2>Muse is not running and no cached value found </h2> <br/><br/>";
		var r = '<MARQUEE style="float:right; width:' + (200)
				+ 'px" title="Muse is not Running">' + new_definition + '</p>';
		$("#searchMuse").append(r);
		return;

	}

	console.log(data);
	console.log(data.addressBook);
	/* Sort address book to find top 100 people as per messageOutCount */
	// data.addressBook.entries[0].messageOutCount =
	// (data.addressBook.entries[0].messageOutCount + 1) * 100;
	data.addressBook.entries.sort(function(a, b) {

		var b_penalty = b.messageOutCount;
		var a_penalty = a.messageOutCount;
		var firstnames;
		for (firstnames = 0; firstnames < b.names.length; firstnames++)
			if (b.names[firstnames].length > 5
					&& b.names[firstnames].indexOf(' ') != -1)
				if (typeof (unsafeWindow_myA) != "undefined"
						&& unsafeWindow_myA && unsafeWindow_myA.length != 0)
					if (unsafeWindow_myA[b.names[firstnames].toLowerCase()])
						b_penalty = b.messageOutCount
								- unsafeWindow_myA[b.names[firstnames]
										.toLowerCase()];

		for (firstnames = 0; firstnames < a.names.length; firstnames++)
			if (a.names[firstnames].length > 5
					&& a.names[firstnames].indexOf(' ') != -1)
				if (typeof (unsafeWindow_myA) != "undefined"
						&& unsafeWindow_myA && unsafeWindow_myA.length != 0)
					if (unsafeWindow_myA[a.names[firstnames].toLowerCase()])
						a_penalty = a.messageOutCount
								- unsafeWindow_myA[a.names[firstnames]
										.toLowerCase()];

		return b_penalty - a_penalty;

	});

	weight_of_hundred = data.addressBook.entries[100].messageOutCount;

	for ( var entry = 0; entry < data.addressBook.entries.length && entry < 100;) {
		// new_query=' +'+ query + ' AND [ ';
		new_query = ' +[' + query + '] AND [ ';
		for ( var firstnames = 0; firstnames < data.addressBook.entries[entry].names.length; firstnames++) {
			if (data.addressBook.entries[entry].names[firstnames].length > 5
					&& data.addressBook.entries[entry].names[firstnames]
							.indexOf(' ') != -1) {
				// TODO factor in penalties once we have the click tracker in
				// place
				/*
				 * unsafeWindow_entry_weight[data.addressBook.entries[entry].names[firstnames].toLowerCase()]=data.addressBook.entries[entry].messageOutCount -
				 * unsafeWindow_myA[data.addressBook.entries[entry].names[firstnames].toLowerCase()];
				 */

				unsafeWindow_entry_weight[data.addressBook.entries[entry].names[firstnames]
						.toLowerCase()] = data.addressBook.entries[entry].messageOutCount;

				new_query += ' ["'
						+ data.addressBook.entries[entry].names[firstnames]
						+ '"]';
				break;
			}
		}
		upperbound = data.addressBook.entries[entry].messageOutCount;
		for ( var i = 1; i < 7; i++) {
			if (entry == data.addressBook.entries.length)
				break;
			var names_in_query = 0;
			for ( var names = 0; names < data.addressBook.entries[entry + i].names.length
					&& names_in_query < 2; names++) {
				if (data.addressBook.entries[entry + i].names[names].length > 5
						&& data.addressBook.entries[entry + i].names[names]
								.toLowerCase().indexOf(' ') != -1) {
					unsafeWindow_entry_weight[data.addressBook.entries[entry
							+ i].names[names].toLowerCase()] = data.addressBook.entries[entry
							+ i].messageOutCount;
					new_query += ' OR ["'
							+ data.addressBook.entries[entry + i].names[names]
									.toLowerCase() + '"]';
					names_in_query++;
				}

			}
		}
		lowerbound = data.addressBook.entries[entry + 6].messageOutCount;
		entry += 7;
		new_query += " ]";
		var callurl = "http://localhost:8080/muse/search/callsearch.jsp?url=";
		var url;
		q = "http://www.google.com/search?q=" + new_query;
		q = encodeURIComponent(q);
		url = callurl + q;
		$.get(url, function(result) {
			displayMuse(result);
		});

	}

}
