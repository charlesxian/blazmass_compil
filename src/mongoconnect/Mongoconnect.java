/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package mongoconnect;

import com.mongodb.MongoClient;
//import com.mongodb.MongoException;
//import com.mongodb.WriteConcern;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
//import com.mongodb.ServerAddress;
//import com.mongodb.MongoClientOptions;
//import org.bson.types.ObjectId;

import java.util.List;
import java.util.ArrayList;
import blazmass.dbindex.MassRange;
import blazmass.dbindex.IndexedSequence;
import blazmass.io.SearchParams;
//import com.mongodb.ReadPreference;
import java.util.Iterator;
//import java.util.HashSet;

//import dbindex.IndexedSequence;
//import java.io.IOException;
//import java.util.Map;

/**
 *
 * @author sandip
 */
public class Mongoconnect {

    /**
     * @param args the command line arguments
     */
    
    private static MongoClient mongoClient = null;
    private static DB db = null;

    public Mongoconnect() {}
    
    public static DBCollection connectToCollection(SearchParams sParam) throws Exception {
        try {

            if(mongoClient == null) {
                
                mongoClient = new MongoClient(sParam.getMongoServer(),sParam.getMongoPort());
                System.out.println("-------------Making new connection to MongoDB at "+sParam.getMongoServer());
                db = mongoClient.getDB(sParam.getDatabaseName());
//                db.setReadOnly(Boolean.TRUE);
            }
            
            return db.getCollection(sParam.getMongoCollection());

        } catch(Exception e){
            System.out.println("MongoDB Connection / Collection Connection error");
            return null;
          }
    }
    
    // substitute for getSequences() method from DBIndexer
    public static List<IndexedSequence> getSequences(List<MassRange> rList, SearchParams sParam) throws Exception {
        
        try {
            DBCollection coll = Mongoconnect.connectToCollection(sParam);
            return Mongoconnect.getSequencesFromColl(rList, coll);
        } catch(Exception e){
            System.out.println("DB Query / getSequences error");
            return null;
          }
    }

    // helper method for getSequences()
    // handles iteration through a MongoDB query cursor
/*    public static List<IndexedSequence> getSequencesFromColl_old(List<MassRange> rList, DBCollection coll) throws Exception {
        
        try {
            DBCursor cursor = Mongoconnect.getCursor(coll,rList);
            List<IndexedSequence> queryResults = new ArrayList<>();
            
            try {
                while(cursor.hasNext()) {
                    queryResults.add(convertDBObjectToIndexedSequence(cursor.next()));
                }
                System.out.printf("Number of results from query: "+queryResults.size());
                
            } finally {
                cursor.close();
            }
            
            return queryResults;
            
        } catch(Exception e){
            System.out.println("DB Query / getSequencesFromColl error");
            return null;
          }
    }
*/
    
    // helper method for getSequences()
    // handles iteration through a MongoDB query cursor
    // returns an arraylist of IndexedSequence objects
    public static List<IndexedSequence> getSequencesFromColl(List<MassRange> rList, DBCollection coll) throws Exception {
        
        try {
            List<IndexedSequence> queryResults = new ArrayList<>();
//            HashSet<String> docIDsFromDBQuery = new HashSet<>();
            
//            long startTime = System.currentTimeMillis(); //REMOVE
//            long endTime = System.currentTimeMillis(); //REMOVE
            int intMass;
            String sequence;
            
            for (MassRange mRange : rList) {

                DBCursor cursor = Mongoconnect.getCursor(coll,mRange).batchSize(300);
                
                try {
                    while(cursor.hasNext()) {
//                        startTime = System.currentTimeMillis(); //REMOVE

                        DBObject obj = cursor.next();
//                        endTime = System.currentTimeMillis(); //REMOVE
//                        System.out.println("Time taken for cursor.next(): " + (endTime - startTime) + " milliseconds"); //REMOVE

//                        startTime = System.currentTimeMillis(); //REMOVE
//                        String objID = obj.get("_id").toString();
//                        if (!docIDsFromDBQuery.contains(objID)) {
//                            docIDsFromDBQuery.add(objID);
//                            queryResults.add(convertDBObjectToIndexedSequence(obj,objID));
                        intMass = (int) obj.get("_id"); // gets intMass from current document
//                        queryResults.add(convertDBObjectToIndexedSequence(obj));

                        BasicDBList peptideSequences = (BasicDBList) obj.get("s");

                        for (Iterator<Object> it = peptideSequences.iterator(); it.hasNext();) {
                            sequence = (String) it.next();
                            queryResults.add(makeIndexedSequence(intMass,sequence));
                        }
                            
//                        }
//                        endTime = System.currentTimeMillis(); //REMOVE
//                        System.out.println("Time taken for conversion to IndexedSequence: " + (endTime - startTime) + " milliseconds"); //REMOVE

                    }
                    System.out.println("Number of peptide sequence results from query: "+queryResults.size());

                } finally {
                    cursor.close();
                }
            }
            
            return queryResults;
            
        } catch(Exception e){
            System.out.println("DB Query / getSequencesFromColl error");
            return null;
          }
    }

