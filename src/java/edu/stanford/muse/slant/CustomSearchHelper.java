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
package edu.stanford.muse.slant;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.sun.el.parser.ParseException;

import edu.stanford.muse.index.LinkInfo;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.webapp.JSPHelper;

// TODOs: 
// batch CSE annotation updates - takes too long currently.
// handle case when password has & 
// urls always accumulate into CSE - is that ok? how are weights affected?
// handle multiple links in strings
// look carefully at weights
// contact url-expander for every url?
// Get the direct link to CSE search page: see http://code.google.com/apis/customsearch/docs/api.html#retrieve_lists

public class CustomSearchHelper {
	//private static final long serialVersionUID = 1657390011452788111L;
    private static Log log = LogFactory.getLog(JSPHelper.class);

	// we upload in batches, because uploading one huge file sometimes fails
	private static int ANNOTATION_UPLOAD_BATCH_SIZE = 100;
	
	// explicitly excluding blogspot, livejournal and wordpress sites
	public final static String [] top100=  { "microsoft.com",
			"163.com",
			"google.fr",
			"google.com.br",
			"googleusercontent.com",
			"flickr.com",
			"paypal.com",
			"fc2.com",
			"mail.ru",
			"google.it",
			"craigslist.org",
			"apple.com",
			"google.es",
			"imdb.com",
			"bbc.co.uk",
			"google.ru",
			"ask.com",
			"sohu.com",
			"go.com",
			"vkontakte.ru",
			"xvideos.com",
			"cnn.com",
			"tumblr.com",
			"livejasmin.com",
			"megaupload.com",
			"bp.blogspot.com", // what ?!! abhinay, are you sure?
			"soso.com",
			"aol.com",
			"google.ca",
			"youku.com",
			"xhamster.com",
			"tudou.com",
			"mediafire.com",
			"yieldmanager.com",
			"zedo.com",
			"pornhub.com",
			"ifeng.com",
			"adobe.com",
			"godaddy.com",
			"espn.go.com",
			"google.co.id",
			"about.com",
			"ameblo.jp",
			"wordpress.org",
			"rakuten.co.jp",
			"4shared.com",
			"ebay.de",
		//	"livejournal.com",
			"google.com.tr",
			"google.com.mx",
			"google.com",
			"facebook.com",
			"youtube.com",
			"yahoo.com",
		//	"blogspot.com",
			"baidu.com",
			"wikipedia.org",
			"live.com",
			"twitter.com",
			"qq.com",
			"msn.com",
			"yahoo.co.jp",
			"sina.com.cn",
			"taobao.com",
			"google.co.in",
			"amazon.com",
			"linkedin.com",
		//	"wordpress.com",
			"google.com.hk",
			"google.de",
			"bing.com",
			"google.co.uk",
			"yandex.ru",
			"ebay.com",
			"google.co.jp",
			"livedoor.com",
			"alibaba.com",
			"google.com.au",
			"myspace.com",
			"youporn.com",
			"cnet.com",
			"uol.com.br",
			"renren.com",
			"weibo.com",
			"google.pl",
			"nytimes.com",
			"conduit.com",
			"hao123.com",
			"thepiratebay.org",
			"orkut.com.br",
			"cnzz.com",
			"ebay.co.uk",
			"chinaz.com",
			"orkut.com",
			"fileserve.com",
			"twitpic.com",
			"netflix.com",
			"dailymotion.com",
			"amazon.de",
	"weather.com" };

