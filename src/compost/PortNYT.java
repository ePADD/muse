package edu.stanford.muse.importers;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

import com.nytlabs.corpus.NYTCorpusDocument;
import com.nytlabs.corpus.NYTCorpusDocumentParser;

import edu.stanford.muse.email.DocCache;
import edu.stanford.muse.email.JarDocCache;
import edu.stanford.muse.index.DatedDocument;
import edu.stanford.muse.index.NYTDoc;

/* creates headers */
public class PortNYT {

public static final String prefix = "nyt";

public static void main (String args[]) throws Exception
{
	if (args.length < 2)
	{
		System.err.println ("2 parameters needed: <file with a list of NYT article paths> <output dir>");
		return;
	}

	String inputFile = args[0];
	String outputDir = args[1];
	DocCache docCache = new JarDocCache(outputDir);

	File f = new File(outputDir);
	if (f.exists())
	{
		System.err.println ("Output directory already exists: " + outputDir);
		if (!f.canWrite())
		{
			System.err.println ("Unable to write to output directory: " + outputDir);
			return;
		}
	}
	else
	{
		boolean b = new File(outputDir).mkdirs();
		if (!b)
		{
			System.err.println ("Unable to create output directory: " + outputDir);
			return;
		}
	}

	LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new FileInputStream(inputFile)));
	int count = 0;
	/* input has lines like:
	/Users/hangal/NYT/data/1987/01/01/0000108.xml
	/Users/hangal/NYT/data/1987/01/01/0000114.xml
	/Users/hangal/NYT/data/1987/01/01/0000117.xml
	*/

	while (true) {
		String line = lnr.readLine();
		if (line == null)
			break;

		String file = line;
	    NYTCorpusDocumentParser p = new NYTCorpusDocumentParser();
	    NYTCorpusDocument doc = p.parseNYTCorpusDocumentFromFile(new File(file), false);

	    DatedDocument dd = new NYTDoc();

	    String subject = doc.getHeadline();
	    Date date = doc.getPublicationDate();
	    dd.description = subject;
	    dd.date = date;
	    dd.docNum = count;

	    String name = new File(file).getName();

	    // write body out to text file
		String body = doc.getBody();
	    StringWriter sw = new StringWriter();
	    PrintWriter pw = new PrintWriter(sw);
	    // often the body starts with LEAD: <first para> and then repeats the first para, so strip the lead
	    if (body == null)
	    	body = "";

	    if (body.startsWith("LEAD: "))
	    {
	    	int idx = body.indexOf("\n");
	    	if (idx >= 0 && (idx+1) < body.length())
	    		body = body.substring(idx+1);
	    }
	    pw.println (body);
	    pw.close();

	    // write header
	    // urls should have fwd slashes only
	    file = file.replaceAll("\\\\", "/");
	    dd.url = docCache.getContentURL(prefix, count);
	    docCache.saveHeader(dd, prefix, count);
	    docCache.saveContents(body, prefix, count);

	    count++;
	    if (count % 100 == 0)
	    	System.out.println ("Processed " + count + " article" + ((count > 1) ? "s": ""));
	}
	docCache.pack();
	System.out.println(count + " docs total");
}

}
