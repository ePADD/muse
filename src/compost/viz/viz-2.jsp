<%@page language="java" import="java.io.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.muse.index.*"%>
<%@page language="java" import="edu.stanford.bespoke.mining.*"%>
<%@page language="java" import="edu.stanford.muse.groups.*"%>
<%@page contentType="text/html; charset=UTF-8"%>

<html>
<head>
	<title>Topology Visualization</title>
	<script type="text/javascript" src="../js/protovis-r3.2.js"></script>
    <script type="text/javascript" src="../js/dump_function.js"></script>
    <style type="text/css">
    body {
      margin: 0;
    }
    </style>
</head>

<body>
<%
	JSPHelper.logRequest(request);
	String viz_addressBook = (String) JSPHelper.getSessionAttribute(session, "viz_addressBook");
	if (viz_addressBook == null || viz_addressBook.trim().isEmpty()){
		viz_addressBook = "empty";
	}
	out.println("<script type=\"text/javascript\"> var addressBook = " + viz_addressBook + "; </script>");

	String viz_groups = (String) JSPHelper.getSessionAttribute(session, "viz_groups");
	if (viz_groups == null || viz_groups.trim().isEmpty())
		viz_groups = "empty";
	out.println("<script type=\"text/javascript\"> var groups = " + viz_groups + "; </script>");
%>

<script type="text/javascript">
/*
	if (addressBook) {document.writeln("***addressBook variable is:*** " + dump(addressBook));}
	else {document.writeln("***addressBook variable not found***");}
	if (groups) {document.writeln("***groups variable is:*** " + dump(groups));}
	else {document.writeln("***groups variable not found***");}
*/
</script>

<script type="text/javascript+protovis">

var w = document.body.clientWidth,
    h = document.body.clientHeight,
    colors = pv.Colors.category19();

///////////////////////////////
// ***        FUNCTIONS     ***
///////////////////////////////

    function populate_info(curr_node, zoom){
      if (curr_node){
	if (curr_node.count==undefined){
		info[0]="Details of selected group node:";
    		info[1]="Group Name: "+curr_node.nodeName;
		info[2]="";
		info[3]="";
		info[4]="";
	}
	else{
		info[0]="Details of selected person node:";
		info[1]="Contact Name: "+((curr_node.name) ? curr_node.name : "Unknown");
		info[2]="Email: "+curr_node.nodeName;
		info[3]="Message count: "+curr_node.count;
		if (!zoom){
			if (curr_node.group!=undefined){
				info[4]="Primary group: "+ top_groups[curr_node.group].nodeName;
			}
			else{
				info[4]="Primary group: None";
			}
		}
	}
      }
    }

    function color_node(d, index, selected_g, selected_n) {
	var color;
	if (d.group==selected_g){
		color = "red";
	}
	else {
		if ( (selected_n == -1) || (d.isGroup) || ((!d.isGroup) && (share_common_group[index][selected_n]==1)) ){
			color = colors(d.group);
		}
		else{
			color = "white";
		}

	}
	return color;
    }

    function call_zoom(curr_node){
    	    if (curr_node.count==undefined){zoom_func(top_groups[curr_node.group]);}
	else{
		if (curr_node.group!=undefined){zoom_func(top_groups[curr_node.group]);}
		else{zoom_func("clear");}
	}
    }

///////////////////////////////
// *** MAIN SOCIAL TOPOLOGY ***
///////////////////////////////

var info = new Array();

var zoom;
var to_zoom;

var i;
var j;

var person_nodes=new Array();
var person_array=new Array();
for (i=0;i<addressBook.length;i++){
	person_addr = addressBook[i].emailAddrs[0];
	person_nodes[i] = {"nodeName":person_addr, "name":addressBook[i].names[0], "isGroup":false, "group":undefined, "count":(addressBook[i].messageOutCount + addressBook[i].messageInCount)};
	person_array[person_addr] = i;
}

var central_links=new Array();
// links between each person node and me
/*
for (i=1;i<addressBook.length;i++){
	central_links[i-1] = {"source":0, "target":i, "value":person_nodes[i].count};
}
*/

var top_groups = new Array();
var top_group_links = new Array();
// group_matrix records all the groups of each node - required to calculate share_common_group
var group_matrix = new Array();
for (i=0;i<addressBook.length;i++){
	group_matrix[i] = new Array();
	for (j=0;j<groups.length;j++){
		group_matrix[i][j] = 0;
	}
}

