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
     var info = new Array();

    function populate_info(curr_node, zoom){
      if (curr_node){
		info[0]="Details of the selected node: ";
    		info[1]="Contact Name: " + curr_node.n;
		info[2]="Email: " + curr_node.a;
		info[3]="Message Count: " + curr_node.c;
		info[4]="Primary Group: " + curr_node.y;
	}

     }

     function call_zoom(curr_node){
    	    if (curr_node.c==undefined){zoom_func(top_groups[curr_node.g]);}
	else{
		if (curr_node.g!=undefined){zoom_func(top_groups[curr_node.g]);}
		else{zoom_func("clear");}
	}
    }

var w = document.body.clientWidth,
    h = document.body.clientHeight - 110;

var person_nodes=new Array();
var person_array=new Array();
for (i=0;i<addressBook.length;i++){
	person_addr = addressBook[i].emailAddrs[0];
	person_nodes[i] = {"nodeName":person_addr, "name":addressBook[i].names[0], "group":undefined,"count":(addressBook[i].messageOutCount + addressBook[i].messageInCount)};
	person_array[person_addr] = i;
}

var top_groups = new Array();
var top_group_links = new Array();
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
	top_groups[i] = {"nodeName":group_name, "group":i};
	var temp_links = new Array();
	var group_members = groups[i].group.members;
	for (j=0;j<group_members.length;j++){
		person_index = person_array[group_members[j].emailAddrs[0]];
		person_count = person_nodes[person_index].count;
		person_name = person_nodes[person_index].name;
		temp_links[j] = {"source":person_index, "target":group_index, "group_name":group_name, "value":1, "count":person_count, "name":person_name ,"addr":person_addr};
		person_nodes[person_index].group = i;
		group_matrix[person_index][i] = 1;

	}
	top_group_links = top_group_links.concat(temp_links);
	//console.log(top_group_links);

}

var numbers2 = new Array();
for (i=0;i<groups.length;i++){
		group_index2 = addressBook.length + i;
		group_name2 = "groupNum" + i;
		group_id = i;
		var group_members = groups[i].group.members;

	for (j=0;j<group_members.length;j++){
		person_index2 = person_array[group_members[j].emailAddrs[0]];
		person_addr= person_nodes[person_index2].nodeName;
		person_name2 = person_nodes[person_index2].name;
		numbers2.push( {x:person_index2, y:group_name2, c:top_group_links[j].count , n:person_name2, a:person_addr, g:group_id});
	}
}


/////////////////////////
// *** ZOOMED IN DATA ***
/////////////////////////

// BEGIN zoom_func

