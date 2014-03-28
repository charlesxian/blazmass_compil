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
import org.bson.types.ObjectId;

import java.util.List;
import java.util.ArrayList;
import blazmass.dbindex.MassRange;
import blazmass.dbindex.IndexedSequence;
import blazmass.io.SearchParams;

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
    
    // These static variables / information can all be pulled from blazmass.params instead of being hardcoded here:
    private static final String mongoHost = "localhost";
    private static final int mongoPort = 27017;
    private static final String dbName = "uniprotDB";
    private static final String collName = "uniprotColl";
//    private static final float massTolerance = 0.2f; // units are amu, not ppm
    
    private static MongoClient mongoClient = null;
    private static DB db = null;

    public Mongoconnect() {}
    
    public static DBCollection connectToCollection(SearchParams sParam) throws Exception {
        try {
            //
//            String collName = null;
            if(mongoClient == null) {
//                System.out.println("-------------MONGODBHOST NAME FROM PARAMS FILE: "+sParam.getMongoServer());
//                System.out.println("-------------MONGODBPORT NUMBER FROM PARAMS FILE: "+String.valueOf(sParam.getMongoPort()));
//                System.out.println("-------------DATABASE NAME FROM PARAMS FILE: "+sParam.getDatabaseName());
//                System.out.println("-------------COLLECTION NAME FROM PARAMS FILE: "+sParam.getDatabaseName());
//                mongoClient = new MongoClient(mongoHost,mongoPort);
                mongoClient = new MongoClient(sParam.getMongoServer(),sParam.getMongoPort());
                System.out.println("-------------Making new connection to MongoDB at "+sParam.getMongoServer());
                db = mongoClient.getDB(sParam.getDatabaseName());
            }
            
            return db.getCollection(sParam.getMongoCollection());

        } catch(Exception e){
            System.out.println("MongoDB Connection / Collection Connection error");
            return null;
          }
    }
    
    // substitute for getSequences() method from DBIndexer
//    public static List<IndexedSequence> getSequences(List<Float> precursorMasses, float massTolerance) throws Exception {
    public static List<IndexedSequence> getSequences(List<MassRange> rList, SearchParams sParam) throws Exception {
        
        try {
            
            DBCollection coll = Mongoconnect.connectToCollection(sParam); //should move this outside this method... only want to make one connection, not one per instance...
            return Mongoconnect.getSequencesFromColl(rList, coll);

        } catch(Exception e){
            System.out.println("DB Query / getSequences error");
            return null;
          }
    }

    // helper method for getSequences()
    // handles iteration through a MongoDB query cursor
    public static List<IndexedSequence> getSequencesFromColl(List<MassRange> rList, DBCollection coll) throws Exception {
//    public static List<IndexedSequence> getSequencesFromColl(List<Float> precursorMasses, float massTolerance, DBCollection coll) throws Exception {
        
        try {
//            DBCursor cursor = Mongoconnect.getCursor(coll,massTolerance,precursorMasses);
            DBCursor cursor = Mongoconnect.getCursor(coll,rList);
            List<IndexedSequence> queryResults = new ArrayList<>();
            
            try {
                while(cursor.hasNext()) {
                    queryResults.add(convertDBObjectToIndexedSequence(cursor.next()));
                }
                System.out.println("Number of results from query:");
                System.out.println(queryResults.size());
            } finally {
                cursor.close();
            }
            
            return queryResults;
            
        } catch(Exception e){
            System.out.println("DB Query / getSequencesFromColl error");
            return null;
          }
    }
    
    // parses DBObject returned from a MongoDB query
    // input: a single MongoDB 'DBObject'
    // (this can be returned from a db.collection.findOne() query, or from a single item returned from a query cursor
    // output: a single IndexedSequence object
    public static IndexedSequence convertDBObjectToIndexedSequence(DBObject obj) throws Exception {
        try {
            // using IndexedSequence constructor below:
            // public IndexedSequence(float precMass, String sequence, int sequenceLen, String objID) {}
            
            float precMass = (Float.parseFloat(String.valueOf(obj.get("MASS"))))/1000;
            String sequence = String.valueOf(obj.get("SEQ"));
            int sequenceLen = Integer.parseInt(String.valueOf(obj.get("LEN")));
            List<String> proteinID = new ArrayList<>();
            
            // LR and RR for just first PARENT:
            BasicDBList parentsList = (BasicDBList) obj.get("PARENTS");
            BasicDBObject firstParent = (BasicDBObject) parentsList.get(0);
            String resLeft = String.valueOf(firstParent.get("LR"));
            String resRight = String.valueOf(firstParent.get("RR"));
            
//            IndexedSequence indSeq = new IndexedSequence(precMass,sequence,resLeft,resRight,sequenceLen,proteinID);
//            Object objID = obj.get("_id");
            String objID = obj.get("_id").toString();
            IndexedSequence indSeq = new IndexedSequence(precMass,sequence,sequenceLen,resLeft,resRight,objID);

            
            // can be removed (just here for testing)
//            System.out.println("precMass:");
//            System.out.println(indSeq.getMass());
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
 
    // structures MongoDB query, calls convertDBObjectToIndexedSequence() to convert type
    public static DBCursor getCursor(DBCollection coll, List<MassRange> rList) throws Exception {
//    public static DBCursor getCursor(DBCollection coll, float massTolerance, List<Float> precursorMasses) throws Exception {

        try {
        
//            BasicDBObject query = new BasicDBObject("MASS", new BasicDBObject("$gte", lowMass).append("$lte", highMass));
            BasicDBList orQuery = new BasicDBList();
            int lowMass;
            int highMass;
            
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
            }
            
            BasicDBObject queryProjection = new BasicDBObject("MASS",1).append("SEQ",1).append("LEN",1).append("PARENTS",1);
            DBCursor cursor = coll.find(new BasicDBObject("$or",orQuery),queryProjection);

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
