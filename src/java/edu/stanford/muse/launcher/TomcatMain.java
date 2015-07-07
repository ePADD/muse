/*
 Copyright (C) 2012 The Stanford MobiSocial Laboratory

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package edu.stanford.muse.launcher;

import edu.stanford.ejalbert.BrowserLauncher;
import edu.stanford.ejalbert.exception.BrowserLaunchingInitializingException;
import edu.stanford.ejalbert.exception.UnsupportedOperatingSystemException;
import org.apache.catalina.Context;
import org.apache.catalina.Session;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.cli.*;
import org.apache.log4j.PropertyConfigurator;
import org.apache.tomcat.util.http.fileupload.FileUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.Timer;

class Splash extends Frame implements ActionListener {
	Graphics2D g;
	SplashScreen splash = SplashScreen.getSplashScreen();

	void close() { splash.close(); }

	void setSplashText(String text) {
		if (splash == null)
			return;

		// clear the previous text
		g.setColor(new Color(1, 117, 188)); // #0175bc
		g.fillRect(300, 175, ((int) splash.getBounds().getWidth()) - 300, 40); // x=300 is where the logo starts, so we left-align to it. width is usually 795
		g.setPaintMode();

		// write the new text
		g.setColor(Color.WHITE); // #0175bc
		g.drawString(text, 300, 200);
//		g.setFont(new Font("Serif", Font.PLAIN, 12));
		splash.update();
	}

	public Splash() {
		final SplashScreen splash = (System.getProperty("nobrowseropen") == null) ? SplashScreen.getSplashScreen() : null;
		if (splash == null) {
			System.out.println("SplashScreen.getSplashScreen() returned null");
			return;
		}
		Rectangle r = splash.getBounds();
		g = splash.createGraphics();
		if (g == null) {
			System.out.println("splash.createGraphics() returned null");
			return;
		}
		System.out.println ("splash url = " + splash.getImageURL() + " w=" + r.getWidth() + " h=" + r.getHeight() + " size=" + r.getSize() + " loc=" + r.getLocation());
//		setVisible(true);
//		toFront();
	}

	public void actionPerformed(ActionEvent ae) {
		// System.exit(0);
	}

	static WindowListener closeWindow = new WindowAdapter(){
		public void windowClosing(WindowEvent e){
			e.getWindow().dispose();
		}
	};
}

/** main launcher class for tomcat with the muse.war webapp */
public class TomcatMain {

	static PrintStream savedSystemOut, savedSystemErr;
	static PrintStream out = System.out, err = System.err;
	static BrowserLauncher launcher;
	static String preferredBrowser;
	static Tomcat server;
	static String BASE_URL, MUSE_CHECK_URL, WEBAPP_NAME = "/muse";
	static boolean debug = false;
	static String debugFile;
	static final int TIMEOUT_SECS = 60;
	static Context museWebapp;
	
	private static final int MB = 1024 * 1024;
	final static int DEFAULT_PORT = 9099;
	static int PORT = DEFAULT_PORT;

	private static void tellUser (Splash splash, String s) { if (splash != null) splash.setSplashText(s); }

	// reads the warName from the classpath, copies it to a tmpdir, and deploys the war at the given path
	public static Context deployWarAt(String warName, String path) throws IOException, ServletException
	{
		// extract the war to tmpdir
		final URL warUrl = TomcatMain.class.getClassLoader().getResource(warName);
		if (warUrl == null)
		{
			err.println ("Sorry! Unable to locate file on classpath: " + warName);
			return null;
		}
		InputStream is = warUrl.openStream();
		String tmp = System.getProperty("java.io.tmpdir");
		String file = tmp + File.separatorChar + warName;
		err.println ("Extracting: " + warName + " to " + file + " is=" + is);

		File existingFile = new File(file);
		if (existingFile.exists())
			err.println ("Existing file: " + file);

//		if (new File(file).exists())
		copy_stream_to_file(is, file);

		File newFile = new File(file);
		if (!newFile.exists()) {
			err.println ("Sorry! Unable to copy war file: " + file);
			return null;			
		}

		err.println ("Copied muse war file with size " + newFile.length() + " bytes to " + newFile.getAbsolutePath());

		// clear the tomcat workdir which unfortunately caches jsp-generated java files sometimes and leads to terribly insiduous bugs.
		String workDir = tmp + File.separator + "muse" + File.separator + "working";
		File workFile = new File(workDir); 
		if (workFile.exists()) {
			err.println ("Tomcat work dir exists: " + workDir); 
			boolean success = deleteDir (new File(workDir));
			if (!success) 
				err.println ("Warning: unable to clear tomcat work dir " + workDir);
			else
				err.println ("Cool... able to clear tomcat work dir " + workDir);
		} 
		else 
			err.println ("Good, tomcat work dir does not already exist: " + workDir); 
		
		return server.addWebapp("/muse", new File(file).getAbsolutePath());
	}

