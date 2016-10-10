// call this when the puzzle is over or answers are being shown

var current_word = null, current_box = null;
var always_repaint_letter_colors = false;
var warned_once = false, completion_message_shown = false; // message issued when the crossword has been completely attempted but is not all right
var audio_volume = 0.75; // default audio volume, range is 0..1
var audio_muted = false;
var pulse_timeout_id = null;
var PULSE_DELAY = 10000, PULSE_DURATION = 1000; // pulse if there is no activity

function linkCluesToMessages()
{
	LOG ('linking clues to ref urls');

	unlinkClues();
	for (var q = 0; q < cross.placedWords.length; q++)
	{
		var direction = cross.placedWords[q].acrossNotDown? 'across':'down';
		var num = cross.placedWords[q].clueNum;
		// find the clueNum, so we can mark it
		var $thisClue = $('.clueDiv').filter(function() {
		    return $(this).attr('direction') == direction && $(this).attr('clueNum') == num;
		});
		if ($thisClue.length == 0)
			continue; // its possible there may be no clue for this word
		$thisClue.off('click'); // remove any existing handlers, just for safety
		if (cross.placedWords[q].clue.url != null && cross.placedWords[q].clue.url != '#')
		{
			$thisClue.click(function(url) { return function(e) { if ($(e.target).closest('audio').length > 0) return true; window.open(url, '_blank'); }; }(cross.placedWords[q].clue.url));
			$thisClue.css('cursor', 'pointer');
		}
	}
	$('.box').off('click'); // remove the box actions
}

function unlinkClues()
{
	LOG ('unlinking clues');
	$('.clueDiv').off('click'); // remove any existing handlers, just for safety
	$('.clueDiv').css('cursor', 'default');
}

function linkCluesToAnswerEntry()
{
	// unlink clues from anything else
	unlinkClues();
	LOG ('linking clues to answer entry');
	$('.clueDiv').click(clueClickHandler);
}

function linkCluesToHints() {
	LOG ('linking clues to hints');

	unlinkClues();

	var showTitleOnClick = function(event)
	{
		var message = $(event.target).attr('title');
		if (message && message.length > 0)
		{
			message = "Hint: <br/>" + message.replace(/\n/g, '<br/>\n');
			$.jGrowl(message, {life:10000});
		}
	};
	$('.clueDiv').click(showTitleOnClick); // in iOS, title has to be shown as a popup on click
}


// will return an array of {x: ..., y:... } objects reflecting coordinates of boxes related to this event (on a cluediv)
function boxesForClue(event) {
	var result = [];
	$clueDiv = $(event.target).closest('.clueDiv');
	var selected_clue_num = $clueDiv.attr('clueNum');
	var selected_direction = $clueDiv.attr('direction');
	for (var q = 0; q < cross.placedWords.length; q++)
	{
		var direction = cross.placedWords[q].acrossNotDown? 'across':'down';
		var num = cross.placedWords[q].clueNum;
		if (direction == selected_direction && num == selected_clue_num)
		{
			var word = cross.placedWords[q];
			var ix = word.acrossNotDown ? 1 : 0;
			var iy = word.acrossNotDown ? 0 : 1;
			var x = word.x;
			var y = word.y;
			
			for (var a = 0; a < word.word.length; a++) {
				result.push({x: x, y: y});
				x += ix; y += iy;
			}
		}
	}
	return result;
}

var unhilite_all_boxes = function(event)
{
	$('.box').removeClass('hilited-box');
	$('.box').removeClass('hilited-box-with-focus');
	// remove any pending pulse ops
	if (pulse_timeout_id)
		clearTimeout(pulse_timeout_id);
};

function enableHighlightOnClueHover() {
//	LOG ('enabling clue hover hilite');
//	$('.clueDiv').mouseenter(highlightBoxes); // in iOS, title has to be shown as a popup on click
//	LOG ('done enabling clue hover hilite');
//	$('.clueDiv').mouseleave(unhilite_all_boxes); // in iOS, title has to be shown as a popup on click
//	$('.clueDiv').fade(highlightOnClueHover); // in iOS, title has to be shown as a popup on click
}