	public final static String[] url_shorteners = {"goo.gl",
			"ow.ly",
			"j.mp",
			"adjix.com",
			"b23.ru",
			"bit.ly",
			"budurl.com",
			"canurl.com",
			"cli.gs",
			"decenturl.com",
			"dolop.com",
			"dwarfurl.com",
			"easyurl.net",
			"elfurl.com",
			"ff.im",
			"fire.to",
			"flq.us",
			"freak.to",
			"fuseurl.com",
			"g02.me",
			"go2.me",
			"idek.net",
			"is.gd",
			"ix.lt",
			"kissa.be",
			"kl.am",
			"korta.nu",
			"krunchd.com",
			"ln-s.net",
			"loopt.us",
			"memurl.com",
			"miklos.dk",
			"moourl.com",
			"myurl.in",
			"nanoref.com",
			"notlong.com",
			"ow.ly",
			"ping.fm",
			"piurl.com",
			"poprl.com",
			"qicute.com",
			"qurlyq.com",
			"reallytinyurl.com",
			"redirx.com",
			"rubyurl.com",
			"rurl.org",
			"shorl.com",
			"short.ie",
			"shorterlink.com",
			"shortlinks.co.uk",
			"shorturl.com", "shout.t", "shrinkurl.us","shurl.net","shw.me", "simurl.com", "smallr.com", "snipr.com","snipurl.com","snurl.com","starturl.com","surl.co.uk", "tighturl.com", "tinylink.com", "tinypic.com", "tinyurl.com", "tinyvh.com", "tr.im", "traceurl.com", "twurl.nl", "u.mavrev.com", "ur1.ca", "url-press.com", "url.ie", "url9.com", "urlcut.com", "urlhawk.com", "urli.ca", "urlpass.com", "urlx.ie", "xaddr.com", "xrl.us", "yep.it", "yuarel.com", "yweb.com", "zurl.ws"};

	// Google has a 5K limit on total #annotations, and 2K "per file" (presumably the same limit applies to one API call).
	// some people have reported errors in saving annotations even before the limit is reached, so we'll set it to 4K.
	// http://code.google.com/intl/en/apis/customsearch/docs/annotations.html#limits
	// we may need to revisit this.
	public static final int DOMAIN_LIMIT = 4900; 
	public static Set<String> url_shorteners_set = new LinkedHashSet<String>();
	static { 
		for (String u: url_shorteners)
			url_shorteners_set.add(u);
	}
	
	
	/*
	 * This method accepts the score i.e weight we assign to a url
	 * this helps us rerank results
	 */
	public static void populateCSE(String authtoken,String domain,double score)
	{
		

		try
		{
			HttpClient client = new HttpClient();
			PostMethod annotation_post = new PostMethod("http://www.google.com/coop/api/default/annotations/");				

			String label;
			domain = URLEncoder.encode( domain, "UTF-8" );
			if(domain.indexOf("http")<0)
				label="<Annotation about=\"http://"+domain+"/*\" score=\""+ score+ "\">";
			else
				label="<Annotation about=\""+domain+"/*\" score=\""+ score+ "\">";
			String new_annotation ="<Batch>" +
					"<Add>" + 
					"<Annotations>" +
					label +
					"<Label name=\"_cse_testengine\"/>" +
	                 "</Annotation>" +
					"</Annotations>" +
					"</Add>" +
					"</Batch>";

			System.out.println("uploading annotation :"+ new_annotation);
			annotation_post.addRequestHeader("Content-type", "text/xml");
			annotation_post.addRequestHeader("Authorization", "GoogleLogin auth=" + authtoken);

			StringRequestEntity rq_en = new StringRequestEntity(new_annotation,"text/xml","UTF-8");
			annotation_post.setRequestEntity(rq_en);

			int astatusCode = client.executeMethod(annotation_post);


			if (astatusCode==HttpStatus.SC_OK)
			{
				System.out.println("Annotations updated");
				 //indexurlcount++;
			}
			else{
				System.out.println("Annotation update failed");
				String responseBody = IOUtils.toString(annotation_post.getResponseBodyAsStream(), "UTF-8");         
				System.out.println("Result remoteRequest: "+responseBody);
				System.out.println("Annotation update failed");
				//rejectedurlcount++;
			}
		}
		catch(Exception e)
		{
			System.out.println("\nexception:"+e.getMessage());
		}
	}


