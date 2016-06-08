package edu.stanford.muse.ner;

import edu.stanford.muse.email.StatusProvider;
import edu.stanford.muse.exceptions.CancelledException;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.index.Indexer;
import edu.stanford.muse.ner.featuregen.FeatureDictionary;
import edu.stanford.muse.ner.model.NERModel;
import edu.stanford.muse.ner.model.BMMModel;
import edu.stanford.muse.ner.tokenizer.Tokenizer;
import edu.stanford.muse.util.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This is the only class dependent on ePADD in this package and has all the interfacing functionality
 *
 * TODO: trainAndrecognise and train methods should take proper options argument and there should be a class that represents training options*/
public class NER implements StatusProvider {
    public static Log log = LogFactory.getLog(NER.class);
    //names and names_original should include all the names in the title
    public static String EPER = "en_person", ELOC = "en_loc", EORG = "en_org", NAMES_ORIGINAL = "en_names_original";
    public static String EPER_TITLE = "en_person_title", ELOC_TITLE = "en_loc_title", EORG_TITLE = "en_org_title";
    public static String NAMES_OFFSETS = "en_names_offsets";

    String status;
    double pctComplete = 0;
    boolean cancelled = false;
    Archive archive = null;
    NERModel nerModel;
    //in seconds
    long time = -1, eta = -1;
    static FieldType ft;
    StatusProvider statusProvider = null;

    public static class NERStats {
        //non-repeating number of instances of each type
        public Map<Short, Integer> counts;
        //total number of entities of each type recognised
        public Map<Short, Integer> rcounts;
        public Map<Short, Set<String>> all;

        public NERStats() {
            counts = new LinkedHashMap<>();
            rcounts = new LinkedHashMap<>();
            all = new LinkedHashMap<>();
        }

        public void update(Span[] chunks) {
            Map<Short, List<Span>> fineTypes = Arrays.asList(chunks).stream().collect(Collectors.groupingBy(c -> c.type));
            Map<Short, List<Span>> coarseTypes = Arrays.asList(chunks).stream().collect(Collectors.groupingBy(c -> FeatureDictionary.getCoarseType(c.type)));

            Set<Short> allTypes = new LinkedHashSet<>();
            allTypes.addAll(fineTypes.keySet());
            allTypes.addAll(coarseTypes.keySet());
            for (Short type : fineTypes.keySet()) {
                List<Span> spans;
                //PER, LOC, ORG are both in fine types and coarse types list, for these types we keep track of all the entities that have coarse type that maps to these
                if (coarseTypes.containsKey(type))
                    spans = coarseTypes.get(type);
                else
                    spans = fineTypes.get(type);
                if (!rcounts.containsKey(type))
                    rcounts.put(type, 0);
                rcounts.put(type, rcounts.get(type) + spans.size());

                if (!all.containsKey(type))
                    all.put(type, new HashSet<>());
                all.get(type).addAll(spans.stream().map(c->c.text).collect(Collectors.toList()));

                counts.put(type, all.get(type).size());
            }

        }

        @Override
        public String toString() {
            String str = "";
            for (Short t : counts.keySet())
                str += "Type: " + t + ":" + counts.get(t) + "\n";
            return str;
        }
    }

    public NERStats stats;

    static {
        ft = new FieldType();
        ft.setStored(true);
        ft.setIndexed(true);
        ft.freeze();
    }

    public NER(Archive archive, NERModel nerModel) {
        this.archive = archive;
        this.nerModel = nerModel;
        time = 0;
        eta = 10 * 60;
        stats = new NERStats();
    }

    public static List<Triple<String, Integer, Integer>> getNameOffsets(org.apache.lucene.document.Document doc, boolean body) {
        Span[] chunks = getEntities(doc, body);
        return Arrays.asList(chunks).stream().map(c->new Triple<>(c.text,c.start,c.end)).collect(Collectors.toList());
    }

