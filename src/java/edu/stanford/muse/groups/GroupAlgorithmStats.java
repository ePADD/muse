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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GroupAlgorithmStats<T extends Comparable<? super T>> implements Serializable {

	public float MAX_SUBSUMPTION_ERROR;
	public float MIN_MERGE_GROUP_SIM; // for manufacturing new groups
	public int MIN_FREQ;
	public int MIN_GROUP_SIZE;

	public GroupStats<T> startingGroups;
	public List<GroupStats<T>> intersectionGroupsAdded = new ArrayList<GroupStats<T>>();
	public GroupStats<T> groupsWithMinSize;
	public GroupStats<T> groupsAfterIntersections;
	public GroupStats<T> groupsWithMinFreqAndMinSize;
	public GroupStats<T> groupsAfterSubsumption;
	public GroupStats<T> manufacturedGroups;
	public GroupStats<T> finalGroups, finalRootGroups;
	public long executionTimeMillis;

	public String toString()
	{
		// do not use html special chars here!

		String s = "AlgorithmParameters:\t " + "MIN_FREQ: " + MIN_FREQ + " MIN_GROUP_SIZE " + MIN_GROUP_SIZE
					+ " MAX_SUBSUMPTION_ERROR: " + MAX_SUBSUMPTION_ERROR + " MIN_MERGE_GROUP_SIM: " + MIN_MERGE_GROUP_SIM + "\n";

		s += "StartingGroups:\t" + startingGroups + "\n"
		   	+ "WithMinSize:\t" + groupsWithMinSize + "\n";
		for (int i = 0; i < intersectionGroupsAdded.size(); i++)
			s += "NewGroupsIntersectionIteration" + (i+1) + "\t" + intersectionGroupsAdded.get(i) + "\n";

		s += "AfterIntersections:\t " + groupsAfterIntersections + "\n"
				+ "WithMinFreq:\t " + groupsWithMinFreqAndMinSize + "\n"
				+ "AfterSubsumption:\t " + groupsAfterSubsumption + "\n"
				+ "ManufacturedGroups:\t " + manufacturedGroups + "\n"
				+ "FinalGroups:\t " + finalGroups + "\n"
				+ "FinalRootGroups:\t " + finalRootGroups + "\n"
				+ "ExecutionTimeInSecs:\t" + executionTimeMillis/1000 + "\n";
		return s;
	}


}
