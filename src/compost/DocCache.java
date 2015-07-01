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
package edu.stanford.muse.email;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import edu.stanford.muse.index.Document;
import edu.stanford.muse.util.CryptoUtils;
import edu.stanford.muse.util.Util;

public class DocCache implements Serializable {

	protected String baseDir;

	public DocCache(String dir)
	{
		this.baseDir = dir;
	}

	public boolean hasHeader(String normalizedFolderName, int msgNum) throws IOException
	{
		return new File(baseDir + File.separator + normalizedFolderName + "." + msgNum + ".header").exists();
	}

	public Document getHeader(String normalizedFolderName, int msgNum) throws IOException, ClassNotFoundException
	{
		String headerFilename = baseDir + File.separator + normalizedFolderName + "." + msgNum + ".header";
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(headerFilename));
		Document ed = (Document) ois.readObject();
		ois.close();
		return ed;
	}
	
	public Map<Integer, Document> getAllHeaders(String prefix) throws IOException, ClassNotFoundException
	{
		return new LinkedHashMap<Integer, Document>();
	}

	public boolean hasContents(String normalizedFolderName, int msgNum) throws IOException
	{
		return new File(baseDir + File.separator + normalizedFolderName + "." + msgNum).exists();
	}

	public Set<Integer> getAllContentIdxs(String prefix) throws IOException, ClassNotFoundException
	{
		return new LinkedHashSet<Integer>();
	}
	
	public void deleteCache()
	{
		Util.deleteDir(baseDir);
	}

	public void saveHeader(Document ed, String normalizedFolderName, int msgNum) throws FileNotFoundException, IOException
	{
		String headerFileName = baseDir + File.separator + normalizedFolderName + "." + msgNum + ".header";
		ObjectOutputStream headerOOS = new ObjectOutputStream(new FileOutputStream(headerFileName));
		headerOOS.writeObject(ed);
		headerOOS.close();
	}

	public void saveContents(String contents, String normalizedFolderName, int msgNum) throws IOException, GeneralSecurityException
	{
		// new file per message, for now
		// make sure we encrypt before writing
		String contentFileName = baseDir + File.separator + normalizedFolderName + "." + msgNum;
		CryptoUtils.writeEncryptedString(contents, contentFileName);
	}

	public String getContentURL(String normalizedFolderName, int msgNum)
	{
		String contentFilename = baseDir + File.separatorChar + normalizedFolderName + "." + msgNum;
		String contentUrl = "file:///" + contentFilename.replace("\\", "/");
		return contentUrl;
	}

	public void close() { }
	
	public void pack() throws IOException
	{
	}
}
