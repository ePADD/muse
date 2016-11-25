
// fixup move types, prettify values when printed etc.
function prepare_moves(moves)
{
	//returns true if a contains b
	function contains (a, b)
	{
		var j = 0; // running ptr to a
		for (var i = 0; i < b.length; i++)
		{
			var found = false;
			do {
				if (a[j] === b[i])
				{
					found = true;
					break;
				}
				j++;
			} while (j < a.length);

			if (!found)
				return false;
			j++;
		}
		return true;
	}

	for (var i = 0; i < moves.length; i++)
	{
		var move = moves[i];
		if (move.type !== 4) // 4 is delete
		{
			if (move.type === 1)
			{
				// merge
				var n1 = move.n1, n2 = move.n2;
				if (contains(n1, n2) || contains(n2, n1))
					move.type = 5; // superset
			}
			else if (move.type === 2) // dominate
            {
				var n1 = move.n1, n2 = move.n2;
				if (contains(n1, n2)) // would have been a merge instead
                {
                    muse.log (' weird: move ' + move.num + ' Dominate ' + n1 + ' |contains| ' + n2);
                }
 //               if (contains(n2, n1))
//                {
//                    move.type = 6; // subset
//                }
            }
			else if (move.type === 3) // 3 is intersect
			{
				var n1 = move.n1, n2 = move.n2;
				if (contains(n1, n2) || contains(n2, n1))
					move.type = 6; // subset
			}
		}
//        move.valueReduction = Math.floor(move.valueReduction * 100)/100.0;
//        move.newValue = Math.floor(move.newValue * 100)/100.0;
//        if (typeof move.n1_value !== 'undefined')
//            move.n1_value = Math.floor (move.n1_value * 100)/100.0;
//        if (typeof move.n2_value !== 'undefined')
//            move.n2_value = Math.floor (move.n2_value * 100)/100.0;
	}
}


