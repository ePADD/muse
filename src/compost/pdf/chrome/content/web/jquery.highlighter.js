/*
jQuery Highlighter 0.5.2 alpha
Copyright (c) 2009 Christoph Hochgatterer | www.dailymarkup.com

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

(function($){
	$.fn.jhighlight = function(options) {
		var defaults = {
			screencolor		: 'black', 
			screenopacity 	: '0.6',
			overlaycolor	: 'transparent',
			boxshadow		: '0px 0px 30px white',
			animationspeed 	: 500,
			autoload 		: false,
			autoloadobject	: "",
			autoplay 		: false,
			autoplaydelay	: 5000,
			description		: false,
			showcount		: 1
		},
		settings = $.extend({}, defaults, options);
		//var cookiecount = $.cookie('jhighlighter');
		showbackground = function(){
			if($("#screen").length  == 0){
				jQuery("body").append("<div id='screen'></div><div id='overlay'><div id='noclick'></div><div id='inset'></div></div>");
				jQuery("#screen").css({
					'background-color' 	: settings.screencolor,
					'opacity'			: settings.screenopacity
				}).fadeIn(settings.animationspeed);
			}
		},
		showelement = function(top, left, width, height, obj, desc){
			showbackground();
			jQuery("#overlay").css({
				'background-color'	: settings.overlaycolor,
				'width' 			: width,
				'height' 			: height,
				'position'			:'absolute',
				'left'				: left,
				'top'				: top,
				'display'			: 'none',
				'-moz-box-shadow'	: settings.boxshadow,
				'-webkit-box-shadow': settings.boxshadow
			}).fadeIn(settings.animationspeed);
			jQuery("#noclick").css({
				'position'			:'absolute',
				'width'				: width,
				'height'			: height,
				'background-color'	:'transparent'
			});
			
			$("#inset").html("");
			obj.clone().prependTo("#inset");
			jQuery("#inset").children(obj).css({'margin' : '0'});
			//$("#noclick").focus;
			//$("#noclick").trigger("focus");
			if(settings.description == true){
				jQuery("#highlight-desc").remove();
				jQuery("body").append("<div id='highlight-desc'>"+ desc +"</div>");
				jQuery("#highlight-desc").fadeIn(settings.animationspeed);
			}

		},
		
		autoplay = function(ocount){
			ocount = $("#control a.jhighlight").length;
			if(pos != ocount){
				pos++;
				$(objects[pos]).trigger("click");
			}
		},
		closer = function(){
			//$.cookie('jhighlighter', 1);
			if(settings.autoplay == true){
				clearInterval(iid);
			}
			jQuery("#screen, #overlay, #highlight-desc").fadeOut(settings.animationspeed, function() {
				jQuery(this).remove();
				jQuery("a.jhighlight-active").removeClass("jhighlight-active");
			});
		},
		
		$(this).each(function(){			  
			var $this = $(this);
			var openerclass 	= $this.attr("class");
			var obj 			=  $("#"+$this.attr("title"));
			var description 	= $this.attr("rel");

			//dimensions
			var dimensions 		= $(obj).offset();
			var top=50;
			var left=50;
			try{
			if(!(dimensions=='null'||typeof(dimensions)=='undefined'))
			{
			
			 top 			= dimensions.top;
			 left 			= dimensions.left;
			 }
			 }
			 catch(err)
			 {
			 		console.log(err.message);
			 }
			var width 			= $(obj).outerWidth();
			var height 			= $(obj).outerHeight();
			
			//click event		
			if(settings.autoload == true && $("body").find("#"+settings.autoloadobject).length !=0){
				$("#control a[title="+settings.autoloadobject+"]").trigger('click');
			};
			$this.click(function() {  
				showelement(top, left, width, height, obj, description);
				$("#control a").removeClass("jhighlight-active");
				$this.addClass("jhighlight-active");
				i = jQuery("a.jhighlight").index(this)+1;

			}
		);// end each
		
	});

		if(settings.autoplay){
			var objects = $("#control a.jhighlight");
			var pos = 0;
			iid = setInterval(autoplay, settings.autoplaydelay);
			//iid = autoplay();
			$(objects[0]).trigger('click');
		}
		
		// global
		// close
		jQuery("#close").click(function() {
			closer();
		});		
		//key
		jQuery(document).keyup(function(e){
			var ar = jQuery("a.jhighlight").length;
			var key = 0;
			if (e == null) {
				key = event.keyCode;
			} else { // mozilla
				key = e.which;
			}
			switch(key) {
				
				case 37:
					var active = jQuery("a.jhighlight-active");
					var next1 = jQuery(active).prev();
					if(i >= 1){
						jQuery(next1).trigger('click');
						i-1;}
					if(i == 1){i=1};
					break;
				case 39:
					var active = jQuery("a.jhighlight-active");
					var next1 = jQuery(active).next();
					if(i<ar){
						jQuery(next1).trigger('click');
						i+1;
					}
					break;
				case 27:
					closer();
					break;
			};
		}); //end key
}
})(jQuery);

