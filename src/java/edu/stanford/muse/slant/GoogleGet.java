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
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleGet
{
	public static String get ( String urlstring )  {
		StringBuffer sBuffer;
		sBuffer = new StringBuffer();
		try {
			System.out.println(urlstring);
			URL url;
			URLConnection conn;
			if(urlstring.indexOf("[")>0)
			{
				int question=urlstring.indexOf("=");
				String host=urlstring.substring(0, question+1);
				String param=urlstring.substring(question+1, urlstring.length());
				param=java.net.URLEncoder.encode( param, "ISO-8859-1");
				String encodedstring = host+param;
				System.out.println("encoded string is :"+ encodedstring);
				url = new URL(encodedstring);
				conn =  url.openConnection();
				conn.setRequestProperty("User-Agent",
						"Mozilla/5.0 (X11; U; Linux x86_64; en-GB; rv:1.8.1.6) Gecko/20070723 Iceweasel/2.0.0.6 (Debian-2.0.0.6-0etch1)"); 

			}
			else
			{   
				url = new URL(urlstring);
				conn =  url.openConnection();
				conn.setRequestProperty("User-Agent",
						"Mozilla/5.0 (X11; U; Linux x86_64; en-GB; rv:1.8.1.6) Gecko/20070723 Iceweasel/2.0.0.6 (Debian-2.0.0.6-0etch1)");
			}




			BufferedReader in = new BufferedReader(
					new InputStreamReader(conn.getInputStream(), "UTF-8")
					);
			String str;

			int i=0;
			while (true) {
				str = in.readLine();
				if(str==null&&i>20)
					break;
				//	System.out.println(str);
				if(str!=null)
					sBuffer.append(str);
				i++;
			}

			in.close();
			String content=sBuffer.toString();
			//System.out.println(content);
			ArrayList <String> al=pullLinks(content);
			
			ListIterator<String> litr = al.listIterator();
		    while (litr.hasNext()) {
		      String element = litr.next();
		     // System.out.println(element);
		      
		      // throw out http://www.google.com/intl/en/images/
		    }

		}
		catch (Exception e) {e.printStackTrace();}



		return sBuffer.toString(); 

		//add some logging for result comparison





	}


	private static ArrayList<String> pullLinks(String text) {
		ArrayList links = new ArrayList();

		String regex = "\\(?\\b(http://|www[.])[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(text);
		while(m.find()) {
			String urlStr = m.group();
			if (urlStr.startsWith("(") && urlStr.endsWith(")"))
			{
				urlStr = urlStr.substring(1, urlStr.length() - 1);
			}
			links.add(urlStr);
		}
		return links;
	}
}
