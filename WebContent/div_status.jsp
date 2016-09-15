 <!--  status box -->
 <%boolean show_help_page = !"memorytest".equals(session.getAttribute("mode")); %>
<div id="status" class="rounded status" style="display:none;z-index:1000;min-height:130px;height:130px;padding:30px;position:fixed;width:600px;box-shadow:0 4px 23px 5px rgba(25,255,255, 0.2), 0 2px 6px rgba(255,255,255,0.15)" >
		<div style="float:left">
		  <span>Working... (<a id="cancel" href="#" onclick="cancelCurrentOp()">Cancel</a></span>
		  <% if (show_help_page) { %>
			  or <a title="Read FAQ while you wait. Opens in a separate window." href="help" target="_new">Read Help</a>			  
		  <% } %>
		  )
		</div>
		<div style="float:right">
		  <img width="25" src="images/spinner.gif"/>
		</div>
		<div style="clear:both"></div>

	    <hr style="color:rgba(0,0,0,0.2);background-color:rgba(0,0,0,0.2);margin-top:1px;"/>
		<span id="status_text">No Status</span> &nbsp;
		<span style="margin-left:20px; max-width:200px;overflow:hidden;" class="teaser"></span>
		<br/>
			<div class="progress_bar_outer_box" style="width:500px;border-style:none;border-width:1px;border-color:rgba(127,127,127,0.2);height:20px">
			  <div class="progress_bar" style="padding:0;width:0;height:20px"></div>
			</div>
		
		<div class="time_remaining" style="display:none;color:gray"></div>
</div>

<div class="muse-overlay"></div>