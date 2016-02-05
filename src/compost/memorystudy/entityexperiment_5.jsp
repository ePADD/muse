<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="edu.stanford.nlp.util.CoreMap" %>
<%@ page import="edu.stanford.nlp.ling.CoreAnnotations" %>
<%@ page import="edu.stanford.nlp.ling.CoreLabel" %>
<%@ page import="edu.stanford.nlp.time.SUTime" %>
<%@ page import="edu.stanford.nlp.pipeline.*" %>
<%@ page import="edu.stanford.nlp.time.TimeAnnotator" %>
<%@ page import="edu.stanford.nlp.time.TimeExpression" %>
<%@ page import="edu.stanford.nlp.time.TimeAnnotations" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="java.text.SimpleDateFormat" %>
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
    Map<String, Integer> times = new LinkedHashMap<String, Integer>();
    Map<String, Set<String>> timeContext = new LinkedHashMap<String,Set<String>>();
    //considers only people names
    AddressBook ab = archive.addressBook;
    try {
        boolean originalOnly = true;
        List<Document> docs = archive.getAllDocs();
        Map<String,Integer> allWordFreqs = new LinkedHashMap<String,Integer>();
        Map<String,Integer> sentWordFreqs = new LinkedHashMap<String,Integer>();
        int di = 0;
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        pipeline.addAnnotator(new TokenizerAnnotator(false));
        pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
        pipeline.addAnnotator(new POSTaggerAnnotator(false));
        pipeline.addAnnotator(new TimeAnnotator("sutime", props));

        List<String> cues = Arrays.asList("i", "my");
        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            Date date = ed.getDate();
            int dir = ed.sentOrReceived(ab);
            String content = archive.getContents(doc, true);
            Annotation document = new Annotation(content);
            Calendar c = new GregorianCalendar();
            c.setTime(date);
            document.set(CoreAnnotations.DocDateAnnotation.class, new SimpleDateFormat("YYYY-MM-DD").format(c.getTime()));
            try{
                pipeline.annotate(document);
            }catch(Exception e){
                continue;
            }
            List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
            for(CoreMap sentence: sentences) {
                String text = sentence.get(CoreAnnotations.TextAnnotation.class);
                List<CoreMap> timexAnnsAll = sentence.get(TimeAnnotations.TimexAnnotations.class);
                if(timexAnnsAll!=null){
                    for (CoreMap cm : timexAnnsAll) {
                        //List tokens = cm.get(CoreAnnotations.TokensAnnotation.class);
                        String tex = cm.get(TimeExpression.Annotation.class).getTemporal().getTimexValue();
                        //out.println(tex+"</br>");
                        if(!times.containsKey(tex)){
                            times.put(tex, 0);
                        }
                        times.put(tex, times.get(tex)+1);
                    }
                }
                List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
                for (int ti=0;ti<tokens.size();ti++) {
                    CoreLabel token = tokens.get(ti);
                    String word = token.get(CoreAnnotations.TextAnnotation.class);
                    String posTag = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                    if(!posTag.startsWith("VB"))
                        continue;
                    if(!allWordFreqs.containsKey(word))
                        allWordFreqs.put(word,0);
                    allWordFreqs.put(word, allWordFreqs.get(word)+1);
                    if(timexAnnsAll!=null && timexAnnsAll.size()>0){
                        if(!sentWordFreqs.containsKey(word))
                            sentWordFreqs.put(word,0);
                        sentWordFreqs.put(word, sentWordFreqs.get(word)+1);
                        for (CoreMap cm : timexAnnsAll) {
                            String tex = cm.get(TimeExpression.Annotation.class).getTemporal().getTimexValue();
                            if(!timeContext.containsKey(tex))
                                timeContext.put(tex, new LinkedHashSet<String>());
                            timeContext.get(tex).add(word);
                        }
                    }
                }
            }
            if ((++di)%100==0){
                out.println(di + " of " + docs.size() + " messages processed...<br/>");
                //break;
            }
        }

        List<Pair<String,Integer>> stimes = Util.sortMapByValue(times);
        for(Pair<String,Integer> p: stimes){
            if(p.first == null)
                continue;
            Set<String> contextTerms = timeContext.get(p.first);
            Map<String,Double> cs = new LinkedHashMap<String,Double>();
            if(contextTerms!=null){
                for(String ct: contextTerms){
                    if(!allWordFreqs.containsKey(ct) || !sentWordFreqs.containsKey(ct))
                        continue;
                    cs.put(ct, (double)sentWordFreqs.get(ct)/(double)allWordFreqs.get(ct));
                }
            }
            String context = "";
            List<Pair<String,Double>> scs = Util.sortMapByValue(cs);
            int i=0;
            for(Pair<String,Double> scsp: scs){
                context += scsp.first+","+scsp.second+":::";
                if(i++>30)
                    break;
            }
            out.println(p.getFirst()+" ::: "+p.getSecond()+":::Ctx: "+context+"</br>---------------</br>");
        }
     } catch (Throwable e) {
        e.printStackTrace();
     }
%>
