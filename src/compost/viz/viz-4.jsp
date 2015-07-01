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
	<script type="text/javascript" src="../js/protovis-d3.2.js"></script>
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
    .top(75)
    .width(w*0.75)
    .height(h)
    .fillStyle("white")
    .overflow("hidden")
    .event("mousedown", pv.Behavior.pan())
    .event("mousemove", pv.Behavior.point())
    .event("mousewheel", pv.Behavior.zoom());

var vis_instructions = vis_left.add(pv.Panel)
    .left(0)
    .top(0)
    .width(w*0.75)
    .height(75)
    .fillStyle("white");

var instr = new Array();
instr.push("Exploratory Network Layout");
instr.push("Each sqaure represents a group node (sized according to cohesiveness). Hover to see all information on right side panel.");
instr.push("Expansion: Double click to view all members. Double click again to view detailed hierarchy (if it exists).");
instr.push("Double click again to collapse the group");

vis_instructions.add(pv.Label)
    .data(instr)
    .left(w*0.75*0.5)
    .top(function() 20*(this.index+1))
    .textAlign("center")
    .font(function() (this.index) ? "12px sans-serif" : "16px sans-serif")
    .text(function(d) d);

///////////////////////////////
// ***        FUNCTIONS     ***
///////////////////////////////

    function populate_info(curr_node){
//      var info = new Array();
    for (i=0; i<info.length; i++) info[i]="";
      if (curr_node){
	if (curr_node.isGroup){
		if (curr_node.isChild){
			info[0]="Details of this sub-group:";
			info[1]="Group Name: "+curr_node.nodeName;
		}
		else{
			info[0]="Details of this group:";
			info[1]="Group Name: "+curr_node.nodeName;
			info[2]="Group Members: ";
			for (i=0; i<group_list[curr_node.group].length; i++){
				info[3+i]=group_list[curr_node.group][i];
			}
		}
	}
	else{
		info[0]="Details of this person:";
		info[1]="Contact Name: "+((curr_node.name) ? curr_node.name : "Unknown");
		info[2]="Email: "+curr_node.nodeName;
		info[3]="Message count: "+curr_node.count;
	}
      }
//      return info;
    }

    function color_node(d, index, selected_g, selected_n) {
	var value;
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


///////////////////////////////
// *** MAIN SOCIAL TOPOLOGY ***
///////////////////////////////

var info = new Array();

var person_nodes=new Array();
var person_array=new Array();
for (i=0;i<addressBook.length;i++){
	person_addr = addressBook[i].emailAddrs[0];
	person_nodes[i] = {"nodeName":person_addr, "name":addressBook[i].names[0], "isGroup":false, "group":undefined, "count":(addressBook[i].messageOutCount + addressBook[i].messageInCount), "hide":1};
	person_array[person_addr] = i;
}
person_nodes[0].hide=0;

var central_links=new Array();
var top_groups = new Array();

// group_matrix records all the groups of each node - required to calculate share_common_group
var group_matrix = new Array();
var group_list = new Array();

for (i=0;i<addressBook.length;i++){
	group_matrix[i] = new Array();
	group_list[i] = new Array();
	for (j=0;j<groups.length;j++){
		group_matrix[i][j] = 0;
	}
}

for (i=0;i<groups.length;i++){
	// NOTE: group num = i;
	group_index = addressBook.length + i;
	group_name = "groupNum" + i;
	top_groups[i] = {"nodeName":group_name, "name":undefined, "isGroup":true, "group":i, "count":groups[i].group.utility, "hide":0};
	var group_members = groups[i].group.members;
	for (j=0;j<group_members.length;j++){
		person_index = person_array[group_members[j].emailAddrs[0]];
		person = person_nodes[person_index];
		group_list[i][j] = (person.name) ? person.name : person.nodeName;
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
	var group_to_me = {"source":i+1, "target":0 , "value":1}
	central_links.push(group_to_me);
}


var share_common_group = new Array()
for (i=0;i<addressBook.length;i++){
	share_common_group[i] = new Array();
	for (j=0;j<addressBook.length;j++){
		share_common_group[i][j]=0;
		for (k=0;k<groups.length;k++){
			if (group_matrix[i][k] > 0 && group_matrix[j][k] > 0) {share_common_group[i][j]=1;}
		}
	}
}

var p_values = new Array();
for (i=0;i<addressBook.length;i++) p_values[i] = 0;

var p_index = new Array();
var p_inverted = new Array();

var sg_index = new Array();
var sg_inverted = new Array();

var v_nodes = new Array();
v_nodes.push(person_nodes[0]);
p_index[0] = 0;
v_nodes = v_nodes.concat(top_groups);

var v_links = central_links;

function get_nodes(){
	return v_nodes;
}
function get_links(){ return v_links; }

var force = vis_main.add(pv.Layout.Force)
    .nodes(function() get_nodes())
    .links(function() get_links());

force.link.add(pv.Line)
    .fillStyle(function(d, p) ((p.source != 0) && (p.target != 0)) ? (colors(v_nodes[p.target].group)) : "white")
    .strokeStyle(function() this.fillStyle().darker());

function clickevent(d){
    if (!d.todo) expand(d);
    else if (d.todo == 1) zoom(d);
    else if (d.todo == 2) collapse(d);
}

function expand(n) {
  var group_num = n.group;
  for (i=0;i<addressBook.length;i++){
	  if(group_matrix[i][group_num]){
		  if (p_values[i] == 0){
			  v_nodes.push(person_nodes[i]);
			  p_index[i] = v_nodes.length - 1;
			  p_inverted[v_nodes.length - 1] = i;
		  }
		  var person_index = p_index[i];
		  if (p_values[i] == -1) {
			  p_values[i]=0; // note that this will be incremented later in this func
		  }
		  v_nodes[person_index].hide = 0;
		  var this_link = {"source":person_index, "target":group_num+1 , "value":1};
		  v_links.push(this_link);
		  p_values[i]++;
	  }
  }
  n.todo = 1;
}

function zoom(top_group_node){

  var top_group = groups[top_group_node.group];

  if (!top_group.subsets) collapse(top_group_node);
  else{

  	var p_links=new Array();

	function hierarchy(input_group, level, parent){
		for (i=input_group.length-1;i>=0;i--){
			if (input_group[i].group){
				var group_name = parent + "-level"+level+"-group"+i;
				var group_num = parent+"-"+level+"-"+i;

				if (sg_index[group_num]){
					var group_index = sg_index[group_num];
					v_nodes[group_index].hide = 0;
				}
				else{
					var group_node = {"nodeName":group_name, "name":undefined, "isGroup":true, "isChild":true, "group":group_num, "count":input_group[i].group.utility, "parent":parent, "highest":top_group_node.group, "hide":0};
					v_nodes.push(group_node);
					var group_index = v_nodes.length - 1;
					sg_index[group_num]=group_index;
					sg_inverted[group_index] = group_num;
				}
				var group_link = {"source":group_index, "target":parent+1, "value":1, "highest":top_group_node.group+1};
				v_links.push(group_link);

				var members = input_group[i].group.members;
				for(j=0;j<members.length;j++){
					var person_num = person_array[members[j].emailAddrs[0]];
					var person_index = p_index[person_num];
					v_nodes[person_index].subgroup = group_num;
					p_links[person_num] = {"source":person_index, "target":group_index, "value":1, "highest":top_group_node.group+1};
				}
			}
			if (input_group[i].subsets){
				parent_group = v_nodes.pop();
				v_nodes.push(parent_group);
				hierarchy(input_group[i].subsets, level+1, parent_group.group);
			}
		}
	}

	hierarchy(top_group.subsets, 1, top_group_node.group);


	var temp_links = new Array();
	for (k=0; k<v_links.length; k++){
		link = v_links[k];
		if (link.target == top_group_node.group+1) {
			var overwritten = 0;
			for (i=0; i<p_links.length; i++){
				if ((p_links[i]) && (p_links[i].source == link.source)){
					temp_links.push(p_links[i]);
					overwritten = 1;
					break;
				}
			}
			if (overwritten==0) temp_links.push(link);
		}
		else temp_links.push(link);
	}
	v_links = temp_links;

	top_group_node.todo = 2;
  }
}

function collapse(n) {
  var group_num = n.group;
  var temp_links = new Array();
  for (i=0; i<v_links.length; i++){
	  link = v_links[i];
	  if ((link.target == group_num+1) || (link.highest == group_num+1)) {
	  	  if (v_nodes[link.source].isGroup){
	  	  	  sg_num = sg_inverted[link.source];
	  	  	  v_nodes[link.source].hide = 1;
		  }
		  else{
			  person_num = p_inverted[link.source];
			  p_values[person_num]--;
			  if (!p_values[person_num]) {
				  p_values[person_num] = -1;
				  v_nodes[link.source].hide = 1;
			  }
	  	  }
	  }
	  else temp_links.push(link);
  }
  v_links = temp_links;
  n.todo = 0;
}

var selected_g = -1;
var selected_n = -1;

force.node.add(pv.Dot)
    .def("active", -1)
    .visible(function(d) (this.index && !d.hide))
    .size(function(d) ( (d.isChild || (d.isGroup && d.todo)) ? 10 : d.count+4 ) * Math.pow(this.scale, -1.5))
    .shape(function(d) (!d.isGroup) ? "circle" : "square")
    .fillStyle(function(d) (d.isChild) ? (colors(d.highest)) : colors(d.group))
//    .fillStyle(function(d) color_node(d, this.index, selected_g, selected_n) )
    .strokeStyle(function() this.fillStyle().darker())
//    .event("mouseover", function(d) {if (d.isChild) {selected_g = d.highest;} else {selected_g = d.group;} selected_n = this.index;} )
//    .event("mouseout", function() {selected_g = -1; selected_n = -1;})
    .event("mousedown", pv.Behavior.drag())
    .event("drag", force)
    .event("dblclick", function(d) {if(d.isGroup && !d.isChild){clickevent(d); force.reset();}})
    .event("point", function(d) {selected_g = d.group; selected_n = this.index; this.active(this.index).parent; populate_info(d); vis.render();})
    .event("unpoint", function() {selected_g = -1; selected_n = -1; this.active(-1).parent;})
    .anchor("right").add(pv.Label)
    .visible(function() this.anchorTarget().active() == this.index)
    //Display the node name immediately
    .text(function(d) (d.name!=null) ? d.name : d.nodeName);



///////////////////////////////
// *** SIDE PANEL ***
///////////////////////////////


var vis_side = vis.add(pv.Panel)
    .left(w*0.76)
    .top(0)
    .width(w*0.24)
    .height(h)
    .fillStyle("white")
    .event("mousedown", pv.Behavior.pan())
    .event("mousewheel", pv.Behavior.zoom());

var vis_info = vis_side.add(pv.Panel)
    .left(0)
    .top(0)
    .width(w*0.24)
    .height(h*0.17)
    .fillStyle("white")
    .event("mousedown", pv.Behavior.pan())
    .event("mousewheel", pv.Behavior.zoom());
/*
var vis_info = vis.add(pv.Panel)
    .left(w*0.76)
    .top(0)
    .width(w*0.24)
    .height(h)
    .fillStyle("white")
    .event("mousedown", pv.Behavior.pan())
    .event("mousewheel", pv.Behavior.zoom());
*/
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
Go to Visualization: <a href="viz-3.jsp">Previous</a> | <a href="viz-5.jsp">Next</a>
<br/>
Or skip by name: <a href="viz-1.jsp">Network Layout without single nodes</a> | <a href="viz-2.jsp">Network Layout with single nodes</a> | <a href="viz-3.jsp">Network Layout with colored links</a> | <a href="viz-4.jsp">Exploratory Network Layout</a> | <a href="viz-5.jsp">Matrix Layout</a>
</p>

</body></html>