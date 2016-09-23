package edu.stanford.muse.ner.model;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by vihari on 23/09/16.
 */
public class NEType {
    static Log log = LogFactory.getLog(NEType.class);

    /**
     * DO NOT change the code for the types.
     * For compactness, the codes are stored, such as in index.
     * Changing the codes may give unexpected results.
     * If changed, then revert to the values in the commit of the previous release -- muse commit: 52aab037434ab6883759eaf108ca70fe0a259473
     * https://github.com/ePADD/muse/commits/master
     * */
    public enum Type{
        PERSON(0, null),
        PLACE(3, null),
            BUILDING(2, PLACE),RIVER(4, PLACE),ROAD(5, PLACE),
            MOUNTAIN(9, PLACE),AIRPORT(10, PLACE),ISLAND(17, PLACE),
            MUSEUM(18, PLACE),BRIDGE(19, PLACE),HOSPITAL(25, PLACE),
            THEATRE(31, PLACE),LIBRARY(33, PLACE),MONUMENT(35, PLACE),
        ORGANISATION(11, null),
            COMPANY(1, ORGANISATION),UNIVERSITY(7, ORGANISATION),
            PERIODICAL_LITERATURE(13, ORGANISATION),AIRLINE(20, ORGANISATION),
            GOVAGENCY(22, ORGANISATION),AWARD(27, ORGANISATION),
            LEGISLATURE(32,ORGANISATION),LAWFIRM(34, ORGANISATION),
            DISEASE(36, ORGANISATION),EVENT(37, ORGANISATION),
        //any other valid type that is not one of types above
        MISC(38, null),
        //no idea of type or if token belongs to an entity or not
        UNKNOWN_TYPE(-10, null),
        //to tag non-entity tokens
        OTHER(-2, null);

        private short code;
        private Type parent;

        Type(int code, Type parent) {
            this.code = (short)code;
            this.parent = parent;
        }

        public short getCode(){
            return code;
        }

        public Type parent(){
            return parent;
        }

    }

    static Map<Type, String[]> dbpediaTypesMap = new LinkedHashMap<>();
    static{
        dbpediaTypesMap.put(Type.PERSON, new String[]{"Person", "Agent"});
        dbpediaTypesMap.put(Type.PLACE, new String[]{"Place", "Park|Place", "ProtectedArea|Place", "PowerStation|Infrastructure|ArchitecturalStructure|Place", "ShoppingMall|Building|ArchitecturalStructure|Place"});
        dbpediaTypesMap.put(Type.COMPANY, new String[]{"Company|Organisation", "Non-ProfitOrganisation|Organisation"});
        dbpediaTypesMap.put(Type.BUILDING, new String[]{"Building|ArchitecturalStructure|Place", "Hotel|Building|ArchitecturalStructure|Place"});
        dbpediaTypesMap.put(Type.RIVER, new String[]{"River|Stream|BodyOfWater|NaturalPlace|Place", "Canal|Stream|BodyOfWater|NaturalPlace|Place", "Stream|BodyOfWater|NaturalPlace|Place", "BodyOfWater|NaturalPlace|Place", "Lake|BodyOfWater|NaturalPlace|Place"});
        dbpediaTypesMap.put(Type.ROAD, new String[]{"Road|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place"});
        dbpediaTypesMap.put(Type.UNIVERSITY, new String[]{"University|EducationalInstitution|Organisation", "School|EducationalInstitution|Organisation", "College|EducationalInstitution|Organisation"});
        dbpediaTypesMap.put(Type.MOUNTAIN, new String[]{"Mountain|NaturalPlace|Place", "MountainRange|NaturalPlace|Place"});
        dbpediaTypesMap.put(Type.AIRPORT, new String[]{"Airport|Infrastructure|ArchitecturalStructure|Place"});
        dbpediaTypesMap.put(Type.ORGANISATION, new String[]{"Organisation", "PoliticalParty|Organisation", "TradeUnion|Organisation"});
        dbpediaTypesMap.put(Type.PERIODICAL_LITERATURE, new String[]{"Newspaper|PeriodicalLiterature|WrittenWork|Work", "AcademicJournal|PeriodicalLiterature|WrittenWork|Work", "Magazine|PeriodicalLiterature|WrittenWork|Work"});
        dbpediaTypesMap.put(Type.ISLAND, new String[]{"Island|PopulatedPlace|Place"});
        dbpediaTypesMap.put(Type.MUSEUM, new String[]{"Museum|Building|ArchitecturalStructure|Place"});
        dbpediaTypesMap.put(Type.BRIDGE, new String[]{"Bridge|RouteOfTransportation|Infrastructure|ArchitecturalStructure|Place"});
        dbpediaTypesMap.put(Type.AIRLINE, new String[]{"Airline|Company|Organisation"});
        dbpediaTypesMap.put(Type.GOVAGENCY, new String[]{"GovernmentAgency|Organisation"});
        dbpediaTypesMap.put(Type.HOSPITAL, new String[]{"Hospital|Building|ArchitecturalStructure|Place"});
        dbpediaTypesMap.put(Type.AWARD, new String[]{"Award"});
        dbpediaTypesMap.put(Type.THEATRE, new String[]{"Theatre|Venue|ArchitecturalStructure|Place"});
        dbpediaTypesMap.put(Type.LEGISLATURE, new String[]{"Legislature|Organisation"});
        dbpediaTypesMap.put(Type.LIBRARY, new String[]{"Library|Building|ArchitecturalStructure|Place"});
        dbpediaTypesMap.put(Type.LAWFIRM, new String[]{"LawFirm|Company|Organisation"});
        dbpediaTypesMap.put(Type.MONUMENT, new String[]{"Monument|Place"});
        dbpediaTypesMap.put(Type.DISEASE, new String[]{"Disease|Medicine"});
        dbpediaTypesMap.put(Type.EVENT, new String[]{"SocietalEvent|Event"});
    }

