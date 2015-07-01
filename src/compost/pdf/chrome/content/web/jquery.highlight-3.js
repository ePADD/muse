/*

highlight v3

Highlights arbitrary terms.

<http://johannburkard.de/blog/programming/javascript/highlight-javascript-text-higlighting-jquery-plugin.html>

MIT license.

Johann Burkard
<http://johannburkard.de>
<mailto:jb@eaio.com>

*/

jQuery.fn.highlight = function(hits, fbgcolor,ind) {
 function innerHighlight(node, hits, fbgcolor,ind) {
  var skip = 0;
  if (node.nodeType == 3) {
  // check if this node contains any hits
    for (var hit = 0; hit < hits.length; hit++)
    {
   		var pos = node.data.toUpperCase().indexOf(hits[hit].toUpperCase());
   		if (pos >= 0) {
    			var spannode = document.createElement('span');
    			 var cnt=ind*10+hit;
    			spannode.className = 'highlight personal'+cnt;
    		// Setting padding & margin to 0px to fix this -- https://github.com/curiosity/Find-Many-Strings/issues/1
    			fbgcolor += ";padding: 0px; margin: 0px;";
    			spannode.setAttribute('style', "border-bottom: 3px red dotted;");
    			var middlebit = node.splitText(pos);
    			var endbit = middlebit.splitText(hits[hit].length);
    			var middleclone = middlebit.cloneNode(true);
    			spannode.appendChild(middleclone);
    			middlebit.parentNode.replaceChild(spannode, middlebit);
    			skip = 1;
   		}
   	}	
  }
  else if (node.nodeType == 1 && node.childNodes && !/(script|style)/i.test(node.tagName)) {
   for (var i = 0; i < node.childNodes.length; ++i) {
    i += innerHighlight(node.childNodes[i], hits, fbgcolor,ind);
   }
  }
  return skip;
 }
 return this.each(function() {
  innerHighlight(this, hits, fbgcolor,ind);
 });
};

jQuery.fn.removeHighlight = function() {
 return this.find("span.highlight").each(function() {
  this.parentNode.firstChild.nodeName;
  var txtNode = document.createTextNode(this.textContent);
  with (this.parentNode) {
   replaceChild(txtNode, this);
   normalize();
  }
 }).end();
};
