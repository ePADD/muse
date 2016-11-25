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

/** return outgoing degree distribution in the graph */
@SuppressWarnings("unchecked")
public List<Pair<Integer, Integer>> getDegreeDistribution()
{
	Map<Integer, Integer> map = new LinkedHashMap<Integer, Integer>();

	for (Node<T> n : allNodes)
	{
		int degree = n.connectedNodes.size();
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
	for (Node<?> succ1: n.connectedNodes)
		for (Node<?> succ2: n.connectedNodes)
			if (succ1.connectedNodes.contains(succ2))
			{
				Util.ASSERT (succ2.connectedNodes.contains(succ1)); // because undirected graph
				triangles++;
			}

	int size = n.connectedNodes.size();
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
		nEdges += n.connectedNodes.size();
	nEdges /= 2;
	return ("Graph with " + allNodes.size() + " nodes and " + nEdges + " edges");
}

public void clearAllEdges()
{
	for (Node<?> n: allNodes)
	{
		n.connectedNodes.clear();
	}
}
}