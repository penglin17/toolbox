/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and limitations under the License.
 *
 */

package gps;

import eu.amidst.core.datastream.DataInstance;
import eu.amidst.core.datastream.DataOnMemory;
import eu.amidst.core.datastream.DataOnMemoryListContainer;
import eu.amidst.core.datastream.DataStream;
import eu.amidst.core.io.DataStreamLoader;
import eu.amidst.core.learning.parametric.bayesian.SVB;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Random;

/**
 * Created by andresmasegosa on 4/5/16.
 */
public class RunSVBRestart {

    public static void main(String[] args) throws Exception{

        //String model = "GPS0";
        //String dataPath = "/Users/andresmasegosa/Dropbox/Amidst/datasets/Geo/out_month_10/";

        String model = "BCC2";
        String dataPath = "/Users/andresmasegosa/Dropbox/Amidst/datasets/cajamarData/IDA2015Data/splittedByMonths/dataWekaNoOutliers/";

        int ntopics = 1;
        int niter = 100;
        double threshold = 0.1;
        int docsPerBatch = 10000;

        if (args.length>1){
            int cont = 0;
            model = args[cont++];

            dataPath=args[cont++];
            ntopics= Integer.parseInt(args[cont++]);
            niter = Integer.parseInt(args[cont++]);
            threshold = Double.parseDouble(args[cont++]);
            docsPerBatch = Integer.parseInt(args[cont++]);

            args[1]="";
        }



        SVB svb = new SVB();

        DataStream<DataInstance> dataInstances = DataStreamLoader.open(dataPath+
                Arrays.asList(new File(dataPath).list())
                .stream()
                .filter(string -> string.endsWith(".arff")).findFirst().get());

        svb.getPlateuStructure().getVMP().setTestELBO(true);
        svb.getPlateuStructure().getVMP().setMaxIter(niter);
        svb.getPlateuStructure().getVMP().setOutput(true);
        svb.getPlateuStructure().getVMP().setThreshold(threshold);

        svb.setWindowsSize(docsPerBatch);
        if (model.compareTo("GPS0")==0) {
            svb.setDAG(DAGsGeneration.getGPSMixtureDAGNoDay(dataInstances.getAttributes(), ntopics));
        }else if (model.compareTo("GPS1")==0) {
            svb.setDAG(DAGsGeneration.getGPSMixtureDAG(dataInstances.getAttributes(), ntopics));
        }else if (model.compareTo("GPS2")==0) {
            svb.setDAG(DAGsGeneration.getGPSFADAG(dataInstances.getAttributes(), ntopics));
        }else if (model.compareTo("BCC0")==0) {
            svb.setDAG(DAGsGeneration.getBCCMixtureDAG(dataInstances.getAttributes(), ntopics));
        }else if (model.compareTo("BCC1")==0) {
            svb.setDAG(DAGsGeneration.getBCCFullMixtureDAG(dataInstances.getAttributes(), ntopics));
        }else if (model.compareTo("BCC2")==0) {
            svb.setDAG(DAGsGeneration.getBCCFADAG(dataInstances.getAttributes(), ntopics));
        }else if (model.compareTo("BCC3")==0) {
            svb.setDAG(DAGsGeneration.getBCCLocalMixtureDAG(dataInstances.getAttributes(), ntopics));
        }else if (model.compareTo("BCC4")==0) {
            svb.setDAG(DAGsGeneration.getBCCNB(dataInstances.getAttributes()));
        }

        svb.setOutput(true);

        svb.initLearning();

        svb.randomInitialize();

        //svb.setNonSequentialModel(true);
        System.out.println(svb.getLearntBayesianNetwork());

        FileWriter fw = new FileWriter(dataPath+"SVBRestart_Output_"+Arrays.toString(args)+"_.txt");


//        Iterator<DataOnMemory<DataInstance>> iterator = dataInstances.iterableOverBatches(docsPerBatch).iterator();

        final String path = dataPath;
        final int finalDocsPerBatch = docsPerBatch;


        int count=0;

        Random random = new Random(1);

        double totalLog = 0;

        String[] strings = new File(dataPath).list();
        Arrays.sort(strings);
        for (String string : strings) {

            if (!string.endsWith(".arff"))
                continue;

            System.out.println("EPOCH: " + count +", "+ string);

            DataOnMemory<DataInstance> batch= DataStreamLoader.loadDataOnMemoryFromFile(path+string);
                        if (batch.getNumberOfDataInstances()<Main.MIN)
                continue;

            Collections.shuffle(batch.getList(),random);






            int limit = (int) ((batch.getNumberOfDataInstances()*2.0)/3.0);


            DataOnMemoryListContainer<DataInstance> train= new
                    DataOnMemoryListContainer(batch.getAttributes());
            train.addAll(batch.getList().subList(0,limit));

            DataOnMemoryListContainer<DataInstance> test= new
                    DataOnMemoryListContainer(batch.getAttributes());
            test.addAll(batch.getList().subList(limit+1,batch.getNumberOfDataInstances()));


            Iterator<DataOnMemory<DataInstance>> iteratorInner = train.streamOfBatches(finalDocsPerBatch).iterator();

            while (iteratorInner.hasNext()){
                svb.updateModel(iteratorInner.next());
                break;
            }

            double log = 0;
            iteratorInner = test.streamOfBatches(finalDocsPerBatch).iterator();
            while (iteratorInner.hasNext()) {
                log+=svb.predictedLogLikelihood(iteratorInner.next());
                break;
            }

            double inst =test.getNumberOfDataInstances();

            System.out.println("OUT"+(count)+"\t"+log/inst+"\t"+inst+"\n");

            fw.write((count++)+"\t"+log/inst+"\t"+inst+"\n");

            fw.flush();

            System.out.println(svb.getLearntBayesianNetwork());

            totalLog+=log/inst;

            svb.initLearning();


            iteratorInner =  test.streamOfBatches(finalDocsPerBatch).iterator();

            while (iteratorInner.hasNext()){
                svb.updateModel(iteratorInner.next());
                break;
            }

            System.out.println(svb.getLearntBayesianNetwork());

            svb.initLearning();

        }
        fw.close();

        System.out.println("TOTAL LOG: " + totalLog);

    }
}