<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>
<div style="float:clear;" class="footer">
<hr style="color:rgba(0,0,0,0.5)" width=80%/>
	<div align="center">
	<span style="margin-top:-10px">
			<%
			String modeStr = (String) JSPHelper.getSessionAttribute(session, "mode");
			if (modeStr != null)
				out.println ("Mode: " + modeStr + ". ");
			if (JSPHelper.getSessionAttribute(session, "vizMode") != null)
				out.println ("Viz mode. ");
			if (JSPHelper.getSessionAttribute(session, "search") != null)
				out.println ("Slant mode. ");
			%>
			Muse v<%=edu.stanford.muse.util.Version.num%>.
			
			Please <a href="https://docs.google.com/spreadsheet/viewform?formkey=dF9yYkhfbEdJNnVYaGhaYUdZaG1EeXc6MQ">send us feedback</a>.
			Or <a href="debug">report</a> a problem.&nbsp;&nbsp;&nbsp;&nbsp;
	</span>
	 <iframe src="//www.facebook.com/plugins/like.php?href=http://www.facebook.com/pages/Muse-Memories-Using-Email/181850975212773&show_faces=false&layout=button_count"
        scrolling="no" frameborder="0"
        style="border:none; width:90px; height:20px; position:relative; top:5px;">
     </iframe>
 </div>
</div>

<!-- bug here, we shouldn't be closing tables for login pages that don't have a header -->
</td>
</tr>
</table>
<!-- this closes the table for all content, opened in header.jsp -->
