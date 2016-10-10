/*
 Copyright 2011 Sudheendra Hangal

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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Triple;
import edu.stanford.muse.util.UnionFindSet;
import edu.stanford.muse.util.Util;

public class Graph<T> {

public Set<Node<T>> allNodes;
private Map<T,Node<T>> nodeMap = new LinkedHashMap<T,Node<T>>();

public PrintStream out = System.out;

private void setOut(String file) throws FileNotFoundException
{
	out = new PrintStream(file);
}

public Graph()
{
    allNodes = new LinkedHashSet<Node<T>>();
}

/**
 * adds a node to the Graph
 */
public void add (Node<T> n)
{
    Util.ASSERT (!allNodes.contains(n));
    allNodes.add (n);
}

/**
 * verifies graph data structure consistency
 */
public void verify ()
{
    for (Node<T> n : allNodes)
        n.verify ();

}

public String toString ()
{
    StringBuffer sb = new StringBuffer();
    sb.append ("Graph with " + allNodes.size() + " nodes\n");
    for (Node<T> n: allNodes)
        sb.append (n + "\n");

    return sb.toString();
}

public static Graph<String> parse(String file) throws IOException
{
	LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new FileInputStream(file)));
	Graph<String> graph = new Graph<String>();

	int id = 0;

	while (true)
	{
		String line = lnr.readLine();
		if (line == null)
			break;
		StringTokenizer st = new StringTokenizer(line, ",");
		st.nextToken();st.nextToken(); st.nextToken();
		StringTokenizer st1 = new StringTokenizer(st.nextToken(), "&");
		List<Node<String>> authorList = new ArrayList<Node<String>>();
		System.out.print ("L" + lnr.getLineNumber() + ". ");
		while (st1.hasMoreTokens())
		{
			String author = st1.nextToken().trim();
			System.out.print (author + ". ");
			Node<String> authorNode = graph.nodeMap.get(author);
			if (authorNode == null)
			{
				authorNode = new Node<String>(author, id++);
				graph.nodeMap.put(author, authorNode);
				graph.add(authorNode);
			}
			authorList.add(authorNode);
		}

		for (int i = 0; i < authorList.size(); i++)
			for (int j = i+1; j < authorList.size(); j++)
			{
				Node<String> author1 = authorList.get(i);
				Node<String> author2 = authorList.get(j);
				if (author1.equals(author2))
				{
					System.out.print ("WARNING: Same author, dropping this edge: " + author1);
					continue;
				}
				authorList.get(i).addEdge(authorList.get(j));
				authorList.get(j).addEdge(authorList.get(i));
			}
		System.out.println();
	}

	graph.verify();

	return graph;
}

public List<List<Node<T>>> findComponentSizes()
{
	UnionFindSet<Node<T>> ufs = new UnionFindSet<Node<T>>();
	for (Node<T> n: allNodes)
	{
		for (Node<T> n1: n.succNodes)
			ufs.unify(n, n1);
	}
	return ufs.getClassesSortedByClassSize();
}

public Map<Integer,Integer> componentSizeScatterPlot()
{
	Map<Integer, Integer> map = new LinkedHashMap<Integer, Integer>();
	List<List<Node<T>>> list = findComponentSizes();
	for (List<Node<T>> l : list)
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

	for (Node<T> n : allNodes)
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
	for (Node<T> n: allNodes)
		sum += clusteringCoeff(n);
	return sum/allNodes.size();
}

public static double clusteringCoeff(Node<?> n)
{
	int triangles = 0;
	for (Node<?> succ1: n.succNodes)
		for (Node<?> succ2: n.succNodes)
			if (succ1.succNodes.contains(succ2))
			{
				Util.ASSERT (succ2.succNodes.contains(succ1)); // because undirected graph
				triangles++;
			}

	int size = n.succNodes.size();
	if (size <= 1)
		return 0.0;
	else
		return ((double) triangles)/(size *(size-1));
}

public static int[] generateRandomPermutation(int n)
{
	Random rng = new Random(0);

	int a[] = new int[n];
	for (int i = 0; i < n; i++)
		a[i] = i;

	for (int i = 0; i < n-1; i++)
	{
		int r = (rng.nextInt() & 0x7fffffff) % (n-i);
		// swap a[i+r] with a[i]
		int tmp = a[i+r];
		a[i+r] = a[i];
		a[i] = tmp;
	}
	return a;
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
	for (Node<?> n: allNodes)
		nEdges += n.succNodes.size();
	nEdges /= 2;
	return ("Graph with " + allNodes.size() + " nodes and " + nEdges + " edges");
}

public void clearAllEdges()
{
	for (Node<?> n: allNodes)
	{
		n.succNodes.clear();
	}
}

