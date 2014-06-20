/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package mongoconnect;

import com.mongodb.MongoClient;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;

import java.util.List;
import java.util.ArrayList;
import blazmass.dbindex.MassRange;
import blazmass.dbindex.IndexedSequence;
import blazmass.io.SearchParams;
import java.util.Iterator;

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
    // returns an arraylist of IndexedSequence objects
    public static List<IndexedSequence> getSequencesFromColl(List<MassRange> rList, DBCollection coll) throws Exception {
        
        try {
            List<IndexedSequence> queryResults = new ArrayList<>();

            int intMass;
            String sequence;
            
            for (MassRange mRange : rList) {

                DBCursor cursor = Mongoconnect.getCursor(coll,mRange).batchSize(300);
                
                try {
                    while(cursor.hasNext()) {

                        DBObject obj = cursor.next();

                        intMass = (int) obj.get("_id"); // gets intMass from current document

                        BasicDBList peptideSequences = (BasicDBList) obj.get("s");

                        for (Iterator<Object> it = peptideSequences.iterator(); it.hasNext();) {
                            sequence = (String) it.next();
                            queryResults.add(makeIndexedSequence(intMass,sequence));
                        }
                            
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

            lowMass = Math.round((mRange.getPrecMass()-mRange.getTolerance())*1000);
            highMass = Math.round((mRange.getPrecMass()+mRange.getTolerance())*1000);
            BasicDBObject query = new BasicDBObject("_id", new BasicDBObject("$gte", lowMass).append("$lte", highMass));
            
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
            for (MassRange mRange : rList) {
                lowMass = Math.round((mRange.getPrecMass()-mRange.getTolerance())*1000);
                highMass = Math.round((mRange.getPrecMass()+mRange.getTolerance())*1000);
                orQuery.add(new BasicDBObject("MASS", new BasicDBObject("$gte", lowMass).append("$lte", highMass)));
                queryString += "{ MASS: { $gte: "+lowMass+", $lte: "+highMass+"} },";

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
    
    public static void main(String[] args) throws Exception {

        try {


        } catch(Exception e){
           System.out.println("general error");
        }
    }
    
}
