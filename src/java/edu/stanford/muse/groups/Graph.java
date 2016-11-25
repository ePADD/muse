package edu.stanford.muse.groups;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.util.Util;

/** class to converts to graph in d3's graph format */
public class Graph {
	private static Log log = LogFactory.getLog(Graph.class);
	private final static long serialVersionUID = 1L;

	class Edge { int source, target, value; }
	class Node { String name; int group; int size; }
	
	List<Edge> links = new ArrayList<Edge>();
	List<Node> nodes = new ArrayList<Node>();
	
	transient Map<String, Integer> nameToIdx = new LinkedHashMap<String, Integer>();
	transient Map<Integer, Map<Integer, Integer>> edgeMap = new LinkedHashMap<Integer, Map<Integer, Integer>>();
	transient int nNodes = 0;
	
	public void addNode (String a) { 
		nameToIdx.put(a, nNodes++);
		Node node = new Node();
		node.name = a;
		nodes.add(node);
	}
	
	public void bumpNodeSize(String a)
	{
		try {			
			// order so that i1 is always less than i2
			int i1 = nameToIdx.get(a);
			nodes.get(i1).size++;
		} catch (Exception e) { Util.print_exception (e, log); }
	}
	
	public void bumpWeight(String a, String b)
	{
		try {			
			// order so that i1 is always less than i2
			int i1 = nameToIdx.get(a);
			int i2 = nameToIdx.get(b);
			if (i1 < i2)
			{
				int tmp = i2; i2 = i1; i1 = tmp;
			}
			
			Map<Integer, Integer> map = edgeMap.get(i1);
			if (map == null)
			{
				map = new LinkedHashMap<Integer, Integer>();
				edgeMap.put (i1, map);
			}
			
			Integer I = map.get(i2);
			if (I == null)
				map.put(i2, 1);
			else
				map.put (i2, I+1);
		} catch (Exception e) { Util.print_exception (e, log); }
	}

	public void finalize(int threshold) {
		for (Integer i1: edgeMap.keySet())
		{
			Map<Integer, Integer> map = edgeMap.get(i1);
			for (Integer I2: map.keySet())
			{
				Integer weight = map.get(I2);
				if (weight < threshold)
					continue;
				Edge e = new Edge();
				e.source = i1; e.target = I2; e.value = weight; 
				links.add(e);
			}
		}
		log.info ("Graph finalized, has " + nodes.size() + " nodes and " + links.size() + " edges");
	}
}
