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
package edu.stanford.muse.mining;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.index.IndexOptions;
import edu.stanford.muse.util.EmailUtils;

public class SimilarDocs {
	IndexOptions indexOptions;
	
	public static void main (String args[]) throws Exception
	{
		if (args.length == 0)
		{
			System.out.println ("Usage: IndexEmail.main <prefix of docs to analyze> <prefix of output files> <stop words> <dict words> (optional)");
			return;
		}

		SimilarDocs sd = new SimilarDocs();
		sd.indexOptions = new IndexOptions();
		sd.indexOptions.parseArgs(args);
		List<EmailDocument> allDocs = EmailUtils.findAllDocs(sd.indexOptions.inputPrefixes);
		sd.run((List) allDocs);
	}
	
	public List<List<Document>> run(List<Document> allDocs) throws Exception
	{		
		// get similarity matching in terms of indices first
		List<List<Integer>> resultInts = new SimilarityMatcher().readDocsAndFindSimilarities(allDocs);

		// now convert indices back to documents
		List<List<Document>> result = new ArrayList<List<Document>>();
		for (List<Integer> list : resultInts)
		{
			// assemble all the email docs corresponding to this equiv. classes into tmp
			List<Document> tmp = new ArrayList<Document>();
			for (Integer i : list)
				tmp.add(allDocs.get(i));
			result.add(tmp);
		}
		return result;
	}
}
