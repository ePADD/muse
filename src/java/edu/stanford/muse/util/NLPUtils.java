package edu.stanford.muse.util;

import java.io.File;
import java.io.InputStream;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;

public class NLPUtils {
	public static SentenceDetectorME	sentenceDetector;
	static {
		InputStream SentStream = NLPUtils.class.getClassLoader()
				.getResourceAsStream("models/en-sent.bin");
		SentenceModel model = null;
		try {
			if (SentStream == null) {
				File SentFile = new File("WebContent/WEB-INF/classes/models/en-sent.bin");
				if (SentFile.exists())
					model = new SentenceModel(SentFile);
				else
					System.err.println(SentFile.getAbsolutePath() + " doesnt exist");
			} else
				model = new SentenceModel(SentStream);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Exception in init'ing sentence model");
		}
		sentenceDetector = new SentenceDetectorME(model);
	}

	public static String[] tokeniseSentence(String text) {
		return sentenceDetector.sentDetect(text);
	}

	public static Span[] tokeniseSentencePos(String text) {
		return sentenceDetector.sentPosDetect(text);
	}
}
