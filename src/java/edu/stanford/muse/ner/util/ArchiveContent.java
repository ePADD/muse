package edu.stanford.muse.ner.util;

/**
 * Interface that returns the content of a doc in archive*/
public interface ArchiveContent {
    int getSize();
    String getContent(int i);
}