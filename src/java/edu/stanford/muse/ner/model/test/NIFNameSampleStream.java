package edu.stanford.muse.ner.model.test;

import edu.stanford.muse.Config;
import edu.stanford.muse.ner.model.NEType;
import edu.stanford.muse.util.EmailUtils;
import edu.stanford.muse.util.Pair;
import opennlp.tools.formats.Conll03NameSampleStream;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by vihari on 07/04/17.
 *
 * Parses NLP Interchange Format and emits spans
 * An example of a file in this sample can be found at: https://github.com/AKSW/n3-collection/blob/master/News-100.ttl
 */
public class NIFNameSampleStream implements ObjectStream<NameSample> {
    private final ObjectStream<String> lineStream;

    int ni = 0;
    List<NameSample> nameSamples = new ArrayList<>();
    public NIFNameSampleStream(InputStream in) {
        try {
            this.lineStream = new PlainTextByLineStream(in, "UTF-8");
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is available on all JVMs, will never happen
            throw new IllegalStateException(e);
        }
        ni = 0;

        try {
            collectAll();
        } catch(IOException ie){
            System.err.println("Could not initialize namesamples from input stream");
            ie.printStackTrace();
        }
    }

    private void collectAll() throws IOException{
        Map<String, NIF> nifs = new LinkedHashMap<>();
        List<String> block;
        while ((block = readBlock())!=null && block.size()>0){
            NIF nif = NIF.parse(block);
            nifs.put(nif.id, nif);
        }
        //collect sentences and spans
        Map<String, List<Span>> spans = new LinkedHashMap<>();
        nifs.entrySet().stream()
                .filter(e->{
                    Set<NIF.Type> types = e.getValue().types;
                    return !types.contains(NIF.Type.Context) && types.contains(NIF.Type.RFC5147String);
                })
                .forEach(e->{
                    NIF nif = e.getValue();
                    assert nif.ref!=null: "Parsing of NIFs not proper";
                    String ct = null;
                    if(nif.ref == null)
                        System.err.println("This NIF block is unexpected: \n\n" + nif + "\n");

                    if (nif.ref.startsWith("http://dbpedia.org/resource/")) {
                        String rsc = nif.ref.substring("http://dbpedia.org/resource/".length()).replaceAll("_", "");
                        String type = EmailUtils.readDBpedia().get(rsc);
                        if (type!=null){
                            NEType.Type nt = NEType.getCoarseType(NEType.parseDBpediaType(type));
                            if (nt != NEType.Type.OTHER)
                                ct = nt.getDisplayName();
                        }
                    }
                    if(ct!=null) {
                        Span span = new Span(nif.beginIndex, nif.endIndex, ct);
                        String refId = nif.referenceContext;
                        if(!spans.containsKey(refId))
                            spans.put(refId, new ArrayList<>());
                        spans.get(refId).add(span);
                    }
                });

        nifs.entrySet().stream()
                .filter(e->{
                    Set<NIF.Type> types = e.getValue().types;
                    return types.contains(NIF.Type.Context) && types.contains(NIF.Type.RFC5147String)
                })
                .forEach(e->{
                    assert e.getValue().text!=null: "Parsing of NIFs not proper";
                    if(e.getValue().text == null)
                        System.err.println("This NIF block is unexpected: \n\n" + e.getValue() + "\n");
                    String text = e.getValue().text.getFirst();
                    String[] tokens = text.split("\\W+");
                    List<Span> thisSpans = spans.getOrDefault(e.getValue().id, new ArrayList<>());
                    nameSamples.add(new NameSample(tokens,
                            thisSpans.toArray(new Span[thisSpans.size()]),
                            nameSamples.size()==0?true:false));
                });

        int numSents = nameSamples.size();
        int numSpans = spans.values().stream().mapToInt(List::size).sum();
        System.out.println("Found " + numSpans + " in "+numSents+" sentences");
    }