	/** pushes the given xml to the user's CSE configuration 
	 * @throws IOException 
	 * @throws HttpException */
	public static boolean pushAnnotations (String authtoken, String xml) throws HttpException, IOException
	{
		// see http://code.google.com/apis/customsearch/docs/api.html#create_annos

		HttpClient client = new HttpClient();
		PostMethod annotation_post = new PostMethod("http://www.google.com/coop/api/default/annotations/");				

		log.debug("uploading annotations:\n"+ xml);
		annotation_post.addRequestHeader("Content-type", "text/xml");
		annotation_post.addRequestHeader("Authorization", "GoogleLogin auth=" + authtoken);
		StringRequestEntity rq_en = new StringRequestEntity (xml, "text/xml", "UTF-8");
		annotation_post.setRequestEntity(rq_en);

		int astatusCode = client.executeMethod(annotation_post);
		if (astatusCode == HttpStatus.SC_OK)
			return true;
		else {
			String responseBody = IOUtils.toString(annotation_post.getResponseBodyAsStream(), "UTF-8");         
			log.warn("Annotation update failed: " + responseBody);
			return false;
		}
	}

	private static void deleteCSE(String authtoken, String cseName) throws IOException, ParserConfigurationException, XPathExpressionException
	{
		// see http://code.google.com/apis/customsearch/docs/api.html

		String url = "http://www.google.com/cse/api/default/annotations/?num=5000"; // w/o num param, returns only top 20 results
		GetMethod get = new GetMethod(url);				
		get.addRequestHeader("Content-type", "text/xml");
		get.addRequestHeader("Authorization", "GoogleLogin auth=" + authtoken);
		int statusCode = new HttpClient().executeMethod(get);
		if (statusCode != HttpStatus.SC_OK)
		{
			log.warn ("Unable to read Google CSE annotations, and therefore unable to delete existing annotations! HTTP status = " + statusCode);
			return;			
		}
	
		/* sample xml: 
		<?xml version="1.0" encoding="UTF-8" ?>
		<Annotations start="0" num="2182" total="2182">
				<Annotation about="visualizing.stanford.edu/*" score="0.000810373" timestamp="0x0004b2a4c7d271fd" href="Chp2aXN1YWxpemluZy5zdGFuZm9yZC5lZHUvKhD948m-zNSsAg">
				<Label name="_cse_testengine" />
				<AdditionalData attribute="original_url" value="http://visualizing.stanford.edu/*" />
			</Annotation>
		...
		 */
	
		// parse xml and get href's for the annotations for this cse.
		String responseBody = IOUtils.toString(get.getResponseBodyAsStream(), "UTF-8");
		InputSource is = new InputSource(new StringReader(responseBody));
		XPath xpath = XPathFactory.newInstance().newXPath();
		String expression = "/Annotations/Annotation/Label[@name='_cse_" + cseName + "']";
		NodeList nodes = (NodeList) xpath.evaluate(expression, is, XPathConstants.NODESET);
		List<String> hrefs = new ArrayList<String>();
		log.info(nodes.getLength() + " existing annotation(s) for search engine " + cseName + ". They will be deleted.");
		for (int i = 0; i < nodes.getLength(); i++)
		{
			Node label = nodes.item(i);
			Node annotation = label.getParentNode();
			Node hrefNode = annotation.getAttributes().getNamedItem("href");
			String href = hrefNode.getNodeValue();
			hrefs.add(href);
		}
		
		// hrefs now has all the existing href's for this CSE. send a remove command with these href's.
		StringBuilder removeXML = new StringBuilder("<Batch><Remove><Annotations>");
		int batchCount = 0;
		for (Iterator<String> it = hrefs.iterator(); it.hasNext(); )
		{
			String href = it.next();
			removeXML.append (" <Annotation href=\"" + href + "\" />");
			batchCount++;
			if (batchCount == ANNOTATION_UPLOAD_BATCH_SIZE || !it.hasNext())
			{
				removeXML.append ("</Annotations></Remove></Batch>");
				boolean success = pushAnnotations(authtoken, removeXML.toString());
				if (!success)
					log.warn ("Failed to delete " + batchCount + " existing annotations for search engine " + cseName + " xml = " + removeXML);
				else
					log.info ("Successfully deleted " + batchCount + " existing annotations for search engine " + cseName);
				removeXML = new StringBuilder("<Batch><Remove><Annotations>");
				batchCount = 0;
			}
		}
	}

