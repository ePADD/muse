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
package edu.stanford.muse.datacache;

import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Date;

public class Blob implements Serializable {

public static Log log = LogFactory.getLog(Blob.class);

private final static long serialVersionUID = 1L;

public long size;
public String filename;
public String contentType;

// we currently store content hash both as byte array and string
//@SuppressWarnings("unused")
//private byte[] content_hash;
//private String content_hash_string;

public Date modifiedDate;

transient private static final Parser parser = new AutoDetectParser();
transient private static final ParseContext context = new ParseContext();

public String getName() { return filename; }
public String getResourceURI() { return this.filename; }
//public String getContentHash() { return this.content_hash_string; }
public Date getModifiedDate() { return modifiedDate; }

public long getSize() { return size; }

public String[] getTypes() { return null; } /* dummy */
public void removeCacheURI(String s) { } /* dummy */
public void addCacheURI(String s) { } /* dummy */
public String[] getCacheURIs() { return null; } /* dummy */

public String[] getTopics()
{
    /* empty for now */
    return new String[0];
}

public String toString()
{
    StringBuilder sb = new StringBuilder("filename " + filename + " size = " + size + "\n");
    return sb.toString();
}

public boolean equals (Object o)
{
	if (!(o instanceof Blob))
		return false;
	Blob b = (Blob) o;
//	return (b.filename != this.filename && Util.byteArrayToHexString(b.content_hash).equals(Util.byteArrayToHexString(this.content_hash)) && b.size == this.size);
	return (b.filename != this.filename && b.size == this.size);
}

public int hashCode()
{
	return this.filename.hashCode() ^ Long.toString(this.size).hashCode();
}

public boolean is_image()
{
    return Util.is_image_filename (filename);
}

public String getURI() { return null; }

public Pair<String, String> getContent(BlobStore store)
{
	Metadata metadata = new Metadata();
	StringBuilder metadataBuffer = new StringBuilder();
	ContentHandler handler = new BodyContentHandler(-1); // no character limit
	InputStream stream = null;
	boolean failed = false;

	try {
		stream = store.getInputStream(this);

		try {
			// skip mp3 files, tika has trouble with it and hangs
			if (!Util.nullOrEmpty(this.filename) && !this.filename.toLowerCase().endsWith(".mp3"))
				parser.parse(stream, handler, metadata, context);
	
		    String[] names = metadata.names();
		    //Arrays.sort(names);
		    for (String name : names) {
		    	// some metadata tags are problematic and result in large hex strings... ignore them. (caused memory problems with Henry's archive)
		    	// https://github.com/openplanets/SPRUCE/blob/master/TikaFileIdentifier/python/config.py
		    	// we've seen at least unknown tags: (0x8649) (0x935c) (0x02bc)... better to drop them all
		    	String lname = name.toLowerCase();
		        if (lname.startsWith("unknown tag") || lname.startsWith("intel color profile"))
		        {
		        	log.info ("Warning: dropping metadata tag: " + name + " for blob: " + this.getName());
		        	continue;
		        }
		        metadataBuffer.append(": ");
		        metadataBuffer.append(metadata.get(name));
		        metadataBuffer.append("\n");
		    }
		} catch (Exception e) {
			log.warn("Tika is unable to extract content of blob " + this + ":" + Util.stackTrace(e));
			// often happens for psd files, known tika issue: 
			// http://mail-archives.apache.org/mod_mbox/tika-dev/201210.mbox/%3Calpine.DEB.2.00.1210111525530.7309@urchin.earth.li%3E
			failed = true;
		} finally {
			try { stream.close(); } catch (Exception e) { failed = true; }
		}

	} catch (IOException e) {
		log.warn("Unable to access content of blob " + filename + ":" + Util.stackTrace(e));
		failed = true;
	}

	if (failed)
		return null;
	else
		return new Pair<String,String>(metadataBuffer.toString(), handler.toString());
}

    public static class BlobStats {
        public long unique_data_size;
        public long total_data_size;
        public long n_unique_pics;
        public long n_total_pics;

        public BlobStats(long unique_data_size, long total_data_size,
                long n_unique_pics, long n_total_pics) {
            this.unique_data_size = unique_data_size;
            this.total_data_size = total_data_size;
            this.n_unique_pics = n_unique_pics;
            this.n_total_pics = n_total_pics;
        }
    }
}