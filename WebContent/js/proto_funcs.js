function get_time_ticks(start_yy, start_mm, nIntervals)
{
	var month_names = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

	var xTicksAt = new Array();
	var xTickLabels = new Array();
	var yearly = true, divisor = 12;

	if (nIntervals <= 12)
	{
		divisor = 2; yearly = false;
	}
	else if (nIntervals <= 18)
	{
		divisor = 3; yearly = false;
	}
	else if (nIntervals <= 24)
	{
		divisor = 4; yearly = false;
	}
	else if (nIntervals <= 36)
	{
		divisor = 6; yearly = false;
	}

	// even if yearly, no more than 10 ticks
	var yearly_divisor = 1;
	if (yearly && nIntervals > 120)
	{
		var years = Math.floor(nIntervals/12) + 1;
		yearly_divisor = Math.floor(years/10) + 1;
	}

	// xticksAt is always in terms of months because data is in terms of months
	for (var i = 0; i < nIntervals+1; i++) // +1 since n intervals need n+1 ticks
	{
		var yy = start_yy + Math.floor((start_mm+i)/12);
		var mm = (start_mm+i)%12; // for display, add 1, e.g. Jan 07 should be shown as 1/2007 though start_mm, start_yy would be 0, 2007.

		if (yearly)
		{
			if ((yy - start_yy) % yearly_divisor != 0)
				continue;
			if (mm != 0) // jan is 0
				continue;
			xTicksAt.push(i);
			xTickLabels.push(yy);
		}
		else if ((start_mm+i)%divisor === 0)
		{
			xTicksAt.push(i);
			xTickLabels.push(month_names[mm]+' '+yy);
		}
	}
	//muse.log (nIntervals + ' intervals, divisor = ' + yearly_divisor);
	//muse.log ('x labels: ' + xTickLabels);
	//muse.log ('ticks at: ' + xTicksAt);

	var result = {};
	result['xTickLabels'] = xTickLabels;
	result['xTicksAt'] = xTicksAt;
	return result;
}

// start and end month/year are inclusive and will be on the left and right edges
function add_time_markers(graph, w, h, start_yy, start_mm, end_yy, end_mm)
{
	var nIntervals = (end_yy - start_yy) * 12 + (end_mm - start_mm);
	var x_ticks = get_time_ticks(start_yy, start_mm, nIntervals);
	var xTicksAt = x_ticks['xTicksAt'];
	var xTickLabels = x_ticks['xTickLabels'];
	var font_size = Math.floor(h/16);
	font_size = font_size < 6 ? 6 : font_size;

	graph.add(pv.Rule)
	.data(xTicksAt)
	.visible(function(d) {return (nIntervals <= 2) ? 1: d;}) // always return true if nIntervals = 2 or less, otherwise we have no x labels at all 
	.left(pv.Scale.linear(0, nIntervals).range(0, w))
	.bottom(font_size * 1.2)
	.top(0)
	.strokeStyle('rgba(127,127,127,0.1)')
	.anchor("bottom").add(pv.Label)
	.textStyle('rgba(0,0,0,0.5)')
	.font(font_size + 'px sans-serif')
	.text(function(d) {return xTickLabels[this.index];});
}

