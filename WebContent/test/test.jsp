<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="edu.stanford.muse.ner.tokenizer.POSTokenizer" %>
<%@ page import="edu.stanford.muse.util.Triple" %>
<%@ page import="java.util.List" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureUtils" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.NLPUtils" %>
<%@ page import="opennlp.tools.util.Span" %>
<%@ page import="java.util.ArrayList" %>
<%
    Archive archive = JSPHelper.getArchive(request.getSession());
    Document doc = archive.docForId("/Users/vihari/epadd-data/Creeley-small/ANSWER-Coalition[294204].txt-0");
    String contents = archive.getContents(doc , true);
    POSTokenizer tokenizer = new POSTokenizer();
    //some random type for getting tokens
    List<Triple<String,Integer,Integer>> tokens = tokenizer.tokenize(contents);
    Span[] sents = NLPUtils.sentenceDetector.sentPosDetect(contents);
    for(Span sent: sents) {
        out.println(sent+"<br>"+contents.substring(sent.getStart(),sent.getEnd())+"<br>---------<br>");
        for (Triple<String, Integer, Integer> t : tokens) {
            if(t.getSecond()>sent.getStart() && t.getThird()<sent.getEnd())
                out.println("["+t.getFirst()+", "+t.getSecond()+", "+t.getThird()+"], ");
        }
        out.println("<br>");
    }

    String uc = "";
    int prev_end = 0;
    try {
        for (Triple<String, Integer, Integer> t : tokens) {
            if (!contents.substring(t.getSecond(), t.getThird()).equals(t.getFirst()))
                System.err.println("Improper offsets for: " + t + ", expected: " + contents.substring(t.getSecond(), t.getThird()));
        }

        for (Triple<String, Integer, Integer> t : tokens) {
            System.err.println(t + ", " + prev_end);
            uc += contents.substring(prev_end, t.getSecond());
            prev_end = t.getThird();
            uc += "<u>" + t.getFirst() + "</u>";
        }
    }catch(Exception e){
        e.printStackTrace();
    }
    uc += contents.substring(prev_end,contents.length());
    contents = uc;
    out.println(contents);

%>
