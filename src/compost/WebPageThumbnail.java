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
package edu.stanford.muse.email;


import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.w3c.tidy.Tidy;
import org.xhtmlrenderer.simple.Graphics2DRenderer;

import edu.stanford.muse.datacache.Blob;
import edu.stanford.muse.datacache.BlobSet;
import edu.stanford.muse.datacache.BlobStore;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Util;
import edu.stanford.muse.index.DatedDocument;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.LinkInfo;

public class WebPageThumbnail extends Blob {

    private static Log log = LogFactory.getLog(EmailFetcherThread.class);

    private DatedDocument doc;

	public WebPageThumbnail(String name, DatedDocument doc)
	{
		this.filename = name;
		this.size = 12345; // dummy
		this.doc = doc;
		this.filename = name;
		this.modifiedDate = doc.date;
	}

	public int hashCode()
	{
		return filename.hashCode();
	}

	public boolean equals(Object o)
	{
		if (o == null || !(o instanceof WebPageThumbnail))
			return false;
		return filename.equals(((WebPageThumbnail) o).filename);
	}

	/** create a dataset and emit top level pages */
	public static void fetchWebPageThumbnails(String rootDir, String prefix, List<Document> allDocs, boolean archivedPages, int limit) throws IOException, JSONException
	{
		List<LinkInfo> links = EmailUtils.getLinksForDocs(allDocs);

		// compute map of url -> doc
		Map<String, List<Document>> fmap = new LinkedHashMap<String, List<Document>>();
		for (LinkInfo li : links)
		{
			List<Document> lis = fmap.get(li);
			if (lis == null)
			{
				lis = new ArrayList<Document>();
				fmap.put(li.link, lis);
			}
			lis.add(li.doc);
		}

		List<Blob> allDatas = new ArrayList<Blob>();
		BlobStore data_store = new BlobStore(rootDir + File.separator + "blobs");

		int successes = 0;
		outer:
		for (String url : fmap.keySet())
		{
			List<Document> lis = fmap.get(url);
			for (Document doc: lis)
			{
				if (doc instanceof DatedDocument)
				{
					String targetURL = url;
					DatedDocument dd = ((DatedDocument) doc);
					if (archivedPages)
					{
						Calendar c = new GregorianCalendar();
						c.setTime(((DatedDocument) doc).date);
						String archiveDate = c.get(Calendar.YEAR) + String.format("%02d", c.get(Calendar.MONTH)) + String.format ("%02d", c.get(Calendar.DATE)) + "120000";
						// color dates
						targetURL = "http://web.archive.org/" + archiveDate + "/" + url;
					}

					try {
						// the name is just the URL, so that different URLs have a different Data
						String sanitizedURL = Util.sanitizeFolderName(targetURL); // targetURL is not really a folder name, but all we want is to convert the '/' to __
						WebPageThumbnail wpt = new WebPageThumbnail(sanitizedURL + ".png", dd); /// 10K as a dummy ??
						boolean targetURLIsHTML = !(Util.is_image_filename(targetURL) || Util.is_office_document(targetURL) || Util.is_pdf_filename(targetURL));

						if (!data_store.contains(wpt))
						{
							// download the html page first
						    HttpClient client = new HttpClient();
							String tmpFile = File.createTempFile("webtn.", ".tmp").getAbsolutePath();
							GetMethod get = new GetMethod(targetURL);
						    int statusCode = client.executeMethod(get);
						    if (statusCode == 200)
						    {
						        // execute method and handle any error responses.
						        InputStream in = get.getResponseBodyAsStream();
						        // Process the data from the input stream.
						        Util.copy_stream_to_file(in, tmpFile);
						        get.releaseConnection();

						        if (targetURLIsHTML)
								{
							        // use jtidy to convert it to xhtml
									String tmpXHTMLFile = File.createTempFile("webtn.", ".xhtml").getAbsolutePath();
							        Tidy tidy = new Tidy(); // obtain a new Tidy instance
							        tidy.setXHTML(true); // set desired config options using tidy setters
							        InputStream is = new FileInputStream(tmpFile);
							        OutputStream os = new FileOutputStream(tmpXHTMLFile);
							        tidy.parse(is, os); // run tidy, providing an input and output stream
							        try { is.close(); } catch (Exception e) { }
							        try { os.close(); } catch (Exception e) { }

							        // use xhtmlrenderer to convert it to a png
									File pngFile = File.createTempFile("webtn.", ".png");
									BufferedImage buff = null;
									String xhtmlURL = new File(tmpXHTMLFile).toURI().toString();
									buff = Graphics2DRenderer.renderToImage(xhtmlURL, 640, 480);
									ImageIO.write(buff, "png", pngFile);
					                data_store.add(wpt, new FileInputStream(pngFile));
								}
								else
								{
									data_store.add(wpt, new FileInputStream(tmpFile));
									if (Util.is_pdf_filename(targetURL))
							        	data_store.generate_thumbnail(wpt);
								}
						        successes++;
						        if (successes == limit)
						        	break outer;
							}
						    else
						    	log.info("Unable to GET targetURL " + targetURL + " status code = " + statusCode);
						}

			            allDatas.add(wpt);
					} catch (Exception e) { log.warn(Util.stackTrace(e)); }
				      catch (Error e) { log.warn(Util.stackTrace(e)); }
				}

				if (!archivedPages)
					break; // only need to show one page if we're not showing archives
			}
		}
		BlobSet bs = new BlobSet(rootDir, allDatas, data_store);
		bs.generate_top_level_page(prefix);
	}

	public String toString()
	{
		return Util.fieldsToString(this);
	}
}