function getWordLens(lens) {
	var result = '(';
	if (lens) {
		for (var i = 0; i < lens.length; i++) {
			result += lens[i];
			if (i < lens.length-1)
				result += ",";
		}
	}
	result += ")";
	return result;
}

function makeCluesEditable()
{
	$('.clueDiv').click(function(e) { 
		$clueDiv = $(e.target).closest('.clueDiv');
		var selected_clue_num = $clueDiv.attr('clueNum');
		var selected_direction = $clueDiv.attr('direction');
		LOG ('updating clue for ' + selected_clue_num + ' ' + selected_direction);
		var text = $('.clueText', $clueDiv).text();
		var new_text = prompt('Enter updated clue for: ' + text);
		if (new_text == null || new_text.length == 0)
			return;
		$('.clueText', $clueDiv).html(new_text);
		for (var q = 0; q < cross.placedWords.length; q++)
		{
			var direction = cross.placedWords[q].acrossNotDown? 'across':'down';
			var num = cross.placedWords[q].clueNum;
			
			if (direction == selected_direction && num == selected_clue_num)
				cross.placedWords[q].clue.clue = new_text;
		}
	});
}

function assignBoxActions()
{
	LOG ("assigning box actions");
	$('.box').mousedown (boxClickHandler);
}

function add_class_to_box(x, y, class_name)
{
	LOG ('adding class ' + class_name + ' to ' + x + ',' + y);
	var $box = $box_x_y[x][y];
	/*
	var $box = $('.box').filter(function() {
	    return $(this).data('x') == x && $(this).data('y') == y;
	});
	*/
	$box.addClass(class_name);
}

function letter_entered(x, y, letter)
{
	LOG ('filling letter ' + letter + ' at ' + x + ',' + y);
	cross.current_state[x][y] = letter;
	var $box = $box_x_y[x][y];
	/*
	var $box = $('.box').filter(function() {
	    return $(this).data('x') == current_box.x && $(this).data('y') == current_box.y;
	});
	*/
	$('.letter-in-box', $box).text(letter);

	// check answers if all boxes have been filled
	if (all_boxes_filled())
		grid_filled(); 
}

function all_boxes_filled() {
	for (var j = 0; j < cross.h; j++) {
		for (var i = 0; i < cross.w; i++) {
			if (cross.box[i][j] == '\u0000' || cross.box[i][j] == '-')
				continue;
			if (cross.current_state[i][j] == '\u0000' || cross.current_state[i][j] == ' ') { 
				return false;
			}
		}
	}
	return true;
}

function any_boxes_filled()
{
	for (var j = 0; j < cross.h; j++) {
		for (var i = 0; i < cross.w; i++) {
			if (cross.box[i][j] == '\u0000' || cross.box[i][j] == '-')
				continue;
			if (cross.current_state[i][j] != '\u0000' && cross.current_state[i][j] != ' ') { 
				return true;
			}
		}
	}
	return false;
}

var boxClickHandler = function(e) { 
		// the target could be one of the letter-in-box, or cluenum-in-box span's, so look for the closest box
		var x = $.data($(e.target).closest('.box')[0], 'x'), y = $.data($(e.target).closest('.box')[0], 'y');
		LOG ('box ' + x + ', ' + y + ' clicked');
		var placedWordsIdxs = cross.boxToPlacedWordsIdxs[x][y];
		if (placedWordsIdxs == null || placedWordsIdxs.length == 0)
			return;
		
		// identify the word this box maps to
		var word = cross.placedWords[placedWordsIdxs[0]];		
		if (current_box != null && current_box.x == x && current_box.y == y) { // toggle if current box clicked again and belongs to multiple words
			if (placedWordsIdxs.length > 1) {
				word = (current_word == cross.placedWords[placedWordsIdxs[0]]) ? cross.placedWords[placedWordsIdxs[1]] : cross.placedWords[placedWordsIdxs[0]];
				LOG ('toggling selected word at ' + x + ', ' + y);
			}
		}
		
		// don't select_word again if we clicked on another box within the same word... 
	//	if (word != current_word)
		select_word(word.clueNum, word.acrossNotDown ? 'across' : 'down');
		current_box = {x: x, y: y, word_offset : (word.acrossNotDown ? (x - word.x) : (y - word.y))};
		hilite_boxes_for_current_word();
};