// all elements of incoming/outgoing should be < normalizer
// start and end month/year will be on the left and right edges. 
function draw_protovis_box(below_data, above_data, w, h, normalizer, start_yy, start_mm, end_yy, end_mm, canvas)
{
	// note: if # of months (above_data.length) > w then we may want to aggregate data into a coarser-grain histogram. otherwise, we may lose accuracy due to under-sampling.

	if (normalizer < 0) {
		// auto-detect
		normalizer = Math.max.apply(null, above_data.concat(below_data));
	}

	// outgoing_data should not be null
	var x_idx = -1;
	var in_n_out = (below_data != null);
	// convert to x, y array first

	var nIntervals = (end_yy - start_yy) * 12 + (end_mm - start_mm);
	if ((nIntervals+1) != above_data.length) {
		alert('There may be a problem in visualization. Please contact developers.');
		nIntervals = above_data.length - 1;
	}

	// x_coords: starting x offset of area for each interval
	var x_coords = pv.range(0, nIntervals+1).map(function(i) { return Math.floor(i * w / nIntervals); }); // +1 since n intervals requires n+1 ticks

	// actual pixel heights
	var panel_h = in_n_out ? h/2 : h;
	var outgoing_bottom = h - panel_h; // for use with .bottom(), not .top()
	var incoming_top = panel_h; // for use with .top(), not .bottom()

	// tmp1 and tmp2, normalized [0..1.0] versions of incoming/outgoing data, with the sqrt
	var tmp1 = above_data.map(function(x) { return Math.sqrt((x*1.0)/normalizer);});
	var above_heights = tmp1.map(pv.Scale.linear(0, 1.0).range(0, panel_h * 15/16 - 5));

	if (in_n_out)
	{
		var tmp2 = below_data.map(function(x) { return Math.sqrt((x*1.0)/normalizer);});
		var below_heights = tmp2.map(pv.Scale.linear(0, 1.0).range(0, panel_h * 15/16 - 5));
	}

//	if (typeof console)
//		console.log ('above = ' + above_heights + ' below = ' + below_heights);

	var vis = new pv.Panel().width(w).height(h);
	if (canvas) {
		vis.canvas($(canvas).attr("id"));
	}

	var panel = vis.add(pv.Panel); // don't overchain this panel handler assignment or we may be assigning the handle to the wrong thing
	panel.add(pv.Rule).bottom(outgoing_bottom).lineWidth(1).left(0).right(0).strokeStyle("rgba(0,0,0,.3)").overflow('visible');

	vis.add(pv.Bar)
	.fillStyle("rgba(0,0,0,.001)")
	.strokeStyle(null)
	.event("mouseout", function() {
		x_idx = -1;
		panel.render();
		//console.log("mouseout " + x_idx);
	})
	.event("mousemove", function() {
		var prev_x_idx = x_idx;
		var mx = panel.mouse().x * nIntervals / w; // x.invert(topmark.mouse().x);
		//      x_idx = pv.search(out_data.map(function(d) d.x), mx);
		//      x_idx = x_idx < 0 ? (-x_idx - 2) : x_idx;
		x_idx = Math.round(mx);
		if (x_idx >= x_coords.length)
			x_idx = x_coords.length-1;
		if (x_idx != prev_x_idx) {
			panel.render();
			//console.log("mousemove " + x_idx);
		} // else, no need to rerender
	});

    // outgoing, above the line
    var topmark = panel.add(pv.Area)
    //.interpolate('basis')
    .data(above_heights)
	.left(function() { return x_coords[this.index];})
    .height(function(d) { return d; })
    .strokeStyle('#808080')
    .bottom(outgoing_bottom);

	// incoming, below the line
    if (in_n_out)
    {
    	// add stuff to panel
	    var botmark = panel.add(pv.Area)
	    //.interpolate('basis')
	    .data(below_heights)
	    .left(function() { return x_coords[this.index]; })
	    .height(function(d) {return d;})
	    .lineWidth(1)
	    .strokeStyle('#808080')
	    .top(incoming_top);

		var botdot = panel.add(pv.Dot)
		.fillStyle('#fff') // function() topmark.strokeStyle())
		.strokeStyle(function() {return botmark.strokeStyle();})
	    .visible(function() { return x_idx >= 0; })
	    .radius(3)
		.left(function() {  return x_coords[x_idx];})
	    .top(function() { return (incoming_top + below_heights[x_idx]); })
	    .lineWidth(1);

		botdot
	    .add(pv.Label)
	    .text(function() {return muse.pluralize(below_data[x_idx],'message');})
	    .left(function() { var left = x_coords[x_idx]; var dist_from_right = w - left; if (dist_from_right < 70) left -= (70 - dist_from_right); return left; })
	    .top(function() { var y = incoming_top + below_heights[x_idx]; return y > h - 20 ? h - 20 : y; }) // message clipped if y is too large
	    .textStyle('orange')
		.textBaseline('top');
    }

    // add topdot after botmark so topdot won't get blocked when it is close to x axis 
    var topdot = panel.add(pv.Dot)
	.fillStyle('#fff') // function() topmark.strokeStyle())
	.strokeStyle(function() { return topmark.strokeStyle();})
    .visible(function() { return x_idx >= 0; })
    .radius(3)
	.left(function() { return x_coords[x_idx];})
    .bottom(function() { return (above_heights[x_idx] + outgoing_bottom); })
    .lineWidth(1);

	topdot.add(pv.Label)
		.text(function() { return muse.pluralize(above_data[x_idx], 'message');})
	    .textStyle('orange')
		// adjust so as not to cross right border... typical width of this dot is 70 px
		.left(function() { var left = x_coords[x_idx];var dist_from_right = w - left; if (dist_from_right < 70) left -= (70 - dist_from_right); return left; })
		.bottom(function() { var y = above_heights[x_idx] + outgoing_bottom; return y > h - 10 ? h - 10 : y; }) // message clipped if y is too large
		.textBaseline('bottom');
	
    add_time_markers(panel, w, h, start_yy, start_mm, end_yy, end_mm);

    vis.render();
}