public void randomizeGraphEdges()
{
	Map<Integer, Node<T>> spokeMap = new LinkedHashMap<Integer,Node<T>>();
	int spokeNum = 0;
	for (Node<T> n: allNodes)
	{
		for (Node<T> succ: n.succNodes)
		{
			Util.ASSERT(!n.equals(succ));
			spokeMap.put(spokeNum++, succ);
		}
	}

	int nSpokes = spokeNum;
	Util.ASSERT (nSpokes%2 == 0);

	clearAllEdges();

	int[] permutation = generateRandomPermutation(nSpokes);
	int selfLoops = 0;
	int doubleEdges =0;
	for (int i = 0; i < permutation.length; i+=2)
	{
		int spokeFrom = permutation[i];
		int spokeTo = permutation[i+1];
		Node<T> from = spokeMap.get(spokeFrom);
		Node<T> to = spokeMap.get(spokeTo);
		if (from != to)
		{
			boolean b1 = from.succNodes.contains(to);
			boolean b2 = to.succNodes.contains(from);
			Util.ASSERT(b1 == b2);
			if (b1)
			{
				doubleEdges++;
				continue;
			}
			from.addEdge(to);
			to.addEdge(from);
		}
		else
			selfLoops++;
	}

	System.out.println ("Random network: # of self loops = " + selfLoops + " duplicated edges = " + doubleEdges);
}

private void rewireNetwork()
{
	randomizeGraphEdges();
	out.println ("Rewired network: Scatterplot of log(degree) : log (#nodes with that degree)");
	List<Pair<Integer, Integer>> degrees = getDegreeDistribution();

	for (Pair<Integer, Integer> p: degrees)
	{
		int x = p.getFirst();
		int nodes = p.getSecond();
		Util.ASSERT (nodes > 0);
		double log = Math.log (x+1);

		out.println (log + " " + Math.log(nodes+1));
	}

	System.out.println (stats());
	System.out.println ("Rewired network: Clustering coeff = " + clusteringCoeff());
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

private void plotMaxComponentSizeWithDeletedEdges(List<Triple<Node<?>, Node<?>,Integer>> weightedEdges)
{
	System.out.println ("plotting percentile edges deleted : max component size");

	int nDivs = 100;
	int quantum = weightedEdges.size()/nDivs;

	int edgesRemoved = 0;
	for (Triple<Node<?>, Node<?>,Integer> t: weightedEdges)
	{
//		out.println ("Deleting edge: " + t.getFirst() + " - " + t.getSecond());
		t.getFirst().deleteEdge(t.getSecond());
		t.getSecond().deleteEdge(t.getFirst());
		edgesRemoved++;
		if (edgesRemoved%quantum == 0)
		{
			int size = getLargestComponentSize();
			out.println ((edgesRemoved/quantum) + "\t" + size);
		}
	}

	System.out.println (stats());
	System.out.println ("Clustering coeff = " + clusteringCoeff());
}

@SuppressWarnings("unchecked")
public List<Triple<Node<?>, Node<?>,Integer>> sortedEdgesByCommonCoauthors()
{
	List<Triple<Node<?>,Node<?>,Integer>> triples = new ArrayList<Triple<Node<?>, Node<?>,Integer>>();

	for (Node<?> n: allNodes)
	{
		for (Node<?> n1: n.succNodes)
		{
			if (n1.id <= n.id)
				continue;
			int sharedNeighbours = 0;
			for (Node<?> n2: n.succNodes)
			{
				if (n2 == n1)
					continue;
				if (n1.succNodes.contains(n2))
					sharedNeighbours++;
			}
			triples.add(new Triple<Node<?>, Node<?>,Integer>(n, n1, sharedNeighbours));
		}
	}

	Util.sortTriplesByThirdElement((List) triples);
//	for (Triple<Node<?>,Node<?>,Integer> t : triples)
//		out.println ("Triple: " + t.getFirst() + " - " + t.getSecond() + " weight: " + t.getThird());
	return triples;
}

@SuppressWarnings("unchecked")
public List<Triple<Node<?>, Node<?>,Integer>> sortedEdgesByCoauthorFrequency(String file) throws IOException
{
	LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new FileInputStream(file)));

	Map<Pair<Node<String>, Node<String>>, Integer> edgeMap = new LinkedHashMap<Pair<Node<String>, Node<String>>, Integer>();
	while (true)
	{
		String line = lnr.readLine();
		if (line == null)
			break;
		StringTokenizer st = new StringTokenizer(line, ",");
		st.nextToken();st.nextToken(); st.nextToken();
		StringTokenizer st1 = new StringTokenizer(st.nextToken(), "&");
		List<Node<String>> authorList = new ArrayList<Node<String>>();
		System.out.print ("L" + lnr.getLineNumber() + ". ");
		while (st1.hasMoreTokens())
		{
			String author = st1.nextToken().trim();
			System.out.print (author + ". ");
			Node<String> authorNode = (Node<String>) this.nodeMap.get(author);
			Util.ASSERT (authorNode != null);
			authorList.add(authorNode);
		}
		System.out.println();

		for (int i = 0; i < authorList.size(); i++)
			for (int j = 0; j < authorList.size(); j++)
			{
				Node<String> author1 = authorList.get(i);
				Node<String> author2 = authorList.get(j);
				if (author2.id <= author1.id)
					continue;
				Pair<Node<String>, Node<String>> edge = new Pair<Node<String>, Node<String>>(author1, author2);

				Integer I = edgeMap.get(edge);
				if (I == null)
					edgeMap.put(edge, 1);
				else
					edgeMap.put(edge, I+1);
			}
	}


	List<Triple<Node<?>, Node<?>,Integer>> triples = new ArrayList<Triple<Node<?>, Node<?>,Integer>>();
	for (Pair<Node<String>, Node<String>> edge: edgeMap.keySet())
		triples.add(new Triple<Node<?>, Node<?>,Integer>(edge.getFirst(), edge.getSecond(), edgeMap.get(edge)));

	Util.sortTriplesByThirdElement((List) triples);