var clueClickHandler = function(e) { 
	// find the clue, and call select_word on it
	$clueDiv = $(e.target).closest('.clueDiv');
	var selected_clue_num = $clueDiv.attr('clueNum');
	var selected_direction = $clueDiv.attr('direction');
	for (var q = 0; q < cross.placedWords.length; q++)
	{
		var direction = cross.placedWords[q].acrossNotDown? 'across':'down';
		var num = cross.placedWords[q].clueNum;
		if (direction == selected_direction && num == selected_clue_num)
		{
			select_word(num, direction);
			break;
		}
	}
};

function grid_filled() {
	// repaint letter colors
	always_repaint_letter_colors = true; // from this point on, grid_filled has to be called for any letter entered
	LOG ('grid is full, repainting letters');
	var any_wrong = false;
	$boxes = $('.box');
	for (var i = 0; i < $boxes.length; i++) {
		var $box = $($boxes[i]);
		var x = $box.data('x'), y = $box.data('y');
		$('letter-in-box', $box).text(cross.box[x][y]);
		if (cross.box[x][y] == '\u0000' || cross.box[x][y] == '-')
			continue;
		$box.removeClass('wrongletter');
		$box.removeClass('letter');
		var is_wrong = (cross.current_state[x][y].toLowerCase() != cross.box[x][y].toLowerCase());
		if (is_wrong)
			any_wrong = true;
		$box.addClass(is_wrong ? 'wrongletter' : 'letter');
	}

	// give message
	if (!any_wrong && !completion_message_shown)
	{
		completion_message_shown = true;
		$.jGrowl('<span class="growl">Congratulations, 100% correct! Now you can click on the clues to get more information.</span>');
		LOG ('setting hrefs for clues to messages');
		linkCluesToMessages();
	}
	else
	{
		if (!warned_once) // show a warning only the first time
		{
			$.jGrowl('<span class="growl">Hmmm... you\'re close. Correct the letters in red.</span>');
			warned_once = true;
		}
	}
}

function show_correct_answers() {
    LOG ('showing answers');
	$boxes = $('.box');
	for (var i = 0; i < $boxes.length; i++) {
		var $box = $($boxes[i]);
		var x = $box.data('x'), y = $box.data('y');
		$('.letter-in-box', $box).text(cross.box[x][y]);
	}
}

function hide_correct_answers() {
    LOG ('hiding answers');
	$boxes = $('.box');
	for (var i = 0; i < $boxes.length; i++) {
		var $box = $($boxes[i]);
		var x = $box.data('x'), y = $box.data('y');
        var letter = cross.current_state[x][y];
        if (letter == '_') 
            letter = ' ';
		$('.letter-in-box', $box).text(letter);
	}
}

var $box_x_y = []; // this is a (x,y) -> $box for fast access
function draw_grid() {
	if (typeof cross == 'undefined')
		return; // we don't have the cross yet
	if (typeof friendcross !== 'undefined')
		LOG(friendcross.box);
	$("#crossword").html("");
	LOG ('rendering...');
	$box_x_y = [];
	
	for (var j = 0; j < cross.h; j++) {
		for (var i = 0; i < cross.w; i++) {
			if (j == 0)
				$box_x_y.push([]);
			
			if (cross.box[i][j] == "\u0000") {
				css_class = "empty";
			} else if (cross.box[i][j] == "-") { 
				css_class = "stop";
			} else {								
				css_class = "letter";
			}
			
//			var input = (cross.box[i][j] == "\u0000") || (cross.box[i][j] == "-") ? '' : '<textarea style="resize:none;padding:0;margin:0;border:0;opacity:0" rows="1" cols="1"></textarea>';

			var clueNumSpan = "";
			if (cross.clueNums[i][j] != 0)
				clueNumSpan = "<span  class=\"cluenum-in-box\">" + cross.clueNums[i][j]+ "</span>";

			// we need a letter-in-box because a box can have a cluenum also. we'll use letter-in-box to update the letter later
			str = "<div class=\"box " + css_class  +  "\">" + clueNumSpan + "<span class=\"letter-in-box\"> </span></div>";
			$("#crossword").append(str);
			
			// assign x, y, data items to the box we just created
			var $boxes = $('.box', '#crossword');
			var box_just_created = $boxes[$boxes.length-1];
			$(box_just_created).data('x', i);
			$(box_just_created).data('y', j);
			$box_x_y[i][j] = $(box_just_created);
		}
		var str2 = "<div class=\"endRow\"></div>";
		$("#crossword").append(str2);
	}
		
	if (typeof editable != 'undefined' && editable) {
		makeCluesEditable();
	} 

//	assignBoxActions();
//	enableHighlightOnClueHover();
}