	/** should be a plain domain, not http:// ... */
	public static boolean isTop100 (String domain)
	{
		for (int i=0;i<top100.length;i++)
			if (domain.contains(top100[i]))
				return true;

		return false;
	}

	/** note: domain must not have http://... and no extraneous www. e.g.should be just bit.ly, not www.bit.ly or http://bit.ly
	 * @param domain
	 * @return
	 */
	public static boolean isShortURL(String domain)
	{
		if (Util.nullOrEmpty(domain))
			return false;
		return url_shorteners_set.contains(domain);
	}

	public boolean isIndexed(String weburl)
	{
		try {
			String userHome = System.getProperty("user.home");  
			//String file = userHome + "/tmp/mailurls.txt";
			String file = userHome + File.separator + "tmp" + File.separator + "mailurls.txt";
			BufferedReader in = new BufferedReader(new FileReader(file));
			//BufferedReader in = new BufferedReader(new FileReader("mailurls.txt"));
			String str;
			while ((str = in.readLine()) != null) {
				if (str.equals(weburl))
					return true;
			}
			in.close();
			return false;
		} catch (IOException e) {
			log.warn ("\nexception:"+e.getMessage());
			return false;
		}
	}

	/** converts shortURL to longURL */
	public static String expandURL(String shortURL)
	{
		String longURL = shortURL;
		try {
			String encodedURL = URLEncoder.encode(shortURL, "UTF-8" );
			URL appurl = new URL("http://url-expander.appspot.com/expand.jsp?url=" + encodedURL);
			URLConnection yc = appurl.openConnection();
			BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
			String inputLine;

			while ((inputLine = in.readLine()) != null) 
			{
				log.info (inputLine);
				if (inputLine.length()>0)
					longURL = inputLine;
				log.info("Input url " + shortURL + " expanded to " + longURL);
			}
			in.close();
		} catch (Exception e) {
			log.warn ("Unable to expand URL: " + shortURL + " Exception is " + e + "\n" + Util.stackTrace(e));
		}
		return longURL;
	}
	