    public static Type[] getAllTypes(){
        return Type.values();
    }

    public static Short[] getAllTypeCodes() {
        java.util.List<Short> codes = Stream.of(NEType.getAllTypes()).map(NEType.Type::getCode).collect(Collectors.toList());
        return codes.toArray(new Short[codes.size()]);
    }

    //Never returns NULL, returns OTHER in every other case
    public static Type parseDBpediaType(String typeStr){
        Type type = Type.OTHER;
        if(typeStr == null)
            return type;

        //strip "|Agent" in the end
        if(typeStr.endsWith("|Agent"))
            typeStr = typeStr.substring(0, typeStr.length()-6);
        String[] fs = typeStr.split("\\|");
        //the loop codes the string type that may look like "University|EducationalInstitution|Organisation|Agent" into the most specific type by looking at the biggest to smallest prefix.
        outer:
        for(int ti=0;ti<fs.length;ti++) {
            StringBuffer sb = new StringBuffer();
            for(int tj=ti;tj<fs.length;tj++) {
                sb.append(fs[tj]);
                if(tj<fs.length-1)
                    sb.append("|");
            }
            String st = sb.toString();
            for (Type t : Type.values()) {
                String[] allowT = dbpediaTypesMap.get(t);
                if (allowT != null)
                    for (String at : allowT)
                        if (st.equals(at)) {
                            type = t;
                            break outer;
                        }
            }
        }
        return type;
    }

    public static Type getTypeForCode(short c){
        Type type = Stream.of(getAllTypes()).filter(t->t.getCode()==c).findAny().orElse(Type.UNKNOWN_TYPE);
        if(type.getCode()!=c)
            log.warn("Unknown code: "+c);
        return type;
    }

    /**Given a type described in text returns a coarse coding for the type
     * for example: "University" -> [ORGANIZATION]*/
    public static NEType.Type getCoarseType(NEType.Type type){
        if(type == null)
            return NEType.Type.OTHER;
        if(type.parent()==null)
            return type;
        else
            return type.parent();
    }

    public static NEType.Type getCoarseType(Short ct){
        return getCoarseType(getTypeForCode(ct));
    }
}