function unhilite_everything() {
	LOG ('unhiliting everything');
	$('.hilited-box').removeClass('hilited-box');
	$('.hilited-box-with-focus').removeClass('hilited-box-with-focus');
	$('.hilited-clue').removeClass('hilited-clue');
}

function hilite_boxes_for_current_word() {
	// unhilite hilited boxes first
	$('.hilited-box').removeClass('hilited-box');
	$('.hilited-box-with-focus').removeClass('hilited-box-with-focus');

	if (current_word == null)
		return;

	
	// now hilite for current word
	for (var i = 0; i < current_word.word.length; i++) {
		var x = current_word.x + (i * (current_word.acrossNotDown ? 1:0));
		var y = current_word.y + (i * (current_word.acrossNotDown ? 0:1));
		if (x == current_box.x && y == current_box.y) {
			add_class_to_box(x, y, 'hilited-box-with-focus');
			if (runningOnTablet) {
				LOG ('enabling keyboard' + $('#dummy')[0]);
				$('#dummy').css('display', 'inline');		
				$('#dummy').css('left', $box_x_y[x][y].offset().left);
				$('#dummy').css('top', $box_x_y[x][y].offset().top);
				$('#dummy').focus();
			}
		}
		else
			add_class_to_box(x, y, 'hilited-box');			
	}

	// ok, repainted this word. pulse if there's no action until PULSE_DELAY
    // reset any pulse timeouts
    if (pulse_timeout_id) {
    	clearTimeout(pulse_timeout_id);
    }
	pulse_timeout_id = setTimeout(pulse_current_box, PULSE_DELAY);
}

