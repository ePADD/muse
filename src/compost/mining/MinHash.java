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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MinHash {

	private static final int N_HASH_FUNCTIONS = 100;

	private final Random r = new Random(0);
	int shingles[][];
	private int nDocs;
	private int M[][]; // min hash vector
	List<Integer> sortedUniqueShingles;
	Map<Integer,Set<Integer>> map = new LinkedHashMap<Integer,Set<Integer>>();

	public MinHash(int shingles[][])
	{
		this.shingles = shingles;
		nDocs = shingles.length;
		int nShingles = 0;
		M = new int[nDocs][N_HASH_FUNCTIONS];
		for (int h = 0; h < N_HASH_FUNCTIONS; h++)
			for (int doc = 0; doc < nDocs; doc++)
				M[doc][h] = Integer.MAX_VALUE; // note: empty docs will all match with each other!
			
		Set<Integer> shingleSet = new LinkedHashSet<Integer>();
		for (int doc = 0; doc < nDocs; doc++)
		{
			nShingles += shingles[doc].length;
			for (int shingle: shingles[doc])
			{
				shingleSet.add(shingle);
				Set<Integer> set = map.get(shingle);
				if (set == null)
				{
					set = new LinkedHashSet<Integer>();
					map.put(shingle, set);
				}
				set.add(doc);
			}
		}

		sortedUniqueShingles = new ArrayList<Integer>();
		sortedUniqueShingles.addAll(shingleSet);		
		Collections.sort(sortedUniqueShingles);		
		System.out.println ("Total shingles " + nShingles);
		System.out.println ("Sorted " + sortedUniqueShingles.size() + " unique shingles");
	}

	public int[][] computeMinHashColumns()
	{
		int[] hash = new int[N_HASH_FUNCTIONS]; // running hash function 
		for (int shingle : sortedUniqueShingles)
		{
			Set<Integer> docSet = map.get(shingle);
			// docset is the set of docs with shingle
			for (int h = 0; h < N_HASH_FUNCTIONS; h++)
			{
				hash[h] = r.nextInt();
				for (Integer doc : docSet)
					if (hash[h] < M[doc][h])
						M[doc][h] = hash[h];
			}
		}
		return M;
	}
}
