<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.index.EmailDocument" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="edu.stanford.muse.email.AddressBook" %>
<%@ page import="edu.stanford.muse.util.NLPUtils" %>
<%@ page import="java.io.BufferedReader" %>
<%@ page import="java.io.InputStreamReader" %>
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
    AddressBook ab = archive.addressBook;
    Map<String,Integer> verbs = new LinkedHashMap<String,Integer>();
    try {
        boolean originalOnly = true;
        List<Document> docs = archive.getAllDocs();
        int di = 0;
        Collections.sort(docs);
        List<String> cues = Arrays.asList("i", "my");
        BufferedReader br = new BufferedReader(new InputStreamReader(Util.class.getClassLoader().getResourceAsStream("event_rels_wiki_reverb.txt")));
        Set<String> reverbVerbs = new LinkedHashSet<String>();
        String line;
        while((line=br.readLine())!=null){
            String rel = line.trim();
            String[] words = rel.split(" ");
            //this is the frequency of the phrase
            int freq = Integer.parseInt(words[0]);
            if(freq>1 && words.length>1){
                String lw = words[words.length-1];
                //if(lw.equals("on") || lw.equals("in") || lw.equals("until"))
                reverbVerbs.add(words[words.length-2]+" "+lw);
            }
        }
        out.println("Read "+reverbVerbs.size()+" from file<br/>");
        br.close();
        for (Document doc : docs) {
            EmailDocument ed = (EmailDocument) doc;
            int dir = ed.sentOrReceived(ab);
            boolean snt = (dir & EmailDocument.SENT_MASK) != 0;
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
                    List<String> tokenList = Arrays.asList(tokens);
//                    Span[] chunks = NLPUtils.chunker.chunkAsSpans(tokens, tags);
//                    for(int i=0;i<chunks.length;i++){
//                        //System.err.println(chunks[i].getType());
//                        if(chunks[i].getType().startsWith("VP")){
//                            String token = "";
//                            for(int ti=chunks[i].getStart();ti<chunks[i].getEnd();ti++)
//                                token += tokens[ti]+" ";
//                            if(!verbs.containsKey(token))
//                                verbs.put(token, 0);
//                            verbs.put(token, verbs.get(token)+1);
//                        }
//                    }
//                    for(int i=0;i<tokens.length;i++){
//                        String tag = tags[i];
//                        String token = tokens[i];
//                        if(tag.startsWith("VB") && reverbVerbs.contains(token)){
//                            if(sent.contains(token+" on")){
//                                sent = sent.replaceAll("\\b"+token+"\\b","<span class='highlight'>"+token+"</span>");
//                                out.println(sent+"<br/>");
//                                break;
//                            }
//                        }
//                    }
                    for(String rel: reverbVerbs){
                        if(sent.contains(rel)){
                            String[] relWords = rel.split(" ");
                            String rv = relWords[0];
                            int idx = tokenList.indexOf(rv);
                            if(idx<0 || !tags[idx].startsWith("VB"))
                                continue;
                            sent = sent.replaceAll("\\b"+rel+"\\b","<span class='highlight'>"+rel+"</span>");
                            out.println(sent+"<br/>");
                            break;
                        }
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
