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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import edu.stanford.muse.util.Util;

/** a blob store which accepts blob objects.
each blob can have multiple views associated with it (views
are keyed by strings, e.g. "tn" for thumbnail)
*/

abstract public class BlobStore implements Serializable {

private static final long serialVersionUID = 1L;

public Set<Blob> uniqueBlobs = new LinkedHashSet<Blob>();

// mapping of each data item to a data id
protected Map<Blob, Integer> id_map = new LinkedHashMap<Blob, Integer>();
protected Map<Blob, URL> urlMap = new LinkedHashMap<Blob, URL>();
// data id's are just assigned sequentially starting from 0
protected int next_data_id = 0;

// mapping of each data to its views
protected Map<Blob, Map<String,Object>> views = new LinkedHashMap<Blob, Map<String,Object>>();

/** add a new piece of data. should not already have been added */
public synchronized void add (Blob b)
{
    Util.ASSERT (!this.contains(b));
   
    uniqueBlobs.add(b);
    id_map.put (b, next_data_id);
    views.put (b, new LinkedHashMap<String,Object>());
    next_data_id++;
}

public synchronized void add(Blob b, URL u)
{
    add(b);
    urlMap.put (b, u);
}

/** remove a piece of data, has to be the last one added. */
protected synchronized void remove (Blob b)
{
    Util.ASSERT (this.contains(b));
    uniqueBlobs.remove(b);
    id_map.remove(b); // leaves a hole in id_map, but that's ok
    views.remove (b);
    next_data_id--;
}

/** add o with the supplied key to the map of views for object d */
public synchronized void addView (Blob b, String key, Object o)
{
    Util.ASSERT (this.contains(b));
    views.get(b).put(key, o);
}

/** return the view for data d with the given key */
public synchronized Object getView (Blob b, String view)
{
	Map<String,Object> map = views.get(b);
	if (map == null)
		return null;
    return map.get(view);
}

/** return the view for data d with the given key */
public synchronized boolean hasView (Blob b, String view)
{
	Map<String,Object> map = views.get(b);
	if (map == null)
		return false;
    return map.get(view) != null;
}

public synchronized boolean contains (Blob b)
{
    return uniqueBlobs.contains(b);
}

/** returns the index of the given data item in this store */
protected int index(Blob b)
{
    Integer i = id_map.get(b);
    if (i == null)
        return -1;
    else
        return i.intValue();
}

abstract public byte[] getViewData(Blob b, String key) throws Exception;

/** returns list of views for b. never returns null */
public Collection<String> getViews(Blob b) 
{ 
	Map<String, Object> map = views.get(b);
	if (map == null)
		map = new LinkedHashMap<String, Object>(); // return empty set
	return map.keySet();
}

abstract public long add(Blob b, InputStream istream) throws IOException;
abstract public void generate_thumbnail(Blob b) throws IOException;
abstract public byte[] getDataBytes(Blob b) throws IOException;
abstract public String get_URL(Blob b);
abstract public String getRelativeURL(Blob b);
abstract public String getViewURL(Blob b, String key);
abstract public InputStream getInputStream (Blob b) throws IOException;
abstract public void pack() throws IOException;
public int uniqueBlobs() { return uniqueBlobs.size(); }

protected synchronized void pack_to_stream (ObjectOutputStream oos) throws IOException
{
   oos.writeObject(uniqueBlobs);
   oos.writeObject(id_map);
   oos.writeObject(views);
   oos.writeInt(next_data_id);
}

protected synchronized void unpack_from_stream (ObjectInputStream ois) throws IOException, ClassNotFoundException
{
    uniqueBlobs = (Set<Blob>) ois.readObject();
    id_map = (Map<Blob, Integer>) ois.readObject();
    views = (Map<Blob, Map<String,Object>>) ois.readObject();
    next_data_id = ois.readInt();
}

public String toString()
{
    StringBuilder sb = new StringBuilder();
    sb.append ("Data store with " + uniqueBlobs.size() + " unique blobs");
	//int count = 0;
	//for (Data d : unique_datas)
	//    sb.append (count++ + ". " + d + "\n");
    return sb.toString();
}

}
