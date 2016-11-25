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


import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.EmailDocument;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Util;

/** version of cache where headers are stored in jar files: prefix.headers and contents in
 * prefix.contents. each has entries of the form _msgnum_.header/content inside.
 * when appending to either jar file, we need to create a copy of the jar first because there isn't a good
 * way to append to a jar file.
 * most of these methods are synchronized
 */
public class JarDocCache extends DocCache {
    private static Log log = LogFactory.getLog(JarDocCache.class);

	public JarDocCache(String dir)
	{
		super(dir);
	}

	// for efficiency we want to keep jaroutputstreams open between requests to save
	// so we'll maintain maps of name -> JarOutputStreams (for writing) for both heads and contents
	// we close the stream only when pack is called
	transient Map<String, JarOutputStream> contentsJarOSMap = new LinkedHashMap<String, JarOutputStream>();
	transient Map<String, JarOutputStream> headersJarOSMap = new LinkedHashMap<String, JarOutputStream>();
	transient Map<String, Set<String>> headersMap = new LinkedHashMap<String, Set<String>>();
	transient Map<String, Set<String>> contentsMap = new LinkedHashMap<String, Set<String>>();

	/** returns a list of files in the jar */
	private synchronized Set<String> readJarFileEntries(JarFile jf) throws IOException
	{
		Set<String> result = new LinkedHashSet<String>();
		Enumeration<JarEntry> entries = jf.entries();
		while (entries.hasMoreElements())
			result.add(entries.nextElement().getName());
		log.info("Jarfile " + jf.getName() + " has " + result.size() + " entries");
		return result;
	}

	/* returns the set of entries in the headers file */
	private synchronized Set<String> getHeaders(String prefix) throws IOException
	{
		Set<String> headers = headersMap.get(prefix);
		if (headers != null)
			return headers;

		// check if the file exists first, if it doesn't return null
		String filename = baseDir + File.separator + prefix + ".headers";
		File f = new File(filename);
		Set<String> result = new LinkedHashSet<String>();
		if (f.exists())
		{
			JarFile jarFile;
			try {
				jarFile = new JarFile(filename);
				result = readJarFileEntries(jarFile);
			} catch (Exception e) {
				// not really a jar file. delete the sucker
				log.warn ("Bad jar file! " + filename + " size " + new File(filename).length() + " bytes");
				Util.print_exception(e, log);
				new File(filename).delete();
			}
		}

		headersMap.put(prefix, result);
		return result;
	}

	/* returns the set of entries in the headers file */
	private synchronized Set<String> getContents(String prefix) throws IOException
	{
		Set<String> contents = contentsMap.get(prefix);
		if (contents != null)
			return contents;

		// check if the file exists first, if it doesn't return null
		String filename = baseDir + File.separator + prefix + ".contents";
		File f = new File(filename);
		Set<String> result = new LinkedHashSet<String>();
		if (f.exists())
		{
			JarFile jarFile;
			try {
				jarFile = new JarFile(filename);
				result = readJarFileEntries(jarFile);
			} catch (Exception e) {
				// not really a jar file. delete the sucker
				log.warn ("Bad jar file! " + filename + " size " + new File(filename).length() + " bytes");
				Util.print_exception(e, log);
				new File(filename).delete();
			}
		}

		contentsMap.put(prefix, result);
		return result;
	}

	/** prepares to create or append to headers jar file */
	private synchronized JarOutputStream getHeadersJarOS(String prefix) throws IOException
	{
		JarOutputStream jos = headersJarOSMap.get(prefix);
		if (jos == null)
		{
			// we're going to copy the existing file
			String filename = baseDir + File.separator + prefix + ".headers";
			jos = appendOrCreateJar(filename);
			headersJarOSMap.put (prefix, jos);
		}
		return jos;
	}

	/** prepares to create or append to contents jar file */
	private synchronized JarOutputStream getContentsJarOS(String prefix) throws IOException
	{
		JarOutputStream jos = contentsJarOSMap.get(prefix);
		if (jos == null)
		{
			String filename = baseDir + File.separator + prefix + ".contents";
			jos = appendOrCreateJar(filename);
			contentsJarOSMap.put (prefix, jos);
		}
		return jos;
	}

