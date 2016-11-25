<%@page language="java" import="java.util.*"%>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>

<div style="display:none;" id="div_advanced" class="toolbox-advanced">
<!--  <div id="div_advanced_title" class="panel-header">Advanced Controls</div> -->

<form name="filterForm" id="filterForm">
<table width="100%"><tr>
<td width="24%" valign="top" style="padding-right:20px;">
<span class="col-header">Message Filters</span><br/>
<hr/>
<INPUT TYPE=CHECKBOX ID="sentOnly" NAME="sentOnly"/> Sent messages only &nbsp;<br/>

Name or email <br/><input class="input-field" type="text" name="filterPersonOrEmail" id="filterPersonOrEmail"/><br/>
Keywords <br/> <input class="input-field" type="text" name="keywords" id="keywords"/><br/>
<%
String dateRange = "";
String checkedMessageText = ""; //"checked";


%>
Date range<br/><input class="input-field" type="text" name="dateRange" id="dateRange" value="<%=dateRange%>"/><br/>
<span class="db-hint">(e.g. 20050101-20051231)</span>
</td>
<td width="25%" valign="top"  style="padding-right:20px;">
<span class="col-header">Indexing Controls</span>
<hr/>
<INPUT TYPE=CHECKBOX ID="incrementalTFIDF" NAME="incrementalTFIDF" checked/> Emerging terms<br/>
<INPUT TYPE=CHECKBOX ID="NER" NAME="NER" checked/> Index names and entites <br/>
<INPUT TYPE=CHECKBOX ID="allText" NAME="allText" checked/> Index all text <br/>
<INPUT TYPE=CHECKBOX ID="locationsOnly" NAME="locationsOnly"/> Locations only <br/>
<INPUT TYPE=CHECKBOX ID="orgsOnly" NAME="orgsOnly"/> Organizations only <br/>
<INPUT TYPE=CHECKBOX ID="includeQuotedMessages" NAME="includeQuotedMessages"/> Include quoted messages <br/>
<INPUT TYPE=CHECKBOX ID="downloadMessageText" NAME="downloadMessageText" <%=checkedMessageText%>/> Fetch message text <br/>
<br/>
Subject Weight &nbsp; <input type="text" name="subjectWeight" size="1" id="subjectWeight" value="2"/><br/>
<br/>
</td>
<td valign="top" width="25%"  style="padding-right:20px;">

<span class="col-header">Groups Algorithm</span>
<hr/>
<% boolean checkedStatus = !("false".equals(JSPHelper.getSessionAttribute(session, "unifiedGroupsAlg")));
String checkedStr = (checkedStatus) ? "checked":"";%>
<INPUT TYPE=CHECKBOX ID="unifiedGroupAlg" NAME="unifiedGroupAlg" onclick="javascript:setUnifiedGroupAlg(this);" <%=checkedStr%> /> Unified<br/>
<INPUT TYPE=CHECKBOX ID="tagsForGroups" NAME="tagsForGroups"/> Tags for groups<br/>
<INPUT TYPE=CHECKBOX ID="disableDropMove" NAME="disableDropMove"/> Disable drop move<br/>
<INPUT TYPE=CHECKBOX ID="disableMergeMove" NAME="disableMergeMove"/> Disable merge move<br/>
<INPUT TYPE=CHECKBOX ID="disableIntersectMove" NAME="disableIntersectMove"/> Disable intersect move<br/>
<INPUT TYPE=CHECKBOX ID="disableDominateMove" NAME="disableDominateMove"/> Disable dominate move<br/>
<INPUT TYPE=TEXT ID="numGroups" NAME="numGroups" value="20" size="2"/> groups<br/>
<INPUT TYPE=TEXT ID="errWeight" NAME="errWeight" value="0.4" size="2"/> Smaller group bias (0-1.0)<br/>

</td>

</tr></table>
</form>
</div>