    public static IndexedSequence makeIndexedSequence(int intMass, String sequence) throws Exception {
        try {            
            
            float floatMass = (float) intMass/1000; //'_id' is 'MASS'
//            String[] parts = pepSeqLR.split("\\.");
//            String resLeft = parts[0];
//            String sequence = parts[1];
//            String resRight = parts[2];
            
//            IndexedSequence indSeq = new IndexedSequence(floatMass,sequence,sequence.length(),resLeft,resRight);
            IndexedSequence indSeq = new IndexedSequence(floatMass,sequence,sequence.length(),"---","---");

            
            return indSeq;
            
        } catch(Exception e){
            System.out.println("DB Query / getSequencesFromColl error");
            return null;
          }
    }

    
    // parses DBObject returned from a MongoDB query
    // input: a single MongoDB 'DBObject' and its ObjectID (converted to String)
    // (this can be returned from a db.collection.findOne() query, or from a single item returned from a query cursor
    // output: a single IndexedSequence object
    public static IndexedSequence convertDBObjectToIndexedSequence(DBObject obj) throws Exception {
        try {
            // using IndexedSequence constructor below:
            // public IndexedSequence(float precMass, String sequence, int sequenceLen, String objID) {}
            
//            float precMass = (Float.parseFloat(String.valueOf(obj.get("MASS"))))/1000;
//            String sequence = String.valueOf(obj.get("SEQ"));
//            int sequenceLen = Integer.parseInt(String.valueOf(obj.get("LEN")));
//            List<String> proteinID = new ArrayList<>();
//            
//            // LR and RR for just first PARENT:
//            BasicDBList parentsList = (BasicDBList) obj.get("PARENTS");
//            BasicDBObject firstParent = (BasicDBObject) parentsList.get(0);
//            String resLeft = String.valueOf(firstParent.get("LR"));
//            String resRight = String.valueOf(firstParent.get("RR"));
//            String objID = obj.get("_id").toString();
//            IndexedSequence indSeq = new IndexedSequence(precMass,sequence,sequenceLen,resLeft,resRight,objID);
//            String protIDLRRR;
//            for (Iterator<Object> it = parentsList.iterator(); it.hasNext();) {
//                BasicDBObject parent = (BasicDBObject) it.next();
//                protIDLRRR = ((String) parent.get("PROT_ID"))+
//                                        "|"+
//                                        ((String) parent.get("LR"))+
//                                        "|"+
//                                        ((String) parent.get("RR"));
//                indSeq.addToMongoProteinIDStrings(protIDLRRR);
            
            
            float precMass = (Float.parseFloat(String.valueOf(obj.get("_id"))))/1000; //'_id' is 'MASS
            String sequence = String.valueOf(obj.get("SEQ"));
            int sequenceLen = Integer.parseInt(String.valueOf(obj.get("LEN")));
            List<String> proteinID = new ArrayList<>();
            
            // LR and RR for just first PARENT:
            BasicDBList parentsList = (BasicDBList) obj.get("PARENTS");
            BasicDBObject firstParent = (BasicDBObject) parentsList.get(0);
            String resLeft = String.valueOf(firstParent.get("LR"));
            String resRight = String.valueOf(firstParent.get("RR"));
            String objID = obj.get("_id").toString();
            IndexedSequence indSeq = new IndexedSequence(precMass,sequence,sequenceLen,resLeft,resRight,objID);
            String protIDLRRR;
            for (Iterator<Object> it = parentsList.iterator(); it.hasNext();) {
                BasicDBObject parent = (BasicDBObject) it.next();
                protIDLRRR = ((String) parent.get("PROT_ID"))+
                                        "|"+
                                        ((String) parent.get("LR"))+
                                        "|"+
                                        ((String) parent.get("RR"));
                indSeq.addToMongoProteinIDStrings(protIDLRRR);
            }
            
            // can be removed (just here for testing)
//            System.out.println("PARENTS PROT_IDs:");
//            if (indSeq.getMongoProteinIDStrings().size() > 1) {
//                System.out.println("----=");
//                System.out.println(indSeq.getMongoProteinIDStrings());}
//            System.out.println("sequence:");
//            System.out.println(indSeq.getSequence());
//            System.out.println("resLeft:");
//            System.out.println(indSeq.getResLeft());
//            System.out.println("resRight:");
//            System.out.println(indSeq.getResRight());
//            System.out.println("sequenceLen:");
//            System.out.println(indSeq.getSequenceLen());
//            System.out.println("ObjectID:");
//            System.out.println(indSeq.getObjID());
//            System.out.println("------------------");
//            System.out.println("Retrieved one document "+indSeq.getObjID()+" and created one IndexedSequence from peptide "+indSeq.getSequence());
            //
            
            return indSeq;
            
        } catch(Exception e){
            System.out.println("DB Query / getSequencesFromColl error");
            return null;
          }
    }