	/** returns a jar outputstream for the given filename.
	 * copies over jar entries if the file was already existing.
	 * unfortunately, we can't append to the end of a jar file easily.
	 * see e.g.http://stackoverflow.com/questions/2223434/appending-files-to-a-zip-file-with-java
	 * and http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4129445
	 * may consider using truezip at some point, but the doc is dense.
	 */
	private static JarOutputStream appendOrCreateJar(String filename) throws IOException
	{
		JarOutputStream jos;
		File f = new File(filename);
		if (!f.exists())
			return new JarOutputStream(new BufferedOutputStream(new FileOutputStream(filename)));

		// bak* is going to be all the previous file entries
		String bakFilename = filename + ".bak";
		File bakFile = new File(bakFilename);
		f.renameTo(new File(bakFilename));
		jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(filename)));
		JarFile bakJF;
		try { bakJF = new JarFile (bakFilename); }
		catch (Exception e) {
			log.warn ("Bad jar file! " + bakFilename + " size " + new File(filename).length() + " bytes");
			Util.print_exception(e, log);
			return new JarOutputStream(new BufferedOutputStream(new FileOutputStream(filename)));
		}

		// copy all entries from bakJF to jos
		Enumeration<JarEntry> bakEntries = bakJF.entries();
		while (bakEntries.hasMoreElements())
		{
			JarEntry je = bakEntries.nextElement();
			jos.putNextEntry(je);
			InputStream is = bakJF.getInputStream(je);

			// copy over everything from is to jos
			byte buf[] = new byte[32 * 1024]; // randomly 32K
			int nBytes;
			while ((nBytes = is.read(buf)) != -1)
				jos.write(buf, 0, nBytes);

			jos.closeEntry();
		}
		bakFile.delete();

		return jos;
	}

	@Override
	public synchronized boolean hasHeader(String prefix, int msgNum) throws IOException
	{
		Set<String> headers = getHeaders(prefix);
		return headers.contains(msgNum + ".header");
	}

	@Override
	public synchronized Document getHeader(String prefix, int msgNum) throws IOException, ClassNotFoundException
	{
		URL u = new URL("jar:file:///" + baseDir + File.separator + prefix + ".headers!/" + msgNum + ".header");
		ObjectInputStream ois = new ObjectInputStream(u.openStream());
		Document ed = (Document) ois.readObject();
		ois.close();
		return ed;
	}

	@Override
	public synchronized boolean hasContents(String prefix, int msgNum) throws IOException
	{
		Set<String> contents = getContents(prefix);
		return contents.contains(msgNum + ".content");
	}

	@Override
	public synchronized void deleteCache()
	{
		Util.deleteDir(baseDir);
	}

	@Override
	public synchronized void saveHeader(Document d, String prefix, int msgNum) throws FileNotFoundException, IOException
	{
		JarOutputStream jos = getHeadersJarOS(prefix);

		// create the bytes
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream headerOOS = new ObjectOutputStream(baos);
		headerOOS.writeObject(d);
		byte buf[] = baos.toByteArray();

		ZipEntry ze = new ZipEntry(msgNum + ".header");
		ze.setMethod (ZipEntry.DEFLATED);
		jos.putNextEntry(ze);
		jos.write(buf, 0, buf.length);
		jos.closeEntry();
		jos.flush();
	}

	@Override
	public String getContentURL(String prefix, int msgNum)
	{
		String contentFilename = baseDir + File.separatorChar + prefix + ".contents";
		String contentUrl = "jar:file:///" + contentFilename.replace("\\", "/") + "!/" + msgNum + ".content";
		return contentUrl;
	}

	@Override
	public synchronized void saveContents(String contents, String prefix, int msgNum) throws IOException, GeneralSecurityException
	{
		JarOutputStream jos = getContentsJarOS(prefix);

		// create the bytes
		ZipEntry ze = new ZipEntry(msgNum + ".content");
		ze.setMethod (ZipEntry.DEFLATED);
//		byte[] buf = CryptoUtils.getEncryptedBytes(contents.getBytes("UTF-8"));
		byte[] buf = contents.getBytes("UTF-8");
		jos.putNextEntry(ze);
		jos.write(buf, 0, buf.length);
		jos.closeEntry();
		jos.flush();
	}

	public Map<Integer, Document> getAllHeaders(String prefix) throws IOException, ClassNotFoundException
	{
		Map<Integer, Document> result = new LinkedHashMap<Integer, Document>();
		JarFile jarFile = null;
		String fname = baseDir + File.separator + prefix + ".headers";
		try {
			jarFile = new JarFile(fname);
		} catch (Exception e) {
			log.info("No Jar file exists: " + fname);
		}
		if (jarFile == null)
			return result;

		Enumeration<JarEntry> entries = jarFile.entries();
		String suffix = ".header";
		while (entries.hasMoreElements())
		{
			JarEntry je = entries.nextElement();
			String s = je.getName();
			if (!s.endsWith(suffix))
				continue;
			String idx_str = s.substring(0, s.length() - suffix.length());
			int index = -1;
			try { index = Integer.parseInt(idx_str); } catch (Exception e) {
				log.error("Funny file in header: " + index);
			}
			
			ObjectInputStream ois = new ObjectInputStream(jarFile.getInputStream(je));
			Document ed = (Document) ois.readObject();
			ois.close();
			result.put(index, ed);
		}
		return result;
	}

	public Set<Integer> getAllContentIdxs(String prefix) throws IOException, ClassNotFoundException
	{
		Set<Integer> result = new LinkedHashSet<Integer>();
		JarFile jarFile = null;
		String fname = baseDir + File.separator + prefix + ".contents";
		try {
			jarFile = new JarFile(fname);
		} catch (Exception e) {
			log.info("No Jar file exists: " + fname);
		}
		if (jarFile == null)
			return result;

		Enumeration<JarEntry> entries = jarFile.entries();
		String suffix = ".content";
		while (entries.hasMoreElements())
		{
			JarEntry je = entries.nextElement();
			String s = je.getName();
			if (!s.endsWith(suffix))
				continue;
			String idx_str = s.substring(0, s.length() - suffix.length());
			int index = -1;
			try { index = Integer.parseInt(idx_str); } catch (Exception e) {
				log.error("Funny file in header: " + index);
			}
			
			result.add(index);
		}
		return result;
	}
	
	@Override
	public synchronized void pack() throws IOException
	{
		// no need to close the JarFile's.
		// clone xxxJarOSMap.keySet() because xxxJarOSMap will also be modified by packHeaders/packContents
		// and that would be concurrent conflict accesses.
		for (String s: new LinkedHashSet<String>(headersJarOSMap.keySet()))
			packHeaders(s);
		for (String s: new LinkedHashSet<String>(contentsJarOSMap.keySet()))
			packContents(s);
	}

	public synchronized void clear(String tag)
	{
		File f = new File(baseDir + File.separatorChar + tag + ".headers");
		if (f.exists())
			f.delete();
		f = new File(baseDir + File.separatorChar + tag + ".contents");
		if (f.exists())
			f.delete();
	}

	private synchronized void packHeaders(String prefix) throws IOException
	{
		headersMap.remove(prefix);
		JarOutputStream jos = headersJarOSMap.remove(prefix);
		if (jos != null)
			jos.close();
	}

	private synchronized void packContents(String prefix) throws IOException
	{
		contentsMap.remove(prefix);
		JarOutputStream jos = contentsJarOSMap.remove(prefix);
		if (jos != null)
			jos.close();
	}
	
	/* Export selected docs with given prefix */
	public void exportAsMbox(String outFilename, String prefix, Collection<EmailDocument> selectedDocs, boolean append) throws IOException, GeneralSecurityException, ClassNotFoundException
	{
		PrintWriter mbox = new PrintWriter (new FileOutputStream ("filename", append));
		Map<Integer, Document> allHeadersMap = getAllHeaders(prefix);
		Map<Integer, Document> selectedHeadersMap = new LinkedHashMap<Integer, Document>();
		Set<EmailDocument> selectedDocsSet = new LinkedHashSet<EmailDocument>(selectedDocs);

		for (Integer I : allHeadersMap.keySet())
		{
			EmailDocument ed = (EmailDocument) allHeadersMap.get(I);
			if (selectedDocsSet.contains(ed))
				selectedHeadersMap.put(I, ed);
		}
			
		JarFile contentJar = null;
		try {
			contentJar = new JarFile(baseDir + File.separator + prefix + ".contents");
		} catch (Exception e) {
			Util.print_exception(e, log);
			return;
		}
		
		Enumeration<JarEntry> contentEntries = contentJar.entries();
		String suffix = ".content";
		while (contentEntries.hasMoreElements())
		{
			JarEntry c_je = contentEntries.nextElement();
			String s = c_je.getName();
			if (!s.endsWith(suffix))
				continue;
			int index = -1;
			String idx_str = s.substring(0, s.length() - suffix.length());
			try { index = Integer.parseInt(idx_str); } catch (Exception e) {
				log.error("Funny file in header: " + index);
			}
			
			EmailDocument ed = (EmailDocument) selectedHeadersMap.get(index);
			if (ed == null)
				continue; // not selected
			
			byte[] b = Util.getBytesFromStream(contentJar.getInputStream(c_je));
			String contents = new String(b, "UTF-8");
			EmailUtils.printHeaderToMbox(ed, mbox);
			EmailUtils.printBodyAndAttachmentsToMbox (contents, ed, mbox, null);
			mbox.println (contents);
		}
	}
}