    List<String> readBlock() throws IOException{
        List<String> block = new ArrayList<>();
        String line;
        while((line = lineStream.read())!=null){
            //skip these...
            if (line.startsWith("@prefix")) {
                while ((line = lineStream.read()) != null && !StringUtils.isEmpty(line)) ;
                line = lineStream.read();
                if (line == null)
                    break;
            }
            if(StringUtils.isEmpty(line))
                break;
            block.add(line.trim());
        }

        return block;
    }

    static class NIF{
        enum Type{
            String, Context, RFC5147String
        }
        String id;
        Set<Type> types = new LinkedHashSet<>();
        int beginIndex, endIndex;
        String anchorOf;
        String referenceContext;
        //itsrdf:taIdentRef <http://dbpedia.org/resource/Clinton,_Ontario> ;
        String ref;
        //text and language code
        Pair<String, String> text;

        @Override
        public String toString(){
            StringBuffer sb = new StringBuffer();
            sb.append("ID: "+id+"\n");
            sb.append("Types: "+types);
            sb.append(" [" +beginIndex + ", " + endIndex + "]\n");
            sb.append("Anchor text: " + anchorOf + "\n");
            sb.append("Context: " + referenceContext + "\n");
            sb.append("Ref: " + ref + "\n");
            sb.append("Text: "+text);

            return sb.toString();
        }

        static Pattern p = Pattern.compile("([^\\s]+)[\\s\\n]+(.+?) [;.]", Pattern.MULTILINE);
        static NIF parse(List<String> lines){
            NIF nif = new NIF();
            nif.id = lines.get(0);

            String all = String.join(" ", lines.subList(1, lines.size()));
            //System.out.println("ALl: "+all);
            Matcher m = p.matcher(all);
            while(m.find()){
                String prop = m.group(1), obj = m.group(2);
                //System.out.println(prop+" -- "+obj);
                if(prop.equals("a")) {
                    String[] stypes = obj.split(" , ");
                    Stream.of(stypes).forEach(st->{
                        if(st.contains(":String"))
                            nif.types.add(Type.String);
                        else if(st.contains(":RFC5147String"))
                            nif.types.add(Type.RFC5147String);
                        else if(st.contains(":Context"))
                            nif.types.add(Type.Context);
                    });
                }
                else if(prop.contains(":beginIndex"))
                    nif.beginIndex = Integer.parseInt(obj.split("\\^\\^")[0].replaceAll("\"", ""));
                else if(prop.contains(":endIndex"))
                    nif.endIndex = Integer.parseInt(obj.split("\\^\\^")[0].replaceAll("\"", ""));
                else if(prop.contains(":anchorOf"))
                    nif.anchorOf = obj.split("\\^\\^")[0].replaceAll("\"", "");
                else if(prop.contains(":taIdentRef"))
                    nif.ref = obj.replaceAll("<|>", "");
                else if(prop.contains(":isString")) {
                    if (obj.contains("@"))
                        nif.text = new Pair<>(obj.replaceAll("@[a-z]+$|\"",""), obj.substring(obj.indexOf("@")+1));
                    else
                        nif.text = new Pair<>(obj.replaceAll("\"", ""), "en");
                }
                else if(prop.contains(":referenceContext"))
                    nif.referenceContext = obj;
            }
            System.out.println("Parsed: "+nif);
            return nif;
        }
    }

    @Override
    public NameSample read() throws IOException {
        if(ni<nameSamples.size())
            return nameSamples.get(ni++);
        return null;
    }

    @Override
    public void reset() throws IOException, UnsupportedOperationException {
        lineStream.reset();
    }

    @Override
    public void close() throws IOException {
        lineStream.close();
    }

    public static void main(String[] args){
        InputStream is = Config.getResourceAsStream("RSS-500.ttl");
        NIFNameSampleStream nifs = new NIFNameSampleStream(is);
        System.out.println(String.join("========\n=======",
                nifs.nameSamples.stream().limit(10).map(NameSample::toString).collect(Collectors.toList())));
    }
}
