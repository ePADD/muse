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
package edu.stanford.muse.index;

import com.nytlabs.corpus.NYTCorpusDocumentParser;

import edu.stanford.muse.exceptions.ReadContentsException;

public class NYTDoc extends DatedDocument {
    private final static NYTCorpusDocumentParser parser = new NYTCorpusDocumentParser();
	private final static long serialVersionUID = 1L;

	@Override
	public String getContents() throws ReadContentsException
	{
		return super.getContents();
/*
	    NYTCorpusDocument doc = parser.parseNYTCorpusDocumentFromFile(new File(new URI(url)), false);
	    String body = doc.getBody();
	    if (body == null)
	    	body = "";
	    if (body.startsWith("LEAD: "))
	    {
	    	int idx = body.indexOf("\n");
	    	if (idx >= 0 && (idx+1) < body.length())
	    		body = body.substring(idx+1);
	    }
	    return body;
	    */
	}
}
