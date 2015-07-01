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

public class AlgoStats {
	public static final int DEFAULT_MIN_FREQUENCY          = 5;
	public static final float DEFAULT_MAX_ERROR            = 0.3f;
	public static final int DEFAULT_MIN_GROUP_SIZE         = 2;
	public static final float DEFAULT_MIN_GROUP_SIMILARITY = 0.35f;
	public static final float DEFAULT_UTILITY_MULTIPLIER   = 1.4f;
	
	// Slightly different params for co-tagged photos
	public static final int DEFAULT_MIN_FREQUENCY_PHOTOS   = 3;
	
	// For new algorithm (August 2010)
	public static final float DEFAULT_ERROR_WEIGHT = 0.5f;
	public static final int   DEFAULT_NUM_GROUPS   = 20;
}
