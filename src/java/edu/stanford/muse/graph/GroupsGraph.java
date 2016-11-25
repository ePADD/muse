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


import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.groups.SimilarGroup;
import edu.stanford.muse.util.Util;

/** Data structure for an undirected graph of groups of type T. A group can exist in the graph only once. */
public class GroupsGraph<T extends Comparable<? super T>> implements java.io.Serializable {
    private static Log log = LogFactory.getLog(GroupsGraph.class);

	private Map<SimilarGroup<T>,Node<SimilarGroup<T>>> allNodes; // maps a group to its node
	private Map<T, Set<Node<SimilarGroup<T>>>> elementToNodes; /** helps us tell quickly how many nodes an element is in */
	public String graphOutput = "";

	public GroupsGraph()
	{
	    allNodes = new LinkedHashMap<SimilarGroup<T>,Node<SimilarGroup<T>>>();
	    elementToNodes = new LinkedHashMap<T, Set<Node<SimilarGroup<T>>>>();
	}

	// simple util methods
	public boolean contains(SimilarGroup<T> g) { return allNodes.containsKey(g); }
	public Node<SimilarGroup<T>> lookup(SimilarGroup<T> g) { return allNodes.get(g); }
	public Set<Node<SimilarGroup<T>>> lookup(T t) { return elementToNodes.get(t); }
	public Collection<SimilarGroup<T>> getAllGroups() { return allNodes.keySet(); }
	public Collection<Node<SimilarGroup<T>>> getAllNodes() { return allNodes.values(); }
	public int size() { return allNodes.size(); }
	
	boolean cancelled = false;
	public void cancel() { cancelled = true; }
	
	/** replaces the existing node if payload already exists. only one payload item can be present in this graph */
	public void addNode(Node<SimilarGroup<T>> n)
	{
		allNodes.put(n.payload, n);
		for (T t: n.payload.elements)
		{
			Set<Node<SimilarGroup<T>>> nodes = elementToNodes.get(t);
			if (nodes == null)
			{
				nodes = new LinkedHashSet<Node<SimilarGroup<T>>>();
				elementToNodes.put(t, nodes);
			}
			nodes.add(n);
		}
	}

	public Node<SimilarGroup<T>> removeGroup(SimilarGroup<T> g) 
	{ 
		Node<SimilarGroup<T>> n = allNodes.remove(g);
		// update elementToNodes map for all the elements in the group
		for (T t: g.elements())
		{
			Set<Node<SimilarGroup<T>>> set = elementToNodes.get(t);
			set.remove(n);
		}
		return n;
	}

	public Node<SimilarGroup<T>> removeNode(Node<SimilarGroup<T>> g) 
	{ 
		return removeGroup(g.payload);
	}
	
	public int getNodeCount(T t)
	{
		Set<Node<SimilarGroup<T>>> nodes = elementToNodes.get(t);
		return (nodes == null) ? 0 : nodes.size();
	}

	/** sets up edges between each pair of groups that has at least one element in common.
	 * returns # of edges created. 
	 * input should already have been cleaned to remove hyper-connected elements etc. */
	public int setupEdges() 
	{
		log.info ("Setting up edges between " + allNodes.size() + " nodes");
		long timeStart = System.currentTimeMillis();
		int nEdges=0;
		cancelled = false;
		
		// n^2 operation: can be made more efficient in future, but not a problem right now
		for (Node<SimilarGroup<T>> n1: allNodes.values())
		{
			if (cancelled)
				break;
		
			SimilarGroup<T> sg1 = n1.payload ;
			for (Node<SimilarGroup<T>> n2: allNodes.values()) 
			{
				if (n1 == n2)
					continue;

				SimilarGroup<T> sg2 = n2.payload;

				boolean added = false;
				// find the set which shares the common elements with it
				// note that edges are set up even if 1 element is shared, though we may or may not consider intersects/merges if only one element is shared
				if (sg2.intersectionSize(sg1) != 0)
				{
					added = n2.addEdge(n1);
					if (added)
						nEdges++;
				}

				if (added && nEdges % 20000 == 0)
					log.info (nEdges + " edges added so far");
				}
		}

		long timeEnd = System.currentTimeMillis();
		log.info (nEdges + " edges set up: " + (timeEnd - timeStart) + " milliseconds");
		return nEdges;
	}

	public void verify()
	{
		for(Node<SimilarGroup<T>> n1: allNodes.values()) {
			Util.ASSERT(n1.equals(n1));
			SimilarGroup<T> g1 = n1.payload;
			Util.ASSERT(allNodes.get(g1) == n1);
		//     for all connected n2
			for(Node<SimilarGroup<T>> n2: n1.connectedNodes) {
				Util.ASSERT(n1.payload.intersectionSize(n2.payload) > 0);
				Util.ASSERT(allNodes.containsKey(n2.payload));
				Util.ASSERT (!n1.equals(n2));
			}
		}
	}
}
