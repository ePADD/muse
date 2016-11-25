package edu.stanford.muse.test;

import edu.stanford.muse.graph.Graph;
import edu.stanford.muse.graph.Node;

public class t1 {

	public static final int N = 10;
	Graph<Integer> g;
	
	public t1() { }
	private void buildGraph() {
		g = new Graph<Integer>();
		Node<Integer> nodes[] = new Node[N];
		for (int i = 0; i < N; i++) {
			nodes[i] = new Node<Integer>(i);
			g.add(nodes[i]);
		}
		nodes[0].addEdge(nodes[1]);
		nodes[0].addEdge(nodes[3]);
		nodes[1].addEdge(nodes[2]);
		nodes[1].addEdge(nodes[3]);
		nodes[1].addEdge(nodes[4]);
		nodes[2].addEdge(nodes[4]);
		nodes[3].addEdge(nodes[4]);
		nodes[3].addEdge(nodes[5]);
		nodes[4].addEdge(nodes[6]);
		nodes[4].addEdge(nodes[5]);
		nodes[5].addEdge(nodes[6]);
	}
	
	private static void doMST()	{
		
	}
	
	public static void main (String args[])
	{
		t1 t = new t1();
		
	}
}