    public static Span[] getEntities(org.apache.lucene.document.Document doc, boolean body) {
        String[] fields;
        if (body)
            fields = new String[]{EPER, ELOC, EORG};
        else
            fields = new String[]{EPER_TITLE, ELOC_TITLE, EORG_TITLE};
        List<Span> names = new ArrayList<>();
        for (String field : fields) {
            String fv = doc.get(field);
            String[] txtChunks = fv.split(Indexer.NAMES_FIELD_DELIMITER);
            if(fv!=null && fv.length()>0)
                names.addAll(Arrays.asList(txtChunks).stream().map(Span::parse).collect(Collectors.toList()));
        }
        return names.toArray(new Span[names.size()]);
    }

    /**
     * A convenience method that can come handy say when we know entities mentioned in a message and want the entities mentioned in a sentence or a chunk of the content
     *
     * @return entities found in the content in the structure used by NERModel interface
     * @arg content - Typically a substring of the content from which the entities (second arg; eMentions) are extracted.
     */
    public static Span[] getEntitiesInDoc(String content, Span[] eMentions) {
        Tokenizer tokenizer = BMMModel.tokenizer;
        List<Triple<String, Integer, Integer>> offsets = tokenizer.tokenize(content);
        Set<String> tokens = offsets.stream().map(t->t.first).collect(Collectors.toSet());
        List<Span> foundChunks = Arrays.asList(eMentions).stream().filter(c->tokens.contains(c)).collect(Collectors.toList());

        return foundChunks.toArray(new Span[foundChunks.size()]);
    }

    /**Use this method with caution, resolving Lucene Doc corresponding to doc can be a costly operation depending on the archive*/
    public static Span[] getEntities(Document doc, boolean body, Archive archive) {
        try {
            org.apache.lucene.document.Document ldoc = archive.getLuceneDoc(doc);
            return getEntities(ldoc, body);
        } catch (IOException e) {
            Util.print_exception("!!Exception while accessing named entities in the doc", e, log);
            return null;
        }
    }

    public static org.apache.lucene.document.Document updateDoc(org.apache.lucene.document.Document ldoc, Span[] chunks, boolean body, Archive archive){
        //Span::parsablePrint prints the finest available type of the span, hence it is not required to store fine entities separately
        List<String> persons = Arrays.asList(chunks).stream().filter(c -> FeatureDictionary.getCoarseType(c.type) == FeatureDictionary.PERSON).map(Span::parsablePrint).collect(Collectors.toList());
        List<String> locs = Arrays.asList(chunks).stream().filter(c -> FeatureDictionary.getCoarseType(c.type) == FeatureDictionary.PLACE).map(Span::parsablePrint).collect(Collectors.toList());
        List<String> orgs = Arrays.asList(chunks).stream().filter(c -> FeatureDictionary.getCoarseType(c.type) == FeatureDictionary.ORGANISATION).map(Span::parsablePrint).collect(Collectors.toList());

        if(body) {
            ldoc.removeField(EPER);
            ldoc.removeField(ELOC);
            ldoc.removeField(EORG);
        }else {
            ldoc.removeField(EPER_TITLE);
            ldoc.removeField(ELOC_TITLE);
            ldoc.removeField(EORG_TITLE);
        }

        if (body) {
            ldoc.add(new StoredField(EPER, Util.join(persons, Indexer.NAMES_FIELD_DELIMITER)));
            ldoc.add(new StoredField(ELOC, Util.join(locs, Indexer.NAMES_FIELD_DELIMITER)));
            ldoc.add(new StoredField(EORG, Util.join(orgs, Indexer.NAMES_FIELD_DELIMITER)));
        } else {
            ldoc.add(new StoredField(EPER_TITLE, Util.join(persons, Indexer.NAMES_FIELD_DELIMITER)));
            ldoc.add(new StoredField(ELOC_TITLE, Util.join(locs, Indexer.NAMES_FIELD_DELIMITER)));
            ldoc.add(new StoredField(EORG_TITLE, Util.join(orgs, Indexer.NAMES_FIELD_DELIMITER)));
        }

        String originalContent = archive.getContents(ldoc, true);
        List<String> names_original = new ArrayList<>();
        int ocs = originalContent.length();
        for (Span chunk: chunks) {
            if(chunk == null)
                continue;
            String name = chunk.text;
            if (chunk.start < ocs)
                names_original.add(name);
        }

        ldoc.add(new StoredField(NAMES_ORIGINAL, Util.join(names_original, Indexer.NAMES_FIELD_DELIMITER)));
        return ldoc;
    }

