/*
 Copyright 2011 Sudheendra Hangal

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

public class FacetItem implements Comparable<FacetItem>
{
	public String name;
	public String description;
	public int count;
	public String url;
	
	public FacetItem(String name, String description, int count, String url)
	{
		this.name = name; this.description = description; this.count = count; this.url = url;
	}
	
	// compare inconsistent with equals
	public int compareTo(FacetItem other)
	{
		return (other.count - this.count); // higher count comes first
	}
}
