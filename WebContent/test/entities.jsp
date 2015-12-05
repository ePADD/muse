<%@ page import="java.util.*" %>
<%@ page import="edu.stanford.muse.ner.featuregen.FeatureDictionary" %>
<%@ page import="edu.stanford.muse.webapp.JSPHelper" %>
<%@ page import="edu.stanford.muse.index.Archive" %>
<%@ page import="edu.stanford.muse.index.Document" %>
<%@ page import="edu.stanford.muse.ner.NER" %>
<%@ page import="edu.stanford.muse.util.Pair" %>
<%@ page import="edu.stanford.muse.util.Util" %>
<%@ page import="com.google.gson.Gson" %>
<%@ page import="com.google.gson.reflect.TypeToken" %>
<%@ page import="java.lang.reflect.Type" %>
<%@ page import="edu.stanford.muse.ner.Entity" %>
<%
    class Some{
        List<Short> parse(String[] excS) {
            List<Short> exc = null;
            if (excS != null) {
                exc = new ArrayList<>();
                int ei = 0;
                for (String es : excS)
                    exc.add(Short.parseShort(es));
            }
            return exc;
        }
    }

    String cutoff = request.getParameter("cutoff");
    Map<Short,String> desc = new LinkedHashMap<>();
    desc.put(FeatureDictionary.PERSON,"PERSON");desc.put(FeatureDictionary.COMPANY,"COMPANY");desc.put(FeatureDictionary.BUILDING,"BUILDING");desc.put(FeatureDictionary.PLACE,"PLACE");desc.put(FeatureDictionary.RIVER,"RIVER");desc.put(FeatureDictionary.ROAD,"ROAD");desc.put(FeatureDictionary.UNIVERSITY,"UNIVERSITY");desc.put(FeatureDictionary.MILITARYUNIT,"MILITARYUNIT");desc.put(FeatureDictionary.MOUNTAIN,"MOUNTAIN");desc.put(FeatureDictionary.AIRPORT,"AIRPORT");desc.put(FeatureDictionary.ORGANISATION,"ORGANISATION");desc.put(FeatureDictionary.DRUG,"DRUG");desc.put(FeatureDictionary.NEWSPAPER,"NEWSPAPER");desc.put(FeatureDictionary.ACADEMICJOURNAL,"ACADEMICJOURNAL");desc.put(FeatureDictionary.MAGAZINE,"MAGAZINE");desc.put(FeatureDictionary.POLITICALPARTY,"POLITICALPARTY");desc.put(FeatureDictionary.ISLAND,"ISLAND");desc.put(FeatureDictionary.MUSEUM,"MUSEUM");desc.put(FeatureDictionary.BRIDGE,"BRIDGE");desc.put(FeatureDictionary.AIRLINE,"AIRLINE");desc.put(FeatureDictionary.NPORG,"NPORG");desc.put(FeatureDictionary.GOVAGENCY,"GOVAGENCY");desc.put(FeatureDictionary.SHOPPINGMALL,"SHOPPINGMALL");desc.put(FeatureDictionary.HOSPITAL,"HOSPITAL");desc.put(FeatureDictionary.POWERSTATION,"POWERSTATION");desc.put(FeatureDictionary.AWARD,"AWARD");desc.put(FeatureDictionary.TRADEUNIN,"TRADEUNIN");desc.put(FeatureDictionary.PARK,"PARK");desc.put(FeatureDictionary.HOTEL,"HOTEL");desc.put(FeatureDictionary.THEATRE,"THEATRE");desc.put(FeatureDictionary.LEGISTLATURE,"LEGISTLATURE");desc.put(FeatureDictionary.LIBRARY,"LIBRARY");desc.put(FeatureDictionary.LAWFIRM,"LAWFIRM");desc.put(FeatureDictionary.MONUMENT,"MONUMENT");desc.put(FeatureDictionary.OTHER,"OTHER");
    if(cutoff==null||cutoff.equals("")){
        String options = "";
        options += "<option value='none'>NONE</option>";
        for(Short t: FeatureDictionary.allTypes)
            options += "<option value='"+t+"'>"+desc.get(t)+"</option>";
        %>
        <html>
        <head><title>Entities</title></head>
        <body>
            <style>
                body{
                    text-align: center;
                    padding-top:30px;
                    line-height: 200%;
                }
            </style>
            <%
                if(cutoff.equals(""))
                    out.println("<span style='color:red'>Please specify cutoff for quality score</span><br>");
                out.println("Cutoff&nbsp&nbsp<span style='color:red'>*</span> &nbsp<input name='cutoff'></input><br>");
                out.println("Include types <select name='include'>" + options + "</select><br>");
                out.println("Exclude types <select name='exclude'>"+options+"</select><br>");
                out.println("Maximum Email Documents <input size='5' name='maxdoc' placeholder='1000'></input>");
            %>
            <br>
            <button onclick="trigger()" style='height:30px;font-size:20px'>Fetch</button>
        <script>
            function trigger(){
                names = ["cutoff","include","exclude","maxdoc"];
                params="";
                for(var i=0;i<names.length;i++){
                    val = document.getElementsByName(names[i])[0].value;
                    if(names[i]=="maxdoc" && val=="")
                        val=1000;
                    if(val!=="none") {
                        params += names[i] + "=" + val;
                        if (i < names.length - 1)
                            params += "&";
                    }
                }
                window.location.href=window.location.href+"?"+params;
            }
        </script>
        </body>
        </html>
        <%
    }
    else{
        String excS = request.getParameter("exclude");
        String incS = request.getParameter("include");
        String mds = request.getParameter("maxdoc");
        int md = 10;
        if(mds!=null)
            md = Integer.parseInt(mds);

        List<Short> exc = new ArrayList<>(), inc = new ArrayList<>();
        if(incS==null) {
            List<Short> tmp = new ArrayList<>();
            for(short t: FeatureDictionary.allTypes)
                if(t!=FeatureDictionary.OTHER)
                    tmp.add(t);
            inc = tmp;
        }
        else
            inc.add(Short.parseShort(incS));
        if(excS!=null){
            exc.add(Short.parseShort(excS));
        }
        double theta = Double.parseDouble(cutoff);
        System.err.println("Params: "+
                "Threshold: "+theta+
                "\nexclude: "+exc+
                "\ninclude: "+inc+
                "\nmaxdoc: "+mds);
        Archive archive = JSPHelper.getArchive(session);
        Map<String,Entity> entities = new LinkedHashMap();
        int di=0;
        for(Document doc: archive.getAllDocs()){
            if(di++>md)
                break;
            Map<Short,Map<String,Double>> es = NER.getEntities(archive.getDoc(doc),true);
            for(Short type: inc)
                if(!exc.contains(type)){
                    if(es.containsKey(type))
                        for(String e: es.get(type).keySet()) {
                            double s = es.get(type).get(e);
                            if(s<theta)
                                continue;
                            if (!entities.containsKey(e))
                                entities.put(e, new Entity(e, s));
                            else
                                entities.get(e).freq++;
                        }
                }
        }
        Map<Entity, Double> vals = new LinkedHashMap<>();
        for(Entity e: entities.values())
            vals.put(e, e.score);
        List<Pair<Entity,Double>> lst = Util.sortMapByValue(vals);
        List<Entity> lst1 = new ArrayList<>();
        for(Pair<Entity,Double> p: lst)
            lst1.add(p.first);
        Gson gson = new Gson();
//        for(Pair<Entity,Double> p: lst)
//            out.println(p.getFirst()+"<br>");
        Type listType = new TypeToken<List<Entity>>() {
        }.getType();
        out.println(gson.toJson(lst1, listType));
    }
%>