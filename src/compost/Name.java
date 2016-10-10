package edu.stanford.muse.index;

public class Name implements Comparable<Name>{

	public String name;
	public String canonicalName;
	float weight;
	String type;
	public Name(String n, float w, String t)
	{
		this.name = n;
		this.weight = w;
		this.type = t;
		this.canonicalName = name.toLowerCase(); // can do other canonicalizations later...
	}
	
	public String toString()
	{
		return this.name + " (" + String.format ("%.0f", weight) + ")";
	}

	@Override
	public int compareTo(Name other) {
		// TODO Auto-generated method stub
		return (other.weight - this.weight > 0) ? 1 : -1;
	}
	
	
}
