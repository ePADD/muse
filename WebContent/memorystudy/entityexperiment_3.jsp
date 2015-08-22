<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="edu.stanford.muse.email.Contact" %>
<%@ page import="edu.stanford.muse.ner.NER" %>
<%@ page import="edu.stanford.muse.util.Triple" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="edu.stanford.muse.util.NLPUtils" %>
<%@ page import="opennlp.tools.util.Span" %>
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
    <style> td { padding: 2px 10px 2px 10px} </style>
</head>
<b>Entity listing by most recent mention (for testing only)</b><br/>
    <%
    /**
     * Crawls and collects entities that appear in the same sentence with any correspondent in the address book */
    //considers only people names
    AddressBook ab = archive.addressBook;
    Map<String,Integer> verbs = new LinkedHashMap<String,Integer>();
    try {
        boolean originalOnly = true;
        List<Document> docs = archive.getAllDocs();
        int di = 0;
        Collections.sort(docs);
        List<String> cues = Arrays.asList("i", "my");
        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            String content = archive.getContents(doc, true);
            String[] sents = NLPUtils.tokeniseSentence(content);
            for(String sent: sents){
                String[] tokens = NLPUtils.tokenise(sent);
                boolean flag = false;
                for(String t: tokens)
                    if(cues.contains(t.toLowerCase())){
                        flag = true;
                        break;
                    }
                if(flag){
                    String[] tags = NLPUtils.posTag(tokens);
                    for(int i=0;i<tokens.length;i++)
                        if(tags[i].startsWith("VB")){
                            String token = tokens[i];
                            if(!verbs.containsKey(token))
                                verbs.put(token, 0);
                            verbs.put(token, verbs.get(token)+1);
                        }
                }
            }
            if ((++di)%1000==0)
                out.println(di + " of " + docs.size() + " messages processed...<br/>");
        }
        List<Pair<String,Integer>> sps = Util.sortMapByValue(verbs);
        for(Pair<String,Integer> p: sps){
            out.println(p.getFirst()+" : "+p.getSecond()+"<br>");
        }
     } catch (Throwable e) {
        e.printStackTrace();
     }
%>
