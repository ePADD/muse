//alert("in content script");

/**
 * Performs an XMLHttpRequest to Twitter's API to get trending topics.
 *
 * @param callback Function If the response from fetching url has a
 *     HTTP status of 200, this function is called with a JSON decoded
 *     response.  Otherwise, this function is called with null.
 */
function processACM() {

 $("a[name=FullTextPDF]").each(function(){

 try{ 
 if($(this).attr('href').indexOf("ft_gateway.cfm?")<0)
 	return;
 	}
 	catch(err)
 	{
 	   console.log(err.message);
 	   return;
 	}
//alert("here");
 var link = $(this);
  var xhr = new XMLHttpRequest();
  xhr.onreadystatechange = function(data) {
    if (xhr.readyState == 4) {
      if (xhr.status == 200) {
        var data =xhr.responseText;
      //  alert(data);
       // console.log(data);
       // callback(data);

       //name:"Saurabh",imageURL:"http://media.linkedin.com/mpr/mprx/0_zdfeRCOzQGOxbEj3cmHcR3mUk3RKbdj3BwRcR3YoUXf8ZSoTMf0kvToXE7U7XapDqoDnqQ87-4gg",text:"Computer" }
           var result = eval( '('+xhr.responseText +')' );

           var htm= "<div>";
    
     			
     			for(var t=0;t<result.length;t++)
     			{
             var imagelink=result[t].imageURL; 
						htm+='<img src="'+imagelink+'" alt="'+result[t].name+'"/>';
     			}

          htm+="</div>";
     			link.parent().parent().append(htm);
       
      } else {
      //  console.log("error");
        //callback(null);
      }
    }
  }
  // Note that any URL fetched here must be matched by a permission in
  // the manifest.json file!
  //refURL
  //
  var senddata="refCookie="+ encodeURIComponent(document.cookie);
  var hr=$(this).attr("href");
  //alert(hr);
  senddata+="&refURL="+encodeURIComponent("http://dl.acm.org/"+hr);
  var url = 'http://localhost:8080/Surface/chaseLinks.jsp?'+senddata;
  xhr.open('GET', url, true);
  xhr.send(null);
  });


};


if(document.URL.indexOf("acm.org")>=0&&document.URL.indexOf("pdf")<0)
	setTimeout(processACM,2000);
  