//	for (Triple<Node<?>,Node<?>,Integer> t : triples)
//		out.println ("Triple: " + t.getFirst() + " - " + t.getSecond() + " weight: " + t.getThird());

	return triples;
}

public static void main (String args[]) throws IOException
{
	Graph<String> g = parse(args[0]);
	System.out.println(g);

	System.out.println ("___________________________________________________________________________________________");

	g.setOut("1.1a");
	System.out.println ("Scatterplot of degree : #nodes with that degree");
	List<Pair<Integer, Integer>> degrees = g.getDegreeDistribution();
	for (Pair<Integer, Integer> p: degrees)
		g.out.println (p.getFirst() + "\t" + p.getSecond());
	g.out.close();

	g.setOut("1.1b");
	System.out.println ("___________________________________________________________________________________________");
	System.out.println ("Scatterplot of log(degree) : log (#nodes with that degree)");
	for (Pair<Integer, Integer> p: degrees)
	{
		int x = p.getFirst();
		int nodes = p.getSecond();
		Util.ASSERT (nodes > 0);
		double log = Math.log (x+1);

		g.out.println (log + "\t" + Math.log(nodes+1));
	}
	g.out.close();

	int i = 0;
	System.out.println (g.stats());
	List<List<Node<String>>> list = g.findComponentSizes();
	System.out.println ("___________________________________________________________________________________________");
	for (List<Node<String>> l : list)
		System.out.println ("Component " + (i++) + ": size " + l.size());

	g.setOut("1.2a");
	System.out.println ("Scatterplot of component size: #components with that size");
	Map<Integer, Integer> map = g.componentSizeScatterPlot();
	for (int size: map.keySet())
		g.out.println (size + "\t" + map.get(size));
	g.out.close();

	System.out.println ("___________________________________________________________________________________________");
	System.out.println ("Scatterplot of log(component size): log (#components with that size)");
	g.setOut("1.2b");
	for (int size: map.keySet())
	{
		int nComponents = map.get(size);
		double log = Math.log(nComponents+1); // plus1
		g.out.println (Math.log (size+1) + "\t"  + log);
	}
	g.out.close();

	System.out.println ("___________________________________________________________________________________________");
	Node<String> n = g.nodeMap.get("Ambjorn");
	System.out.println ("Ambjorn node = " + n);
	System.out.println ("Clustering coeff = " + g.clusteringCoeff());

	System.out.println ("___________________________________________________________________________________________");
	g.rewireNetwork();

	g.setOut("1.3a");
	System.out.println ("Scatterplot of degree : #nodes with that degree");
	degrees = g.getDegreeDistribution();
	for (Pair<Integer, Integer> p: degrees)
		g.out.println (p.getFirst() + "\t" + p.getSecond());
	g.out.close();

	System.out.println ("Clustering coeff = " + g.clusteringCoeff());
	g.setOut("1.3b");
	System.out.println ("___________________________________________________________________________________________");
	System.out.println ("Scatterplot of log(degree) : log (#nodes with that degree)");
	for (Pair<Integer, Integer> p: degrees)
	{
		int x = p.getFirst();
		int nodes = p.getSecond();
		Util.ASSERT (nodes > 0);
		double log = Math.log (x+1);

		g.out.println (log + "\t" + Math.log(nodes+1));
	}
	g.out.close();

//	g.plotMaxComponentSizeWithDeletedEdges();

	System.out.println ("___________________________________________________________________________________________");
	System.out.println ("Percentile edges removed (common co-authors) : Max component size");
	g = parse(args[0]);
	g.setOut("1.4a");
	g.plotMaxComponentSizeWithDeletedEdges(g.sortedEdgesByCommonCoauthors());

	System.out.println ("___________________________________________________________________________________________");
	System.out.println ("Percentile edges removed (co-authorship frequency) : Max component size");
	g = parse(args[0]);
	g.setOut("1.4b");
	g.plotMaxComponentSizeWithDeletedEdges(g.sortedEdgesByCoauthorFrequency(args[0]));
}

}