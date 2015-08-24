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
        POSModel posModel;
		try {
			if (sentStream == null) {
				File SentFile = new File("WebContent/WEB-INF/classes/models/en-sent.bin");
				if (SentFile.exists())
					model = new SentenceModel(SentFile);
				else
					log.info(SentFile.getAbsolutePath() + " doesnt exist");
			} else
				model = new SentenceModel(sentStream);

            posModel = new POSModel(posStream);
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
            close(sentStream);
            close(posStream);
            close(tokenStream);
            close(chunkerStream);
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

    //TODO: OpenNLP is too bad with tokenisation of special chars except period. Atleast handle new lines, '>' whicgh are common in the case of ePADD and muse
	public static String[] tokeniseSentence(String text) {
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

    public static List<Pair<String,String>> posTag(String sent){
        String[] tokens = tokenise(sent);
        String[] tags = posTag(tokens);
        if(tokens.length!=tags.length){
            log.warn("Something wrong with POS tagging. Number of POS tags: " + tags.length + " not the same as number of tokens " + tokens.length);
        }
        List<Pair<String,String>> ret = new ArrayList<>();
        for(int i=0;i<Math.min(tokens.length, tags.length);i++)
            ret.add(new Pair<String,String>(tokens[i],tags[i]));
        return ret;
    }

}
