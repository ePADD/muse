package edu.stanford.muse.ie;

import java.util.*;

/**
 * Can handle results from both reconcile and search apis of Freebase
 * Make sure to access the right fields depending on the type of api.*/
public class FreebaseApi {
	public static class Cost {
		Integer	hits;
		Integer	ms;
	}

	//Cost						costs;
	public FreebaseType			match;
	public List<FreebaseType>	candidate;
	//	public Integer				cursor, cost, hits;
	//	public String				status;
	public List<FreebaseType>	result;
	
}
