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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import edu.stanford.muse.index.Document;
import edu.stanford.muse.util.UnionFindSet;

/* similar documents matcher, based on shingles */
public class SimilarityMatcher {

	private static final int SHINGLE_LENGTH = 9;
	private static final boolean IGNORE_QUOTED_MESSAGES = true;
	private static final float SIMILARITY_THRESHOLD = 0.8f;
	public List<Document> docs = new ArrayList<Document>();

	public String preprocessDocument(String docText) throws IOException
	{
		StringBuilder result = new StringBuilder();

		BufferedReader br = new BufferedReader(new StringReader(docText));
		while (true)
		{
			String line = br.readLine();
			if (line == null)
				break;
			line = line.trim();

			if (IGNORE_QUOTED_MESSAGES && line.startsWith(">")) // ignore quoted messages
				continue;

			StringTokenizer st = new StringTokenizer(line);
			while (st.hasMoreTokens())
			{
				result.append (st.nextToken());
				result.append (" ");
			}
		}

		return result.toString();
	}

	public List<List<Integer>> readDocsAndFindSimilarities(List<Document> c) throws Exception
	{
		int shingles[][] = new int[c.size()][];
		int i = 0;
		for (Document d: c)
		{
			int x[] = computeShingles (d.getContents(), d);
			shingles[i++] = x;
		}
//		findSimilarDocs(shingles);
		List<List<Integer>> list = findSimilarDocsWithMinHash(shingles);
		return list;
	}

	public List<List<Integer>> findSimilarDocsWithMinHash(int shingles[][])
	{
		UnionFindSet<Integer> ufs = new UnionFindSet<Integer>();
		int M[][] = new MinHash(shingles).computeMinHashColumns();
		for (int i = 0; i < M.length; i++)
		{
			for (int j = i+1; j < M.length; j++)
			{
				float sim = computeSimilarityMinHash(M[i], M[j]);
				if (sim > SIMILARITY_THRESHOLD)
				{
					ufs.unify(i, j);
					System.out.println ("Columns " + i + " and " + j + " sim: " + sim);
				}
			}
		}
		return ufs.getClassesSortedByClassSize();
	}

	// compute similarity of 2 minhash columns
	public float computeSimilarityMinHash(int a[], int b[])
	{
		int score = 0;
		for (int i = 0; i < a.length; i++)
		{
			if (a[i] == b[i])
				score++;
		}
		return ((float) score)/a.length;
	}

	// returns shingles for docs
	public int[] computeShingles(String documentText, Document doc) throws IOException
	{
		List<Integer> shingles = new ArrayList<Integer>();
		documentText = preprocessDocument(documentText);
		for (int idx = 0; true; idx++)
		{
			int endIdx = idx+9;
			if (endIdx > documentText.length())
				break;
			String shingle = documentText.substring(idx, idx+SHINGLE_LENGTH);
			int hash = shingle.toLowerCase().hashCode();
			shingles.add(hash);
		}

		// for empty docs, we don't want to have any shingles at all
		if (shingles.size() == 0 && documentText.length() > 0)
			shingles.add(documentText.toLowerCase().hashCode());

		// convert list to an array
		int[] shingleArray = new int[shingles.size()];
		int i = 0;
		for (Integer s: shingles)
			shingleArray[i++] = s;

		return shingleArray;
	}

	public List<List<Integer>> findSimilarDocs(int[][] shingles)
	{
		UnionFindSet<Integer> ufs = new UnionFindSet<Integer>();

		int ndocs = shingles.length;
		float[][] similarity = new float[ndocs][ndocs];
		Set<Integer> shingleSets[] = new LinkedHashSet[ndocs];
		for (int i = 0; i < ndocs; i++)
		{
			shingleSets[i] = new LinkedHashSet<Integer>();
			for (int x: shingles[i])
				shingleSets[i].add(x);
		}

		for (int i = 0; i < ndocs; i++)
			for (int j = i+1; j < ndocs; j++)
			{
				float sim = computeSimilarity(shingleSets[i], shingleSets[j]);
				similarity[i][j] = sim;
				if (sim > SIMILARITY_THRESHOLD)
				{
					System.out.println ("docs " + i + " and " + j + " are similar with score "+ sim);
					ufs.unify(i, j);
				}
			}

		return ufs.getClassesSortedByClassSize();
	}

	public float computeSimilarity(Set<Integer> a, Set<Integer> b)
	{
		Set<Integer> aClone = new LinkedHashSet<Integer>();
		aClone.addAll(a);
		int asize = aClone.size();
		int bsize = b.size();
		aClone.retainAll(b);
		int intersectionSize = aClone.size();
		// jaccard sim
		float sim = (intersectionSize)/(asize + bsize - intersectionSize);
		return sim;
	}


}