    //main method trains the model, recognizes the entities and updates the doc.
    public void recognizeArchive() throws CancelledException, IOException {
        time = 0;
        archive.openForRead();
        archive.setupForWrite();

        if (cancelled) {
            status = "Cancelling...";
            throw new CancelledException();
        }

        List<Document> docs = archive.getAllDocs();

        if (cancelled) {
            status = "Cancelling...";
            throw new CancelledException();
        }

        int di = 0, ds = docs.size();
        int ps = 0, ls = 0, os = 0;

        long totalTime = 0, updateTime = 0, recTime = 0, duTime = 0, snoTime = 0;
        long st1 = System.currentTimeMillis();
        for (Document doc : docs) {
            long st = System.currentTimeMillis();
            org.apache.lucene.document.Document ldoc = archive.getLuceneDoc(doc);
            //pass the lucene doc instead of muse doc, else a major performance penalty
            //do not recognise names in original content and content separately
            //Its possible to improve the performance further by using linear kernel
            // instead of RBF kernel and classifier instead of a regression model
            // (the confidence scores of regression model can be useful in segmentation)
            String content = archive.getContents(ldoc, false);
            String title = archive.getTitle(ldoc);
            //original content is substring of content;
            Span[] chunks = nerModel.find(content);
            Span[] chunksTitle = nerModel.find(title);
            recTime += System.currentTimeMillis() - st;
            st = System.currentTimeMillis();

            stats.update(chunks);
            stats.update(chunksTitle);
            updateTime += System.currentTimeMillis() - st;
            st = System.currentTimeMillis();

            ldoc = updateDoc(ldoc, chunks, true, archive);
            ldoc = updateDoc(ldoc, chunksTitle, false, archive);
            //log.info("Found: "+names.size()+" total names and "+names_original.size()+" in original");

            //TODO: Sometimes, updating can lead to deleted docs and keeping these deleted docs can bring down the search performance
            //Makes me think building a new index could be faster
            archive.updateDocument(ldoc);
            duTime += System.currentTimeMillis() - st;
            di++;

            pctComplete = 30 + ((double) di / (double) ds) * 70;
            double ems = (double) (totalTime * (ds - di)) / (double) (di * 1000);
            status = "Recognized entities in " + Util.commatize(di) + " of " + Util.commatize(ds) + " emails ";
            //Util.approximateTimeLeft((long)ems/1000);
            eta = (long) ems;

            if (di % 100 == 0)
                log.info(status);
            time += System.currentTimeMillis() - st;

            if (cancelled) {
                status = "Cancelling...";
                throw new CancelledException();
            }
        }

        log.info("Trained and recognised entities in " + di + " docs in " + (System.currentTimeMillis()-st1) + "ms");
        archive.close();
        //prepare to read again.
        archive.openForRead();
    }

    //arrange offsets such that the end offsets are in increasing order and if there are any overlapping offsets, the bigger of them should appear first
    //to make sure the redaction is proper.
    private static void arrangeOffsets(List<Triple<String, Integer, Integer>> offsets) {
        Collections.sort(offsets, (t1, t2) -> {
            if (t1.getSecond() != t2.getSecond())
                return t1.getSecond() - t2.getSecond();
            else
                return t2.getThird() - t1.getThird();
        });
    }

