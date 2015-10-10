package edu.stanford.muse.wpmine;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;

/**
 * Created by vihari on 10/10/15.
 * Use this to parse the DBpedia instance NT file.
 */
public class DBpediaTypeParser {
    public static Log log = LogFactory.getLog(DBpediaTypeParser.class);

    public static Map<String,String> parseOntology(String ontologyFile) {
        try {
            LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new BZip2CompressorInputStream(DBpediaTypeParser.class.getClassLoader().getResourceAsStream(ontologyFile)), "UTF-8"));
            //node -> Parent
            Map<String,String> tree = new LinkedHashMap<>();
            String line;
            int typeS = "<http://dbpedia.org/ontology/".length();
            Set<String> allTypes = new LinkedHashSet<>();
            while((line=lnr.readLine())!=null){
                String[] fields = line.split("\\s+");
                String[] fs = new String[]{fields[0],fields[2]};
                for(String f: fs) {
                    if (f.startsWith("<http://dbpedia.org/ontology/")) {
                        String type = f.substring(typeS, f.length() - 1);
                        allTypes.add(type);
                    }
                }
                //there are cases where, the DBpedia type is the sub-class of schema types, we ignore such lines and only consider dbpedia type nodes
                if(fields.length<3 || !fields[0].startsWith("<http://dbpedia.org/ontology/") || !fields[1].equals("<http://www.w3.org/2000/01/rdf-schema#subClassOf>") || !fields[2].startsWith("<http://dbpedia.org/ontology/"))
                    continue;
                //System.err.println(fields[0]+" - "+fields[2]);
                String type1 = fields[0].substring(typeS, fields[0].length()-1);
                String type2 = fields[2].substring(typeS, fields[2].length()-1);
                tree.put(type1, type2);
            }
            //type ->  path to root
            Map<String,String> ontology = new LinkedHashMap<>();
            for(String val: allTypes){
                String path = val+"|";
                String parent = val;
                while(tree.containsKey(parent)) {
                    path += tree.get(parent) + "|";
                    parent = tree.get(parent);
                }
                path = path.substring(0, path.length()-1);
                ontology.put(val, path);
            }
            return ontology;
        } catch(IOException e){
            log.info("Exception while trying to read ontology file: "+ontologyFile);
            edu.stanford.muse.util.Util.print_exception(e,log);
            return null;
        }
    }

    public static void parse(String typesFile, String typeOntologyFile, String outPath) {
        try {
            Map<String,String> ontology = parseOntology(typeOntologyFile);
            if(ontology==null)
                return;
            LineNumberReader lnr = new LineNumberReader(new InputStreamReader(new BZip2CompressorInputStream(DBpediaTypeParser.class.getClassLoader().getResourceAsStream(typesFile), true), "UTF-8"));
            String line;
            int titleS = "<http://dbpedia.org/resource/".length();
            int typeS = "<http://dbpedia.org/ontology/".length();
            Map<String,String> dbpedia = new LinkedHashMap<>();
            while((line=lnr.readLine())!=null){
                String[] fields = line.split("\\s+");
                if(fields.length<3 || !fields[2].startsWith("<http://dbpedia.org/ontology/") || fields[0].contains("__"))
                    continue;
                String title = fields[0].substring(titleS,fields[0].length()-1);
                String type = fields[2].substring(typeS, fields[2].length()-1);
                if(ontology.get(type) == null)
                    System.err.println("NULL for type: "+type+" -- "+line);
                //sometimes, the same title has multiple entries for type, considering the first one is the right one based on observation on a small sample set
                if(!dbpedia.containsKey(title))
                    dbpedia.put(title, ontology.get(type));
                if(lnr.getLineNumber()%10000 == 0)
                    System.err.println("Done: " + lnr.getLineNumber());
            }
            lnr.close();
            String name = typesFile.split("\\.")[0];
            OutputStreamWriter osw = new OutputStreamWriter(new BZip2CompressorOutputStream(new FileOutputStream(new File(outPath + File.separator + name+".en.txt.bz2"))));
            int numRecords = 0;
            for(String str: dbpedia.keySet()) {
                osw.write(str+" "+dbpedia.get(str)+"\n");
                numRecords++;
            }
            System.err.println("Wrote: "+numRecords+" to "+outPath+File.separator+name+".en.txt.bz2");
            osw.flush();
            osw.close();
        }catch(IOException e){
            log.info("Exception while reading types file: "+typesFile);
            edu.stanford.muse.util.Util.print_exception(e,log);
        }
    }

    public static void main(String[] args){
//        Map<String,String> ontology = parseOntology("dbpedia_2015-04.nt.bz2");
//        for(String str: ontology.keySet())
//            System.err.println(str+" : "+ontology.get(str));
        parse("instance-types_2015-04.en.nt.bz2", "dbpedia_2015-04.nt.bz2", System.getProperty("user.home")+File.separator+"epadd-ner");
    }
}
