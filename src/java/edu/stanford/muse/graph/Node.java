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

import java.util.*;

import edu.stanford.muse.util.Util;


public class Node<T> {

	public T payload;
//	public List<Node<T>> succNodes;   //for directed graph
	public Set<Node<T>> connectedNodes; //for undirected graph
	// List<Float> weights;
	public int id;
	public float value;
	public List<Integer> createdBy = new ArrayList<Integer>();
	
	public void addCreatedByMove(int m)
	{
		createdBy.add(m);
	}
	
	public Node(T t, int id)
	{
		this.id = id;
		this.payload = t;
//		succNodes = new ArrayList<Node<T>>();
		connectedNodes = new LinkedHashSet<Node<T>>();
	}

	public Node(T t)
	{
		this.payload = t;
//		succNodes = new ArrayList<Node<T>>();
		connectedNodes = new LinkedHashSet<Node<T>>();
	}

	public void setValue(float val){
		this.value=val;
	}

//	public List<Node<T>> getSuccs() { return succNodes; }

	public Set<Node<T>> getConnect() { return connectedNodes; }

	public String toShortString() { return payload.toString(); }

	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("N" + id + ": value " + value + " " + connectedNodes.size() + " links ");
		sb.append(toShortString());
		return sb.toString();
	}

	/**
	 * adds an edge from this node to n and from n to this
	 */
	public boolean addEdge (Node<T> n)
	{
		Util.ASSERT (n != null);
		// duplicate edges are not allowed
//		Util.ASSERT (!this.succNodes.contains (n));
//		Util.ASSERT (!n.predNodes.contains (this));

		this.connectedNodes.add (n);
		return n.connectedNodes.add(this);
	}

	/**
	 * adds an edge from this node to n
	 */
	public void deleteEdge (Node<?> n)
	{
		Util.ASSERT (n != null);
		// duplicate edges are not allowed
		//Util.ASSERT (this.connectedNodes.contains (n));

		this.connectedNodes.remove (n);
		n.connectedNodes.remove(this);
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
		Node<T> n = (Node<T>) o;
		return this.payload.equals(n.payload);
	}
}