function zoom_func(top_group_node){

var vis_zoom = vis_bottom.add(pv.Panel)
    .left(300)
    .bottom(0)
    .width(w - 300)
    .height(110)
    .strokeStyle("#aaa")
    .fillStyle("white");

var i;
var j;

if (top_group_node!="clear"){

var p_nodes=new Array();
var p_array=new Array();
var g_nodes=new Array();
var g_array=new Array();

var top_group = groups[top_group_node.group];

g_nodes[0] = {"nodeName":top_group_node.n, "name":undefined, "group":"0-0", "count":top_group.group.c , "isGroup":true};

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
        var group_node = {"nodeName":group_name, "name":undefined, "isGroup":true, "group":group_num, "count":input_group[i].group.utility, "parent":parent, "highest":top_group_node.group};
        g_nodes.push(group_node);
        var members = input_group[i].group.members;
        for(j=0;j<members.length;j++){
          var person_index = p_array[members[j].emailAddrs[0]];
          p_nodes[person_index].group += "." + group_num;
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

var matrix_temp = new Array();

for (j=0;j<p_nodes.length;j++){
	p_links[j]={"source":j, "target":g_array[p_nodes[j].group], "value":1};
	var test = p_nodes[j].group.split(".");

for (i=0;i<test.length;i++){
	matrix_temp.push({x:p_nodes[j].nodeName, y:p_nodes[j].group.split(".")[i], c:p_nodes[j].count,n:p_nodes[j].name, a:p_nodes[j].nodeName});
}}
for (j=1;j<g_nodes.length;j++){
	g_links[j-1]={"source":g_array[g_nodes[j].group], "target":g_array[g_nodes[j].parent], "value":1};

}

var a_links = p_links.concat(g_links);
var a_nodes = p_nodes.concat(g_nodes);


var sel_g = -1;

var x2 = pv.Scale.ordinal(pv.range(p_nodes.length).sort()).splitBanded(0,w - 300, .9),
    y2 = pv.Scale.ordinal(pv.range(g_nodes.length)).splitBanded(0,110, .9),
    c = pv.Scale.linear(0,15).range("#DEEBF7","#3182BD");


vis_zoom.add(pv.Bar)
    .def("active", -1)
    .data(matrix_temp)
    .left(function(d) x2(d.x))
    .top(function(d) y2(d.y))
    .width(x2.range().band)
    .height((y2).range().band)
    .fillStyle(function(d) c(d.c))

  // .event("click", function(d) {populate_info(d, 1); vis.render();})
  .event("point", function(d) {populate_info(d, 1); this.active(this.index).parent;vis.render();})
    .event("unpoint", function() this.active(-1).parent)
    .anchor("right").add(pv.Label)
    .visible(function() this.anchorTarget().active() == this.index)
    //Display the node name immediately
    .text(function(d) d.n);
} //END if (top_group_node!="clear")

} //END zoom_func


/* Sizing parameters and scales. */
var x = pv.Scale.ordinal(pv.range(person_nodes.length).sort()).splitBanded(0,w, 0.9),
    y = pv.Scale.ordinal(pv.range(top_groups.length)).splitBanded(0, h, 0.9),
    c = pv.Scale.linear(0,15).range("#DEEBF7","#3182BD");

/* The root panel. */
var vis = new pv.Panel()
    .width(w)
    .height(h)
    .top(100)
    .left(40)
    .right(20)
    .bottom(110)
    .event("mousemove", pv.Behavior.point())
    .strokeStyle("#aaa");


var vis_main = vis.add(pv.Panel)
    .left(0)
    .top(0)
    .width(100)
    .height(h)
    .event("mousemove", pv.Behavior.point()) ;

var vis_bottom = vis.add(pv.Panel)
    .left(0)
    .bottom(-110)
    .width(w)
    .height(110)
    .strokeStyle("#aaa")
    .fillStyle("white");


var vis_info = vis_bottom.add(pv.Panel)
    .left(0)
    .bottom(0)
    .width(300)
    .height(110)
    .strokeStyle("#aaa")
    .fillStyle("white");

var vis_instructions = vis_main.add(pv.Panel)
    .left(0)
    .top(-80)
    .width(w)
    .height(29)
    .fillStyle("white");

var instr = new Array();
instr.push("Bipartite Matrix Layout");
instr.push("Rows represent groups and columns represent a contacts. Bars are colored according to messages exchanged (The more the messages, the darker the bars.)");
instr.push("Hover over any contact to see the details in the bottom panel");
instr.push("Click to see the detailed social hierarchy in the bottom panel. If a group contains no subgroups, nothing will be displayed.");

    vis_instructions.add(pv.Label)
    .data(instr)
    .left(700)
    .top(function() 20*(this.index+1))
    .textAlign("center")
    .font(function() (this.index) ? "12px sans-serif" : "16px sans-serif")
    .text(function(d) d);


/* The matrix plot. */
vis_main.add(pv.Bar)
    .def("active", -1)
    .data(numbers2)
    .left(function(d) x(d.x))
    .top(function(d) y(d.y))
    .width(x.range().band)
    .height((y).range().band)
    .fillStyle(function(d) c(d.c))
    .event("click", function(d) {call_zoom(d);vis.render();})
    .event("point", function(d) {populate_info(d, 1); this.active(this.index).parent;vis.render();})
    .event("unpoint", function() this.active(-1).parent)
    .anchor("right").add(pv.Label)
    .visible(function() this.anchorTarget().active() == this.index)
    //Display the node name immediately
    .text(function(d) d.n);

/* Detailed information Panel */

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
Go to Visualization: <a href="viz-4.jsp">Previous</a> | Go to: <a href="https://spreadsheets.google.com/viewform?formkey=dHlVRnhGekhKcDBVelI5bmVTbFBlU3c6MQ"><b>Feedback</b></a>
<br/>
Or skip by name: <a href="viz-1.jsp">Network Layout without single nodes</a> | <a href="viz-2.jsp">Network Layout with single nodes</a> | <a href="viz-3.jsp">Network Layout with colored links</a> | <a href="viz-4.jsp">Exploratory Network Layout</a> | <a href="viz-5.jsp">Matrix Layout</a>
</p>

</body></html>