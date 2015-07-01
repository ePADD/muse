package edu.stanford.muse.iris;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gdata.client.calendar.CalendarService;
import com.google.gdata.util.ServiceException;
import com.joestelmach.natty.*;

import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.iris.calendar.*;
//import com.joestelmach.natty.
public class NattyTest {
	
	// The base URL for a user's calendar metafeed (needs a username appended).
	  private static final String METAFEED_URL_BASE = 
	      "https://www.google.com/calendar/feeds/";

	  // The string to add to the user's metafeedUrl to access the event feed for
	  // their primary calendar.
	  private static final String EVENT_FEED_URL_SUFFIX = "/private/full";

	  // The URL for the metafeed of the specified user.
	  // (e.g. http://www.google.com/feeds/calendar/jdoe@gmail.com)
	  private static URL metafeedUrl = null;

	  // The URL for the event feed of the specified user's primary calendar.
	  // (e.g. http://www.googe.com/feeds/calendar/jdoe@gmail.com/private/full)
	  private static URL eventFeedUrl = null;

	
	public static void main(String[] args) {
		try{
			
		
			Date reference = DateFormat.getDateInstance(DateFormat.SHORT).parse("2/28/2011");
		    CalendarSource.setBaseDate(reference);
			Parser parser = new Parser();
			List <DateGroup>groups = parser.parse("the day before next thursday");
			for(DateGroup group:groups) {
			  List dates = group.getDates();
			  System.out.println(dates.get(0));
			  int line = group.getLine();
			  int column = group.getPosition();
			  String matchingValue = group.getText();
			 // String syntaxTree = group.getSyntaxTree().toStringTree();
			  //Map> parseMap = group.getParseLocations();
			  boolean isRecurreing = group.isRecurring();
			  Date recursUntil = group.getRecursUntil();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
	  }
	
	
	
	public static void parseEmailforEvents(Collection<EmailDocument> allDocs)
	{
		for (Iterator iter = allDocs.iterator(); iter.hasNext();) {
			
			try{
				EmailDocument emailObj = (EmailDocument) iter.next();
			
				Date date= emailObj.getDate();
				String messageHeader= emailObj.getSubject();
				String messageBody= emailObj.getContents();
				
				//System.out.println(messageBody);
				parseMessageandCreateCalendarEntry(messageHeader,messageBody,date,"abhinaynagpal@gmail.com",":P");
				//rest of the code block removed
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}

	}
	
	public static void parseMessageandCreateCalendarEntry(String messageHeader,String messagebody,Date messageDate,String username,String password)
	{
		
		//meet time itenary flight booking reservation 
		if(!messagebody.toLowerCase().contains("meet")&&!messagebody.toLowerCase().contains("iternary")
				&&!messagebody.toLowerCase().contains("flight")&&!messagebody.toLowerCase().contains("book")&&!messagebody.toLowerCase().contains("reservation"))
				return;
			
		CalendarService myService = new CalendarService("exampleCo-exampleApp-1");
		 // Create the necessary URL objects.
	    try {
	      metafeedUrl = new URL(METAFEED_URL_BASE + username);
	      eventFeedUrl = new URL(METAFEED_URL_BASE + username
	          + EVENT_FEED_URL_SUFFIX);
	    } catch (MalformedURLException e) {
	      // Bad URL
	      System.err.println("Uh oh - you've got an invalid URL.");
	      e.printStackTrace();
	      return;
	    }
	    
	    try{
			
			
			//Date reference = DateFormat.getDateInstance(DateFormat.SHORT).parse("2/28/2011");
	    	Date reference = messageDate;
		    CalendarSource.setBaseDate(reference);
			Parser parser = new Parser();
			List <DateGroup>groups = parser.parse(messagebody);
			for(DateGroup group:groups) {
			  List dates = group.getDates();
			  //System.out.println(dates.get(0));
			  int line = group.getLine();
			  int column = group.getPosition();
			  String matchingValue = group.getText();
			  
			  
			//  myService.setUserCredentials(username, password);
		      if(matchingValue.length()>4)
		    	  System.out.println("Subject: "+ messageHeader + "Event: "+ matchingValue+ (Date)dates.get(0));
			  /*EventFeedUtils.createIrisEvent(myService,
		    		  messageHeader, matchingValue, (Date)dates.get(0));*/
			 // String syntaxTree = group.getSyntaxTree().toStringTree();
			  //Map> parseMap = group.getParseLocations();
			  //boolean isRecurreing = group.isRecurring();
			  //Date recursUntil = group.getRecursUntil();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

	   
	}

}
