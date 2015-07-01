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
package edu.stanford.muse.launcher;

public class GroupsMain {

	public static void main (String args[]) throws Exception
	{
		// add the groups mode arg to any other args
		String newArgs[] = new String[args.length+1];
		newArgs[0] = "-groupsmode";
		System.arraycopy (args, 0, newArgs, 1, args.length);
		Main.KILL_AFTER_MILLIS = 2L * 3600 * 1000; // kill after 2 hours
		Main.main(newArgs);
	}
}
