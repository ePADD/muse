package edu.stanford.muse.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.chunker.Chunker;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class NLPUtils {
    public static Log log					= LogFactory.getLog(NLPUtils.class);

    public static SentenceDetectorME	sentenceDetector;
    public static POSTagger posTagger;
    public static Tokenizer tokenizer;
    public static Chunker chunker;

	static {
		InputStream sentStream = NLPUtils.class.getClassLoader()
				.getResourceAsStream("models/en-sent.bin");
		SentenceModel model = null;
        InputStream posStream = NLPUtils.class.getClassLoader()
                .getResourceAsStream("models/en-pos-maxent.bin");
        InputStream tokenStream = NLPUtils.class.getClassLoader()
                .getResourceAsStream("models/en-token.bin");
        InputStream chunkerStream = NLPUtils.class.getClassLoader()
                .getResourceAsStream("models/en-chunker.bin");
        try {
			if (sentStream == null) {
				File SentFile = new File("WebContent/WEB-INF/classes/models/en-sent.bin");
				if (SentFile.exists())
					model = new SentenceModel(SentFile);
				else
					log.info(SentFile.getAbsolutePath() + " doesnt exist");
			} else
				model = new SentenceModel(sentStream);

            POSModel posModel = new POSModel(posStream);
            posTagger = new POSTaggerME(posModel);
            TokenizerModel tokenizerModel = new TokenizerModel(tokenStream);
            tokenizer = new TokenizerME(tokenizerModel);

            ChunkerModel chunkerModel = new ChunkerModel(chunkerStream);
            chunker = new ChunkerME(chunkerModel);
        } catch (Exception e) {
			e.printStackTrace();
			log.warn("Exception in init'ing sentence model");
		    Util.print_exception(e, log);
        }finally {
            InputStream[] streams = new InputStream[]{sentStream, posStream, tokenStream, chunkerStream};
            for(InputStream is: streams)
                if(is!=null)
                    close(is);
        }
        sentenceDetector = new SentenceDetectorME(model);
	}

    private static void close(InputStream stream){
        try{
            stream.close();
        }catch(IOException ie){
            log.warn("Could not close stream");
        }
    }

    //TODO: OpenNLP is too bad with tokenisation of special chars except period. At least handle new lines, '>' whicgh are common in the case of ePADD and muse
	public static String[] tokeniseSentence(String text) {
        if(text == null)
            return new String[]{};
        return sentenceDetector.sentDetect(text);
	}

	public static Span[] tokeniseSentenceAsSpan(String text) {
		return sentenceDetector.sentPosDetect(text);
	}

    public static String[] tokenise(String sentence){
        return tokenizer.tokenize(sentence);
    }

    public static String[] posTag(String[] tokens) {
        return posTagger.tag(tokens);
    }

    public static List<String> getAllProperNouns(String content){
        String[] sents = tokeniseSentence(content);
        List<String> properNouns = new ArrayList<>();
        for(String sent: sents) {
            String[] tokens = tokenise(sent);
            String[] tags = posTag(tokens);
            Span[] chunks = chunker.chunkAsSpans(tokens,tags);
            for(Span chunk: chunks){
                String chunkText = "";
                if("NP".equals(chunk.getType())){
                    boolean NNP = false;
                    for(int s = chunk.getStart();s<chunk.getEnd();s++){
                        if("NNP".equals(tags[s]))
                            NNP = true;
                        chunkText += tokens[s];
                        if(s<(chunk.getEnd()-1))
                            chunkText+=" ";
                    }
                    if(NNP)
                        properNouns.add(chunkText);
                }
            }
        }
        return properNouns;
    }

    public static List<Pair<String,String>> posTag(String sent){
        String[] tokens = tokenise(sent);
        String[] tags = posTag(tokens);
        if(tokens.length!=tags.length){
            log.warn("Something wrong with POS tagging. Number of POS tags: " + tags.length + " not the same as number of tokens " + tokens.length);
        }
        List<Pair<String,String>> ret = new ArrayList<>();
        for(int i=0;i<Math.min(tokens.length, tags.length);i++)
            ret.add(new Pair<>(tokens[i],tags[i]));
        return ret;
    }

    public static List<Pair<String,Triple<String,Integer,Integer>>> posTagWithOffsets(String sent){
        Span[] tokenSpans = tokenizer.tokenizePos(sent);
        String[] tokens = new String[tokenSpans.length];
        for(int si=0;si<tokenSpans.length;si++)
            tokens[si] = tokenSpans[si].getCoveredText(sent).toString();
        String[] tags = posTag(tokens);
        if(tokens.length!=tags.length){
            log.warn("Something wrong with POS tagging. Number of POS tags: " + tags.length + " not the same as number of tokens " + tokens.length);
        }
        List<Pair<String,Triple<String,Integer,Integer>>> ret = new ArrayList<>();
        for(int i=0;i<Math.min(tokens.length, tags.length);i++)
            ret.add(new Pair<>(tokens[i],new Triple<>(tags[i], tokenSpans[i].getStart(), tokenSpans[i].getEnd())));
        return ret;
    }
}
