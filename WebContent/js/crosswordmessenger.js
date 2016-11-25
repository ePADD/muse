/**
 * 
 */
var colors;
var mycolor;
var count = 0;
var playerindex;
function Update(){       
    colors[0] = "#ff7f0e";   
    colors[1] = "#17becf";
    colors[2] = "#d62728";
    colors[3] = "#2ca02c";
    colors[4] = "#9467bd";
    colors[5] = "#8c564b";
    colors[6] = "#e377c2";
    colors[7] = "#7f7f7f";
    colors[8] = "#bcbd22";
    colors[9] = "#1f77b4";
}

function LOG (s) { 	if (typeof console != 'undefined') 	console.log(s);}

var activity = {ad:"xword.app"};

function UpdateWords(direction, num, answer){
    LOG("Called Update Words. Direction: " + direction + " num : " + num  + " answer " + answer);
    for(var q = 0; q < cross.placedWords.length; q++){
	var directionMatches = (cross.placedWords[q].acrossNotDown && direction == "across") || (!cross.placedWords[q].acrossNotDown && direction == "down");
	if(cross.placedWords[q].clueNum == num && directionMatches ){	 			   			     
	    for(var a = 0; a < answer.length; a++){
		if(direction == "across"){
		    friendcross.box[cross.placedWords[q].x + a][cross.placedWords[q].y] =answer.charAt(a);
		}
		else{
		    friendcross.box[cross.placedWords[q].x][cross.placedWords[q].y + a] =answer.charAt(a);
		}
	    }
	    Render(); 		    
	    return;			    
	}
    }		    
    Render();

}
	
function new_crossword_player(name) {
    return {
	// each participating person or team has a name ('neel', 'red'), that uniquely identifies him/them to the others.
	// and also a uuid (random, unique, long string). the uuid should never be displayed on screen, only used for debugging
      id: 'uninitialized',
	setId: function(id) { this.id = id; },
	name: name,
	setName: function(name) { this.name = name; },
	onMessageReceived: function(msg) { 
            if (msg.id == this.id) {
		// our own message -- just log and ignore it.
		LOG ('received own message: ' + msg.name + ' (' + msg.id + ') operation: ' + msg.op + ' ' + msg.num + msg.d_or_a); 
            } else {
		if (msg.op === 'joined')
                    LOG ('just joined: ' + msg.name + ' (' + msg.id + ')'); 
		else {
                    LOG ('received message from ' + msg.name + ' (' + msg.id + ') operation: ' + msg.op + ' ' + msg.num + msg.d_or_a); 
		    UpdateWords(msg.d_or_a, msg.num, msg.answer);
		    // do more stuff here like display something on the grid or at the clue
		}
            }
	},
	// this function should be called when a word is filled in
	// d_or_a is a single char 'd' or 'a', indicating down or across
	
      wordFilled: function(direction, wordNum, answer) { 
          LOG ('word filled'); 
          // sends a message to everyone else saying this participant filled in this word
          // can add more stuff to the message if needed
	  //  UpdateWords(direction, wordNum, answer, false);
	  this.sendMessageToSession({id: this.id, name: this.name, op: 'filled', num:wordNum, d_or_a: direction, answer:answer}); 
      },
	// this function is called when (if) a word is cleared
	wordCleared: function(wordNum, d_or_a) { 
            LOG ('word cleared'); 
            this.sendMessageToSession({id: this.id, name: this.name, op: 'cleared', num:wordNum, d_or_a: d_or_a}); 
	},
	// this function is called when a new player joins the session
	onActivityJoin : function() { 
          LOG ('player ' + this.name + ' joined session ' + this.junction.sessionID);
          if (is_creator)
        	  {
        	  	LOG ("i am activity creator");
        	  } else
        	  {
        		  LOG ("i am not activity creator");
        	  }
          this.sendMessageToSession({id: this.id, name: this.name, op: 'joined'});
      }
  };
};

var crossword = null;
// this function is called once at the start
function join() {
    if(window.location.href.indexOf("jxuri") < 0)
    	return;
    var index = window.location.href.lastIndexOf("=");
    var name = window.location.href.substring(index + 1);
    LOG ('name = ' + name);
    crossword = new_crossword_player(name);
    var jx = JX.getInstance("prpl.stanford.edu").newJunction(activity, crossword);
    crossword.setId(jx.actorID);
    LOG ('my id is ' + jx.actorID);
    LOG('creator = ' + is_creator);
    LOG ('my session is ' + crossword.junction.sessionID);
    return false;
}