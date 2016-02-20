package edu.stanford.muse.test;

import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.webapp.SimpleSessions;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Created by vihari on 20/02/16.
 */
public class LuceneQueryBenchmark {
    /**
     * Tests query search on fields untokenized that is on docId
     * **********
     * Archive size: 1567
     * Search time: 0.1146ms averaged over 10000 queries
     * **********
     * **********
     * Archive size: 78284
     * Search time: 9.9517ms averaged over 10000 queries
     * **********
     */
    static void testKeywordQueries(Archive archive){
        long st;
        List<Document> docs = archive.getAllDocs();
        int numTest = 10000;
        Random rand = new Random(2);
        long searchTime = 0;
        for(int i=0;i<numTest;i++) {
            st = System.currentTimeMillis();
            Document doc = docs.get(rand.nextInt(docs.size()));
            try {archive.getDoc(doc);} catch(IOException e){}
            searchTime += System.currentTimeMillis()-st;
        }
        System.out.println(
                "**********\n" +
                "Archive size: "+docs.size()+"\n" +
                "Search time: "+((float)searchTime/numTest)+"ms averaged over "+numTest+" queries\n" +
                "**********");
    }

    public static void main(String[] args) {
        try {
            String userDir = System.getProperty("user.home") + File.separator + "epadd-appraisal" + File.separator + "user-terry-important";
            Archive archive = SimpleSessions.readArchiveIfPresent(userDir);
            testKeywordQueries(archive);
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