    public static Span[] getCoarseEntities(org.apache.lucene.document.Document ldoc, Short ct, boolean body){
        if(!FeatureDictionary.mappings.containsKey(ct)) {
            log.warn("Type: " + ct + " is not a recognized coarse type!!");
            return new Span[]{};
        }
        Span[] entities = getEntities(ldoc, body);
        List<Span> es = new ArrayList<>();
        if(es!=null)
            es = Arrays.asList(entities).stream()
                    .filter(s -> s!=null && FeatureDictionary.mappings.get(ct).contains(s.type))
                    .collect(Collectors.toList());
        return es.toArray(new Span[es.size()]);
    }

    public static Span[] getCoarseEntities(Document doc, Short ct, boolean body, Archive archive) {
        try {
            org.apache.lucene.document.Document ldoc = archive.getLuceneDoc(doc);
            return getCoarseEntities(ldoc, ct, body);
        } catch(IOException e) {
            log.warn("No lucene doc found for "+doc.getUniqueId()+"!!");
            return new Span[]{};
        }
    }

    //retains only filtered entities
    public static String retainOnlyNames(String text, org.apache.lucene.document.Document doc) {
        List<Triple<String, Integer, Integer>> offsets = edu.stanford.muse.ner.NER.getNameOffsets(doc, true);
        if (offsets == null) {
            //mask the whole content
            offsets = new ArrayList<>();
            log.warn("Retain only names method received null offset, redacting the entire text");
        }

        int len = text.length();
        offsets.add(new Triple<>(null, len, len)); // sentinel
        StringBuilder result = new StringBuilder();
        int prev_name_end_pos = 0; // pos of first char after previous name

        //make sure the offsets are in order, i.e. the end offsets are in increasing order
        arrangeOffsets(offsets);
        List<String> people = Arrays.asList(getCoarseEntities(doc, FeatureDictionary.PERSON, true)).stream().map(s->s.text).collect(Collectors.toList());
        List<String> orgs = Arrays.asList(getCoarseEntities(doc, FeatureDictionary.ORGANISATION, true)).stream().map(s->s.text).collect(Collectors.toList());
        List<String> places = Arrays.asList(getCoarseEntities(doc, FeatureDictionary.PLACE, true)).stream().map(s->s.text).collect(Collectors.toList());
        Set<String> allEntities = new LinkedHashSet<>();
        if (people != null)
            allEntities.addAll(people);
        if (orgs != null)
            allEntities.addAll(orgs);
        if (places != null)
            allEntities.addAll(places);

        for (Triple<String, Integer, Integer> t : offsets) {
            String entity = t.first;
            if (!allEntities.contains(entity))
                continue;

            int begin_pos = t.second();
            int end_pos = t.third();
            if (begin_pos > len || end_pos > len) {
                //TODO: this is unclean. Happens because we concat body & title together when we previously generated these offsets but now we only have body.
                begin_pos = end_pos = len;
            }

            if (prev_name_end_pos > begin_pos) // something strange, but possible - the same string can be recognized as multiple named entity types
                continue;
            String filler = text.substring(prev_name_end_pos, begin_pos);
            //filler = filler.replaceAll("\\w", "."); // CRITICAL: \w only matches (redacts) english language
            filler = filler.replaceAll("[^\\p{Punct}\\s]", ".");
            result.append(filler);
            result.append(text.substring(begin_pos, end_pos));
            prev_name_end_pos = end_pos;
        }

        return result.toString();
    }

    @Override
    public String getStatusMessage() {
        if (statusProvider != null)
            return statusProvider.getStatusMessage();

        return JSONUtils.getStatusJSON(status, (int) pctComplete, time, eta);
    }

    @Override
    public void cancel() {
        cancelled = true;
        if (statusProvider != null)
            statusProvider.cancel();
    }

    @Override
    public boolean isCancelled() {
        return cancelled || statusProvider.isCancelled();
    }
}
