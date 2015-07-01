package edu.cmu.lti.wikipedia_redirect;
import java.io.File;

public class Demo {
  public static void main(String[] args) throws Exception {
    WikipediaRedirect wr = IOUtil.load(new File(args[0]));
    String[] srcTerms = {"オサマビンラディン", "オサマ・ビンラーディン"
            ,"慶大", "NACSIS", "平成12年", "3.14"};
    StringBuilder sb = new StringBuilder();
    for ( String src : srcTerms ) {
      sb.append("\""+src +"\" is redirected to \""+wr.get(src)+"\"\n");
    }
    System.out.println(sb.toString());
  }
}
