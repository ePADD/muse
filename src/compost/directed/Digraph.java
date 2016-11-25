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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.UnionFindSet;
import edu.stanford.muse.util.Util;

public class Digraph<T> {
    public static Log log = LogFactory.getLog(Digraph.class);

public Map<T, DigraphNode<T>> allNodes;
public int nEdges;

public PrintStream out = System.out;

private void setOut(String file) throws FileNotFoundException
{
	out = new PrintStream(file);
}

public Digraph()
{
    allNodes = new LinkedHashMap<T, DigraphNode<T>>();
}

/**
 * adds a node to the Graph
 */
public DigraphNode<T> add (T t)
{
	DigraphNode<T> n = allNodes.get(t);
	if (n != null)
		return n;

	n = new DigraphNode<T>(t);
    allNodes.put (t, n);
    return n;
}

/**
 * verifies graph data structure consistency
 */
public void verify ()
{
    for (T t : allNodes.keySet())
    {
    	DigraphNode<T> n = allNodes.get(t);
    	Util.ASSERT (t.equals(n.payload));
        n.verify ();
    }
}

public String toString ()
{
    StringBuffer sb = new StringBuffer();
    sb.append ("Graph with " + allNodes.size() + " nodes\n");
    for (DigraphNode<T> n: allNodes.values())
        sb.append (n + "\n");

    return sb.toString();
}


public List<List<DigraphNode<T>>> findComponentSizes()
{
	UnionFindSet<DigraphNode<T>> ufs = new UnionFindSet<DigraphNode<T>>();
	for (DigraphNode<T> n: allNodes.values())
	{
		for (DigraphNode<T> n1: n.succNodes.keySet())
			ufs.unify(n, n1);
	}
	return ufs.getClassesSortedByClassSize();
}

public Map<Integer,Integer> componentSizeScatterPlot()
{
	Map<Integer, Integer> map = new LinkedHashMap<Integer, Integer>();
	List<List<DigraphNode<T>>> list = findComponentSizes();
	for (List<DigraphNode<T>> l : list)
	{
		int size = l.size();
		Integer x = map.get(size);
		if (x == null)
			map.put(size, 1);
		else
			map.put(size, x+1);
	}
	return map;
}


/** return outgoing degree distribution in the graph */
@SuppressWarnings("unchecked")
public List<Pair<Integer, Integer>> getDegreeDistribution()
{
	Map<Integer, Integer> map = new LinkedHashMap<Integer, Integer>();

	for (DigraphNode<T> n : allNodes.values())
	{
		int degree = n.succNodes.size();
		Integer nNodes = map.get(degree);
		if (nNodes == null)
			map.put(degree, 1);
		else
			map.put (degree, nNodes+1);
	}

	List<Pair<Integer, Integer>> result = new ArrayList<Pair<Integer, Integer>>();
	for (Map.Entry<Integer,Integer> e: map.entrySet())
		result.add(new Pair<Integer,Integer>(e.getKey(), e.getValue()));
	Util.sortPairsByFirstElement((List) result);
	return result;
}

public double clusteringCoeff()
{
	double sum = 0.0;
	for (DigraphNode<T> n: allNodes.values())
		sum += clusteringCoeff(n);
	return sum/allNodes.size();
}

public static double clusteringCoeff(DigraphNode<?> n)
{
	int triangles = 0;
	for (DigraphNode<?> succ1: n.succNodes.keySet())
		for (DigraphNode<?> succ2: n.succNodes.keySet())
			if (succ1.succNodes.keySet().contains(succ2))
			{
				Util.ASSERT (succ2.succNodes.keySet().contains(succ1)); // because undirected graph
				triangles++;
			}

	int size = n.succNodes.size();
	if (size <= 1)
		return 0.0;
	else
		return ((double) triangles)/(size *(size-1));
}

/** returns pairs, sorted by first element */
@SuppressWarnings({ "unchecked", "unused" })
private static List<Pair<Integer,Integer>> convertMapToPairs(Map<Integer, Integer> map)
{
	List<Pair<Integer, Integer>> result = new ArrayList<Pair<Integer, Integer>>();
	for (Map.Entry<Integer,Integer> e: map.entrySet())
		result.add(new Pair<Integer,Integer>(e.getKey(), e.getValue()));
	Util.sortPairsByFirstElement((List) result);
	return result;
}

public String stats()
{
	long nEdges = 0;
	for (DigraphNode<?> n: allNodes.values())
		nEdges += n.succNodes.size();
	nEdges /= 2;
	return ("Graph with " + allNodes.size() + " nodes and " + nEdges + " edges");
}

public void clearAllEdges()
{
	for (DigraphNode<?> n: allNodes.values())
	{
		n.succNodes.clear();
	}
}

public int getLargestComponentSize()
{
	Map<Integer, Integer> map = componentSizeScatterPlot();
	int maxSize = Integer.MIN_VALUE;
	for (Integer size: map.keySet())
		if (maxSize < size)
			maxSize = size;
	return maxSize;
}

@SuppressWarnings("unchecked")
public List<Triple<DigraphNode<?>, DigraphNode<?>,Integer>> sortedEdgesByCommonCoauthors()
{
	List<Triple<DigraphNode<?>,DigraphNode<?>,Integer>> triples = new ArrayList<Triple<DigraphNode<?>, DigraphNode<?>,Integer>>();

	for (DigraphNode<?> n: allNodes.values())
	{
		for (DigraphNode<?> n1: n.succNodes.keySet())
		{
			if (n1.id <= n.id)
				continue;
			int sharedNeighbours = 0;
			for (DigraphNode<?> n2: n.succNodes.keySet())
			{
				if (n2 == n1)
					continue;
				if (n1.succNodes.keySet().contains(n2))
					sharedNeighbours++;
			}
			triples.add(new Triple<DigraphNode<?>, DigraphNode<?>,Integer>(n, n1, sharedNeighbours));
		}
	}

	Util.sortTriplesByThirdElement((List) triples);
//	for (Triple<Node<?>,Node<?>,Integer> t : triples)
//		out.println ("Triple: " + t.getFirst() + " - " + t.getSecond() + " weight: " + t.getThird());
	return triples;
}

public String dump()
{
	Map<T, Integer> mapNum = new LinkedHashMap<T, Integer>();
	StringBuilder sb = new StringBuilder();
	sb.append ("nodedef>name INTEGER, label VARCHAR\n");
	int i = 0;
	for (T t: allNodes.keySet())
	{
		mapNum.put (t, i);
		String label = t.toString().replaceAll(",", " "); // .replaceAll("&", "_"); // .replaceAll("/", "_").replaceAll("\\", "_");
		sb.append (i + "," + label + "\n");
		i++;
	}

	sb.append ("edgedef>src INTEGER, dest INTEGER, weight double\n");

	for (T t: allNodes.keySet())
	{
		Map<DigraphNode<T>, Float> conns = allNodes.get(t).succNodes;
		if (conns == null)
			continue;

		for (DigraphNode<T> n1: conns.keySet())
		{
			T from = t;
			T to = n1.payload;
			float weight = conns.get(n1);
			sb.append (mapNum.get(from) + "," + mapNum.get(to) + "," + weight + "\n");
		}
	}
	return sb.toString();
}

public void addEdge (T t1, T t2, float weight)
{
	DigraphNode<T> n1 = add(t1);
	DigraphNode<T> n2 = add(t2);
	int orig_size = n1.succNodes.size();
	n1.succNodes.put(n2,  weight);
	if (n1.succNodes.size() > orig_size)
	{
		nEdges++;
		if (nEdges % 1000 == 0)
			System.out.println ("#edges = " + nEdges);
	}
}

/** input is a set of docid -> terms in the doc map 
 * @throws FileNotFoundException */
public static void doIt (Map<Integer, Collection<Collection<String>>> docMap, String outfile) throws FileNotFoundException
{
	// index stores for each term, count of how many times it co-occurs with another in a doc.
	Map<String, Map<String, Integer>> index = new LinkedHashMap<String, Map<String, Integer>>();
	Map<String, Integer> termFreq = new LinkedHashMap<String, Integer>();
	 
	// compute index
	for (Integer num: docMap.keySet())
	{
		Collection<Collection<String>> paras = docMap.get(num);
		
		for (Collection<String> paraNames: paras)
		{
			System.out.println (num + ". " + paraNames.size() + " names " + " prev index size " + index.size() + " term freq size " + termFreq.size());
			if (paraNames.size() > 100) 
			{
				log.warn ("skipping long para" + paraNames);
				continue;
			}
			
			for (String s: paraNames)
			{
				s = s.toLowerCase();
				// bump up term freq for this term
				Integer X = termFreq.get(s);
				termFreq.put (s, (X == null) ? 1:X+1);
	
				// bump up counts for co-occurring terms... 
				// unfortunately n^2 operation here
				for (String s1: paraNames)
				{
					if (s == s1)
						continue;
	
					Map<String, Integer> termMap = index.get(s);
					if (termMap == null)
					{
						// allocate termMap if this is the first time we've seen s
						termMap = new LinkedHashMap<String, Integer>(1);
						index.put(s, termMap);
					}
	
					// bump the count
					Integer I = termMap.get(s1);
					termMap.put(s1, (I == null) ? 1: I+1);
				}
			}
		}
	}
	
	// process index and store it as a graph structure

	Digraph<String> graph = new Digraph<String>();
	for (String term: index.keySet())
	{
		Map<String, Integer> map = index.get(term);
		if (map == null)
		{
			// no edges, just add it to the graph and continue
			graph.add(term); 
			continue;
		}
		
		// compute total co-occurrence across all other terms this term is associated with
		int total = 0;
		for (Integer x: map.values())
			total += x;
		// proportionately allocate weight
		for (String x: map.keySet())
			graph.addEdge(term, x, ((float) map.get(x)));
//		graph.addEdge(term, x, ((float) map.get(x))/total);
	}
	String s = graph.dump();
	PrintStream pw = new PrintStream(new FileOutputStream(outfile));
	pw.print(s);
	pw.close();
}

}