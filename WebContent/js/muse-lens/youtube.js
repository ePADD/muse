
//////////////////////////////////// Youtube re-ordering /////////////////////////////////

var youtubepersonalized=new Array();
var youtubecounter= new Array();
var youtubeit=0;

function prefetchYoutube()
{
    if (window.top != window.self)
      return;
      
    var query=$_("#masthead-search-term").val();
    if(query.length<=0)
    	return;

    //http://www.youtube.com/results?search_query=java&page=2
		// create iframe and attach it into the DOM
    $_('<iframe id="myFrame" name="myFrame">').appendTo('body');

		// Setting iframe's source
		$_('#myFrame').attr('src', 'http://www.youtube.com/results?search_query='+query+'&page=2'); 

   $_('#myFrame').load(function(){
  
   	$_("#myFrame").contents().find(".result-item").each(function(i, obj) {
    	  //alert("iframe"+i);
    		$_("#search-results").append($_(this).clone());
     
		});

		$_("#myFrame").remove();
	});
	 //$_("#myFrame").remove();
}

function reRankYoutube() {
	$_('.result-item').each(function(i, obj) {
     GM_log ("hilited elems="+ $_(".muse-highlight",this).size());

     youtubepersonalized[youtubeit]=$_(this).clone();
     youtubecounter[youtubeit]=$_(".muse-highlight",this).size();
		 youtubeit++;
	});

	for(var i=0;i<youtubeit;i++)
	{
			for(var j=i+1;j<youtubeit;j++)
			{
					if(youtubecounter[i]<youtubecounter[j])
					{
							var tempcounter=youtubecounter[i];
							youtubecounter[i]=youtubecounter[j];
							youtubecounter[j]=tempcounter;

							var temppersonalized=youtubepersonalized[i];
							youtubepersonalized[i]=youtubepersonalized[j];
							youtubepersonalized[j]=temppersonalized;
					}
			}
	}

	$_("#search-results").html(" ");
	for(var i=0;i<youtubeit;i++)
	{
			$_("#search-results").append(youtubepersonalized[i]);
	}
}
