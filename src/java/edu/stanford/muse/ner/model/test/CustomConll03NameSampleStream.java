package edu.stanford.muse.ner.model.test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.muse.Config;
import opennlp.tools.formats.Conll02NameSampleStream;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.*;

/**
 * An import stream which can parse the CONLL03 data.
 */
public class CustomConll03NameSampleStream implements ObjectStream<NameSample>{

    public enum DATASET {
        CONLL_EN,
        CONLL_DE,
        NER_WEL_ILLINOIS
    }

    private DATASET dataset;
    private final LineNumberReader lineStream;

    private final int types;

    /**
     *
     * @param in
     * @param types
     */
    public CustomConll03NameSampleStream(DATASET dataset, InputStream in, int types) {

        this.dataset = dataset;
        try {
            this.lineStream = new LineNumberReader(new InputStreamReader(in, "UTF-8"));
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is available on all JVMs, will never happen
            throw new IllegalStateException(e);
        }
        this.types = types;
    }

    static final Span extract(int begin, int end, String beginTag) throws InvalidFormatException {

        String type = beginTag.substring(2);

        if ("PER".equals(type)) {
            type = "person";
        }
        else if ("LOC".equals(type)) {
            type = "location";
        }
        else if ("MISC".equals(type)) {
            type = "misc";
        }
        else if ("ORG".equals(type)) {
            type = "organization";
        }
        else {
            throw new InvalidFormatException("Unknown type: " + type);
        }

        return new Span(begin, end, type);
    }

    public NameSample read() throws IOException {

        List<String> sentence = new ArrayList<String>();
        List<String> tags = new ArrayList<String>();

        boolean isClearAdaptiveData = false;

        // Empty line indicates end of sentence

        String line;
        while ((line = lineStream.readLine()) != null && !StringUtil.isEmpty(line)) {

            if (line.contains(Conll02NameSampleStream.DOCSTART)) {
                isClearAdaptiveData = true;
                String emptyLine = lineStream.readLine();

                if (!StringUtil.isEmpty(emptyLine))
                    throw new IOException("Empty line after -DOCSTART- not empty: '" + emptyLine +"'!");

                continue;
            }

            String fields[] = line.split("[\\s|\\t]+");

            // For English: WORD  POS-TAG SC-TAG NE-TAG
            if (DATASET.CONLL_EN.equals(dataset) && (fields.length == 4)) {
                sentence.add(fields[0]);
                tags.add(fields[3]); // 3 is NE-TAG
            }
            // For German: WORD  LEMA-TAG POS-TAG SC-TAG NE-TAG
            else if (DATASET.CONLL_DE.equals(dataset) && (fields.length == 5)) {
                sentence.add(fields[0]);
                tags.add(fields[4]); // 4 is NE-TAG
            }
            else if (DATASET.NER_WEL_ILLINOIS.equals(dataset) && fields.length>6) {
                sentence.add(fields[5]);
                tags.add(fields[0]);
            }
            else {
                throw new IOException("Incorrect number of fields per line for language: '" + line + "'! Found: "+fields.length+" Line no: "+lineStream.getLineNumber());
            }
        }

        if (sentence.size() > 0) {

            // convert name tags into spans
            List<Span> names = new ArrayList<Span>();

            int beginIndex = -1;
            int endIndex = -1;
            for (int i = 0; i < tags.size(); i++) {

                String tag = tags.get(i);

                if (tag.endsWith("PER") &&
                        (types & Conll02NameSampleStream.GENERATE_PERSON_ENTITIES) == 0)
                    tag = "O";

                if (tag.endsWith("ORG") &&
                        (types & Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES) == 0)
                    tag = "O";

                if (tag.endsWith("LOC") &&
                        (types & Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES) == 0)
                    tag = "O";

                if (tag.endsWith("MISC") &&
                        (types & Conll02NameSampleStream.GENERATE_MISC_ENTITIES) == 0)
                    tag = "O";

                if (tag.equals("O")) {
                    // O means we don't have anything this round.
                    if (beginIndex != -1) {
                        names.add(extract(beginIndex, endIndex, tags.get(beginIndex)));
                        beginIndex = -1;
                        endIndex = -1;
                    }
                }
                else if (tag.startsWith("B-")) {
                    // B- prefix means we have two same entities next to each other
                    if (beginIndex != -1) {
                        names.add(extract(beginIndex, endIndex, tags.get(beginIndex)));
                    }
                    beginIndex = i;
                    endIndex = i + 1;
                }
                else if (tag.startsWith("I-")) {
                    // I- starts or continues a current name entity
                    if (beginIndex == -1) {
                        beginIndex = i;
                        endIndex = i + 1;
                    }
                    else if (!tag.endsWith(tags.get(beginIndex).substring(1))) {
                        // we have a new tag type following a tagged word series
                        // also may not have the same I- starting the previous!
                        names.add(extract(beginIndex, endIndex, tags.get(beginIndex)));
                        beginIndex = i;
                        endIndex = i + 1;
                    }
                    else {
                        endIndex ++;
                    }
                }
                else {
                    throw new IOException("Invalid tag: " + tag);
                }
            }

            // if one span remains, create it here
            if (beginIndex != -1)
                names.add(extract(beginIndex, endIndex, tags.get(beginIndex)));

            return new NameSample(sentence.toArray(new String[sentence.size()]), names.toArray(new Span[names.size()]), isClearAdaptiveData);
        }
        else if (line != null) {
            // Just filter out empty events, if two lines in a row are empty
            return read();
        }
        else {
            // source stream is not returning anymore lines
            return null;
        }
    }

    public void reset() throws IOException, UnsupportedOperationException {
        lineStream.reset();
    }

    public void close() throws IOException {
        lineStream.close();
    }

    public static void main(String[] args) throws IOException {
        InputStream nwiTestIn = Config.getResourceAsStream("NERWeb" + File.separator +  "test.dat");
        InputStream nwiTrainIn = Config.getResourceAsStream("NERWeb" + File.separator + "train.dat");
        CustomConll03NameSampleStream nwiTrainStream = new CustomConll03NameSampleStream(CustomConll03NameSampleStream.DATASET.NER_WEL_ILLINOIS, nwiTrainIn, 7);
        while(nwiTrainStream.read()!=null);
    }

}
