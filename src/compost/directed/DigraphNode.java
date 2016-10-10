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
package edu.stanford.muse.graph.directed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.muse.util.Util;

public class DigraphNode<T> {

	public T payload;
	public Map<DigraphNode<T>, Float> succNodes;   //for directed graph
//	public Set<Node<T>> connectedNodes; //for undirected graph
	// List<Float> weights;
	public int id;
	public float value;
	public List<Integer> createdBy = new ArrayList<Integer>();
	
	public void addCreatedByMove(int m)
	{
		createdBy.add(m);
	}
	
	public DigraphNode(T t, int id)
	{
		this.id = id;
		this.payload = t;
		succNodes = new LinkedHashMap<DigraphNode<T>, Float>(1);
		//connectedNodes = new LinkedHashSet<Node<T>>();
	}

	public DigraphNode(T t)
	{
		this.payload = t;
		succNodes = new LinkedHashMap<DigraphNode<T>, Float>(1);
		//connectedNodes = new LinkedHashSet<Node<T>>();
	}

	public void setValue(float val){
		this.value = val;
	}

	public Map<DigraphNode<T>, Float> getSuccs() { return succNodes; }

//	public Set<Node<T>> getConnect() { return connectedNodes; }

	public String toShortString() { return payload.toString(); }

	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("N" + id + ": value " + value + " " + succNodes.size() + " links ");
		sb.append(toShortString());
		return sb.toString();
	}

	/**
	 * adds an edge from this node to n and from n to this
	 */
	public void addEdge (DigraphNode<T> n, float weight)
	{
		Util.ASSERT (n != null);
		// duplicate edges are not allowed
//		Util.ASSERT (!this.succNodes.contains (n));
//		Util.ASSERT (!n.predNodes.contains (this));

		succNodes.put (n, weight);
	}

	/**
	 * verify the node data structure
	 */
	public void verify ()
	{
	}

	// simplistic hashcode/equals right now - it just passes on to the payload which may not be appropriate if we also want to look at edges
	public int hashCode() { return payload.hashCode(); }
	public boolean equals(Object o)
	{
		DigraphNode<T> n = (DigraphNode<T>) o;
		return this.payload.equals(n.payload);
	}
}