/** important: single point of entry when a word is selected */
function select_word(num, direction) {
	LOG ('selecting word ' + num + ' ' + direction);

	// unhilited all hilited clues, and hilite the new one
	$('.hilited-clue').removeClass('hilited-clue');
	var $thisClue = $('.clueDiv').filter(function() {
	    return $(this).attr('direction') == direction && $(this).attr('clueNum') == num;
	});
	LOG ('highlighting ' + $thisClue.length + ' clue(s)');
	$thisClue.addClass('hilited-clue');
	
	// since we're decommissioning this clue, save its volume so the next clip can start with the same volume
	if ($('audio').length > 0) {
		try {
			audio_volume = $('audio')[0].volume;
			audio_muted = $('audio')[0].muted;
		} catch (e) { }
	}

	for (var q = 0; q < cross.placedWords.length; q++){
		var directionMatches = (cross.placedWords[q].acrossNotDown && direction == "across") || (!cross.placedWords[q].acrossNotDown && direction == "down");
		if (cross.placedWords[q].clueNum == num && directionMatches) {
			current_box = {x: cross.placedWords[q].x, y: cross.placedWords[q].y, word_offset: 0};

			// don't restart audio if we are simply replacing position within the same word
			if (current_word != cross.placedWords[q]) {
				current_word = cross.placedWords[q];
				LOG ('current_word = ' + current_word);
				unhilite_all_boxes(); // unhilite all boxes first
				hilite_boxes_for_current_word();
				if (current_word.clue.audioURL) {
					var url = current_word.clue.audioURL;
					var is_mpeg = (url.indexOf(".mp3", url.length - ".mp3".length) !== -1) || (url.indexOf(".m4a", url.length - ".m4a".length) !== -1) ;
					var is_ogg = (url.indexOf(".ogg", url.length - ".ogg".length) !== -1);
					if (is_mpeg || is_ogg) {
						if ($.browser.mozilla && is_mpeg) {
							$('#audio').html("Sorry, Firefox does not play MP3 files. <br/>Please use Chrome or Safari instead.<br/>Or open this URL in a program that plays MP3 files: <br/>" + current_word.clue.audioURL);
						}
						else {
							var type = is_mpeg ? "audio/mpeg" : "audio/ogg";
							$('#audio').html("<audio controls><source src=\"" + current_word.clue.audioURL + "\" type=\"" + type + "\"></audio>");
							try {
								$('audio')[0].volume = audio_volume;
								$('audio')[0].muted = audio_muted;
							} catch (e) { }
							$('audio')[0].play();
						}
						$('#video').html('');
						$('#picture').html('');
					}
					else {
						LOG ('warning: unknown audio type: ' + url);
					}
				}
				
				// do multimedia stuff for the current word
				if (current_word.clue.URLs && current_word.clue.URLs[0] && current_word.clue.URLs[0].indexOf("youtube.com") >= 0) {
					var url = current_word.clue.URLs[0];
					// youtube id spec: https://groups.google.com/forum/#!topic/youtube-api-gdata/maM-h-zKPZc
					// currently youtube ids are 0-9a-zA-z and the chars - and _, though this may change in the future
					var yid = url.replace(/.*v=([0-9a-zA-Z-_]+).*/, '$1'); 
					if (yid && yid.length > 0 && yid.length < url.length) {
						// parse '.*#t=20.* and extract 20 into start_time
						var start_time = url.replace(/.*#t=([0-9]+).*/, '$1');
						var start_param = (start_time || start_time.length > 0) ? '&start=' + start_time : '';
						var src = '//www.youtube.com/embed/' + yid + '?rel=0&modestbranding=1&autoplay=1&showinfo=0' + start_param;
						LOG ('youtube embed src = ' + src);
						$x = $('<iframe class="fancybox" width="420" height="315" src="' + src + '" frameborder="0" allowfullscreen></iframe>');
						$('#audio').html('');
						$('#picture').html('');
//						$("#video").eq(0).trigger('click');
						$x.fancybox({width:420});
						$x.eq(0).trigger('click');
						// neither the start=20 nor t=20 works
					}
				} else if (current_word.clue.URLs && current_word.clue.URLs[0]) {
					var url = current_word.clue.URLs[0];
					var lc_url = url.toLowerCase();
					var is_image = (lc_url.lastIndexOf(".jpg") == (lc_url.length - ".jpg".length)) ||
								   (lc_url.lastIndexOf(".png") == (lc_url.length - ".png".length)) ||
								   (lc_url.lastIndexOf(".gif") == (lc_url.length - ".gif".length));
					
					if (is_image) {
						$x = $('<div style="width:420"><a target="_blank" href="' + url + '"><img width="420" src="' + url + '"/></a></div>');
						$('#audio').html('');
						$('#video').html('');	
						$x.fancybox({width:420});
						$x.eq(0).trigger('click');
					}				
				}
			}
			
			return;	    
		}
	}
}

function clearCrossword(){
	
	if (typeof cross == 'undefined')
		return;

	// allocate for current state
	cross.current_state = new Array();
	if (typeof friendcross !== 'undefined')
		friendcross.current_state = new Array();
	for (var i = 0; i < cross.w; i++){
		cross.current_state[i] = new Array();
		if (typeof friendcross !== 'undefined')
			friendcross.current_state[i] = new Array();
	}

	// fill in current state with empties
	for (var j = 0; j < cross.h; j++){
		for (var i = 0; i < cross.w; i++){
			if (typeof friendcross !== 'undefined')
				friendcross.current_state[i][j] = "/";
			cross.current_state[i][j] = "\u0000"; // signifies empty
		}
	}
}

function keypress_handler(event) {
	// returns whether a move was actually made
	function move_current_box(incr) {
		var moved = false;
		if (current_word.acrossNotDown)
   		{
   			if ((current_box.x+incr) >= current_word.x && (current_box.x+incr) < (current_word.x+current_word.word.length)) {
   				current_box.x += incr;
   				current_box.word_offset += incr;
   				moved = true;
   			}
   		}
   		else
   		{
   			if ((current_box.y+incr) >= current_word.y && (current_box.y+incr) < (current_word.y+current_word.word.length)) {
   				current_box.y += incr;
   				current_box.word_offset += incr;
   				moved = true;
   			}
   		}
		
		if (runningOnTablet) {
			$('#dummy').css('left', $box_x_y[current_box.x][current_box.y].offset().left);
			$('#dummy').css('top', $box_x_y[current_box.x][current_box.y].offset().top);
			$('#dummy').focus();
		}

		return moved;
	}

	var code = (event.keyCode) ? event.keyCode : event.charCode;
    
    LOG ('key code received = ' + code);
    
    if (current_box != null) {
	    if (code == 27) { // escape
	    	current_box = current_word = null;
			unhilite_everything();
	    } else if (code == 16 || code == 17 || code == 18 || code == 91 || code == 9 || code == 20) { // ignore control, shift, meta, command, tab, caps lock
	    } else if (code == 8) { // backspace
	    	move_current_box(-1);
	   		letter_entered(current_box.x, current_box.y, ' ');
	        event.preventDefault(); // otherwise browser navigates to the prev. page! http://stackoverflow.com/questions/1495219/how-can-i-prevent-the-backspace-key-from-navigating-back
	   	} else if (code == 37) { // left arrow
	   	       	if (current_word.acrossNotDown) 
	   	       		move_current_box(-1);	    	
	   	} else if (code == 39) { // right arrow
	       		if (current_word.acrossNotDown)
   	       			move_current_box(1);	    	
	   	} else if (code == 38) { // up arrow
	   			if (!current_word.acrossNotDown)
	       			move_current_box(-1);	    	
	   	} else if (code == 40) { // down arrow
   				if (!current_word.acrossNotDown)
   					move_current_box(1);	    		   		
	   	} else {
	   		var ch = String.fromCharCode(code).charAt(0);
	   		letter_entered(current_box.x, current_box.y, ch);
	   		var moved = move_current_box(1);
			if (!moved && current_box.word_offset == current_word.word.length-1) { // end of word
				current_box = current_word = null;
				unhilite_everything();
				return false; // kill the letter going anywhere else -- on the android tablet, it goes to the location bar
			}
	   	}
    }
    else
   	 	LOG ('keypress unused because no box is currently selected');
    
    // we'll repaint the whole word, not just the boxes that changed
    if (current_word != null)
    	hilite_boxes_for_current_word(); // if current word is null, we've already unhilited whatever we need to
}

function pulse_current_box() {
	LOG ('pulse timeout received');
	if (!current_box) 
		return;
	if (pulse_timeout_id)
		clearTimeout(pulse_timeout_id);
	
	var current_box_for_pulse = current_box; // track which box is being pulsed
	// start the pulsed state
	var elem = $('.hilited-box-with-focus')[0];
	$(elem).removeClass('hilited-box-with-focus');
	$(elem).addClass('hilited-box');
	pulse_timeout_id = setTimeout(restore_current_box, PULSE_DURATION);

	// this will restore the non-pulsed state
	function restore_current_box() { 
		// if current box has changed, do nothing
		if (!current_box || current_box != current_box_for_pulse) 
			return;
		// restore the non-pulsed state
		$(elem).removeClass('hilited-box');
		add_class_to_box(current_box.x, current_box.y, 'hilited-box-with-focus');
		
		pulse_timeout_id = setTimeout(pulse_current_box, PULSE_DELAY);
	}
}

var showing_answers = false;
$(document).ready(function() {
	if (picClues)
        $('.fancybox').fancybox();

	if (!picClues && runningOnTablet)
		linkCluesToHints();
	
	$('#answers-button').click(function() { 
		// toggle showing_answers, re-render the grid
		if (showing_answers) { showing_answers = false; linkCluesToAnswerEntry(); hide_correct_answers(); $('#answers-button u').text("Answers");}
		else {
			showing_answers = true;  
			show_correct_answers(); 
			$('#answers-button u').text("Hide");
     		linkCluesToMessages();
 		    $.jGrowl('<span class="growl">Click on a clue for more information.</span>', {life: 15000});
		}
	});

	if ($('#save-button').length > 0) {
		$('#save-button').click(save_button_clicked);
	}
	function save_button_clicked() { 
		muse.log('save button clicked');
		var save_json = (JSON.stringify(cross));
		$.ajax({
			type: 'POST',
			url: 'crossword-manage',
		    contentType: "application/x-www-form-urlencoded; charset=UTF-8",
		    dataType: "json",
			data: {save: 'true', save_json: save_json},
			success: function(response) {
				if (response && response.status == 0 && typeof response.url !== 'undefined') {
					$('#save_info').html("Reload this puzzle at: " + response.url);
				}
				else {
					alert('Sorry, there was an error in saving this puzzle. (HTTP status = ' + response.status + ')');
				}
			},
			error: function(response) {	alert('Sorry, there was an error in saving this puzzle'); }
		});
	}
	
    // inject fb code
    (function(d) {
        var js, id = 'facebook-jssdk'; if (d.getElementById(id)) {return;}
        js = d.createElement('script'); js.id = id; js.async = true;
        js.src = "//connect.facebook.net/en_US/all.js";
        d.getElementsByTagName('head')[0].appendChild(js);
      })(document); 
    // set up fb lib
    var appId =  '1395718427331638'; // pmr at muse:8000
    
    window.fbAsyncInit = function() {
        FB.init({
          	appId: appId, 
          //  auth_response: auth,
            status     : true, 
            cookie     : true,
            xfbml      : true,
            oauth      : true,
          });	          
  	};
  
	$('#fb-button').click(function() { 
		muse.log('save button clicked');
		alert ("Please ensure that popups are enabled in your browser. Your crossword grid will be saved in an album called Muse Photos. The picture will also be published to your feed for your friends to see.");
		var save_json = (JSON.stringify(cross));
		$.post('crossword-manage',
			{save: 'true', saveImage: 'true', save_json: save_json},
			function(response) {
				if (response && response.status == 0 && typeof response.id !== 'undefined') {
					var xwordImageURL = document.location.origin + '/muse/xwordImage.jsp?id=' + response.id;
			        muse.log('saved image response id = ' + response.id + ', xword image url is ' + xwordImageURL + ', about to fb login');
			        FB.login(function(response) {
				        muse.log('fb logged in ' + FB.getUserID() + ', calling ui');
				        /*
			        	  var obj = {
						          method: 'feed',
						          link: 'http://muse.stanford.edu/muse',
						          picture: xwordImageURL,
						          name: 'Personal Crossword by Muse',
						          caption: 'My personal Crossword',
						          description: 'A personal crossword generator from your own emails!'
						        };

				        // FB.ui(obj, function() { muse.log('posted by fb user id ' + FB.getUserID()); });
				        */
							FB.api('/' + FB.getUserID() + '/photos', 'post', {
							    message: 'Play Puzzle Me Raga at http://bit.ly/ragas',
							    url:xwordImageURL,
							    link: 'http://bit.ly/fbpuzzle',
							    caption: cross.title + ' (by Muse)'
							}, function(response){						
							    if (!response || response.error) {
							        alert('Error occured while saving image to Facebook. Please try again.');
							    } else {
							    	log.info('photo posted ' + response.id)
							        alert('Posted to Facebook. You will find the image under an album called Muse photos');
							    }
							});
			        	LOG (response.authResponse);
			        },
					{scope:'publish_stream,user_photos'});	       					       
				}
				else {
					alert('Sorry, there was an error in sharing this puzzle');
				}
			});
	});
	
	$('#logout-button').click(function() { window.location = 'logout.jsp?clearCache=';});
	$('#nextlevel-button').click(function() { 
		var page = "ajax/xwordAsJson.jsp?&webui=";
		fetch_page_with_progress(page, "status", document.getElementById('status'), document.getElementById('status_text') /* post_params undefined */);
	});
	
	draw_grid();
	if (!picClues)
		linkCluesToAnswerEntry();
	assignBoxActions();
	$('body').keydown(keypress_handler);
	
	/*
	if (runningOnTablet) {
		$(document).click(function() {
		LOG ('enabling keyboard' + $('#dummy')[0]);
		$('#dummy').css('display', 'inline');
		$('#dummy').focus();
		});
	}
	*/
	
	window.onbeforeunload = function() { 
		if (any_boxes_filled()) // only prompt if at least one box is filled
			return "Are you sure you want to navigate away from this page? You will lose your progress.";
	};
});

var is_creator = false;
