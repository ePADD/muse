package edu.stanford.muse.ie;

/**
 * Created by vihari on 26/12/15.
 */

import edu.stanford.muse.index.EmailDocument;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.mail.Address;

/**
 * The levels are:
 * <ul>
 *     <li>Same document</li>
 *     <li>Same thread</li>
 *     <li>Same from and to</li>
 *     <li>Same from/to [depending on which one is not own name]</li>
 *     <li>Sent mails</li>
 *     <li>Anywhere in the archive</li>
 * </ul>*/
public class EmailHierarchy implements Hierarchy{
    static Log log = LogFactory.getLog(EmailHierarchy.class);

    public int getNumLevels(){
        return 6;
    }

    public String getName(int level){return level+"";}

    public String getValue(int level, Object o) {
        if (!(o instanceof EmailDocument)) {
            log.info("Improper object passed to get value");
            return null;
        }

        EmailDocument ed = (EmailDocument)o;
        if(level == 0)
            return ed.getUniqueId();
        else if(level == 1)
            return ""+ed.threadID;
        else if(level == 2) {
            String str = "";
            Address[] to = ed.to;
            Address[] from = ed.from;
            if(to!=null && to.length>0)
                str += "to:"+to[0]+"-";
            if(from!=null && from.length>0)
                str += "from:"+from[0];
            return str;
        }
        else if(level == 3){
            Address[] from = ed.from;
            String str = "";
            if(from!=null && from.length>0)
                str += "from:"+from[0];
            return str;
        }else if(level == 4){
            Address[] to = ed.to;
            String str = "";
            if(to!=null && to.length>0)
                str += "to:"+to[0];
            return  str;
        }
        //the default
        return "def";
    }
}
