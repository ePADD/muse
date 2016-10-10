<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@page language="java" import="edu.stanford.muse.webapp.JSPHelper"%>

<html>
<head>
    <meta content="text/html; charset=utf-8" http-equiv="Content-Type"/>
    <title>Sign in with Twitter example</title>
</head>
<body>
<%if(null == JSPHelper.getSessionAttribute(session, "twitter")){%>    
<div style="margin:50px 0px 0px 100px;">	
Sign in to Twitter to create a search engine with sites recommended by your Twitter friends.<br/><br/>
<a href="signin"><img src="Sign-in-with-Twitter-darker.png"/></a></div>
<%} %>
<%if(null != JSPHelper.getSessionAttribute(session, "twitter")){%>
    <h1>Welcome</h1><br/>
    You are already logged in. <br/>
    <form action="./post" method="post">
        <textarea cols="80" rows="2" name="text"></textarea>
        <input type="submit" name="post" value="update"/>
    </form>
    <a href="./logout">logout</a>
<%} %>
</body>
</html>

