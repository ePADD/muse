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
package edu.stanford.muse.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UnionFindSet<T> {

	private Map<T, UnionFindBox<T>> map = new LinkedHashMap<T, UnionFindBox<T>>();
		
	private UnionFindBox<T> createBox(T element)
	{
		UnionFindBox<T> box = new UnionFindBox<T>(element);
		map.put(element, box);
		return box;
	}
	
	public void unify(T a, T b)
	{
		UnionFindBox<T> boxA = map.get(a);
		if (boxA == null)
			boxA = createBox(a);
		UnionFindBox<T> boxB = map.get(b);
		if (boxB == null)
			boxB = createBox(b);
		boxA.unify(boxB);	
	}

	private List<List<T>> getClasses()
	{
		// assign cluster #s
		int numClasses = UnionFindBox.assignClassNumbers(map.values());

		// allocate result arrays
		List<List<T>> result = new ArrayList<List<T>>();
		for (int i = 0; i < numClasses; i++)
			result.add(new ArrayList<T>());
		
		// populate result arrays based on class numbers of the elements
		for (T t : map.keySet())
		{
			UnionFindBox<T> box = map.get(t);
			result.get(box.classNum).add(t); 
		}

		return result;
	}
	
	public List<List<T>> getClassesSortedByClassSize()
	{
		List<List<T>> result = getClasses();
		Collections.sort (result, new Comparator<List>() { 
			public int compare (List l1, List l2)
			{
				return l2.size() - l1.size();
			}
		});
		
		return result;
	}
}
