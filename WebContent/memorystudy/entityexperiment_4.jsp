<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="edu.stanford.nlp.util.CoreMap" %>
<%@ page import="edu.stanford.nlp.pipeline.Annotation" %>
<%@ page import="edu.stanford.nlp.pipeline.StanfordCoreNLP" %>
<%@ page import="edu.stanford.nlp.ling.CoreAnnotations" %>
<%@ page import="edu.stanford.nlp.ling.CoreLabel" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@include file="../getArchive.jspf" %>
<html>
<head>
    <link rel = "stylesheet" type ="text/css" href="memorystudy/css/screen.css">
    <link href="css/jquery.jgrowl.css" rel="stylesheet" type="text/css"/>
    <meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
    <link rel="icon" href="memorystudy/images/stanford-favicon.gif">
    <jsp:include page="../css/css.jsp"/>
    <script type="text/javascript" src="js/jquery/jquery.js"></script>
    <script type="text/javascript" src="js/jquery.safeEnter.1.0.js"></script>
    <script type="text/javascript" src="js/jquery.jgrowl_minimized.js"></script>
    <script type="text/javascript" src="js/statusUpdate.js"></script>
    <script type="text/javascript" src="js/muse.js"></script>
    <script type="text/javascript" src="js/ops.js"></script>
    <title>Entity stats</title>
    <style> td { padding: 2px 10px 2px 10px}
    .highlight{
        background-color: yellow;}
    </style>
</head>
<b>Entity listing by most recent mention (for testing only)</b><br/>
    <%
    /**
     * Crawls and collects entities that appear in the same sentence with any correspondent in the address book */
    //considers only people names
    System.err.println("JAva version: "+System.getProperty("java.version"));
    AddressBook ab = archive.addressBook;
    try {
        boolean originalOnly = true;
        List<Document> docs = archive.getAllDocs();
        int di = 0;
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        List<String> cues = Arrays.asList("i", "my");
        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            int dir = ed.sentOrReceived(ab);
            String content = archive.getContents(doc, true);
            Annotation document = new Annotation(content);

            pipeline.annotate(document);

            List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
            for(CoreMap sentence: sentences) {
                String text = sentence.get(CoreAnnotations.TextAnnotation.class);
                boolean flag = false;
                String[] words = text.split(" ");
                for(String w: words){
                    if(cues.contains(w.toLowerCase())){
                        flag = true;
                        break;
                    }
                }
                if(!flag)
                    continue;
                List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
                for (int ti=0;ti<tokens.size();ti++) {
                    CoreLabel token = tokens.get(ti);
                    String word = token.get(CoreAnnotations.TextAnnotation.class);
                    String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                    if(ne.equals("DATE") || ne.equals("TIME") || ne.equals("DURATION")){
                        if((++ti)<tokens.size()){
                            token = tokens.get(ti);
                            ne = tokens.get(ti).get(CoreAnnotations.NamedEntityTagAnnotation.class);
                            while(ti<tokens.size() && (ne.equals("DATE") || ne.equals("TIME") || ne.equals("DURATION"))){
                                word += " "+token.get(CoreAnnotations.TextAnnotation.class);
                                ti++;
                                if(ti>=tokens.size())
                                    ne = "";
                                else{
                                    token = tokens.get(ti);
                                    ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                                }
                            }
                        }
                        if(word.split("[<>\\\"']").length==1)
                            text = text.replaceAll("\\b"+word+"\\b", "<span class='highlight'>"+word+"</span>");
                        out.println(text+"<br>");
                        break;
                    }
                }
            }

            if ((++di)%100==0){
                out.println(di + " of " + docs.size() + " messages processed...<br/>");
                //break;
            }
        }
     } catch (Throwable e) {
        e.printStackTrace();
     }
%>