for (i=0;i<groups.length;i++){
	// NOTE: group num = i;
	group_index = addressBook.length + i;
	group_name = "groupNum" + i;
	top_groups[i] = {"nodeName":group_name, "name":undefined, "isGroup":true, "group":i, "count":groups[i].group.utility};
	var temp_links = new Array();
	var group_members = groups[i].group.members;
	for (j=0;j<group_members.length;j++){
		person_index = person_array[group_members[j].emailAddrs[0]];
		temp_links[j] = {"source":person_index, "target":group_index, "value":1};
		person = person_nodes[person_index];
		if (person.group == undefined) {
			person.group = i;
		}
		else {
			if (top_groups[person.group].count < top_groups[i].count){
				person.group = i;
			}
		}
		group_matrix[person_index][i] = 1;
	}
	var group_to_me = new Array()
	group_to_me = {"source":0, "target":group_index , "value":1}
	top_group_links = top_group_links.concat(temp_links, group_to_me);
}


var share_common_group = new Array()
for (i=0;i<addressBook.length;i++){
	share_common_group[i] = new Array();
	for (j=0;j<addressBook.length;j++){
		share_common_group[i][j]=0;
		for (k=0;k<groups.length;k++){
			if (group_matrix[i][k] > 0 && group_matrix[j][k] > 0){
				share_common_group[i][j]=1;
				//k = groups.length;
			}
		}
	}
}


var all_nodes = person_nodes.concat(top_groups);
var all_links = central_links.concat(top_group_links);

//    document.writeln(dump(all_nodes));
//    document.writeln(dump(all_links));


/////////////////////////
// *** ZOOMED IN DATA ***
/////////////////////////

// BEGIN zoom_func

function zoom_func(top_group_node){

var i;
var j;

var vis_zoom = vis_side.add(pv.Panel)
    .left(0)
    .top(h*0.55 + 40)
    .width(w*0.24)
    .height(h*0.45 - 40)
    .fillStyle("white")
    .event("mousedown", pv.Behavior.pan())
    .event("mousewheel", pv.Behavior.zoom())
    .event("mousemove", pv.Behavior.point());


if (top_group_node!="clear"){

var p_nodes=new Array();
var p_array=new Array();
var g_nodes=new Array();
var g_array=new Array();

var top_group = groups[top_group_node.group];

g_nodes[0] = {"nodeName":top_group_node.nodeName, "name":undefined, "group":"0-0", "count":top_group.group.utility , "isGroup":true};

var persons = top_group.group.members;
for (j=0;j<persons.length;j++){
	person_addr = persons[j].emailAddrs[0];
	p_nodes[j] = {"nodeName":person_addr, "name":persons[j].names[0], "isGroup":false, "group":"0-0", "count":(persons[j].messageOutCount + persons[j].messageInCount)};
	p_array[person_addr] = j;
}

function hierarchy(input_group, level, parent){
  if (input_group){
    for (i=input_group.length-1;i>=0;i--){
      if (input_group[i].group){
      	var group_name = "level"+level+"-group"+i;
      	var group_num = level+"-"+i;
        var group_node = {"nodeName":group_name, "name":undefined, "isGroup":true, "group":group_num, "count":input_group[i].group.utility, "parent":parent};
        g_nodes.push(group_node);
        var members = input_group[i].group.members;
        for(j=0;j<members.length;j++){
          var person_index = p_array[members[j].emailAddrs[0]];
          p_nodes[person_index].group = group_num;
        }
      }
      if(input_group[i].subsets){
      	  parent_group = g_nodes.pop();
      	  g_nodes.push(parent_group);
    	  hierarchy(input_group[i].subsets, level+1, parent_group.group);
      }
    }
  }
}

hierarchy(top_group.subsets, 1, "0-0");

for (i=0; i<g_nodes.length; i++){
	g_array[g_nodes[i].group] = persons.length+i;
}

var p_links = new Array();
var g_links = new Array();

for (j=0;j<p_nodes.length;j++){
	p_links[j]={"source":j, "target":g_array[p_nodes[j].group], "value":1};
}
for (j=1;j<g_nodes.length;j++){
	g_links[j-1]={"source":g_array[g_nodes[j].group], "target":g_array[g_nodes[j].parent], "value":1};
}

var a_links = p_links.concat(g_links);
var a_nodes = p_nodes.concat(g_nodes);

var vis_zoom_label = vis_side.add(pv.Panel)
    .left(0)
    .top(h*0.55)
    .width(w*0.24)
    .height(40)
    .fillStyle("white");

vis_zoom_label.add(pv.Label)
    .left(0)
    .top(20)
    .textAlign("left")
    .font("16px sans-serif")
    .text("Detailed social hierarchy of primary group: ");

var sel_g = -1;

var force = vis_zoom.add(pv.Layout.Force)
    .nodes(a_nodes)
    .links(a_links);

// Add arrows to zoomed hierarchy
force.link.add(pv.Line)
  .strokeStyle("#999")
        .lineWidth(2)
        .add(pv.Dot)
                .data(function(l) [{
                        x: l.sourceNode.x - 2.6 *
Math.cos(Math.atan2(l.sourceNode.y -
l.targetNode.y, l.sourceNode.x - l.targetNode.x)),
                        y: l.sourceNode.y - 2.6 *
Math.sin(Math.atan2(l.sourceNode.y -
l.targetNode.y, l.sourceNode.x - l.targetNode.x)),
z:l.sourceNode.isGroup
		}])
                .angle(function (n,l) Math.atan2(l.sourceNode.y -
l.targetNode.y,
l.sourceNode.x - l.targetNode.x) - Math.PI/2)
	.shape(function(l)  (l.z) ? "triangle" : "line")
        .fillStyle("black")
        .size(function(l)  (l.z) ? "2" : "0");

force.node.add(pv.Dot)
    .def("active", -1)
    .size(function(d) ( (d.isGroup) ? 10 : d.count+4 ) * Math.pow(this.scale, -1.5))
    .fillStyle(function(d) ((d.group == sel_g) || d.fix)&& !(d.group==undefined) ? "red" : colors(d.group))
    .strokeStyle(function() this.fillStyle().darker())
    //.title(function(d) d.nodeName)
    .event("mousedown", pv.Behavior.drag())
    .event("mouseover", function(d) sel_g = d.group )
    .event("click", function(d) {populate_info(d, 1); vis.render();})
    .event("drag", force)
    .shape(function() (this.index < persons.length) ? "circle" : "cross")
    .event("point", function() this.active(this.index).parent)
    .event("unpoint", function() this.active(-1).parent)
    .anchor("right").add(pv.Label)
    .visible(function() this.anchorTarget().active() == this.index)
    //Display the node name immediately
    .text(function(d) (d.name!=null) ? d.name : d.nodeName);


} //END if (top_group_node!="clear")

} //END zoom_func