    public static DBCursor getCursor(DBCollection coll, MassRange mRange) throws Exception {

        try {
            int lowMass;
            int highMass;
//            BasicDBObject query = new BasicDBObject();

            lowMass = Math.round((mRange.getPrecMass()-mRange.getTolerance())*1000);
            highMass = Math.round((mRange.getPrecMass()+mRange.getTolerance())*1000);
//            query = new BasicDBObject("MASS", new BasicDBObject("$gte", lowMass).append("$lte", highMass));
            BasicDBObject query = new BasicDBObject("_id", new BasicDBObject("$gte", lowMass).append("$lte", highMass));
            
//            BasicDBObject queryProjection = new BasicDBObject("MASS",1).append("SEQ",1).append("LEN",1).append("PARENTS",1);
//            DBCursor cursor = coll.find(query,queryProjection);
            DBCursor cursor = coll.find(query);

            return cursor;
        
        } catch(Exception e){
            System.out.println("DB Query / Cursor retrieval error");
            return null;
          }
    }

    public static DBCursor getCursor_old(DBCollection coll, List<MassRange> rList) throws Exception {

        try {
            
            BasicDBList orQuery = new BasicDBList();
            int lowMass;
            int highMass;
            String queryString = "db.collection.find({ $or: [ ";
            
//            for (float precursorMass : precursorMasses) {
//                lowMass = Math.round((precursorMass-massTolerance)*1000);
//                highMass = Math.round((precursorMass+massTolerance)*1000);
//                orQuery.add(new BasicDBObject("MASS", new BasicDBObject("$gte", lowMass).append("$lte", highMass)));
//            }
            for (MassRange mRange : rList) {
//                lowMass = Math.round((precursorMass-massTolerance)*1000);
//                highMass = Math.round((precursorMass+massTolerance)*1000);
                lowMass = Math.round((mRange.getPrecMass()-mRange.getTolerance())*1000);
                highMass = Math.round((mRange.getPrecMass()+mRange.getTolerance())*1000);
                orQuery.add(new BasicDBObject("MASS", new BasicDBObject("$gte", lowMass).append("$lte", highMass)));
//                System.out.printf("MASS RANGE: ");
//                System.out.printf(String.valueOf(lowMass));
//                System.out.printf(" to ");
//                System.out.println(String.valueOf(highMass));
                queryString += "{ MASS: { $gte: "+lowMass+", $lte: "+highMass+"} },";
                //"{ $or: [ { MASS: { $gte: 1193526, $lte: 1193574 } }, { MASS: { $gte: 1192523, $lte: 1192571 } }, { MASS: { $gte: 1191519, $lte: 1191567 } }, { MASS: { $gte: 1190516, $lte: 1190564 } }, { MASS: { $gte: 1189513, $lte: 1189561 } } ] }";
            }
            
            BasicDBObject queryProjection = new BasicDBObject("MASS",1).append("SEQ",1).append("LEN",1).append("PARENTS",1);
            DBCursor cursor = coll.find(new BasicDBObject("$or",orQuery),queryProjection);
            
            queryString = queryString.substring(0, queryString.length() - 1); //remove trailing comma
            queryString += "] })";
            System.out.println("Query performed: "+queryString);

            return cursor;
        
        } catch(Exception e){
            System.out.println("DB Query / Cursor retrieval error");
            return null;
          }
    }
    
    public DBObject getDocumentByObjectID(String objID) {
        try {
/*
            DBCollection coll = connectToCollection();

            DBObject qResult = coll.findOne(new BasicDBObject("_id",new ObjectId(objID)));
            
            // can be removed, just here for testing:
            System.out.println("------SEQUENCE FROM OBJID LOOKUP:");
            System.out.println(String.valueOf(qResult.get("SEQ")));
            //
            
        return qResult;*/
            return null;
        
        } catch(Exception e){
            System.out.println("getDocumentByObjectID error");
            return null;
          }
    }
    
    public static void main(String[] args) throws Exception {

        try {
            
            /////////////// test for getSequences():            
           // Mongoconnect connection = new Mongoconnect();
            
            // test list of 2 floats for mass range query (+/- massTolerance value):
//            List<Float> precursorMasses = new ArrayList<>();
//            List<MassRange> rList = new ArrayList<>();
            
//            rList.add(new MassRange(precursorMass, ppmTolerance));
//            rList.add(new MassRange(2490.097f, 6.0f));
//            rList.add(new MassRange(4259.330f, 6.0f));

            //connection.getSequences(precursorMasses);
//            Mongoconnect.getSequences(precursorMasses, 0.02f);
//            Mongoconnect.getSequences(rList);


            
            /////////////// test for getDocumentByObjectID():
//            Mongoconnect connection2 = new Mongoconnect();
//            connection2.getDocumentByObjectID("531f92750817a4c6d48ac932");

    // can parallelize by mapping getSequences on precursorMass on different threads:
    //            connection.getSequences(precursorMass,massTolerance);
    //            connection.getSequences(precursorMass,massTolerance);
    //            connection.getSequences(precursorMass,massTolerance);
    //            connection.getSequences(precursorMass,massTolerance);

        } catch(Exception e){
           System.out.println("general error");
        }
    }
    
}
