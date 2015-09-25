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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;

import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.exceptions.CancelledException;
import edu.stanford.muse.graph.GroupsGraph;
import edu.stanford.muse.graph.Node;
import edu.stanford.muse.util.JSONUtils;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;

/** main class for grouping algorithm. Top level entry-points are the findGroups* methods.
 * call getStatus() to read status while the algorithm is running, and cancel() to cancel it.
 */
public class Grouper<T extends Comparable<? super T>> implements StatusProvider
{
	static Log log = LogFactory.getLog(Grouper.class); // logger used by move class also

	// algorithm params
	public int MAX_SIZE_PER_INPUT = 50; // we'll ignore input groups larger than this size
	public int MAX_EDGES_PER_NODE = 1000; // we'll ignore elements that are present in > MAX_EDGES_PER_NODE unique groups
	public int MIN_COMMON_ELEMENTS_FOR_MERGE = 2; // won't merge 2 groups unless there are at least these many elements in common
	public int MIN_COMMON_ELEMENTS_FOR_INTERSECT = 2; // won't intersect 2 groups unless there are at least these many elements in common
    public static int PREFERRED_MAX_GROUP_SIZE = 20;
	private  boolean DISABLE_DOMINATE_MOVE = true, DISABLE_MERGE_MOVE = false, DISABLE_INTERSECT_MOVE = false, DISABLE_DROP_MOVE = false;

	private String statusMessage = "Computing best groups"; // default message
	private int pctComplete = 0;

	private GroupsGraph<T> graph, graphBeingSetup;
	private int inputSize;
	private Map<T, Map<T, Float>> affinityMap;
	private Map<T, Float> individualElementsValueMap;
	Comparator comparator = new Move.MyComparator<T>();

	public void setAffinityMap(Map<T, Map<T, Float>> affinityMap) {
		this.affinityMap = affinityMap;
		dumpAffinityMap();
	}

	public void dumpAffinityMap()
	{
		if (log.isDebugEnabled())
		{
			StringBuilder sb = new StringBuilder("Affinity map:\n");
	
			for (T t: affinityMap.keySet())
			{
				sb.append (t + ": ");
				Map<T, Float> map = affinityMap.get(t);
				for (T t1: map.keySet())
					sb.append (t1 + " (" + map.get(t1) + ")  ");
				sb.append ("\n");
			}
			log.debug (sb);
		}
	}

	public void setIndividualElementsValueMap(Map<T, Float> affinityMap) {
		this.individualElementsValueMap = affinityMap;
	}

	private GrouperStats<T> grouperStats;

	boolean cancelled = false;
	public void cancel() {
		log.info("Grouper cancelled");
		cancelled = true;
		if (graphBeingSetup != null)
			graphBeingSetup.cancel();
	}