var vis = new pv.Panel()
    .width(w)
    .height(h-75)
    .fillStyle("gray");

var vis_left = vis.add(pv.Panel)
    .left(0)
    .top(0)
    .width(w*0.75)
    .height(h)
    .fillStyle("white");

var vis_main = vis_left.add(pv.Panel)
    .left(0)
    .top(45)
    .width(w*0.75)
    .height(h-90)
    .fillStyle("white")
    .overflow("hidden")
    .event("mousedown", pv.Behavior.pan())
    .event("mousemove", pv.Behavior.point())
    .event("mousewheel", pv.Behavior.zoom());

var vis_instructions = vis_left.add(pv.Panel)
    .left(0)
    .top(0)
    .width(w*0.75)
    .height(45)
    .fillStyle("white");


var force = vis_main.add(pv.Layout.Force)
    .nodes(all_nodes)
    .links(all_links);

var selected_g = -1;
var selected_n = -1;
force.link.add(pv.Line)
    .visible(function(d, p) ((p.source != 0) && (p.target != 0)));

force.node.add(pv.Dot)
    .def("active", -1)
    .size(function(d) ( (d.isGroup) ? 10 : d.count+4 ) * Math.pow(this.scale, -1.5))
    .fillStyle(function(d) color_node(d, this.index, selected_g, selected_n) )
    .strokeStyle(function() this.fillStyle().darker())
    //.title(function(d) d.nodeName)
    .shape(function(d) (!d.isGroup) ? "circle" : "cross")
    // making me node and all lose nodes invisible.
    .visible(function(d) this.index ? (d.group==undefined ? 0 : 1) : 0)
    .event("mouseover", function(d) {selected_g = d.group; selected_n = this.index;} )
    .event("mouseout", function() {selected_g = -1; selected_n = -1;})
    .event("click", function(d) {populate_info(d, 0); call_zoom(d); vis.render(); })
    .event("mousedown", pv.Behavior.drag())
    .event("drag", force)
    .event("point", function() this.active(this.index).parent)
    .event("unpoint", function() this.active(-1).parent)
  .anchor("right").add(pv.Label)
    .visible(function() this.anchorTarget().active() == this.index)
    //Display the node name immediately
    .text(function(d) (d.name!=null) ? d.name : d.nodeName);