	public static Document loadXMLFromString(String xml) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xml));
        return builder.parse(is);
    }

	/* gets ID of the CSE */
	public static String readCreatorID(String authtoken)
	{
		String responseBody=null;
		try{
			HttpClient client = new HttpClient();
			GetMethod annotation_post = new GetMethod("http://www.google.com/cse/api/default/cse/");				
	
			annotation_post.addRequestHeader("Content-type", "text/xml");
			annotation_post.addRequestHeader("Authorization", "GoogleLogin auth=" + authtoken);
			
			int astatusCode = client.executeMethod(annotation_post);
			if (astatusCode == HttpStatus.SC_OK)
			{
				responseBody = IOUtils.toString(annotation_post.getResponseBodyAsStream(), "UTF-8");         
				log.info("Result request for creator id: "+responseBody);
			}
			else {
				responseBody = IOUtils.toString(annotation_post.getResponseBodyAsStream(), "UTF-8");         
				log.warn("Search id failed: Result request: "+responseBody);
			}
		}
		catch (Exception e) {
			log.warn ("Exception reading xml for creator id: " + e + " " + Util.stackTrace(e));
		}
		
		try {			 
			Document doc = loadXMLFromString(responseBody);
			doc.getDocumentElement().normalize();
	 
			NodeList nList = doc.getElementsByTagName("CustomSearchEngine");
			for (int temp = 0; temp < nList.getLength(); temp++) {
	 
			   Node nNode = nList.item(temp);
			   if (nNode.getNodeType() == Node.ELEMENT_NODE) {
	 
			      Element eElement = (Element) nNode;
			      String creator = eElement . getAttribute("creator"); // eElement is not HttpSession
	              if (creator!=null)
	              {
	            	  log.info("creator id:" + creator);
	            	  return creator;
	              }
			   }
			}
		  } catch (Exception e) {
			  log.warn ("Invalid xml from google while reading creator id: " + e + " " + Util.stackTrace(e));
		  }

		  return null;	
	}

	public static List<CSEDetails> getExistingCSEs(String authtoken) throws HttpException, IOException
	{
		HttpClient client = new HttpClient();

		GetMethod get = new GetMethod("http://www.google.com/cse/api/default/cse/");
		get.addRequestHeader("Content-type", "text/xml");
		get.addRequestHeader("Authorization", "GoogleLogin auth=" + authtoken);

		int astatusCode = client.executeMethod(get);

		List<CSEDetails> cses = new ArrayList<CSEDetails>();
		
		if (astatusCode == HttpStatus.SC_OK)
		{
			//If successful, the CSE annotation is displayed in the response.
			String s = get.getResponseBodyAsString();
			try {
				Document doc = loadXMLFromString(s);
				doc.getDocumentElement().normalize();
				 
				NodeList nList = doc.getElementsByTagName("CustomSearchEngine");
				for (int temp = 0; temp < nList.getLength(); temp++) {
		 
				   Node nNode = nList.item(temp);
				   if (nNode.getNodeType() == Node.ELEMENT_NODE) {
	
					  CSEDetails cse = new CSEDetails();
					  
				      Element eElement = (Element) nNode;
				      String creator = eElement . getAttribute("creator"); // eElement is not HttpSession
		              if (creator != null)
		            	  cse.creator = creator;
				      eElement = (Element) nNode;
				      String title = eElement . getAttribute("title"); // eElement is not HttpSession
		              if (creator != null)
		            	  cse.title = title;
				      eElement = (Element) nNode;
				      String description = eElement . getAttribute("description"); // eElement is not HttpSession
		              if (creator != null)
		            	  cse.description = description;
		              cses.add(cse);
				   }
				}
			} catch (Exception e) { 
				Util.print_exception(e);
				return cses;
			}
			log.info ("Existing CSEs: " + s);
		}
		else
			log.warn ("get existing CSEs failed");
		return cses;
	}
	
	private static void initializeCSE(String authtoken, String cseName) throws IOException
	{
		HttpClient client = new HttpClient();

		//First perform the ClientLogin to get the authtoken
		//See http://code.google.com/apis/accounts/docs/AuthForInstalledApps.html

		PostMethod create_post = new PostMethod("http://www.google.com/cse/api/default/cse/" + cseName);

		String new_annotation ="<CustomSearchEngine language=\"en\">" +
				"<Title>" + Util.escapeXML(cseName) + "</Title>" + 
				"<Description>" + Util.escapeXML(cseName) + " - generated by Slant</Description>" + 
				"<Context>" +
				"<BackgroundLabels>"+
				"<Label name=\"_cse_testengine\" mode=\"BOOST\" />"+
				"<Label name=\"_cse_exclude_testengine\" mode=\"ELIMINATE\" />"+
			//	"<Label name=\"high\" mode=\"BOOST\" weight=\"1.0\"/>" +
			//	"<Label name=\"medium\" mode=\"BOOST\" weight=\"0.8\"/>" +
			//	"<Label name=\"low\" mode=\"BOOST\" weight=\"0.1\"/>" +
				"</BackgroundLabels>"+
				"</Context>"+
				"<LookAndFeel nonprofit=\"true\" />"+
				"<ImageSearchSettings enable=\"true\" />"+
				"</CustomSearchEngine>";

		create_post.addRequestHeader("Content-type", "text/xml");
		create_post.addRequestHeader("Authorization", "GoogleLogin auth=" + authtoken);

		StringRequestEntity rq_en = new StringRequestEntity(new_annotation,"text/xml","UTF-8");
		create_post.setRequestEntity(rq_en);
//		PostMethod create_post = new PostMethod("http://www.google.com/cse/api/default/cse/testengine");

/*
		String new_annotation ="<CustomSearchEngine  language=\"en\">" +
				"<Title> CSE </Title>" + 
				"<Description>Custom Search</Description>" + 
				"<Context>" +
				"<BackgroundLabels>"+
				"<Label name=\"_cse_testengine\" mode=\"FILTER\" />"+
				"<Label name=\"_cse_exclude_testengine\" mode=\"ELIMINATE\" />"+
				"<Label name=\"high\" mode=\"BOOST\" weight=\"1.0\"/>" +
				"<Label name=\"medium\" mode=\"BOOST\" weight=\"0.8\"/>" +
				"<Label name=\"low\" mode=\"BOOST\" weight=\"0.1\"/>" +
				"</BackgroundLabels>"+
				"</Context>"+
				"<LookAndFeel nonprofit=\"false\" />"+
				"</CustomSearchEngine>";

		create_post.addRequestHeader("Content-type", "text/xml");
		create_post.addRequestHeader("Authorization", "GoogleLogin auth=" + authtoken);

		StringRequestEntity rq_en = new StringRequestEntity(new_annotation,"text/xml","UTF-8");
		create_post.setRequestEntity(rq_en);
*/

		int astatusCode = client.executeMethod(create_post);

		if (astatusCode == HttpStatus.SC_OK)
		{
			log.info ("CSE " + cseName + " created");
			//If successful, the CSE annotation is displayed in the response.
			log.info (create_post.getResponseBodyAsString());
		}
		else
			log.warn ("CSE " + cseName + " creation failed");
	}

	/** returns an oauth token for the user's CSE. returns null if login failed. */
	public static String authenticate(String login, String password)
	{
		//System.out.println("About to post\nURL: "+target+ "content: " + content);
		String authToken = null;
		String response = "";
		try {
			URL url = new URL("https://www.google.com/accounts/ClientLogin");
			URLConnection conn = url.openConnection();
			// Set connection parameters.
			conn.setDoInput (true);
			conn.setDoOutput (true);
			conn.setUseCaches (false);

			// Make server believe we are form data...

			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			DataOutputStream out = new DataOutputStream (conn.getOutputStream());
			// TODO: escape password, we'll fail if password has &
			// login = "musetestlogin1";
			// password = "whowonthelottery";
			String content = "accountType=HOSTED_OR_GOOGLE&Email="+login+"&Passwd=" + password + "&service=cprose&source=muse.chrome.extension";
			out.writeBytes(content);
			out.flush ();
			out.close ();
			
			// Read response from the input stream.
			BufferedReader in = new BufferedReader (new InputStreamReader(conn.getInputStream ()));
			String temp;
			while ((temp = in.readLine()) != null) {
				response += temp + "\n";
			}
			temp = null;
			in.close ();
			log.info ("Obtaining auth token: Server response:\n'" + response + "'");

			String delimiter = "Auth=";
			/* given string will be split by the argument delimiter provided. */
			String [] token = response.split(delimiter);
			authToken=token[1];
			authToken=authToken.trim();
			authToken = authToken.replaceAll("(\\r|\\n)", "");
			log.info("auth token: " + authToken);
			return authToken;
		}
		catch (Exception e) {
			log.warn("Unable to authorize: " + e.getMessage());
			return null;
		}
	}

	public static int posTop100(String weburl)
	{
		String domain = getDomainFromURL(weburl);

		for (int i = 0; i < top100.length; i++)
			if (domain.contains(top100[i]))
				return i;

		return 100;
	}
	
	/** recursively expands url shortened http links and returns the resolution, also as a http url. 
	 * Warning: shortened links are case-sensitive, do not pass in a canonicalized version! */
	public static String expandShortenedHttpURL (String httpURL)
	{
		while (true) // TOFIX: look out for possibility of circular loops in url expansion. haven't seen it happen so far.
		{
			String domain = getDomainFromURL(httpURL);

			if (!isShortURL(domain))
				break;

			String expandedURL = expandURL(httpURL);
			if (httpURL.equals(expandedURL))
				break;
			httpURL = expandedURL;
		}		
		return httpURL;
	}

	/** returns domain from given httpurl (must start with http://)
	 * removes www. from the beginning of the domain if its present
	 * e.g. http://www.stanford.edu/~hangal1 returns stanford.edu
	 */
	public static String getDomainFromURL(String httpurl)
	{
		String domain = null;
		try {
			URL url = new URL(httpurl);
			domain = url.getHost();
			if (domain.startsWith("www."))
				domain = domain.substring("www.".length()); // strip the "www."			

			return domain;
		} catch (MalformedURLException mfe) {
			log.warn ("Bad URL: " + httpurl);
		}
		return domain;
	}
	
	public static void expandShortenedURLs (List<LinkInfo> links)
	{
		for (LinkInfo li: links)
		{	
			String httpURL = li.link; 
			String domain = getDomainFromURL(httpURL);
			if (isShortURL(domain))
			{
				String s = expandShortenedHttpURL(li.originalLink);  // IMPORTANT! use originalLink here because expanders are case sensitive. e.g. bit.ly/SWres should not be looked up as bit.ly/swres				
				if (!Util.nullOrEmpty(s))
					li.link = s;
			}
		}
	}
	
	/** this should be our single point "url generalization" method -- it can become arbitrarily complicated in future.
	 * it takes in a url like http://cs.stanford.edu/~hangal and returns something like stanford.edu -- i.e. the result
	 * can be directly used in a CSE annotation. */
	public static String generalizeLink (String httpurl)
	{
		// CSE testing notes (sgh, nov 12/01)
		// in the annotation, 
		// www.stanford.edu => only returns web pages like stanford.edu/~hangal/ and www.stanford.edu/~lam, but not cs.stanford.edu pages
		// stanford.edu => returns the above as well as cs.stanford.edu and other subdomains of stanford.edu
		// therefore we should prefer the form "stanford.edu" in the CSE annotation
			return getDomainFromURL(httpurl);
	}
	
	public static List<String> linksToHttpURLs (List<LinkInfo> links) throws JSONException {
		List<String> httpURLs = new ArrayList<String>();
		String json = LinkInfo.linksToJson(links);
		
		JSONArray jsonarr = null;
		jsonarr = new JSONArray(json);
		
		for (int j = 0; j < jsonarr.length(); j++)
		{
			JSONObject linkInfo = (JSONObject)jsonarr.get(j);
			String httpurl = (String) linkInfo.get("url");
			if (!Util.nullOrEmpty(httpurl))
				httpURLs.add(httpurl);
		}
		return httpURLs;
	}

	/** checks if domain is valid. imp. since google rejects the entire xml if any domains are invalid */
	public static boolean isValidDomain(String d)
	{
		char forbiddenChars[] = new char[]{'[', ']', '(', ')', '*', '\\', '$', '%'}; 
		for (char c : forbiddenChars)
			if (d.indexOf(c) >= 0)
				return false;
				
		if (d.indexOf("..") > 0)
			return false;
		if (d.startsWith("."))
			return false;
		
		// a valid domain must have at least one '.'
		if (d.indexOf(".") < 0)
			return false;
		return true;
	}
	
	/** given a bunch of link infos, returns site weights.
	 * See http://code.google.com/apis/customsearch/docs/ranking.html for score/weight policy and syntax
	 * @throws JSONException 
	 * @throws ParseException */
	public static Map<String, Float> getDomainWeights (List<String> httpURLs) throws JSONException {
		Map<String, Integer> domainFreqMap = new LinkedHashMap<String, Integer>();
		
		// generalize httpurls
		for (String httpURL: httpURLs)
		{
			String domain = generalizeLink(httpURL);
			
			// check again if any shortened urls have snuck in. can happen if url-expander fails for any reason.
			// even if this happens, we do not want bit.ly's in our search engine!
			if (Util.nullOrEmpty(domain))
				continue;
			
			if (isShortURL(domain))
				continue;
			
			if (!isValidDomain(domain))
			{
				log.warn ("Ignoring bad domain: " + domain);
				continue;
			}
			
			Integer freq = domainFreqMap.get(domain);
			domainFreqMap.put(domain, (freq == null) ? 1 : freq + 1);			
		}
		
		// find high/low of freqs
		int high = Integer.MIN_VALUE, low = Integer.MAX_VALUE;		
		for (Integer count: domainFreqMap.values()) {
			if (count > high)
				high = count;
			if (count < low)
				low = count;
		}
		
		// now convert to domain -> weight map
		Map<String, Float> map = new LinkedHashMap<String, Float>();
		
		// convert counts to scores
		List<Pair<String, Integer>> sortedPairs = Util.sortMapByValue (domainFreqMap); // no real need to sort, but still...
		log.info(sortedPairs.size() + " annotations for CSE\n");
        for (Pair<String, Integer> p: sortedPairs) 
        {	
        	String domain = p.getFirst();
        	int count = p.getSecond();
        	log.info ("domain = " + domain + " count = " + count);
			float score = ((float) (count+1-low))/(high+1-low);
			// score is in the range of 0.0 to 1.0f
			// penalize top100 sites
			if (isTop100 (domain))
				score -= 1.0f;
			map.put (domain, score);
        }
        return map;
	}
	
	 /** returns # of domains uploaded, failed and not uploaded due to limits. */
	public static Triple<Integer,Integer, Integer> annotateCSEFromLinkInfos(String oauthToken, String cseName, List<LinkInfo> links) throws JSONException, IOException
	{
		initializeCSE(oauthToken, cseName);
		expandShortenedURLs(links); // this may already have been done, but doesn't hurt to do it again
		List<String> httpURLs = linksToHttpURLs(links);
		return annotateCSE (oauthToken, cseName, httpURLs);
	}
	
	/** given a list of http://... URLs, publishes them to the given cse name.
	 * does url expansion and weighting of needed.
	 * returns # of domains uploaded, failed and not uploaded due to limits. */
	public static Triple<Integer,Integer, Integer> annotateCSE(String oauthToken, String cseName, List<String> httpURLs) throws JSONException, HttpException, IOException
	{
		Map<String, Float> weightsMap = getDomainWeights(httpURLs);
		
		// delete existing annotations
		try {
			deleteCSE(oauthToken, cseName);
		} catch (Exception e)
		{
			log.warn ("Failed to delete existing annotations for search engine " + cseName);
			// soldier on even if delete failed
		}
			
		// now upload new annotations
		log.info ("Creating XML for " + weightsMap.size() + " domains");

		String xml = "<Batch><Add><Annotations>\n";	
		int count = 0, batch_count = 0;
		int uploaded = 0, failed = 0, notUploaded = 0;
		
		for (Iterator<String> it = weightsMap.keySet().iterator(); it.hasNext(); )
		{
			String domain = it.next();
			float score = weightsMap.get(domain);
			
			// populateCSE(oauthToken,domain,score); 
			String label;
			
			//change rating to 1.0 for all non top 100 sites
			
			if (!isTop100 (domain))
				score =1.0f;
			
			label = "<Annotation about=\"" + domain + "/* \" score=\"" + score + "\">";

//			String this_annotation = label + "<Label name=\"_cse_" + cseName + "\"/>" + "</Annotation>\n";
			String this_annotation = label + "<Label name=\"_cse_testengine\"/>" + "</Annotation>\n";
			xml += this_annotation;
			count++;
			batch_count++;

			// push out annotations if we are at the end of a batch, 
			// or no more domains, or we know we're above the google limit 
			if (batch_count == ANNOTATION_UPLOAD_BATCH_SIZE || !it.hasNext() || count > DOMAIN_LIMIT)
			{
				xml += "</Annotations></Add></Batch>";
				log.info("uploading xml"+xml);
				log.info ("Adding " + batch_count + " domains to the search engine " + cseName);
				boolean success = pushAnnotations (oauthToken, xml);
				
				// soldier on even in case of failure
				if (!success)
				{
					log.warn ("Pushing annotations to CSE " + cseName + " failed!!!");
					failed += batch_count;
				}
				else
					uploaded += batch_count;

				batch_count = 0;
				xml = "<Batch><Add><Annotations>\n";
			}
			
			if (count > DOMAIN_LIMIT)
			{
				log.warn ("Max # domains (" + DOMAIN_LIMIT + ") reached, remaining sites will be ignored");
				notUploaded = weightsMap.size() - count;
				break;
			}
		}
		return new Triple<Integer,Integer, Integer>(uploaded, failed, notUploaded);
	}
}