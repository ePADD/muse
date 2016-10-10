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

import java.util.ArrayList;
import java.util.List;

import edu.stanford.muse.email.Contact;


public class SimpleGroup {
	//public Map<Set<String>, Object> properties=new HashMap<Set<String>, Object>();
	public List<Contact> contacts=new ArrayList<Contact>();
	public int totalNum;
	public int height;


	public SimpleGroup(){
		//empty
		totalNum=0;
		height=0;
	}

	public void put(Contact b){
		contacts.add(b);
		totalNum++;
	}

	public Object get (int i) { return contacts.get(i); }
}
