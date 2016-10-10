<%@ page contentType="text/html; charset=UTF-8"%>
<%@page language="java" import="java.net.*"%>
<%@page language="java" import="java.util.*"%>
<%@page language="java" import="org.json.*"%>
<%@page language="java" import="edu.stanford.muse.util.*"%>
<%@page language="java" import="edu.stanford.muse.email.*"%>
<%@page language="java" import="edu.stanford.bespoke.webapp.*"%>
<%
      response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
      response.setHeader("Pragma","no-cache"); //HTTP 1.0
      response.setDateHeader ("Expires", 0); //prevent caching at the proxy server
	  
      // SocialFlows mode
      String socialflowsURL = request.getParameter("socialflow");
      if (socialflowsURL != null) {
    	  socialflowsURL = URLDecoder.decode(socialflowsURL, "UTF-8");
          session.setAttribute("mode", "socialflow");
          session.setAttribute("socialflow", socialflowsURL);
          JSPHelper.log.info ("running in socialflow mode with socialflow=" + socialflowsURL);
      }
      
      // JSONP processing
      String jsonpCallback = (String)request.getParameter("jsonp_callback");
      if (jsonpCallback == null) {
          System.out.println(request.getServletPath()+": jsonp_callback is empty!");
          return;
      }
      
      
      // Handle login error messages
      String errorMsg = (String)JSPHelper.getSessionAttribute(session, "loginErrorMessage");
      if (errorMsg != null) {
    	  session.removeAttribute("loginErrorMessage");
    	  
    	  // Setup error msg
          JSONObject result = new JSONObject();
          result.put("error", true);
          result.put("msg", errorMsg);
          
          // JSON & Javascript based response to facilitate JSONP cross-domain
          String output = jsonpCallback+"("+result.toString()+")";
          response.setContentType("application/json");
          response.setHeader("Cache-Control", "no-cache");
          out.println(output);
          return;
      }
      
      // Handle other error messages
      String errorMessage = "Oops! An error occurred with our email analysis utility. Please retry again.";
      String x = (String) JSPHelper.getSessionAttribute(session, "errorMessage");
      if (x != null)
      {
          session.removeAttribute("errorMessage");
          
          x = Util.escapeHTML(x); // htmlize
          x = x.replace("\n", "<br/>\n"); // htmlize
          errorMessage += "<br/>\n" + x;
          
          // Setup error msg
          JSONObject result = new JSONObject();
          result.put("error", true);
          result.put("msg", errorMessage);

          String output = jsonpCallback+"("+result.toString()+")";
          System.out.println(request.getServletPath()+": JSONP output server side is - "+output);

          // JSON & Javascript based response to facilitate JSONP cross-domain
          response.setContentType("application/json");
          response.setHeader("Cache-Control", "no-cache");
          out.println(output);
          return;
      }
      
      
      // Check whether email folders processing is done or not
      Boolean foldersDone = (Boolean)JSPHelper.getSessionAttribute(session, "DONE_foldersAndCounts");
      if (foldersDone == null || !foldersDone.booleanValue())
      {
    	  // Setup not done yet msg
          JSONObject result = new JSONObject();
          result.put("foldersNotDone", true);
          
          // Get current status message
          StatusProvider obj = (StatusProvider) JSPHelper.getSessionAttribute(session, "statusProvider");
          if (obj == null) {
              result.put("statusMsg", "");
          }
          else {
              result.put("statusMsg", obj.getStatusMessage());
          }
          
          // JSON & Javascript based response to facilitate JSONP cross-domain
          String output = jsonpCallback+"("+result.toString()+")";
          response.setContentType("application/json");
          response.setHeader("Cache-Control", "no-cache");
          out.println(output);
          return;
      }
      session.removeAttribute("DONE_foldersAndCounts");
      

  	// the key variables for email accounts: 2 parallel lists of email stores and the folders in each of those stores.
  	// foldersAndCounts:
  	// one entry for every emailstore
  	// each entry is a list of folder info's

    // re-read accounts again only if we don't already have them in this session.
    // later we might want to provide a way for users to refresh the list of folders.
    List<List<FolderInfo>> foldersAndCounts 
    = (List<List<FolderInfo>>) JSPHelper.getSessionAttribute(session, "foldersAndCounts");
  	if (foldersAndCounts == null || foldersAndCounts.size() == 0)
  	{
  	    // Setup error msg
        JSONObject result = new JSONObject();
        result.put("error", true);
        result.put("msg", "No email folders were found for your account. Please specify an account with email messages.");
        
        // JSON & Javascript based response to facilitate JSONP cross-domain
        String output = jsonpCallback+"("+result.toString()+")";
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-cache");
        out.println(output);
        return;
  	}
  	
  	JSONArray sentMailFolders = new JSONArray();
  	for (List<FolderInfo> emailFolders : foldersAndCounts) {
  		for (FolderInfo emailFolder : emailFolders) {
  			if (emailFolder.messageCount <= 0)
  				continue;
  			String folderName = emailFolder.shortName.toLowerCase();
  			if (folderName.contains("sent") 
  				|| folderName.contains("send")
  				|| folderName.contains("outbox"))
  				sentMailFolders.put(emailFolder.longName);
  		}
  	}
  	
  	
  	
    // Setup success msg, also return list of selected email folders
    JSONObject result = new JSONObject();
    result.put("success", true);
    result.put("emailFolders", sentMailFolders);

    String output = jsonpCallback+"("+result.toString()+")";
    System.out.println(request.getServletPath()+": JSONP output server side is - "+output);

    // JSON & Javascript based response to facilitate JSONP cross-domain
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-cache");
    out.println(output);
    return;

%>