// data: array of objects with the fields: caption, urlhistogram (histogram captures monthly frequencies, or the x-axis variable)
// start_yy and start_mm are starting dates. start_mm indexed from 0
// Note: vis is stored in global var stacked_graph, so it can re-rendered when options change
// assumption: only one stacked graph per page
// returns the pv stack graph object
// totalVolume is required only for Pct view.
function draw_stacked_graph(data, totalVolume, normalizer, w, h, start_yy, start_mm, colorCategory, click_func, graph_type, x_labels)
{
	var nIntervals = (totalVolume != null) ? totalVolume.length : data[0].histogram.length;
	if (nIntervals == 1)
	{
		// force to 2 intervals, otherwise we don't see anything
		for (var i = 0; i < data.length; i++)
			data[i].histogram[1] = data[i].histogram[0];
		if (totalVolume != null)
			totalVolume[1] = totalVolume[0];
		nIntervals = 2; 
	}
	// labels go on the y axis
	var labels = data.map(function(d) {return d.caption;});
	var full_labels = data.map(function(d) { return (typeof d.full_caption != 'undefined' && d.full_caption) ? d.full_caption: d.caption;});
	
	// readjust h if its too small to accomodate all the labels
	if (15*labels.length + 20 > h)
		h = 15*labels.length+20;

	// we transpose the incoming data because vals needs to have #timesteps elements, w/#layers items per element
	var vals = pv.transpose(data.map(function(d) {return d.histogram;}));
	var valSumsPerXstep = vals.map(function(d)  {return pv.sum(d);});
	var maxValInAnyXStep = pv.max(valSumsPerXstep);
	if (maxValInAnyXStep < 10)
		maxValInAnyXStep = 10;
	
	muse.log ('effective w = ' + w + ' effective h = ' + h + ', x intervals: ' + nIntervals + ' y layers: ' + labels.length);

	// set up y. it has a funny domain: 0.. maxValInAnyXStep for counts and 0..100 for percents
	// but the nice thing is ticks can be directly derived from it
	var yByCount = pv.Scale.linear(0, maxValInAnyXStep).range(0, h);
	var yByPct = pv.Scale.linear(0,100).range(0, h);
	var byCount = true;	// this can be reset later
	var y = (byCount) ? yByCount : yByPct;

	var graph_x_offset = 100; // to accomodate legend
	var x = pv.Scale.linear(0, nIntervals).range(graph_x_offset, w);

	// create x tick labels for the beginning of each year (unless xlabels are given to us)
	if (!x_labels)
	{
		var month_names = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

		var xTicksAt = new Array();
		var xTickLabels = new Array();
		var yearly = true, divisor = 12;
		
		if (nIntervals <= 12)
		{
			divisor = 2; yearly = false;
		}
		else if (nIntervals <= 18)
		{
			divisor = 3; yearly = false;
		}
		else if (nIntervals <= 24)
		{
			divisor = 4; yearly = false;
		}
		else if (nIntervals <= 36)
		{
			divisor = 6; yearly = false;
		}
		
		// even if yearly, no more than 10 ticks
		var yearly_divisor = 1;
		if (yearly && nIntervals > 120)
		{
			var years = Math.floor(nIntervals/12) + 1;
			yearly_divisor = Math.floor(years/10) + 1;
		}

		// xticksAt is always in terms of months because data is in terms of months
		for (var i = 0; i < nIntervals; i++)
		{
			var yy = start_yy + Math.floor((start_mm+i)/12);
			var mm = (start_mm+i)%12; // for display, add 1, e.g. Jan 07 should be shown as 1/2007 though start_mm, start_yy would be 0, 2007.

			if (yearly)
			{
				if ((yy - start_yy) % yearly_divisor != 0)
					continue;
				if (mm != 1)
					continue;
				xTicksAt.push(i);
				xTickLabels.push(yy);
			}
			else if ((start_mm+i)%divisor === 0)
			{
				xTicksAt.push(i);
				xTickLabels.push(yearly ? '' + yy : month_names[mm]+' '+yy);
			}
		}
		muse.log (nIntervals + ' intervals, divisor = ' + yearly_divisor);
		muse.log ('x labels: ' + xTickLabels);
		muse.log ('ticks at: ' + xTicksAt);
	}

	// main panel
	var stacked_graph = new pv.Panel()
	    .width(w)
	    .height(h)
	    .bottom(40)
	    .left(20 + graph_x_offset)
	    .right(10)
	    .top(5);

	stacked_graph.set_counts_view = function(useCounts) { muse.log('setting usecounts to ' + useCounts); byCount = useCounts; }

	var colorMap = colorCategory.range();
	muse.log (' we have ' + colorMap.length + ' colors');

	var no_highlight = colorMap.length == 1;

	// the .def method to control alpha levels of each layer turns out to be a pain to use. (it was painting all the layers at once w/the method i tried)
	// much easier to define an layerAlphas array
	var UNHIGHLIGHTED_ALPHA = 0.9;
	var HIGHLIGHTED_ALPHA = 1.0;
	var layerAlphas = new Array(); // tracks current alphaLevel for each layer
	for (var level = 0; level < labels.length; level++) { layerAlphas[level] = HIGHLIGHTED_ALPHA; }
	var all_areas_highlighted = true;

	var highlight_all_areas = function() { all_areas_highlighted = true; for (var area = 0; area < layerAlphas.length; area++) { layerAlphas[area] = HIGHLIGHTED_ALPHA;}};
	var unhighlight_all_areas = function() { all_areas_highlighted = false; for (var area = 0; area < layerAlphas.length; area++) { layerAlphas[area] = UNHIGHLIGHTED_ALPHA;}};
	var highlight_area = function (idx) { unhighlight_all_areas(); /* muse.log('hiliting layer ' + idx); */ layerAlphas[idx] = HIGHLIGHTED_ALPHA; return stacked_graph;};
	var unhighlight_area = function (idx) { /* muse.log('unhiliting layer ' + idx); */ highlight_all_areas(); return stacked_graph;  }; // muse.log('unhiliting ' + idx); layerAlphas[idx] = UNHIGHLIGHTED_ALPHA;  return stacked_graph; }
	var toggle_highlight_area = function (idx) { layerAlphas[idx] = (layerAlphas[idx] == HIGHLIGHTED_ALPHA) ? UNHIGHLIGHTED_ALPHA : HIGHLIGHTED_ALPHA;  return stacked_graph; };

	// main layers
	stacked_graph.add(pv.Layout.Stack)
	    .layers(pv.range(0, labels.length)) // send in [0..#layers-1] as the layer id's so that we can use the id to index into data for each timestep
	    .values(vals) // extract only the histogram part, not the caption
	  .offset(function() {return stack_layout_offset;})
	    .orient('bottom')
	    .x(function(d) {return x(this.index);})
	    .y(function(d, l) {return byCount ? yByCount(d[l]) : yByPct(100 * d[l]/totalVolume[this.index]);})
	  .layer.add(pv.Area) // we're going into the layer prototype now, so any attribute here will be set for all the areas.
	  	.interpolate(graph_type=='curvy'?'basis':'step-after')
	  	.fillStyle(function(d,l) { return colorMap[l%colorMap.length].alpha(layerAlphas[l]);})
	  	.strokeStyle(function (d, l) { return ((layerAlphas[l] == HIGHLIGHTED_ALPHA) && !all_areas_highlighted) ? 'rgba(16,16,16,0.7)':null;})
	  	.lineWidth(2)
		.title(function(d, l) {  return full_labels[l];}) // read the real label
		.event("click", function(d, l) {
			muse.log('mouse = ' + this.mouse().x + ' inverted idx = ' + x.invert(this.mouse().x));
			var entryPct = Math.floor((100 * x.invert(this.mouse().x)/nIntervals));
			muse.log('entryPct = ' + entryPct);
			var xstep = parseInt(x.invert(this.mouse().x));
			var month = parseInt((start_mm + xstep)%12);
			var year = parseInt(start_yy + (start_mm + xstep)/12);
			click_func(xstep, l, year, month);})
	    .event("mouseover", no_highlight ? function(d, l){} : function(d, l) { return highlight_area(l); return this;})
		.event("mouseout", function(d, l) { return unhighlight_area(l); return this;});

	// x rules
	if (!x_labels)
	{
		// little 5px x-ticks
		stacked_graph.add(pv.Rule)
		.data(xTicksAt)
	    .visible(function(d) {return (nIntervals <= 2) ? 1: d;}) // always return true if nIntervals = 2 or less, otherwise we have no x labels at all 
	    .left(x)
	    .bottom(-5)
	    .height(5)
	    .strokeStyle('rgba(127,127,127,0.5)')
	  .anchor("bottom").add(pv.Label)
	  	.textStyle('rgba(127,127,127,0.5)')
	  	.font('20px sans-serif')
	    .text(function(d) {return xTickLabels[this.index];});

		// long vertical lines
		stacked_graph.add(pv.Rule)
		.data(xTicksAt)
	    .visible(function(d) {return d;})
	    .left(x)
	    .bottom(0)
	    .height(h)
	    .strokeStyle('rgba(127,127,127,0.1)');
	}
	else
	{
		stacked_graph.add(pv.Rule)
		.data(pv.range(0,20))
	  //  .visible(function(d) d)
	    .left(x)
	    .bottom(-5)
	    .height(5)
	    .strokeStyle('rgba(127,127,127,0.5)')
	  .anchor("bottom").add(pv.Label)
	  	.textStyle('rgba(127,127,127,1.0)')
	  //	.font('10px sans-serif')
	    .textAngle(-0.2)
	    .text(function(d) {return x_labels[this.index];});
	}

	var yaxis_ticks = new Array();
	for (var i = 0; i <= 10; i++)
		byCount ? yaxis_ticks.push(parseInt(maxValInAnyXStep * i / 10)): 10*i;
	
	stacked_graph.add(pv.Rule)
	.data(function() {return yaxis_ticks;})
	.bottom(function(d) {return (byCount) ? yByCount(d) : yByPct(d);})
	.left(graph_x_offset)
	.width(w-graph_x_offset)
	.strokeStyle(function(d) {return d ? "rgba(128,128,128,.2)" : "rgba(128,128,128,.3)";})
	.anchor("left")
	.add(pv.Label) // label only for the topmost y horiz line
		.text(function (d) { if (this.index != 10) return ''; return byCount ? maxValInAnyXStep + ' messages/month' : '100%'; }) // byCount ? yByCount.tickFormat:yByPct.tickFormat);
		.textStyle('red');

	// legend
//	muse.log('labels = ' + labels);
	var legend_bottom = (h - (15 * labels.length))/2;
	stacked_graph.add(pv.Panel)
		.add(pv.Bar)
		.data(labels)
		.left(0)
		.bottom(function() {return legend_bottom + this.index * 15;})
		.width(function() { return (labels[this.index] !== '') ? 20 : 0; }) // no legend bar if caption is empty
		.height(5)
		.fillStyle(function() {return colorCategory(this.index%colorCategory.range().length);})
		.event("mouseover", function() { highlight_area(this.index); click_func(0, this.index, -1, -1);})
		.event("mouseout", function() { return unhighlight_area(this.index);})
		.event("click", function() { return toggle_highlight_area(this.index);})
//		.add(pv.Bar)
//		.left(25)
//		.width(function() { return labels[this.index].length * 6; })
//		.height(10)
//		.fillStyle(function() { return (layerAlphas[this.index] == HIGHLIGHTED_ALPHA) ? "rgba(0,0,0,1.0)" : "rgba(0,0,0,0.0)"; })
		.add(pv.Label)
			.left(25)
			.textStyle(function() { return (layerAlphas[this.index] == HIGHLIGHTED_ALPHA) ? colorCategory(this.index%colorCategory.range().length) : "rgba(0,0,0,1.0)"; })
		// underline works only in chrome and doesn't look that great anyway
		//	.textDecoration(function() { return (layerAlphas[this.index] == HIGHLIGHTED_ALPHA) ? 'underline' : ''; })
			.title(function(d, l) {  return full_labels[l];}) 
			.bottom(function() {return legend_bottom + this.index * 15 - 3;}) // small 3-pix correction so text and bar appear aligned
			.text(function() {return labels[this.index];})
			// events on labels don't seem to work!
			.event("click", function() { click_func(0, this.index, -1, -1);})
			.event("mouseover", function() { muse.log ('hover on ' + this.index); return highlight_area(this.index);})
			.event("mouseout", function() { return unhighlight_area(this.index); });

	stacked_graph.render();
	return stacked_graph;
}

