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
package edu.stanford.muse.mining;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import edu.stanford.muse.groups.Group;
import edu.stanford.muse.groups.Grouper;
import edu.stanford.muse.util.JSONUtils;
import edu.stanford.muse.util.Util;

// to run standalone, 
// setenv CLASSPATH $HOME/workspace1/muse/WebContent/WEB-INF/lib/json-20070829.jar:$HOME/workspace1/muse/build/jar/muse.jar:$HOME/workspace1/muse/WebContent/WEB-INF/lib/json-simple-1.1.jar:$HOME/workspace1/muse/WebContent/WEB-INF/lib/gson-1.5.jar:$HOME/workspace1/muse/WebContent/WEB-INF/lib/log4j-1.2.15.jar:$HOME/workspace1/muse/WebContent/WEB-INF/lib/commons-logging-1.1.1.jar
public class TestHarness
{
	public static void main(String args[]) throws IOException
	{
		File[] dataFiles;
		if (args.length >= 1)
		{
			File f = new File(args[0]);
			if (f.isDirectory())
			{
				dataFiles = f.listFiles();				
			}
			else
			{
				dataFiles = new File[1];
				dataFiles[0] = f;
			}
		}
		else
		{
			String dataDirBase = "/Users/dianamaclean/Data/xobni_data/ANON/EmailMessage/fmt_1_persona/"; //path to folder
			File dataDir = new File(dataDirBase);
			dataFiles = dataDir.listFiles();
		}	
		
		/*
		BufferedWriter fileLogS = new BufferedWriter(new FileWriter("/Users/dianamaclean/Data/xobni_data/Files_log.txt")); //path to log of files done (useful for crash analysis)
		fileLogS.write("New round\n");
		fileLogS.close();
		*/
		
		for(File dataFile : dataFiles){
			/*
			BufferedWriter fileLog = new BufferedWriter(new FileWriter("/Users/dianamaclean/Data/xobni_data/Files_log.txt", true)); //reopen
			fileLog.write("Doing file " + dataFile + "\n");
			fileLog.flush();
			 */
			List<Group<String>> groups = JSONUtils.parseXobniFormat(dataFile.getAbsolutePath());
			// System.out.println ("Starting groups");
			for (Group<String> g: groups)
			{
			//	System.out.println (g);
			}
			// System.out.println ("End starting groups");
			
			Grouper g = new Grouper<String>();
			g.findGroups(groups, 0);

			String stats = g.getUnanonmyizedGrouperStats();
			String s = Util.getFileContents("grouper-moves-template.html");
			s = s.replace("JSON_HERE", stats);
			
			PrintWriter fw = new PrintWriter(new FileOutputStream(dataFile + ".moves.html"));
			fw.println(s);
			fw.close();

			/*
			fileLog.write("Done file " + dataFile + "\n");
			fileLog.flush();
			fileLog.close(); //Hacked this together fast; was crashing a lot and wasn't flushing the latest file opened.
			*/		
		}
		System.out.println("done");
	}
}
