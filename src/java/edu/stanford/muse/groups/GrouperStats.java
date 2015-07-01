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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GrouperStats<T extends Comparable<? super T>> {

	// some kind of user id or session id
	private static boolean UNANONYMIZE = false;
	
	private String id;
	private ArrayList<Move<T>> orderedMoves; 
	private List<Group<T>> startingGroups;
	private List<SimilarGroup<T>> finalGroups;
	private GroupHierarchy<T> hierarchy;
	
	//DM: anonymize user IDs when we create the graph
	private HashMap<String, Integer> anonIDs = new HashMap<String, Integer>();
	
	public GrouperStats(String name, String timestamp){
		this.id=NameHash.SHA1(name) + ":" + timestamp;
		orderedMoves = new ArrayList<Move<T>>();
	}
	
	// assumes that the relevant classes have appropriate toString methods.
	public String toString(){
		
		JSONObject jsonLog = new JSONObject();

		try {
			jsonLog.put("id", id);
			jsonLog.put("startingGroups", getJSONStartingGroups());
			jsonLog.put("finalGroups", getJSONFinalGroups());
			jsonLog.put("moves", getJSONMoves());
			jsonLog.put("topology", jsonForHierarchy(finalGroups, hierarchy.parentToChildrenMap));
			return jsonLog.toString();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "";
	}
	
	public String unanonymizedStats()
	{
		UNANONYMIZE = true;
		JSONObject jsonLog = new JSONObject();

		try {
			jsonLog.put("moves", getJSONMoves());
			return jsonLog.toString();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
			UNANONYMIZE = false;
		}
		return "{}";
	}

	//DM: prints out the increasing value of moves (i.e. sums all move values so far)
	public String printValuesOnly(){
		
		String vals = "";
		float startVal = 0;
		
		for(Move<T> move : orderedMoves){
			startVal += move.valueReduction;
			vals = vals + "," + startVal;
		}

		return vals.substring(1) + "\n";
	}
	
	
	public void addAnonIDs(HashMap<String,Integer> anonIDs){
		this.anonIDs = anonIDs;
	}
	
	public void addHierarchy(GroupHierarchy<T> h){
		this.hierarchy = h;
	}
	
	public void addFinalGroups(List<SimilarGroup<T>> finalGroups){
		this.finalGroups = finalGroups;
	}
	
	public void addStartingGroups(List<Group<T>> startingGroups){
		this.startingGroups = startingGroups;
	}
	
	public void addMove(Move<T> move){
		orderedMoves.add(move);
	}
		
	//TODO: print out hierarchy
	
	private JSONArray getJSONStartingGroups(){

		JSONArray sgroups = new JSONArray();

		for(Group<T> group : startingGroups){
			JSONArray sgroup = new JSONArray();
			List<T> elements = group.elements;
			for(T element : elements){
				sgroup.put(anonIDs.get(element));
			}
			sgroups.put(sgroup);
		}
		return sgroups;
	}
	
	private JSONArray getJSONFinalGroups(){
		
		JSONArray fgroups = new JSONArray();
		
		for(SimilarGroup<T> group : finalGroups){
			JSONArray fgroup = new JSONArray();
			List<T> elements = group.elements;
			for(T element : elements){
				fgroup.put(anonIDs.get(element));
			}
			fgroups.put(fgroup);
		}
		return fgroups;
	}
	
	private JSONArray getIDsFromNode(String s){
		String[] constituents = s.split(" ");
		JSONArray ret = new JSONArray();
		// the first two are other values of the move
		for(int i = 2; i < constituents.length; i++){
			String constituent = constituents[i];
			if (UNANONYMIZE)
				ret.put(constituent.trim());
			else
				ret.put(anonIDs.get(constituent.trim()));
		}
		return ret;
	}
	
	private JSONArray getJSONMoves() throws JSONException{
		
		JSONArray moves = new JSONArray();
		
		for(Move<T> move : orderedMoves){
			JSONObject moveObj = new JSONObject();
			moveObj.put("num", move.moveNum);
			moveObj.put("valueReduction", move.valueReduction);
			moveObj.put("newValue", move.newValue);
			moveObj.put("type", move.numForType());
			moveObj.put("n1", getIDsFromNode(move.n1.payload.toString()));
			moveObj.put("n1_value", move.n1Value);
			moveObj.put("n2", getIDsFromNode(move.n2.payload.toString()));
			moveObj.put("n2_value", move.n2Value);
			moveObj.put("n2", getIDsFromNode(move.n2.payload.toString()));
			moveObj.put("n1_createdby", jsonFromList(move.n1.createdBy));
			moveObj.put("n2_createdby", jsonFromList(move.n2.createdBy));
			
			moves.put(moveObj);
		}
		
		return moves;
	}

	public JSONArray jsonFromList(List<?> l) throws JSONException
	{
		JSONArray a = new JSONArray();
		if (l == null)
			return a;
		
		for (int i = 0; i < l.size(); i++)
			a.put(i, l.get(i).toString());
		return a;
	}
	
	public String getAnonIDsJSON() throws JSONException{
		JSONArray map = new JSONArray();
		for(String key : anonIDs.keySet()){
			JSONObject entity = new JSONObject();
			entity.put(key, anonIDs.get(key));
			map.put(entity);
		}
		return map.toString();
	}

	public JSONObject jsonForHierarchy(List<SimilarGroup<T>> rootGroups,
			  Map<SimilarGroup<T>, List<SimilarGroup<T>>> parentToChildrenMap) throws JSONException{
		
		JSONObject result = new JSONObject();
		JSONArray groups = new JSONArray();
		int groupNum = 0;

		for (SimilarGroup<T> g: rootGroups)
		{
			JSONObject rootGroup = getJSONForHierarchyRecursive(parentToChildrenMap, g);
			groups.put(groupNum++, rootGroup);
		}

		result.put ("groups", groups);
		return result;
	}
	
	private JSONObject getJSONForHierarchyRecursive (Map<SimilarGroup<T>, List<SimilarGroup<T>>> parentToChildrenMap,
			SimilarGroup<T> g) throws JSONException{
		
		JSONObject result = new JSONObject();
		result.put ("group", jsonForGroup (g));	// the top level group
		List<SimilarGroup<T>> children = parentToChildrenMap.get(g);
		if (children != null && children.size() > 0) // don't print <td></td> if there are no children
		{
			JSONArray subsets = new JSONArray();
			for (int i = 0; i < children.size(); i++)
			{
				SimilarGroup<T> child = children.get(i);
				subsets.put(i, getJSONForHierarchyRecursive(parentToChildrenMap, child));
			}
			result.put("subsets", subsets);
		}
		return result;
	}	
	
	public JSONObject jsonForGroup(SimilarGroup<T> group) throws JSONException
	{
		JSONObject jgroup = new JSONObject();
		JSONArray groupMembers = new JSONArray();
		int i = 0;

		for (T s: group.elements)
		{
			groupMembers.put(i++, "{emailAddr: ['" + anonIDs.get(s) + "']}");
		}
		jgroup.put("members", groupMembers);
		jgroup.put("freq", group.freq);
		jgroup.put("utility", group.utility);
		jgroup.put("isSuperGroup", (group instanceof SimilarSuperGroup) ? "true" : "false");

		return jgroup;
	}
	
}