var instr = new Array();
instr.push("Network Layout with Single Nodes (contacts not in any group) in side panel");
instr.push("Hover over a node to see the details and group highlighting; click to see detailed hierarchy of group");

vis_instructions.add(pv.Label)
	.data(instr)
    .left(w*0.75*0.5)
    .top(function() 20*(this.index+1))
    .textAlign("center")
    .font(function() (this.index) ? "12px sans-serif" : "16px sans-serif")
    .text(function(d) d);

/////////////////////////
// *** SIDE PANEL     ***
/////////////////////////

var vis_side = vis.add(pv.Panel)
    .left(w*0.76)
    .top(0)
    .width(w*0.24)
    .height(h)
    .fillStyle("white")
    .event("mousedown", pv.Behavior.pan())
    .event("mousewheel", pv.Behavior.zoom());


/////////////////////////
// *** SINGLE NODES   ***
/////////////////////////
var vis_others = vis_side.add(pv.Panel)
    .left(0)
    .top(40)
    .width(w*0.24)
    .height(h*0.30)
    .fillStyle("white")
    .overflow("hidden")
    //make elements invisible outside of the panel area
    .transform(pv.Transform.identity.scale(0.25))
    //set the default zoom value
    .event("mousedown", pv.Behavior.pan())
    .event("mousemove", pv.Behavior.point())
    //used for the fast tooltip
    .event("mousewheel", pv.Behavior.zoom());


var vis_others_label = vis_side.add(pv.Panel)
    .left(0)
    .top(0)
    .width(w*0.24)
    .height(40)
    .fillStyle("white");

vis_others_label.add(pv.Label)
    .left(0)
    .top(30)
    .textAlign("left")
    .font("16px sans-serif")
    .text("Single nodes: ");

var separation = vis_side.add(pv.Panel)
    .left(0)
    .top(h*.30 +40)
    .width(w*.24)
    .height(h*.02)
    .fillStyle("gray");


var force_others = vis_others.add(pv.Layout.Force)
    .nodes(all_nodes)
    .links(all_links);

var selected_g = -1;
var selected_n = -1;


force_others.node.add(pv.Dot)
    .def("active", -1)
    .size(function(d) ( (d.isGroup) ? 10 : d.count+4 ) * Math.pow(this.scale, -1.5))
    .fillStyle(function(d) (((this.index < addressBook.length)?(group_matrix[this.index][selected_g]):(selected_g == d.group)) && !(d.isGroup)) ? "red" : (((selected_n == -1) || (d.isGroup) || ((this.index < addressBook.length) && (share_common_group[this.index][selected_n]==1))) ? colors(d.group) : "white"))
    .strokeStyle(function() this.fillStyle().darker())
    //.title(function(d) d.nodeName)
    .event("mousedown", pv.Behavior.drag())
    .event("click", function(d) {populate_info(d, 1); vis.render();})
    .event("drag", force_others)
    .shape(function(d) (!d.isGroup) ? "circle" : "cross")
    .visible(function(d) this.index ? (d.group==undefined ? 1 : 0) : 0)
    // making me node and all lose nodes visible.
    .event("point", function() this.active(this.index).parent)
    .event("unpoint", function() this.active(-1).parent)
    .anchor("right").add(pv.Label)
    .visible(function() this.anchorTarget().active() == this.index)
    //Display the node name immediately
    .text(function(d) (d.name!=null) ? d.name : d.nodeName);


/////////////////////////
// ***  TEXTUAL INFO  ***
/////////////////////////

var vis_info = vis_side.add(pv.Panel)
    .left(0)
    .top(h*0.35 + 40)
    .width(w*0.24)
    .height(h*0.17)
    .fillStyle("white")
    .event("mousedown", pv.Behavior.pan())
    .event("mousewheel", pv.Behavior.zoom());

vis_info.add(pv.Label)
    .data(info)
    .left(0)
    .top(function() 20*(this.index+1))
    .textAlign("left")
    .font("16px sans-serif")
    .text(function(d) d);

vis.render();

</script>

<p>
Go to Visualization: <a href="viz-1.jsp">Previous</a> | <a href="viz-3.jsp">Next</a>
<br/>
Or skip by name: <a href="viz-1.jsp">Network Layout without single nodes</a> | <a href="viz-2.jsp">Network Layout with single nodes</a> | <a href="viz-3.jsp">Network Layout with colored links</a> | <a href="viz-4.jsp">Exploratory Network Layout</a> | <a href="viz-5.jsp">Matrix Layout</a>
</p>

</body></html>