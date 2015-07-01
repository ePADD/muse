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
package edu.stanford.muse.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.mail.Address;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.stanford.muse.email.AddressBook;
import edu.stanford.muse.email.Contact;
import edu.stanford.muse.groups.Group;
import edu.stanford.muse.groups.SimilarGroup;
import edu.stanford.muse.groups.SimilarSuperGroup;
import edu.stanford.muse.index.EmailDocument;


public class VizUtils {
    private static Log log = LogFactory.getLog(VizUtils.class);

    public static String getAddressBook(JSONObject data){
    	try{
	    	JSONObject addressBookJSON = data.getJSONObject("addressBook");
	    	Object entries = addressBookJSON.get("entries");
	    	return entries.toString();
    	}
    	catch (JSONException e){
    		System.out.println(e.getStackTrace());
    		return "null";
    	}
    }

    public static String getGroups(JSONObject data){
    	try{
	    	Object groups = data.get("groups");
	    	return groups.toString();
    	}
    	catch (JSONException e){
    		System.out.println(e.getStackTrace());
    		return "null";
    	}    	
    }
    
	public static void main(String args[]) throws IOException
	{
	}
}