        // Deletes all files and subdirectories under dir.
        // Returns true if all deletions were successful.
        // If a deletion fails, the method stops attempting to delete and returns
        // false.
        public static boolean deleteDir(File f)
        {
                if (f.isDirectory())
                {
                        String[] children = f.list();
                        for (int i = 0; i < children.length; i++)
                        {
                                boolean success = deleteDir(new File(f, children[i]));
                                if (!success)
                                {
                                        System.err.println("warning: failed to delete file " + f);
                                        return false;
                                }
                        }
                }

                // The directory is now empty so delete it
                return f.delete();
        }


	private static boolean isURLAlive(String url) throws IOException
	{
		try {
			// attempt to fetch the page
			// throws a connect exception if the server is not even running
			// so catch it and return false

			// since "index" may auto load default archive, attach it to session, and redirect to "info" page,
			// we need to maintain the session across the pages.
			// see "Maintaining the session" at http://stackoverflow.com/questions/2793150/how-to-use-java-net-urlconnection-to-fire-and-handle-http-requests
			CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));

			out.println("Testing liveness at " + url);
			HttpURLConnection u = (HttpURLConnection) new URL(url).openConnection();
			if (u.getResponseCode() == 200)
			{
				u.disconnect();
				return true;
			}
			u.disconnect();
		} catch (ConnectException ce) { }
		return false;
	}

	private static boolean killRunningServer(String url) throws IOException
	{
		try {
			// attempt to fetch the page
			// throws a connect exception if the server is not even running
			// so catch it and return false
			String http = url + "/exit.jsp?message=Shutdown%20request%20from%20a%20different%20instance%20of%20ePADD"; // version num spaces and brackets screw up the URL connection
			err.println ("Sending a kill request to " + http);
			HttpURLConnection u = (HttpURLConnection) new URL(http).openConnection();
			u.connect();
			if (u.getResponseCode() == 200)
			{
				u.disconnect();
				return true;
			}
			u.disconnect();
		} catch (ConnectException ce) { }
		return false;
	}

	/** waits till the page at the given url is alive, subject to timeout
	 * returns true if the page is alive.
	 * false if the page is not alive at the timeout
	 */

	private static boolean waitTillPageAlive(String url, int timeoutSecs) throws MalformedURLException, IOException
	{
		int tries = 0;
		int secsBetweenTries = 1;
		while (true)
		{
			boolean alive = isURLAlive(url);
			tries++;
			if (alive)
				break;

			out.println ("Web app not deployed after " + tries + " tries");

			try { Thread.sleep (secsBetweenTries * 1000); } catch (InterruptedException ie) { }
			if (tries * secsBetweenTries > timeoutSecs)
			{
				out.println ("\n\n\nSORRY! FAILED TO START CORRECTLY AFTER " + tries + " TRIES!\n\n\n");
				return false;
			}
		}
		out.println ("The ePADD web application was deployed successfully (#tries: " + tries + ")");
		return true;
	}

	private static boolean browserOpen = true, searchMode = false, amuseMode = false;
	private static String startPage = null, baseDir = null;

 	public static void aggressiveWarn (String message, long sleepMillis)
 	{
 		out.println ("\n\n\n\n\n");
 		out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
 		out.println ("\n\n\n\n\n\n" + message + "\n\n\n\n\n\n");
 		out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
 	 	out.println ("\n\n\n\n\n");

 		if (sleepMillis > 0)
 			try { Thread.sleep (sleepMillis); } catch (Exception e) { }
 	}
 	
	private static Options getOpt()
	{
		// create the Options
		// consider a local vs. global (hosted) switch. some settings will be disabled if its in global mode
		Options options = new Options();
		options.addOption( "h", "help", false, "print this message");
		options.addOption( "p", "port", true, "port number");
//		options.addOption( "a", "alternate-email-addrs", true, "use <arg> as alternate-email-addrs");
		options.addOption( "b", "base-dir", true, "use <arg> as archive dir");
		options.addOption( "d", "debug", false, "turn debug messages on");
		options.addOption( "df", "debug-fine", false, "turn detailed debug messages on (can result in very large logs!)");
		options.addOption( "dab", "debug-address-book", false, "turn debug messages on for address book");
		options.addOption( "dg", "debug-groups", false, "turn debug messages on for groups");
		options.addOption( "sp", "start-page", true, "start page");
		options.addOption( "n", "no-browser-open", false, "no browser open");
		options.addOption( "ns", "no-shutdown", false, "no auto shutdown");
		return options;
	}
	
	public static void launchBrowser(String url, Splash splash) throws BrowserLaunchingInitializingException, UnsupportedOperatingSystemException, IOException, URISyntaxException
	{
		// we use the browser launcher only for windows to try and skip IE
    	if (System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0) 
    	{
    		launcher = new BrowserLauncher();
        	List<String> browsers = (List<String>) launcher.getBrowserList();
    		out.print ("The available browsers on this system are: ");
    		// the preferred browser is the first browser, that is not IE.
    		for (String str: browsers)
    		{
    			out.print (str + " ");
    			if (preferredBrowser == null && !"IE".equals(str))
    				preferredBrowser = str;
    		}
    		out.println();
    		launcher.setNewWindowPolicy(true); // force new window
			out.println ("Launching URL in browser: " + url);

			if (splash != null)
				tellUser (splash, "Launching URL in browser: " + url);

    		if (preferredBrowser != null)
    			launcher.openURLinBrowser(url);
    		else
    			launcher.openURLinBrowser(preferredBrowser, url);
    	}
        else
        {
        	out.println ("Using Java Desktop launcher to browse to " + url);
        	desktop.browse(new java.net.URI(url));
        }
	}
	
	public static long KILL_AFTER_MILLIS = 24L * 3600L * 1000L; // default is 1 day, but can be changed
	private static java.awt.Desktop desktop;
	static { if (System.getProperty("nobrowseropen") == null) { desktop = java.awt.Desktop.getDesktop(); } } // necessary to do this early, causes problems with java 7 (both for taskbar icon and launching a browser) if you try and do it later
	
	private static void setupLogging()
	{
		// do this right up front, before JSPHelper is touched (which will call Log4JUtils.initialize())
		String settingsDir = System.getProperty("user.home") + File.separatorChar + ".muse";
		String logFile = settingsDir + File.separatorChar + "muse.log";
		String launcherLogFile = settingsDir + File.separatorChar + "muse-launcher.log";
		new File(settingsDir).mkdirs();

		System.setProperty("muse.log", logFile);

		System.out.println ("Set logging to " + logFile);
		try {
			out = new PrintStream(new FileOutputStream(launcherLogFile), true /* autoFlush */, "UTF-8");
			err = new PrintStream(new FileOutputStream(launcherLogFile), true /* autoFlush */, "UTF-8");
		} catch (Exception e) {
			out = System.out;
			err = System.err; // reset them back
			System.err.println ("Error redirecting out and err streams to " + launcherLogFile);
			e.printStackTrace();
		}
	}
	
	private static void basicSetup(String[] args) throws ParseException
	{
		// set javawebstart.version to a dummy value if not already set (might happen when running with java -jar from cmd line)
		// exit.jsp doesn't allow us to showdown unless this prop is set
		if (System.getProperty("javawebstart.version") == null)
			System.setProperty("javawebstart.version", "UNKNOWN");

    	if (args.length > 0)
    	{
        	out.print (args.length + " argument(s): ");
        	for (int i = 0; i < args.length; i++)
        		out.print (args[i] + " ");
        	out.println();
    	}
    	
    	Options options = getOpt();
    	CommandLineParser parser = new PosixParser();
    	CommandLine cmd = parser.parse(options, args);
        if (cmd.hasOption("help"))
        {
        	HelpFormatter formatter = new HelpFormatter();
        	formatter.printHelp( "ePADD batch mode", options);
        	return;
        }

        debug = false;
        if (cmd.hasOption("debug"))
        {
			URL url = ClassLoader.getSystemResource("log4j.properties.debug");
        	out.println ("Loading logging configuration from url: " + url);
            PropertyConfigurator.configure(url);
            debug = true;
        } else if (cmd.hasOption("debug-address-book"))
        {
			URL url = ClassLoader.getSystemResource("log4j.properties.debug.ab");
        	out.println ("Loading logging configuration from url: " + url);
            PropertyConfigurator.configure(url);
            debug = false;
        }
        else if (cmd.hasOption("debug-groups"))
        {
			URL url = ClassLoader.getSystemResource("log4j.properties.debug.groups");
        	out.println ("Loading logging configuration from url: " + url);
            PropertyConfigurator.configure(url);
            debug = false;
        }
        
        if (cmd.hasOption("no-browser-open") || System.getProperty("nobrowseropen") != null)
        	browserOpen = false;

        if (cmd.hasOption("port"))
        {
    		String portStr = cmd.getOptionValue('p');
    		try { 
    			PORT = Integer.parseInt (portStr); 
    			String mesg = " Running on port: " + PORT;
    			out.println (mesg);
    		} catch (NumberFormatException nfe) {
    			out.println ("invalid port number " + portStr);
    		}
        } 

        if (cmd.hasOption("start-page"))
    		startPage = cmd.getOptionValue("start-page");
        if (cmd.hasOption("base-dir"))
    		baseDir = cmd.getOptionValue("base-dir"); 
    	
    	if (!cmd.hasOption("no-shutdown")) {
        	// arrange to kill Muse after a period of time, we don't want the server to run forever

        	// i clearly have too much time on my hands right now...
        	long secs = KILL_AFTER_MILLIS/1000;
        	long hh = secs/3600;
        	long mm = (secs%3600)/60;
        	long ss = secs % (60);
        	out.print ("ePADD will shut down automatically after ");
        	if (hh != 0)
        		out.print (hh  + " hours ");
        	if (mm != 0 || (hh != 0 && ss != 0))
        		out.print (mm + " minutes");
        	if (ss != 0)
        		out.print (ss + " seconds");
        	out.println();

        	Timer timer = new Timer(); 
        	TimerTask tt = new ShutdownTimerTask();
        	timer.schedule (tt, KILL_AFTER_MILLIS);
    	}
        System.setSecurityManager(null); // this is important	
	}
	
	public static void setupResources() throws IOException, ServletException
	{
        out.println("Setting up Tomcat");

        server = new Tomcat();
        server.setPort(PORT);
        String baseDir = System.getProperty("java.io.tmpdir") + File.separator + "muse";

        // create the webapps dir under tmpdir, otherwise we see a disturbing error message, it creates a local tomcat.9099 dir for deployment etc.
        // need to first clear the webapps dir, otherwise it seems to not overwrite an existing war. 
        FileUtils.deleteDirectory(new File(baseDir + File.separator + "webapps"));
        new File(baseDir + File.separator + "webapps").mkdirs();
        server.setBaseDir(baseDir);
		err.println("Starting up at time " + formatDateLong(new GregorianCalendar()));
        err.println ("Basedir set to " + baseDir);

		museWebapp = deployWarAt("muse.war", WEBAPP_NAME);
        if (museWebapp == null)
        {
        	err.println ("Aborting... no webapp");
        	return;
        }
     
        out.println ("Deployed webapp to " + WEBAPP_NAME);
        
        // disabled for tomcat - enable if needed
        // if in any debug mode, turn blurring off
//        if (debug)
 //       	webapp1.setAttribute("noblur", true);
        
        // we set this and its read by JSPHelper within the webapp 
        System.setProperty("muse.container", "tomcat");
        
        // need to copy crossdomain.xml file and make it available at /crossdomain.xml in the server
		String tmp = System.getProperty("java.io.tmpdir");
		String xdomainDotXmlDir = tmp + File.separator + "ePADD-crossdomain-xml" + File.separatorChar;
		new File(xdomainDotXmlDir).mkdirs();
		
		final URL url = TomcatMain.class.getClassLoader().getResource("crossdomain.xml");
        try {
			InputStream is = url.openStream();
			String file = xdomainDotXmlDir + File.separatorChar + "crossdomain.xml";
			copy_stream_to_file(is, file);
			server.addWebapp("/", new File(xdomainDotXmlDir).getAbsolutePath()); // See http://grokbase.com/t/tomcat/users/131xsqrrm2/embedded-tomcat-how-to-use-addcontext-for-docbase
        } catch (Exception e) {
        	err.println ("Aborting copy of crossdomain.xml..." + e);
        }
        
        out.println ("Deployed crossdomain.xml in dir " + xdomainDotXmlDir);

        /*
	    ResourceHandler resource_handler = new ResourceHandler();
//        resource_handler.setWelcomeFiles(new String[]{ "index.html" });
        resource_handler.setResourceBase(tmp);
*/        
        // set the header buffer size in the connectors, default is a ridiculous 4K, which causes failures any time there is
        // is a large request, such as selecting a few hundred folders. (even for posts!)
        // usually there is only one SocketConnector, so we just put the setHeaderBufferSize in a loop.
        /*
        Connector conns[] = server.getConnectors();
        for (Connector conn: conns)
        {
        	int NEW_BUFSIZE = 1000000;
        	// out.println ("Connector " + conn + " buffer size is " + conn.getHeaderBufferSize() + " setting to " + NEW_BUFSIZE);
        	conn.setHeaderBufferSize(NEW_BUFSIZE);
        }
        */
     	
        out.println ("ePADD running check URL is " + MUSE_CHECK_URL);
		debugFile = tmp + File.separatorChar + "debug.txt";

		/*
		HandlerList hl = new HandlerList();
		if (webapp0 != null)
			hl.setHandlers(new Handler[]{webapp1, webapp0, resource_handler});
		else
			hl.setHandlers(new Handler[]{webapp1, resource_handler});
		server.setHandler(hl);
		*/
	}
	
	public static void shutdownSessions() {
		out.println ("shutting down sessions...");
        Session[] sessions = museWebapp.getManager().findSessions();
        for (Session session: sessions)
        {
        	if (session instanceof HttpSession)
        		try {
        			((HttpSession) session).invalidate();
        			out.println ("Successfully shut down session: " + session);
        		} catch (Exception e) {
        			out.println ("Exception " + e + " while shutting down session " + session);
        		}
        }
	}


	public static void main (String args[]) throws Exception
	{
		Splash splash = null;
		splash = new Splash();

		tellUser (splash, "Setting up logging...");
		setupLogging();
		tellUser (splash, "Setting up shutdown port...");
		basicSetup(args);
        BASE_URL = "http://localhost:" + PORT + "/" + WEBAPP_NAME;
        MUSE_CHECK_URL = BASE_URL + "/js/muse.js"; // for quick check of existing muse or successful start up. BASE_URL may take some time to run and may not always be available now that we set dirAllowed to false and public mode does not serve /muse.

		tellUser (splash, "Log file: " + debugFile + "***\n");

		out.println ("Starting up ePADD on the local computer at " + BASE_URL + ", " + formatDateLong(new GregorianCalendar()));
		out.println ("***For troubleshooting information, see this file: " + debugFile + "***\n");
    	out.println ("Current directory = " + System.getProperty("user.dir") + ", home directory = " + System.getProperty("user.home"));
    	out.println("Memory status at the beginning: " + getMemoryStats());
    	if (Runtime.getRuntime().maxMemory()/MB < 512)
    		aggressiveWarn ("You are probably running ePADD without enough memory. \nIf you launched ePADD from the command line, you can increase memory with an option like java -Xmx1g", 2000);
		tellUser (splash, "Memory: " + getMemoryStats());

		// handle frequent error of user trying to launch another server when its already on
		// server.start() usually takes a few seconds to return
		// after that it takes a few seconds for the webapp to deploy
        // ignore any exceptions along the way and assume not if we can't prove it is alive
        boolean urlAlive = false;
        try { urlAlive = isURLAlive(MUSE_CHECK_URL); }
        catch (Exception e) { out.println ("Exception: e"); e.printStackTrace(out); }

        boolean disableStart = false;
        if (urlAlive)
		{
			out.println ("Oh! ePADD already running at: " + BASE_URL + ", will shut it down!");
			tellUser (splash, "ePADD already running at: " + BASE_URL + ", will shut it down!");
			killRunningServer(BASE_URL);

			boolean killed = false;
			int N_KILL_TRIES = 20, N_KILL_PERIOD_MILLIS = 3000;
			// check every 3 secs 20 times (total 1 minute) for the previous muse to die
			// if it doesn't die after a minute, fail
			for (int i = 0; i < N_KILL_TRIES; i++)
			{
				try { Thread.sleep(N_KILL_PERIOD_MILLIS);} catch (InterruptedException ie) {}
				out.println("Checking " + MUSE_CHECK_URL);
				tellUser (splash, "Checking " + MUSE_CHECK_URL);
				try {urlAlive = isURLAlive(MUSE_CHECK_URL);}
				catch (Exception e)
				{
					out.println("Exception: " + e);
					e.printStackTrace(out);
				}
				if (!urlAlive)
					break;
			}

	        if (!urlAlive)
			{
				out.println("Good. Shutdown succeeded, will restart");
				tellUser (splash, "Good. Shutdown succeeded, we'll restart ePADD.");
			} else
			{
				String message = "Previously running ePADD still alive despite attempt to shut it down, disabling fresh restart!\n";
	        	message += "If you just want to use the previous instance of ePADD, please go to " + BASE_URL;
	        	message += "\nTo kill this instance, please go to your computer's task manager and kill running java or javaw processes.\nThen try launching ePADD again.\n";
	        	aggressiveWarn(message, 2000);
				tellUser (splash, "Sorry, unable to kill previous ePADD. Quitting!");
				try { Thread.sleep (10000); } catch (InterruptedException ie) { }
				if (splash != null)
					splash.close();
				return;
	        }
		}
        
//        else
//       	out.println ("Muse not already alive at URL: ..." + URL);

        if (!disableStart) {
    		setupResources();
	        out.println ("Starting ePADD at URL: ..." + BASE_URL);
			tellUser (splash, "Starting ePADD at " + BASE_URL);

			server.start();

			PrintStream debugOut1 = err;
	        try {
		        File f = new File(debugFile);
		        if (f.exists())
		        	f.delete(); // particular problem on windows :-(
				debugOut1 = new PrintStream(new FileOutputStream(debugFile), false, "UTF-8");
	        } catch (IOException ioe) {
	        	err.println ("Warning: failed to delete debug file " + debugFile + " : " + ioe);
	        }
	
	        final PrintStream debugOut = debugOut1;
	
	        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
	                public void run() {
	                        try {
	                        		shutdownSessions();
	                                server.stop();
	                                server.destroy();
	                                debugOut.close();
	                        } catch (Exception e) {
	                                // TODO Auto-generated catch block
	                                e.printStackTrace();
	                        }
	                }
	        }));
	//        InfoFrame frame = new InfoFrame();
	//        frame.doShow();
	
	        boolean success = waitTillPageAlive(MUSE_CHECK_URL, TIMEOUT_SECS);
	//        frame.updateText ("Opening a browser window");
	
	        if (success)
	        {
	        	try {
	        		int shutdownPort = PORT + 1; // shut down port is arbitrarily set to port + 1. it is ASSUMED to be free. 
	        		
	        		new ShutdownThread(server, shutdownPort).start();
	        		out.println ("Listening for ePADD shutdown message on port " + shutdownPort);
					tellUser (splash, "Listening for ePADD shutdown message on port " + shutdownPort);

				} catch (Exception e) {
	            	out.println ("Unable to start shutdown listener, you will have to stop the server manually using Cmd-Q on Mac OS or kill javaw processes on Windows");
	        	}
	
	        	try { setupSystemTrayIcon(); }
	        	catch (Exception e) { err.println ("Unable to setup system tray icon: " + e); e.printStackTrace(err); }
	        	
	        	// open browser window
	        	if (browserOpen)
	        	{
	        		preferredBrowser = null;
		        	// launch a browser here
	        		try {
			        	
			        	String link;
			        	link = "http://localhost:" + PORT + "/muse/index.jsp";
			        	
			        	if (startPage != null)
			        	{
			        		// startPage has to be absolute
			        		link = "http://localhost:" + PORT + "/muse/" + startPage;
			        	}
			        		
			        	if (baseDir != null)
			        		link = link + "?cacheDir=" + baseDir; // typically this is used when starting from command line. note: still using name, cacheDir
			        	
						launchBrowser(link, splash);
			        
	        		} catch (Exception e) {
		        		out.println ("Warning: Unable to launch browser due to exception (use the -n option to prevent ePADD from trying to launch a browser):");
		        		e.printStackTrace(out);
	        		}
	        	}
	
	        }
	        else
	        {
	        	out.println ("\n\n\nSORRY!!! UNABLE TO DEPLOY WEBAPP, EXITING\n\n\n");
				tellUser (splash, "SORRY!!! UNABLE TO DEPLOY WEBAPP, EXITING");
			}
	
	        savedSystemOut = out;
	        savedSystemErr = err;
			System.setOut(debugOut);
			System.setErr(debugOut);
			if (splash != null)
				splash.close();
        }
		// splashDemo.closeWindow();
	}

	static class ShutdownTimerTask extends TimerTask {
		// possibly could tie this timer with user activity
		public void run() {
			out.println ("Shutting down ePADD completely at time " +  formatDateLong(new GregorianCalendar()));
			savedSystemOut.println ("Shutting down ePADD completely at time " +  formatDateLong(new GregorianCalendar()));

			// maybe throw open a browser window to let user know muse is shutting down ??
			System.exit(0); // kill the program
		}
	}

	// util methods
	public static void copy_stream_to_file(InputStream is, String filename) throws IOException
	{
	    int bufsize = 64 * 1024;
	    BufferedInputStream bis = null;
	    BufferedOutputStream bos = null;
	    try {
	    	File f = new File(filename);
	    	if (f.exists())
	    	{
		    	// out.println ("File " + filename + " exists");
				boolean b = f.delete(); // best effort to delete file if it exists. this is because windows often complains about perms 
				if (!b)
					out.println ("Warning: failed to delete " + filename);
	    	}
	        bis = new BufferedInputStream(is, bufsize);
	        bos = new BufferedOutputStream(new FileOutputStream(filename), bufsize);
	        byte buf[] = new byte[bufsize];
	        while (true)
	        {
	            int n = bis.read(buf);
	            if (n <= 0)
	                break;
	            bos.write (buf, 0, n);
	        }
		} catch (IOException ioe)
		{
			out.println ("ERROR trying to copy data to file: " + filename + ", forging ahead nevertheless");
	    } finally {
	        if (bis != null) bis.close();
	        if (bos != null) bos.close();
	    }
	}

	public static String getStreamContents(InputStream in) throws IOException
	{
		BufferedReader br = new BufferedReader(new InputStreamReader (in));
		StringBuilder sb = new StringBuilder();
		// read all the lines one by one till eof
		while (true)
		{
			String x = br.readLine();
			if (x == null)
				break;

			sb.append(x);
			sb.append("\n");
		}
		return sb.toString();
	}

	public static String formatDateLong(Calendar d)
	{
		if (d == null)
			return "??-??";
		else
			return d.get(Calendar.YEAR) + "-" + String.format("%02d", (1+d.get(Calendar.MONTH))) + "-" + String.format ("%02d", d.get(Calendar.DAY_OF_MONTH)) + " "
				 + String.format("%02d", d.get(Calendar.HOUR_OF_DAY)) + ":" + String.format("%02d", d.get(Calendar.MINUTE)) + ":" +
				 String.format("%02d", d.get(Calendar.SECOND));
	}
	
	/** we need a system tray icon for management.
	 * http://docs.oracle.com/javase/6/docs/api/java/awt/SystemTray.html */
	public static void setupSystemTrayIcon()
	{
		// Set the app name in the menu bar for mac. 
		// c.f. http://stackoverflow.com/questions/8918826/java-os-x-lion-set-application-name-doesnt-work
		System.setProperty("apple.laf.useScreenMenuBar", "true");
		System.setProperty("com.apple.mrj.application.apple.menu.about.name", "ePADD");
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) { throw new RuntimeException(e); }

	    TrayIcon trayIcon = null;
	     if (SystemTray.isSupported()) 
	     {
		    System.out.println ("Adding ePADD to the system tray");
	         SystemTray tray = SystemTray.getSystemTray();
	         
	         URL u = TomcatMain.class.getClassLoader().getResource("muse-icon.png"); // note: this better be 16x16, Windows doesn't resize! Mac os does.
	         System.out.println ("ePADD icon resource is " + u);
	         Image image = Toolkit.getDefaultToolkit().getImage(u);
	         System.out.println ("Image = " + image);
	         
	         // create menu items and their listeners
	         ActionListener openMuseControlsListener = new ActionListener() {
	             public void actionPerformed(ActionEvent e) {
	            	 try {
						launchBrowser(BASE_URL + "/info", null); 
					} catch (Exception e1) {
						e1.printStackTrace();
					}
	             }
	         };
	         
	         ActionListener QuitMuseListener = new ActionListener() {
	             public void actionPerformed(ActionEvent e) {
			        out.println("*** Received quit from system tray. Stopping the Tomcat embedded web server.");
	 	            try { shutdownSessions(); server.stop(); } catch(Exception ex) { throw new RuntimeException(ex); }
		            System.exit(0); // we need to explicitly system.exit because we now use Swing (due to system tray, etc).
	             }
	         };

	         // create a popup menu
	         PopupMenu popup = new PopupMenu();
	         MenuItem defaultItem = new MenuItem("Open ePADD window");
	         defaultItem.addActionListener(openMuseControlsListener);
	         popup.add(defaultItem);
	         MenuItem quitItem = new MenuItem("Quit ePADD");
	         quitItem.addActionListener(QuitMuseListener);
	         popup.add(quitItem);
	         
	         /// ... add other items
	         // construct a TrayIcon
            String message = "ePADD menu";
            // on windows - the tray menu is a little non-intuitive, needs a right click (plain click seems unused)
            if (System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0)
                message = "Right click for ePADD menu";
	         trayIcon = new TrayIcon(image, message, popup);
            System.out.println ("tray Icon = " + trayIcon);
	         // set the TrayIcon properties
//	         trayIcon.addActionListener(openMuseControlsListener);
	         try {
	             tray.add(trayIcon);
	         } catch (AWTException e) {
	             err.println(e);
	         }
	         // ...
	     } else {
	         // disable tray option in your application or
	         // perform other actions
//	         ...
	     }
	     System.out.println("Done!");
	     // ...
	     // some time later
	     // the application state has changed - update the image
	     if (trayIcon != null) {
	 //        trayIcon.setImage(updatedImage);
	     }
	     // ...
	}

	public static String getMemoryStats()
	{
		Runtime r = Runtime.getRuntime();
		System.gc();
		return r.freeMemory()/MB + " MB free, " + (r.totalMemory()/MB - r.freeMemory()/MB) + " MB used, "+ r.maxMemory()/MB + " MB max, " + r.totalMemory()/MB + " MB total";
	}

/** this is a stop tomcat thread to listen to a message on some port -- any message on this port will shut down Muse.
 * mainly meant so that a new launch of Muse will kill the previously running version. */
static class ShutdownThread extends Thread {
	static PrintStream out = System.out;

    private ServerSocket socket;
    private int shutdownPort;
    private Tomcat server;
    public ShutdownThread(Tomcat server, int shutdownPort) {
    	this.server = server;
    	this.shutdownPort = shutdownPort;
        setDaemon(true);
        setName("Stop Tomcat");
        try {
            socket = new ServerSocket(this.shutdownPort, 1, InetAddress.getByName("127.0.0.1"));
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        out.println("*** running tomcat 'stop' thread");
        Socket accept;
        try {
            accept = socket.accept();
            BufferedReader reader = new BufferedReader(new InputStreamReader(accept.getInputStream()));
            // wait for a readline
            String line = reader.readLine();
            // any input received, stop the server
            shutdownSessions();
            server.stop();
            out.println("*** Stopped the Tomcat embedded web server. received: " + line);
            accept.close();
            socket.close();
            System.exit(1); // we need to explicitly system.exit because we now use Swing (due to system tray, etc).
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
}