	public String getStatusMessage()
	{
		return JSONUtils.getStatusJSON(statusMessage, pctComplete, -1, -1);
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public String getMovesAsHTML() {
		return graph.graphOutput;
	}

	public static <T extends Comparable<? super T>> void dumpGroupsForDebug(String title, List<SimilarGroup<T>> list)
	{
		if (!log.isDebugEnabled())
			return;
		log.debug(title + ": " + list.size() + " groups");
		for (int i = 0; i < list.size(); i++)
			log.debug(i + ". " + list.get(i));
	}

	public static <T extends Comparable<? super T>> List<SimilarGroup<T>> convertToSimilarGroups(Collection<Group<T>> input)
    {
		List<SimilarGroup<T>> result = new ArrayList<SimilarGroup<T>>();
		for (Group<T> g : input)
			result.add(new SimilarGroup<T>(g));
		return result;
    }

	/** finds elements which occur in > 1000 unique configurations in the input. */
	private static<T extends Comparable<? super T>> Set<T> findHyperConnectedElements(List<Pair<Group<T>, ?>> input)
    {
		List<Group<T>> justGroups = new ArrayList<Group<T>>();
		for (Pair<Group<T>, ?> p: input)
			justGroups.add(p.getFirst());
		int MAX = 1000;
		return findHyperConnectedElementsRaw(justGroups, MAX);
    }
	
	/** finds elements which occur in > threshold unique configurations in the input. */
	public static<T extends Comparable<? super T>> Set<T> findHyperConnectedElementsRaw(List<Group<T>> input, int threshold)
	{	
		Set<Group<T>> exactGroups = new LinkedHashSet<Group<T>>();
		Set<T> result = new LinkedHashSet<T>();
		// generate a map of how many unique groups each element is in
		Map<T, Integer> map = new LinkedHashMap<T, Integer>();
		for (Group<T> g : input)
		{			
			if (exactGroups.contains(g))
				continue;

			// new group, we haven't seen it before
			exactGroups.add(g); 
			for (T t: g.elements)
			{
				Integer I = map.get(t);
				if (I == null)
					map.put (t, 1);
				else
					map.put (t, I+1);
			}
		}

		for (T t: map.keySet())
		{
			if (map.get(t) > threshold)
			{
				result.add(t);
				log.warn ("Dropping element from all groups: " + t + " because it is part of " + map.get(t) + " unique groups");
				// go over all the groups, and delete this element
				// note: we are modifying the actual input, which is what we want
			}
		}
		
		return result;
    }

	public static <T extends Comparable<? super T>> List<Group<T>> allUniqueGroups(List<Group<T>> input)
    {
		Set<Group<T>> exactGroups = new LinkedHashSet<Group<T>>();
		for (Group<T> g : input)
			exactGroups.add(g);
		return new ArrayList<Group<T>>(exactGroups);
	}

	public void setDisableDropMove(boolean b)
	{
		DISABLE_DROP_MOVE = b;
	}

	public void setDisableMergeMove(boolean b)
	{
		DISABLE_MERGE_MOVE = b;
	}

	public void setDisableIntersectMove(boolean b)
	{
		DISABLE_INTERSECT_MOVE = b;
	}

	public void setDisableDominateMove(boolean b)
	{
		DISABLE_DOMINATE_MOVE = b;
	}

	/** in future, we may want to play around with this function, e.g. return some complex function of the distribution -- right now, just return 1/n */
	public static float f(int n, Map<Integer, Integer> distribution, int totalN)
	{
		/*
		if (n == 0)
			return 1;
		if (n > 20)
			return (float) distribution.get(20) / totalN;
		return (float) distribution.get(n) / totalN;
		*/
		return 1.0f/n;
	}

	/*
	private List<SimilarGroup<T>> cleanCandidates (List<SimilarGroup<T>> candidates)
	{
		List<SimilarGroup<T>> result1 = new ArrayList<SimilarGroup<T>>();		
		// remove very large input groups
		for (SimilarGroup<T> candidate: candidates)
		{
			if (candidate.size() <= MAX_SIZE_PER_INPUT)
				result1.add(candidate);
		}
		return result1;
	}
	*/
	/** input is a list of <group, weight> */
	public GroupsGraph<T> setupGraph(List<Pair<Group<T>, Float>> input)
	{
		statusMessage = "Setting up groups";

		// first remove hyper-connected elements... the user probably forgot to give his own address.
		// failing to do so causes a large # of edges to be created in the graph and therefore takes MUCH longer, sometimes not completing at all
		Set<T> hyperConnected = findHyperConnectedElements((List) input);
		if (hyperConnected.size() > 0)
		{
			log.warn ("Dropping " + hyperConnected.size() + " from all groups due to hyper-connectedness");
			for (T t: hyperConnected)
				log.warn ("Dropping " + t);
		}

		// now create the graphs' nodes
		graphBeingSetup = new GroupsGraph<T>();
		int id = 1;
		Map<Group<T>, Node<SimilarGroup<T>>> groupToNodeMap = new LinkedHashMap<Group<T>, Node<SimilarGroup<T>>>();
		for (Pair<Group<T>, Float> p: input)
		{
			Group<T> g = p.getFirst();
			float weight = p.getSecond();
			
			for (Iterator<T> it = g.elements.iterator(); it.hasNext(); )
			{
				T e = it.next();
				if (hyperConnected.contains(e))
					it.remove();
			}
			if (g.elements.size() == 0)
				continue;

			// group might already exist, e.g. because (A,B) existed as a group and 
			// (A,B,C) was a different group but C was removed as it was hyperconnected, so it reduced to (A,B)
			// if it already exists, just add the new weight to the existing value
			Node<SimilarGroup<T>> n = groupToNodeMap.get(g);
			if (n == null)
			{
				id++;
				SimilarGroup<T> sg = new SimilarGroup<T>(g);
				Node<SimilarGroup<T>> newNode = new Node<SimilarGroup<T>>(sg, id);
				newNode.setValue(weight);
				groupToNodeMap.put(g, newNode);
				graphBeingSetup.addNode(newNode);
			}
			else
				n.setValue (n.value + weight);
		}

		// set up edges
		graphBeingSetup.setupEdges();
		graphBeingSetup.verify();
		return graphBeingSetup;
	}

	/** find top numGroups groups, uses default err weight 
	 * @throws CancelledException */
	public GroupHierarchy<T> findGroups(List<Group<T>> input, int numGroups) throws CancelledException
	{
		return findGroups (input, numGroups, AlgoStats.DEFAULT_ERROR_WEIGHT);
	}

	public GroupHierarchy<T> findGroupsFromSets(List<Set<T>> input, int numGroups, float errWeight) throws CancelledException
	{
		List<Group<T>> inputGroups = new ArrayList<Group<T>>();
		for (Set<T> s: input)
			inputGroups.add(new Group<T>(s));
		return findGroups (inputGroups, numGroups, errWeight);
	}

	public GroupHierarchy<T> findGroupsFromLists(List<List<T>> input, int numGroups, float errWeight) throws CancelledException
	{
		List<Group<T>> inputGroups = new ArrayList<Group<T>>();
		for (List<T> l: input)
			inputGroups.add(new Group<T>(l));
		return findGroups (inputGroups, numGroups, errWeight);
	}
	
	/** Find top numGroups. top level entry point for the algorithm described in the SNAKDD-2011 paper.
	 * input is a set of initial groups with their weights (aka values) 
	 * @throws CancelledException */
	public GroupHierarchy<T> findGroupsWeighted(List<Pair<Group<T>, Float>> weightedInput, int numGroups, float errWeight) throws CancelledException
	{
		log.info ("-----------------------------------------------   GROUPER  -----------------------------------------------");
		if (weightedInput.size() == 0)
			return new GroupHierarchy<T>(new ArrayList<SimilarGroup<T>>()); // just return an empty hierarchy, otherwise we see an IOOB at input.get(0);

		inputSize = 0;
		inputSize = weightedInput.size();
		
		log.info ("Starting grouping with " + weightedInput.size() + " starting unique (weighted) groups, err weight = " + errWeight);
		statusMessage = "Starting up";

		if (log.isDebugEnabled())
		{
			log.debug("Grouper Input:");
			for (Pair<Group<T>, Float> p: weightedInput)
				log.debug (String.format ("%.4f", p.getSecond()) + " " + p.getFirst());
			
			log.debug("Grouper Individual values:");
			if (log.isDebugEnabled())
			{
				log.debug("Grouper Input:");
				for (T t: individualElementsValueMap.keySet())
					log.debug (String.format ("%.4f", individualElementsValueMap.get(t)) + " " + t);
			}
		}
		
		// DM: Sketchy. We hash based on the String of the first input group + time stamp to get a unique ID
		// We want to hash on the user's email address because then it is unique and we can tell if people
		// have been running the algo multiple times.
		grouperStats = new GrouperStats<T>(weightedInput.get(0).toString(), String.valueOf(System.currentTimeMillis()));
		// List<Group<String>> input = JSONUtils.parseXobniFormat("/home/xp/DUMP.peter.42519.anon");
		// converted to the weighted format now
		
		graph = setupGraph(weightedInput);

		Move.errWeight = errWeight;
		GroupHierarchy<T> hierarchy	= findGroupsCore(numGroups, graph);
		if (hierarchy != null)
			log.info("Grouper output: " + hierarchy.getAllGroups().size() + " groups, " + hierarchy.rootGroups.size() + " root groups");
		log.info ("-----------------------------------------------   END GROUPER  -----------------------------------------------");

		return hierarchy;
	}

	/** like findGroupsWeighted, but assumes a 1.0 weight for each instance in the input. 
	 * @throws CancelledException */
	public GroupHierarchy<T> findGroups(List<Group<T>> input, int numGroups, float errWeight) throws CancelledException
	{
		if (input.size() == 0)
			return new GroupHierarchy<T>(new ArrayList<SimilarGroup<T>>()); // just return an empty hierarchy
		inputSize = 0;
		for (Group<T> g: input)
			inputSize += g.size();

		log.info ("Starting grouping with " + input.size() + " starting groups, err weight = " + errWeight);
		statusMessage = "Starting up";

		//DM: Sketchy. We hash based on the String of the first input group + time stamp to get a unique ID
		// We want to hash on the user's email address because then it is unique and we can tell if people
		// have been running the algo multiple times.
		grouperStats = new GrouperStats<T>(input.get(0).toString(), String.valueOf(System.currentTimeMillis()));
		//DM: create a table of numbers that map to usernames for anonymity later on, then add this to our stat collector.
		HashMap<String, Integer> anonIDs = anonymizeIDs(input);
		grouperStats.addAnonIDs(anonIDs);
		//DM: add starting groups to stat collector
		grouperStats.addStartingGroups(input);

		// List<Group<String>> input = JSONUtils.parseXobniFormat("/home/xp/DUMP.peter.42519.anon");
		// converted to the weighted format now
		List<Pair<Group<T>, Float>> weightedInput = new ArrayList<Pair<Group<T>, Float>>();
		for (Group<T> i: input)
			weightedInput.add (new Pair<Group<T>, Float>(i, 1.0f));
		
		graph = setupGraph(weightedInput);

		Move.errWeight = errWeight;
		GroupHierarchy<T> hierarchy	= findGroupsCore(numGroups, graph);
		if (hierarchy != null)
		{
			log.info("output: " + hierarchy.getAllGroups().size() + " groups, " + hierarchy.rootGroups.size() + " root groups");
		//	long editDistance = computeEditDistance(input, hierarchy.getAllGroups());
		//	float editDistanceFraction = ((float) editDistance)/inputSize;
		//	log.info ("edit distance: " + editDistance + ", fraction of input size = " + editDistanceFraction);
		}
		return hierarchy;
	}
	
	/** compute nodes whose best moves are invalidated due to lostElements -- these are elements that are going to be deleted. */
	private Set<Node<SimilarGroup<T>>> computeInvalidatedDueToLostElements(GroupsGraph<T> graph, Collection<T> lostElements, BestMoveTracker bmt)
	{
		// what are the nodes to recompute best moves for?
        Set<Node<SimilarGroup<T>>> invalidatedNodes = new LinkedHashSet<Node<SimilarGroup<T>>>();

		  for (T t: lostElements)
          	if (graph.getNodeCount(t) == 2) // 2 because we haven't removed dropNode from the graph yet
          	{
          		Set<Node<SimilarGroup<T>>> nodesWithT = graph.lookup(t); 
          		Util.softAssert(nodesWithT.size() == 2); // nodesWithT should be just dropNode and one other node with t
          		for (Node<SimilarGroup<T>> n: nodesWithT)
          			for (Move<T> m: bmt.getBestMoves(n))
          			{
          				invalidatedNodes.add(m.n1);
          				invalidatedNodes.add(m.n2);
          			}
          	}

          return invalidatedNodes;
	}

	/** core grouper algorithm, working on an input graph that has been set up with values and edges -- DO NOT USE DIRECTLY. use findGroups instead. 
	 * @throws CancelledException */
	private GroupHierarchy<T> findGroupsCore(int numGroups, GroupsGraph<T> graph) throws CancelledException
	{
		// Key data structures: 
		BestMoveTracker bmt = new BestMoveTracker();
		// 2. a treemap of all moves, that is maintained sorted by move value reduction (using MyComparator), so we can quickly read off the first (lowest value reduction) [drop/union/intersection]
		// note that the treemap uses MyComparator for determining if 2 moves are equal, not Move.equals()

		statusMessage = "Computing best operations per group";
		for (Node<SimilarGroup<T>> n : graph.getAllNodes())
			bmt.refreshBestMoveForNode(n);

		bmt.verify();
		graph.verify();

		log.info("Initialized " + graph.size() + " nodes, and " + bmt.size() + " node --> move hashes");

		int edgeEnds = 0;
		for (Node<SimilarGroup<T>> n : graph.getAllNodes())
			edgeEnds += n.connectedNodes.size();
		log.info ("edge ends in graph: " + edgeEnds);

		log.info("Done initializing " + bmt.size() + " moves");

		List<Move<T>> resultMoves = new ArrayList<Move<T>>();
		List<SimilarGroup<T>> rGroups = new ArrayList<SimilarGroup<T>>();

		int count = 0;
		int startingGraphSize = graph.size();
		int moveNum = -1;
		while (graph.size() > 0 && bmt.size() > 0)
		{
			if (isCancelled())
				throw new CancelledException();

			pctComplete = (100 - (graph.size() * 100) / startingGraphSize);
			statusMessage = "Grouping people"; // can toString percentage remaining etc.

			// get the best move on the heap
			Move<T> currentMove = bmt.firstMove();
			currentMove.grabSnapshot();
			currentMove.setMoveNum(++moveNum);
			resultMoves.add(currentMove);
			grouperStats.addMove(currentMove);

			count++;
			log.debug("Move " + count);

			float newGroupValue = currentMove.n1.value + currentMove.n2.value - currentMove.valueReduction*currentMove.multiplier;

			// update the graph and moves
			if (currentMove.type == Move.Type.UNION || currentMove.type == Move.Type.DOMINATE || currentMove.type == Move.Type.INTERSECT)
			{
				Node<SimilarGroup<T>> n1 = currentMove.n1;
				Node<SimilarGroup<T>> n2 = currentMove.n2;
				Util.softAssert(graph.contains(n1.payload));
				Util.softAssert(graph.contains(n2.payload));

				if (log.isDebugEnabled())
				{
					log.debug(graph.size() + ". " + currentMove.type + " [ " + n1.payload.elementsToString() + n1.value
				                 + "] [ " + n2.payload.elementsToString() + n2.value + "] valueReduction=" + currentMove.valueReduction + " multiplier=" + currentMove.multiplier);
					log.debug("affinity = " + computeAffinity(n1.payload, n2.payload));
				}
				
				SimilarGroup<T> newGroup = null;
				if (currentMove.type == Move.Type.UNION)
					newGroup = n1.payload.union(n2.payload);
				else if (currentMove.type == Move.Type.DOMINATE)
					newGroup = new SimilarGroup<T>(currentMove.n1.payload);
				else if (currentMove.type == Move.Type.INTERSECT)
					newGroup = (SimilarGroup<T>) n1.payload.intersect(n2.payload);

				// update graph and moves
				// verifyAllMoves(allMoves, nodeToBestMoves);
//				Node<SimilarGroup<T>> updatedNode = updateGraph(graph, n1, n2, newGroup, newGroupValue);
			//	graph.verify();
				Node<SimilarGroup<T>> updatedNode = doBinaryMove(graph, newGroup, currentMove.n1, currentMove.n2, bmt, newGroupValue);
				updatedNode.addCreatedByMove(moveNum);
				// verifyAllMoves(allMoves, nodeToBestMoves);
				if (log.isDebugEnabled())
					log.debug("Result node: [ " + updatedNode.payload.elementsToString() + updatedNode.value + "]");
			}
			else if (currentMove.type == Move.Type.DROP)
			{
				Node<SimilarGroup<T>> dropNode = currentMove.n1;
				SimilarGroup<T> dropGroup = dropNode.payload;
				dropGroup.utility = dropNode.value;

				if (log.isDebugEnabled())
					log.debug (graph.size() + ". " + currentMove.type + " [" + dropNode.payload.elementsToString()
				                             + dropNode.value + "] valueReduction=" + currentMove.valueReduction + " multiplier=" + currentMove.multiplier);
				Util.softAssert(graph.contains(dropNode.payload));

				// when we drop a node, we lose elements. this may change the value of a different node that is now the lone place for a lost element...
				Set<Node<SimilarGroup<T>>> invalidatedNodes = computeInvalidatedDueToLostElements(graph, dropNode.payload.elements, bmt);
				// we also need to recompute best moves for nodes that were dependent on some operation with this drop node
                invalidatedNodes.addAll(bmt.getDependentNodes(dropNode));
                Util.softAssert (invalidatedNodes.contains(dropNode));
                
                // first remove best moves for these nodes
                bmt.removeBestMovesFor(invalidatedNodes);
				Util.softAssert(bmt.getBestMove(dropNode) == null);

                // now, delete the node
				Set<Node<SimilarGroup<T>>> neighbors = new LinkedHashSet<Node<SimilarGroup<T>>>(dropNode.connectedNodes);
        		for (Node<SimilarGroup<T>> node : neighbors)
					dropNode.deleteEdge(node);
                graph.removeNode(dropNode);

                // then recompute moves for nodes
                invalidatedNodes.remove(dropNode); // of course we aren't going to recompute dropNode
                Util.softAssert (!invalidatedNodes.contains(dropNode));
                for (Node<SimilarGroup<T>> n : invalidatedNodes)
					bmt.refreshBestMoveForNode(n);
			}

			if (log.isDebugEnabled())
				log.debug(" reduced value: " + currentMove.valueReduction * currentMove.multiplier);
			
			if (graph.size() <= numGroups) {
				rGroups = getRemainingNodes(graph);
				break;
			}
		}

		//toString out the results of groups left in the graph
		for (Iterator<SimilarGroup<T>> iter = graph.getAllGroups().iterator(); iter.hasNext();)
		{
			SimilarGroup<T> key = iter.next();
			Node<SimilarGroup<T>> val = graph.lookup(key);
			if (log.isDebugEnabled())
				log.info(val.payload.elementsToString());
		}

		//DM: add the final groups to our stats collector
		grouperStats.addFinalGroups(rGroups);

		/*** COMPUTE HIERARCHY ***/
		// now compute hierarchy, just to identify the root groups
		GroupHierarchy<T> hierarchy = new GroupHierarchy<T>(rGroups);
		Set<SimilarGroup<T>> rootGroups = hierarchy.rootGroups;
		log.info("hierarchy: #root groups = " + rootGroups.size());

		//add the hierarchy to the stats collector
		grouperStats.addHierarchy(hierarchy);

		return hierarchy;
	}



	/** affinity between 2 different groups based on affinity map */
	private float computeAffinity(SimilarGroup<T> g1, SimilarGroup<T> g2)
	{
		float cum_affinity = 0.0f, max_affinity = 0.0f;
		// compute pairwise affinity for non-common elements
		Group<T> g2_minus_g1 = g2.minus(g1);
		for (T t1: g1.minus(g2).elements)
		{
			//if (g2.contains(t1))
			//	continue;
			for (T t2: g2_minus_g1.elements)
			{
				//if (g1.contains(t2))
				//	continue;

				float affinity = 0.0f;
				Map<T, Float> map = affinityMap.get(t1);
				if (map != null)
				{
					Float F = map.get(t2);
					if (F != null)
						affinity = F;
				}
				cum_affinity += affinity;
				max_affinity += 1.0f;
			}
		}
		
		if (max_affinity == 0)
			return 0.0f;
		
		return cum_affinity/max_affinity;
	}

	/** retarget n1's and n2's edges to newNode.
	 * 	n1, n2 may be the same as update node...
	 */
	private static <T extends Comparable<? super T>> void redirectEdges(Node<SimilarGroup<T>> n1, Node<SimilarGroup<T>> n2, Node<SimilarGroup<T>> newNode)
	{
		// connect all of n1's neighbors to update node if at least one element in common
		if (n1 != newNode)
		{
			List<Node<SimilarGroup<T>>> connectedNodes	= new ArrayList<Node<SimilarGroup<T>>>(n1.connectedNodes);
			for (Node<SimilarGroup<T>> node : connectedNodes)
			{
				node.deleteEdge(n1);
				if (node.equals(n1) || node.equals(n2))
					continue;
				if (!node.equals(newNode)
				    && node.payload.intersectionSize(newNode.payload) > 0) {
					node.addEdge(newNode);
				}
			}
		}

		// same for n2.
		if (n2 != newNode)
		{
			List<Node<SimilarGroup<T>>> connectedNodes = new ArrayList<Node<SimilarGroup<T>>>(n2.connectedNodes);
			for (Node<SimilarGroup<T>> node : connectedNodes) {
				node.deleteEdge(n2);
				if (node.equals(n1) || node.equals(n2))
					continue;
				if (!node.equals(newNode)
				    && node.payload.intersectionSize(newNode.payload) > 0) {
					node.addEdge(newNode);
				}
			}
		}
	}

	/** removes n1, n2, updates the node for newGroup, if it already exists, otherwise creates it and returns it	 */
	private static <T extends Comparable<? super T>> Node<SimilarGroup<T>>
	    updateGraph(GroupsGraph<T> graph, Node<SimilarGroup<T>> n1, Node<SimilarGroup<T>> n2, SimilarGroup<T> newGroup, float newGroupValue)
	{
		//graph.verify();

		Util.softAssert(newGroupValue <= n1.value + n2.value);
		Util.softAssert(graph.contains(n1.payload));
		Util.softAssert(graph.contains(n2.payload));
		Util.softAssert (!n1.payload.equals(n2.payload));

		// first, remove the nodes, but not the edges
		graph.removeNode(n1);
		graph.removeNode(n2);

		Node<SimilarGroup<T>> updatedNode = null;
		// 4 possibilities for updateNode: same as n1, same as n2, same as another node in the graph, not present in the graph
		if (newGroup.equals(n1.payload))
		{
			n1.value = newGroupValue;
			updatedNode = n1;
			// put this node back in the graph
			graph.addNode(updatedNode);
		}
		else if (newGroup.equals(n2.payload))
		{
			n2.value = newGroupValue;
			updatedNode = n2;
			// put this node back in the graph
			graph.addNode(updatedNode);
		}
		else
		{
			// this is a different group from n1 and n2
			// check if group already exists, if it does, just add the new value to its existing value
			updatedNode = (Node<SimilarGroup<T>>) graph.removeGroup(newGroup); // note important to remove
			if (updatedNode != null) {
				updatedNode.value += newGroupValue;
			}
			else {
				updatedNode = new Node<SimilarGroup<T>>(newGroup);
				updatedNode.value = newGroupValue;
				// put this node in the graph
				graph.addNode(updatedNode);
			}
		}

		// updateNode is replacing n1 and n2, so redirect their edges to it
		redirectEdges(n1, n2, updatedNode);

		//graph.verify();
		return updatedNode;
	}

	/** returns nodes for which best move has to be recomputed when n1 and n2 are being dropped, and newNode is being added.
	 * (newNode could be the same as n1 or n2).
	 * we're smart and we return only those nodes that have a best move connected to n1 or n2
	 */
	private Collection<Node<SimilarGroup<T>>> computeInvalidatedNodes(Node<SimilarGroup<T>> n1, Node<SimilarGroup<T>> n2, BestMoveTracker bmt)
	{
		// We collect the nodes whose moves are tied to their best moves;
		// currently their best moves are tied to (potentially) disappearing nodes
		Set<Node<SimilarGroup<T>>> invalidatedNodes = new LinkedHashSet<Node<SimilarGroup<T>>>();
		invalidatedNodes.addAll(bmt.getDependentNodes(n1));
		invalidatedNodes.addAll(bmt.getDependentNodes(n2));
		return invalidatedNodes;
	}

	private Node<SimilarGroup<T>> doBinaryMove(GroupsGraph<T> graph, SimilarGroup<T> newGroup, Node<SimilarGroup<T>> n1, Node<SimilarGroup<T>> n2, BestMoveTracker bmt, float newNodeValue)
	{
		// 1. first figure out which nodes are being invalidated... w/o updating the graph
		Collection<Node<SimilarGroup<T>>> invalidatedNodes = computeInvalidatedNodes(n1, n2, bmt);
		
		// what elements are we losing? add invalidated nodes based on those
		Collection<T> lostElements = n1.payload.minus(newGroup).union(n2.payload.minus(newGroup)).elements();
		if (lostElements != null)
			invalidatedNodes.addAll(computeInvalidatedDueToLostElements(graph, lostElements, bmt));
	
		bmt.removeBestMovesFor(invalidatedNodes);
		
		// 2. then update the graph.
		Node<SimilarGroup<T>> resultingNode = updateGraph(graph, n1, n2, newGroup, newNodeValue);

		// 3. then recompute moves for invalidated nodes
		// we don't want to recalculate for the nodes we're dropping; just for the one we're adding
		invalidatedNodes.remove(n1);
		invalidatedNodes.remove(n2);
		invalidatedNodes.add(resultingNode);
	
		for (Node<SimilarGroup<T>> affected : invalidatedNodes)
			bmt.refreshBestMoveForNode(affected);	
		return resultingNode;
	}
	
	/** used for reading off the result */
	private static <T extends Comparable<? super T>> List<SimilarGroup<T>> getRemainingNodes(GroupsGraph<T> graph)
	{
		List<SimilarGroup<T>> rGroups = new ArrayList<SimilarGroup<T>>();
		for (Iterator<SimilarGroup<T>> iter = graph.getAllGroups().iterator(); iter.hasNext();)
		{
			SimilarGroup<T> key = iter.next();
			Node<SimilarGroup<T>> val = graph.lookup(key);
			key.utility = val.value;
			rGroups.add(key);
		}
		Collections.sort(rGroups);
		return rGroups;
	}

	private HashMap<String,Integer> anonymizeIDs(List<Group<T>> input){
		HashMap<String,Integer> anonIDs = new HashMap<String, Integer>();
		Integer counter = 0;

		for(Group<T> group : input){
			for(T element : group.elements){
				if(!anonIDs.containsKey(element)){
					anonIDs.put(element.toString(),counter);
					counter++;
				}
			}
		}
		return anonIDs;
	}

	public String getAnonMappings() throws JSONException{
		return grouperStats.getAnonIDsJSON();
	}

	public String getGrouperStats(){
		return grouperStats.toString();
	}

	public String getUnanonmyizedGrouperStats()
	{
		return grouperStats.unanonymizedStats();
	}
	
	/** little inner class to localize all the delicate logic related to move management */
	private class BestMoveTracker {
		// 1. nodeToBestMoves is a map of node to set of all (best) moves that it is involved in (incl. best moves for other nodes that involve it.)
		// this is useful to know quickly which nodes to recompute best moves for
		// 2. nodeToItsBestMove is a map of node to the move that's best for it.
		private Map<Node<SimilarGroup<T>>, Set<Move<T>>> nodeToBestMoves = new LinkedHashMap<Node<SimilarGroup<T>>, Set<Move<T>>>();
		private Map<Node<SimilarGroup<T>>, Move<T>> nodeToItsBestMove = new LinkedHashMap<Node<SimilarGroup<T>>, Move<T>>();
		private TreeSet<Move<T>> allMoves = new TreeSet<Move<T>>(comparator); // all moves contains only the best move per node

		Set<Move<T>> getBestMoves(Node<SimilarGroup<T>> n) { return nodeToBestMoves.get(n); }
		Move<T> getBestMove(Node<SimilarGroup<T>> n) { return nodeToItsBestMove.get(n); }
		//boolean hasNode(Node<SimilarGroup<T>> n) { return nodeToBestMoves.containsKey(n); }
		int size() { return nodeToBestMoves.size(); }
		Move<T> firstMove() {
			Move<T> m = allMoves.first();
			if (!allMoves.contains(m))
				Util.softAssert (false, "currentMove not in all moves! " + m);
			return m;
		}

		Collection<Node<SimilarGroup<T>>> getDependentNodes(Node<SimilarGroup<T>> n)
		{
			Set<Node<SimilarGroup<T>>> result = new LinkedHashSet<Node<SimilarGroup<T>>>();
			for (Move<T> m: nodeToBestMoves.get(n))
			{
				if (m.n1 != null)
					result.add(m.n1);
				if (m.n2 != null)
					result.add(m.n2);
			}
			return result;
		}
		
		public void removeBestMovesFor(Collection<Node<SimilarGroup<T>>> nodes)
		{
			for (Node<SimilarGroup<T>> n: nodes)
			{
				Move<T> m = nodeToItsBestMove.remove(n);
				// now remove all traces of m -- it may be in allMoves and in both of its nodes' nodeToBestMoves entries
				boolean removed = allMoves.remove(m);				
				Util.softAssert(removed);
				Util.softAssert(!allMoves.contains(m));

				removed = nodeToBestMoves.get(m.n1).remove(m);
				removed = nodeToBestMoves.get(m.n2).remove(m);
				// no assert here...
			}
		}

		/** recomputes best move for given node, keeping allMoves and nodeToBestMoves updated. */
		public void refreshBestMoveForNode(Node<SimilarGroup<T>> n)
		{
			nodeToItsBestMove.remove(n);
			Move<T> bestMoveForNode = computeBestMoveForNode(n);
			if (bestMoveForNode == null)
				return;
	
			allMoves.add(bestMoveForNode);
			Util.softAssert (allMoves.contains(bestMoveForNode));
	
			// update the best moves maps
			nodeToItsBestMove.put (n, bestMoveForNode);

			//Util.softAssert (bestMoveForNode.n1 == n);
			/* QUESTION_BEGIN: what does this code do? if bestMoveForNode.n1 == n is always true, set1 will never be consumed. not sure what the computation around set2 is for. */
			Set<Move<T>> set1;
			if (nodeToBestMoves.containsKey(bestMoveForNode.n1))
				set1 = nodeToBestMoves.get(bestMoveForNode.n1);
			else 
			{
				set1 = new LinkedHashSet<Move<T>>();
				nodeToBestMoves.put(bestMoveForNode.n1, set1);
			}
			set1.add(bestMoveForNode);
	
			Set<Move<T>> set2;
			if (bestMoveForNode.n2 != null && bestMoveForNode.n2 != bestMoveForNode.n1)
			{
				if (nodeToBestMoves.containsKey(bestMoveForNode.n2))
					set2 = nodeToBestMoves.get(bestMoveForNode.n2);
				else 
				{
					set2 = new LinkedHashSet<Move<T>>();
					nodeToBestMoves.put(bestMoveForNode.n2, set2);
				}
				set2.add(bestMoveForNode);
			}
			/* QUESTION_END */
		}
		
		/** computes the best move for node n */
		private Move<T> computeBestMoveForNode(Node<SimilarGroup<T>> n)
		{
			Util.softAssert (graph.contains(n.payload));
	
			Move<T> bestMoveForNode = null;
	
			Move<T> dropMove = new Move<T>(n, individualElementsValueMap, graph);
			if (!DISABLE_DROP_MOVE)
				bestMoveForNode = dropMove;
	
			for (Node<SimilarGroup<T>> connectedNode : n.connectedNodes)
			{
				Util.softAssert (graph.contains(connectedNode.payload));
				int intersectionSize = n.payload.intersectionSize(connectedNode.payload);
				Util.softAssert(intersectionSize > 0);
				
				if (!DISABLE_MERGE_MOVE && intersectionSize >= MIN_COMMON_ELEMENTS_FOR_MERGE)
				{
					float groupsAffinity = computeAffinity (n.payload, connectedNode.payload);
					Move<T> mergeMove = new Move<T>(Move.Type.UNION, n, connectedNode, groupsAffinity);
					if (bestMoveForNode == null || comparator.compare(mergeMove, bestMoveForNode) < 0)
						bestMoveForNode = mergeMove;
				}
				
				if (!DISABLE_DOMINATE_MOVE)
					if (!n.payload.contains(connectedNode.payload)) // use dominate moves only when n does not completely contain the other group
					{
						Move<T> dominateMove = new Move<T>(Move.Type.DOMINATE, n, connectedNode, individualElementsValueMap, graph);
						if (bestMoveForNode == null || comparator.compare(dominateMove, bestMoveForNode) < 0)
							bestMoveForNode = dominateMove;
					}
	
				if (!DISABLE_INTERSECT_MOVE && intersectionSize >= MIN_COMMON_ELEMENTS_FOR_INTERSECT)
				{
					Move<T> intersectMove = new Move<T>(Move.Type.INTERSECT, n, connectedNode, individualElementsValueMap, graph);
					if (bestMoveForNode == null || comparator.compare(intersectMove, bestMoveForNode) < 0)
						bestMoveForNode = intersectMove;
				}
			}
			return bestMoveForNode;
		}
	
		public void verify()
		{
			// ensure all moves in allMoves are unique
			Set<Move<T>> set = new LinkedHashSet<Move<T>>(allMoves);
			log.info("Unique moves " + set.size() + " moves");
			Util.ASSERT (set.size() == allMoves.size());
			for (Move<T> m: allMoves)
			{
				Util.ASSERT(m.equals(m));
				if (m.n1 != null)
					Util.softAssert(graph.contains(m.n1.payload));
				if (m.n2 != null)
					Util.softAssert(graph.contains(m.n2.payload));
			}

			for (Move<T> m: allMoves)
				Util.softAssert(allMoves.contains(m));
		}
	}
	
	public static void main (String args[]) throws CancelledException
	{
		int NGROUPS = 1;
		float ERR = 0.5f;
		
		List<List<Integer>> input = new ArrayList<List<Integer>>();

		// small input data set with 3 input groups
		List<Integer> l = new ArrayList<Integer>(); l.add(0); l.add(1); l.add(2); l.add(3); input.add (l);
		l = new ArrayList<Integer>();  l.add(2); l.add(3); l.add(4); input.add (l);
		l = new ArrayList<Integer>();  l.add(2); l.add(3); l.add(5); input.add (l);
		
		// get back all the groups
		GroupHierarchy<Integer> hierarchy = new Grouper<Integer>().findGroupsFromLists(input, NGROUPS, ERR);
		List<SimilarGroup<Integer>> groups = hierarchy.getAllGroups();

		int i = 0;
		System.out.println ("Grouping result:");
		for (SimilarGroup<Integer> sg: groups)
			System.out.println ("Group " + i++ + ". " + sg);
	}
}
