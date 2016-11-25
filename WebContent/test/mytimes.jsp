<%@page language="java" import="edu.stanford.muse.webapp.*"%>

<%String path = HTMLUtils.getRootURL(request); %>
<html>
<head>
<jsp:include page="../css/css.jsp"/>
<link rel="icon" type="image/png" href="images/muse-favicon.png">
<title>MY Times</title>
</head>
<body>
<div style="padding:100px">

Drag this bookmarklet <a class="bookmarklet" style="padding:5px" href="javascript:(function(){window.MUSE_URL = '<%=path%>'; var S=document.createElement('SCRIPT'); S.type='text/javascript'; S.src='<%=path%>/js/mytimes.js'; document.getElementsByTagName('head')[0].appendChild(S);})();">MY Times</a> to your browser's bookmarks toolbar.<br/>
To re-order articles on the NY Times web site, click on the bookmarklet, and watch the articles below the fold.
<p>
You can also use this to <a class="bookmarklet" style="padding:5px" href="javascript:(function(){window.MUSE_URL = '<%=path%>'; var S=document.createElement('SCRIPT'); S.type='text/javascript'; S.src='<%=path%>/js/zzz.js'; document.getElementsByTagName('head')[0].appendChild(S);})();">Jog</a> through the articles.
</div>

</body>
</html>