function draw_move_graph(stats)
{
	var legend = ['Merge', 'Dominate','Intersect', 'Drop',  'Superset', 'Subset'];
	var moves = stats.moves;
	prepare_moves(moves);
    var colorMap = ['#69AA35' /* evernote green */,
                    'black',
                    '#F36E5D' /* emberglow */,
                    'rgb(203,101,134)' /*honeysuckle pink (coty, lol) */,
                    '#C8AF91' /*mackage, lighter brown */,
                    '#6E5745' /* coffee liqeur */];

	var hilitedMoves = []; // same as selected moves

	function describeMove(move, idx)
	{
        // join array elements with a <br/>, clipping to MAX chars
        function array_of_names(a)
        {
            var s = '', MAX = 30;
            for (var i = 0; i < a.length; i++)
            {
                s += ((a[i].length < MAX) ? a[i] : a[i].substring (0, MAX));
                s += '<br/>\n';
            }
            return s;
        }
		var result = "";
		if (typeof move.num != 'undefined')
			result += " M" + move.num + ". ";
        else
			result += " M" + idx + ". ";

		result += "<b>" + legend[move.type-1] + "</b>";
		result += "&nbsp;&nbsp; New value " + move.newValue + " (-" + move.valueReduction + ")<br/>";
		if ((typeof move.n1_createdby != 'undefined') && move.n1_createdby.length > 0)
			result += "Depends on " + move.n1_createdby;
		if (legend[move.type-1] !== 'Drop' && (typeof move.n2_createdby != 'undefined') && move.n2_createdby.length > 0)
			result += ' ' + move.n2_createdby;
		result += "<br/>";

        var style = (legend[move.type-1] !== 'Drop') ? "border-right:solid;border-width:1px;" : "";
		var n1_descr = 'Value: ' + move.n1_value + '<br/>';
		n1_descr += array_of_names(move.n1);
		result += '<div style="float:left;width:250px;border-top:solid;border-width:1px;' + style + '">' + n1_descr + '</div>\n';
		if (legend[move.type-1] !== 'Drop')
		{
            var n2_descr = 'Value: ' + move.n2_value + '<br/>';
			n2_descr += array_of_names(move.n2);
			result += '<div style="float:left;width:200px;border-top:solid;border-width:1px;padding-left:20px">' + n2_descr + '</div>\n';
		}
		result += '<div style="clear:both"></div>';
		return result;
	}

	function is_selected(moveNum)
	{
		for (var i = 0; i < hilitedMoves.length; i++)
			if (hilitedMoves[i] == moveNum)
				return true;
		return false;
	}

	function setup_search_hits(search_term)
	{
		hilitedMoves = new Array();
		if (search_term.length === 0)
		{
			vis.render();
			return;
		}
		for (var i = 0; i < moves.length; i++)
		{
			var move = moves[i];
			if (move.n1.join(' ').indexOf(search_term) >= 0 || move.n2.join(' ').indexOf(search_term) >= 0)
				hilitedMoves.push(i);
		}
		muse.log ('search hits for ' + search_term + ' = ' + hilitedMoves);
		vis.render();
	}

	function draw_arcs(i, arr)
	{
		for (var j = 0; j < arr.length; j++)
		{
			var to = arr[j];
			if (to >= i)
				continue;
			// we want be to be 100, a is (i-to)*BAR_WIDTH/2
			var b = 100;
			var a = Math.abs((i-to)*BAR_WIDTH/2.0);
			if (a < b)
				a = b;
			var b_by_a = b/a;
			var e = Math.sqrt(1 - (b_by_a * b_by_a)); // doesn't work
			e = (1-b_by_a) * (1-b_by_a); // seems to work

			mainPanel.add(pv.Line)
			.def("from", i)
			.def("to", to)
			.data([i,to])
			.visible(function() {return is_selected(this.from()) || is_selected(this.to());})
			.bottom(0)
			.interpolate('polar')
			.eccentricity(e)
			.left(function(d) {return d * BAR_WIDTH + BAR_WIDTH/2;});
		}
	}


	// H, W are ht/width of main panel, the full canvas is larger
	var H = 500, BAR_WIDTH = 5, W = BAR_WIDTH * moves.length + 100;
	var vis = new pv.Panel().width(W+100).height(H+200).overflow('visible');

	var mainPanel = vis.add(pv.Panel).height(H).width(W).bottom(120).left(50);

	var values = moves.map(function(d) {return d.valueReduction;});
	var maxValue = pv.max(values);
	var y = pv.Scale.linear(0, maxValue).range(5, H);
	var x = pv.Scale.linear(0, moves.length).range(0, BAR_WIDTH*moves.length);

	// actual data
	mainPanel.add(pv.Bar)
	.data(moves)
	.height(function(d) {return Math.floor(y(d.valueReduction));})
	.width(BAR_WIDTH)
	.bottom(0)
	.left(function(d) {return this.index * BAR_WIDTH;})
	.title(function(d) { var xIdx = this.index; labelText = legend[moves[xIdx].type-1] + ' ' + moves[xIdx].valueReduction; return labelText;})
	.fillStyle(function(d) {return colorMap[d.type-1];})
	//.strokeStyle('yellow')
	//.lineWidth(function() { return is_selected(this.index) ? 3: 0;})
	.event("mouseover", function(d) {
			var xIdx = this.index;
			$('#message').html(describeMove(moves[xIdx], xIdx));
			$('#message').show();
		})
	.event("mouseout", function() { $('#message').hide();})
	.event("click", function(e) {
			muse.log ('selectedMove ' + this.index + ' e =  ' + dump_obj(e));
			var move = moves[this.index];
			if (!pv.event.shiftKey)
				hilitedMoves = [];
			muse.log('hilitedMoves.length = ' + hilitedMoves.length);
			hilitedMoves.push(this.index);
			muse.log(move.n1_createdby);
			if (typeof move.n1_createdby != 'undefined')
				hilitedMoves = hilitedMoves.concat(move.n1_createdby);
			if (typeof move.n2_createdby != 'undefined')
				hilitedMoves = hilitedMoves.concat(move.n2_createdby);
			vis.render();
		});

	// arcs for each move
	for (var i = 0; i < moves.length; i++)
	{
		var move = moves[i];
		if (typeof move.n1_createdby != 'undefined')
			draw_arcs(i, move.n1_createdby);
		if (typeof move.n2_createdby != 'undefined')
			draw_arcs(i, move.n2_createdby);
	}

	// legend
	mainPanel.add(pv.Bar).data(legend).height(5).width(20).left(60)
	.top(function(d) {return 25 + this.index * 25;})
	.fillStyle(function(d) {return colorMap[this.index];}).anchor('left').left(85).add(pv.Label).text(function(d) {return d;});

	var hilitedMoves = '[]';
	mainPanel.add(pv.Dot)
		.data(function() {return hilitedMoves;})
        .visible(function() {return hilitedMoves.length > 0;})
		.radius(BAR_WIDTH).left(function(d) {return x(d)+BAR_WIDTH/2;}).bottom(Math.floor(-BAR_WIDTH/2)-3).fillStyle('white');

	var stColor = 'rgba(127,127,127,0.3)';
	mainPanel.add(pv.Rule).bottom(0).strokeStyle(stColor)
			 .add(pv.Rule).data((x.ticks)).visible(function(d) {return d;}).left(x).bottom(0).height(5).strokeStyle(stColor)
			 .anchor("bottom").add(pv.Label).text(function(d) {return d;});

	mainPanel.add(pv.Rule).left(0).strokeStyle(stColor)
			 .add(pv.Rule).data(y.ticks()).bottom(y).width(5).strokeStyle(stColor)
			 .anchor("left").add(pv.Label).text(function(d) {return d;});

	mainPanel.add(pv.Label).left(100).bottom(-30).text('Moves').font('16px sans-serif')
			 .add(pv.Label).left(-20).bottom(100).text('Absolute Value Reduction').textAngle(-Math.PI / 2).font('16px sans-serif');

	vis.render();
	return setup_search_hits;
}

