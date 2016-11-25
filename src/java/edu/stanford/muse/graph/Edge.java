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
package edu.stanford.muse.graph;

import edu.stanford.muse.groups.SimilarGroup;


public class Edge<T extends Comparable<? super T>> {
	Node<SimilarGroup<T>> n1,n2;
//	float value;

	public Edge(Node<SimilarGroup<T>> n1,Node<SimilarGroup<T>> n2){
		this.n1=n1;
		this.n2=n2;
	}
}