// used only for links
function draw_bar_graph(counts, labels)
{
	muse.log ('counts = ' + counts);
	muse.log ('labels = ' + labels);
	/* Sizing and scales. */
	var w = 4000,
	    h = 250,
	    x = pv.Scale.linear(0, counts.length).range(0, w),
	    y = pv.Scale.linear(0, pv.max(counts)).range(0, h);
	
	/* The root panel. */
	var vis = new pv.Panel()
	    .width(w)
	    .height(h+150)
	    .bottom(150)
	    .left(20)
	    .right(10)
	    .top(5);
	
	/* The bars. */
	var bar = vis.add(pv.Bar)
	    .data(counts)
	    .bottom(0)
	    .height(function(d) { var h = parseInt(y(d)); muse.log ('returning ' + h); return h; } )
	    .left(function() { muse.log(this.index * 15); return this.index * 15; })
	    .width(10)
	    .title(function(d) { return labels[this.index] + ': ' + counts[this.index];})
	/* The value label. */
	.anchor("bottom").add(pv.Label)
	    .textStyle("black")
	    .textAlign("right")
	    .font('9pt sans-serif')
	    .textAngle(-Math.PI/2)
	    .title(function(d) { return labels[this.index] + ': ' + counts[this.index];})
	    .text(function(d) { return labels[this.index];});
	
	vis.render();
}


