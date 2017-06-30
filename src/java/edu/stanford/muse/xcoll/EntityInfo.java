package edu.stanford.muse.xcoll;

import java.util.Date;

public class EntityInfo {
    public String displayName;
    public int archiveNum; // this number (0 onwards) refers to the CrossCollectionSearch.archiveMetadata
    public boolean isConfirmed;
    public Date firstDate, lastDate;
    public int count;
}
