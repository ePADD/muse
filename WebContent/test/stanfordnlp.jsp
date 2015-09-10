<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.nlp.util.CoreMap" %>
<%@ page import="edu.stanford.nlp.ling.CoreAnnotations" %>
<%@ page import="edu.stanford.nlp.ling.CoreLabel" %>
<%@ page import="edu.stanford.nlp.pipeline.*" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%
    Archive archive = JSPHelper.getArchive(session);
    List<Document> docs = archive.getAllDocs();
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.addAnnotator(new TokenizerAnnotator(false));
    pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
    pipeline.addAnnotator(new NERCombinerAnnotator(false));

    int di = 0;
    for (Document doc : docs) {
        String content = archive.getContents(doc, true);
        Annotation document = new Annotation(content);
        try{
            pipeline.annotate(document);
        }catch(Exception e){
            continue;
        }
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);

        for(CoreMap sentence: sentences) {
            String text = sentence.get(CoreAnnotations.TextAnnotation.class);

            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            String str = "";
            for (int ti=0;ti<tokens.size();ti++) {
                CoreLabel token = tokens.get(ti);
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                String nerTag = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                if(!nerTag.equals("ORGANIZATION")) {
                    if(!str.equals(""))
                        out.println(str + "<br>");
                    str = "";
                }
                else{
                    str += " "+word;
                }
            }
        }
        if ((++di)%100==0){
            out.println(di + " of " + docs.size() + " messages processed...<br/>");
            //break;
        }
    }
%>