package edu.stanford.muse.index;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Util;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

public class OpenNLP {
	public static void main(String args[]) throws IOException, ClassCastException, ClassNotFoundException {

		try {
			String s = Util.readFile("/tmp/in");
			/*
			List<Pair<String,Float>> pairs = NER.namesFromText(s);
			for (Pair<String,Float> p: pairs) {
				System.out.println (p);
			}
			System.out.println ("-----");
			*/

			InputStream pis = OpenNLP.class.getClassLoader().getResourceAsStream("en-ner-person.bin");
			TokenNameFinderModel pmodel = new TokenNameFinderModel(pis);
			InputStream lis = OpenNLP.class.getClassLoader().getResourceAsStream("en-ner-location.bin");
			TokenNameFinderModel lmodel = new TokenNameFinderModel(lis);
			InputStream ois = OpenNLP.class.getClassLoader().getResourceAsStream("en-ner-organization.bin");
			TokenNameFinderModel omodel = new TokenNameFinderModel(ois);
		    InputStream tokenStream = OpenNLP.class.getClassLoader().getResourceAsStream("en-token.bin");
		    TokenizerModel modelTokenizer = new TokenizerModel(tokenStream);
		    TokenizerME tokenizer = new TokenizerME(modelTokenizer);
			Span[] tokSpans = tokenizer.tokenizePos(s); // Util.tokenize(s).toArray(new String[0]);

			String tokens[] = new String[tokSpans.length];
			for (int i = 0; i < tokSpans.length; i++)
				tokens[i] = s.substring(tokSpans[i].getStart(), tokSpans[i].getEnd());
			
			NameFinderME pFinder = new NameFinderME(pmodel);
			Span[] pSpans = pFinder.find(tokens);
			NameFinderME lFinder = new NameFinderME(lmodel);
			Span[] lSpans = lFinder.find(tokens);
			NameFinderME oFinder = new NameFinderME(omodel);
			Span[] oSpans = oFinder.find(tokens);
			System.out.println ("Names found:");
			for (Span span: pSpans)
			{
				for (int i = span.getStart(); i < span.getEnd(); i++)
					System.out.print (tokens[i] + " ");
				System.out.println();
			}
			
			System.out.println ("Locations found:");
			for (Span span: lSpans)
			{
				for (int i = span.getStart(); i < span.getEnd(); i++)
					System.out.print (tokens[i] + " ");
				System.out.println();
			}

			System.out.println ("Orgs found:");
			for (Span span: oSpans)
			{
				for (int i = span.getStart(); i < span.getEnd(); i++)
					System.out.print (tokens[i] + " ");
				System.out.println();
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}
