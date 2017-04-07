/**
 * This file is part of General Entity Annotator Benchmark.
 *
 * General Entity Annotator Benchmark is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * General Entity Annotator Benchmark is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with General Entity Annotator Benchmark.  If not, see <http://www.gnu.org/licenses/>.
 */
package edu.stanford.muse.ner.model.test;

import edu.stanford.muse.Config;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import org.aksw.gerbil.dataset.Dataset;
import org.aksw.gerbil.dataset.DatasetConfiguration;
import org.aksw.gerbil.dataset.impl.nif.NIFFileDatasetConfig;
import org.aksw.gerbil.datatypes.ExperimentType;
import org.aksw.gerbil.exceptions.GerbilException;
import org.aksw.gerbil.transfer.nif.Document;
import org.aksw.gerbil.transfer.nif.Marking;
import org.aksw.gerbil.transfer.nif.data.TypedNamedEntity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


public class NIFNameSampleStream implements ObjectStream<NameSample> {

    static String DUL_PERSON = "http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#Person",
            DUL_LOCATION = "http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#Place",
            DUL_ORG = "http://www.ontologydesignpatterns.org/ont/dul/DUL.owl#Organization";

    //private static final String TASK1_FILE = "src/test/resources/OKE_Challenge/example_data/task1.ttl";

    int di = 0;
    List<Document> documents;
    public NIFNameSampleStream(String filePath){
        di = 0;
        DatasetConfiguration datasetConfig = new NIFFileDatasetConfig("OKE_Task1", filePath, false,
                ExperimentType.A2KB, null, null);
        try {
            Dataset dataset = datasetConfig.getDataset(ExperimentType.A2KB);
            documents = dataset.getInstances();
        } catch (GerbilException ge) {
            ge.printStackTrace();
        }
    }

    @Override
    public NameSample read(){
        if (di>=documents.size())
            return null;
        Document document = documents.get(di++);
        String text = document.getText();
        String[] tokens = text.split("\\W+");

        List<Marking> markings = document.getMarkings();
        List<Span> spans = new ArrayList<>();
        for (int mi = 0; mi < markings.size(); mi++) {
            Marking marking = markings.get(mi);
            TypedNamedEntity tne = (TypedNamedEntity) marking;
            String ct = null;
            if (tne.getTypes().contains(DUL_PERSON))
                ct = "PER";
            else if (tne.getTypes().contains(DUL_LOCATION))
                ct = "LOC";
            else if (tne.getTypes().contains(DUL_ORG))
                ct = "ORG";

            if (ct!=null)
                spans.add(new Span(tne.getStartPosition(), tne.getStartPosition()+tne.getLength(), ct));
        }

        return new NameSample(tokens, spans.toArray(new Span[spans.size()]), di<=1?false:true);
    }

    @Override
    public void reset() throws IOException, UnsupportedOperationException {
        di = 0;
    }

    @Override
    public void close() throws IOException {
    }

    public static void main(String[] args){
        String filePath = String.join(File.separator,
                new String[]{Config.SETTINGS_DIR, "evaluation-dataset-task1.ttl"});
        NIFNameSampleStream nss = new NIFNameSampleStream(filePath);
        int numSents = 0, numSpans = 0;
        int numPer = 0, numOrg = 0, numPlace = 0;
        NameSample ns;
        while((ns=nss.read())!=null) {
            numSents ++;
            numSpans += ns.getNames().length;
            numPer += Stream.of(ns.getNames()).filter(sp->"PER".equals(sp.getType())).count();
            numOrg += Stream.of(ns.getNames()).filter(sp->"ORG".equals(sp.getType())).count();
            numPlace += Stream.of(ns.getNames()).filter(sp->"LOC".equals(sp.getType())).count();
        }

        System.out.println("Found " + numSents + " sentences and " + numSpans + " spans");
        System.out.println("Found #" + numPer + "persons #" + numOrg + " orgs #" + numPlace + " places");
    }
}
