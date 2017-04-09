package edu.stanford.muse.ner.model.test;

import edu.stanford.muse.Config;
import edu.stanford.muse.util.Pair;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;
import org.aksw.gerbil.dataset.Dataset;
import org.aksw.gerbil.datatypes.ExperimentType;
import org.aksw.gerbil.exceptions.GerbilException;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static opennlp.tools.formats.Conll02NameSampleStream.DOCSTART;

/**
 * Created by vihari on 08/04/17.
 */
public class IllinoisHTMLSampleStream implements ObjectStream<NameSample> {
    LineNumberReader stream;
    opennlp.tools.tokenize.Tokenizer tokenizer;
    Pattern namePattern = Pattern.compile("\\[([A-Z]+) (.+?) \\]");

    public IllinoisHTMLSampleStream(InputStream is){
        stream = new LineNumberReader(new InputStreamReader(is));
        InputStream modelIn = null;
        try {
            modelIn = Config.getResourceAsStream("models/en-token.bin");
            tokenizer = new TokenizerME(new TokenizerModel(modelIn));
        } catch (IOException ie) {
            ie.printStackTrace();
        }
        finally {
            if (modelIn != null) {
                try {
                    modelIn.close();
                }
                catch (IOException e) {
                }
            }
        }
    }

    @Override
    public NameSample read() throws IOException {
        //each line is a sentence
        String line = stream.readLine();
        while(line!=null && StringUtil.isEmpty(line))
            line = stream.readLine();

        if (line == null)
            return null;

        boolean clearAdaptive = false;
        if(line.startsWith(DOCSTART)){
            line=stream.readLine();
            if(StringUtil.isEmpty(line)) {
                clearAdaptive = true;
                line = stream.readLine();
            }
            else
                System.err.println("No empty line after DOC start");
        }

        Matcher m = namePattern.matcher(line);
        Map<String, String> names = new LinkedHashMap<>();
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group(2));
            if(!m.group(1).equals("MISC")) {
                names.put(m.group(2), m.group(1));
            }
        }
        m.appendTail(sb);
        String text = sb.toString();
        String[] tokens = tokenizer.tokenize(text);

        List<Span> spans = new ArrayList<>();
        for (int ti = 0; ti < tokens.length; ti++) {
            int from = ti, to = Math.min(ti + 10, tokens.length);
            for (int tj = to - 1; tj >= from; tj--) {
                String cand = String.join(" ", Arrays.copyOfRange(tokens, ti, tj+1));
                String type = names.get(cand);
                if (type != null) {
                    spans.add(new Span(ti, tj+1, type));
                    //ti will be incremented by one by the loop
                    ti = tj;
                    break;
                }
            }
        }

        //The dataset is such that some names that are recognized in the same sentence are unmarked sometimes
        int numSpans = spans.stream().map(sp->String.join(" ", Arrays.copyOfRange(tokens, sp.getStart(), sp.getEnd()))).collect(Collectors.toSet()).size();
        //sometimes when a sentence is annotated with "[George Bush]", it sometimes also annotates the same sentences "George [Bush]" leaving out the
        if(numSpans != names.size())
            System.err.println("The total names found is not the right number. Found: " + numSpans + " -- " +
                    spans.stream().map(sp->String.join(" ", Arrays.copyOfRange(tokens, sp.getStart(), sp.getEnd()))).collect(Collectors.toSet()) +
                    " regex recognized "+names.size() + " -- " + names);

        return new NameSample(tokens, spans.toArray(new Span[spans.size()]), clearAdaptive);
    }

    @Override
    public void reset() throws IOException, UnsupportedOperationException {
        stream.reset();
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    public static void main(String[] args) throws IOException {
        IllinoisHTMLSampleStream sampleStream = new IllinoisHTMLSampleStream(Config.getResourceAsStream("datasets/IllinoisHTML/train.txt"));
        NameSample sample;
        int numTokens = 0, numNames = 0;
        while((sample = sampleStream.read())!=null){
            numTokens += sample.getSentence().length;
            numNames += sample.getNames().length;
            if(numTokens%10==0)
                System.out.println("Found "+numTokens+" tokens and "+numNames +" names");
        }
        System.out.println("Found "+numTokens+" tokens and "+numNames +" names");
    }
}
