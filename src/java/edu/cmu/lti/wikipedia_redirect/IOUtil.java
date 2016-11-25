/*
 * Copyright 2011 Carnegie Mellon University
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package edu.cmu.lti.wikipedia_redirect;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Reads and writes wikipedia redirect data.
 * 
 * @author Hideki Shima
 *
 */
public class IOUtil {

  public static void save( Map<String,String> redirectData ) throws Exception {
    File outputDir = new File("target");
    if (!outputDir.exists()) {
      outputDir.mkdirs();
    }
    WikipediaRedirect list = new WikipediaRedirect(redirectData);
    
    File txtFile = new File(outputDir, "wikipedia_redirect.txt");
    FileOutputStream fosTxt = new FileOutputStream(txtFile);
    OutputStreamWriter osw = new OutputStreamWriter(fosTxt, "utf-8");
    BufferedWriter bw = new BufferedWriter(osw);
    for ( Entry<String,String> entry : list.entrySet() ) {
      bw.write( entry.getKey()+"\t"+entry.getValue()+"\n" );
    }
    bw.flush();
    bw.close();
    osw.close();
    fosTxt.close();
    System.out.println("Saved redirect data in text format: "+txtFile.getAbsolutePath());
    
    File objFile = new File(outputDir, "wikipedia_redirect.ser");
    FileOutputStream fosObj = new FileOutputStream(objFile);
    ObjectOutputStream outObject = new ObjectOutputStream(fosObj);
    outObject.writeObject(list);
    outObject.close();
    fosObj.close();
    System.out.println("Serialized redirect data: "+objFile.getAbsolutePath());
  }
  
  public static WikipediaRedirect load( File f ) throws Exception {
    WikipediaRedirect object;
    try {
      FileInputStream inFile = new FileInputStream(f);
      ObjectInputStream inObject = new ObjectInputStream(inFile);
      object = (WikipediaRedirect)inObject.readObject();
      inObject.close();
      inFile.close();      
    } catch (Exception e) {
      throw e;
    }    
    return object;
  }
  
}
