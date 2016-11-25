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
package edu.stanford.muse.groups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SimilarSuperGroup<T extends Comparable<? super T>> extends SimilarGroup<T> {

	public List<SimilarGroup<T>> subgroups; // the component group this group is a union of
	
	public SimilarSuperGroup(SimilarGroup<T> g1, SimilarGroup<T> g2) 
	{
		// TODO Auto-generated constructor stub
		subgroups = new ArrayList<SimilarGroup<T>>();
		subgroups.add(g1);
		subgroups.add(g2);
		
		Set<T> set = new LinkedHashSet<T>();

		set.addAll(g1.elements);
		set.addAll(g2.elements);
		elements = new ArrayList<T>();
		elements.addAll(set);
		Collections.sort(elements);
	}

}
