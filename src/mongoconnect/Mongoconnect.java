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
//import blazmass.io.SearchParamReader;
import blazmass.io.SearchParams;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.NoSuchElementException;
//import java.util.logging.Level;
//import java.util.logging.Logger;

/**
 *
 * @author sandip
 */
public class Mongoconnect {

    /**
     * @param args the command line arguments
     */
//    private static final Logger logger = Logger.getLogger(SearchParamReader.class.getName());
    private static MongoClient massDBMongoClient = null;
    private static MongoClient seqDBMongoClient = null;
    private static MongoClient protDBMongoClient = null;
    private static DB massDB = null;
    private static DB seqDB = null;
    private static DB protDB = null;
    private static DBCollection massDBCollection = null;
    private static DBCollection seqDBCollection = null;
    private static DBCollection protDBCollection = null;

    public Mongoconnect() {
    }

    /*
     connectToMassDB
    
     Connects to MongoDB 'MassDB' with hostname, port, dbname, 
     collection name specified in blazmass.params file (sParam object).
        
     Sets variables massDBMongoClient, massDB, massDBCollection for use 
     throughout the class.  Should only make one of each object per thread.
     */
    private static void connectToMassDB(SearchParams sParam) throws Exception {

        try {

            if (massDBMongoClient == null) {

                massDBMongoClient = new MongoClient(sParam.getMassDBServer(), sParam.getMassDBPort());
                System.out.println("-------------Making new connection to MongoDB/MassDB at " + sParam.getMassDBServer());
                massDB = massDBMongoClient.getDB(sParam.getMassDBName());
                massDBCollection = massDB.getCollection(sParam.getMassDBCollection());
            }

        } catch (Exception e) {
            System.out.println("connectToMassDB error");
        }
    }

    /*
     connectToSeqDB
    
     Connects to MongoDB 'SeqDB' with hostname, port, dbname, 
     collection name specified in blazmass.params file (sParam object).
     (using SeqDB is optional)
        
     Sets variables seqDBMongoClient, seqDB, seqDBCollection for use 
     throughout the class.  Should only make one of each connection object, 
     to be reused many times.
     */
    private static void connectToSeqDB(SearchParams sParam) throws Exception {

        try {

            if (seqDBMongoClient == null) {

                seqDBMongoClient = new MongoClient(sParam.getSeqDBServer(), sParam.getSeqDBPort());
                System.out.println("-------------Making new connection to MongoDB/SeqDB at " + sParam.getSeqDBServer());
                seqDB = seqDBMongoClient.getDB(sParam.getSeqDBName());
                seqDBCollection = seqDB.getCollection(sParam.getSeqDBCollection());
            }

        } catch (Exception e) {
            System.out.println("connectToSeqDB error");
        }
    }

    /*
     connectToProtDB
    
     Connects to MongoDB 'ProtDB' with hostname, port, dbname, 
     collection name specified in blazmass.params file (sParam object).
     (using ProtDB is optional)
        
     Sets variables protDBMongoClient, protDB, protDBCollection for use 
     throughout the class.  Should only make one of each connection object, 
     to be reused many times.
     */
    private static void connectToProtDB(SearchParams sParam) throws Exception {

        try {

            if (protDBMongoClient == null) {

                protDBMongoClient = new MongoClient(sParam.getProtDBServer(), sParam.getProtDBPort());
                System.out.println("-------------Making new connection to MongoDB/ProtDB at " + sParam.getProtDBServer());
                protDB = protDBMongoClient.getDB(sParam.getProtDBName());
                protDBCollection = protDB.getCollection(sParam.getProtDBCollection());
            }

        } catch (Exception e) {
            System.out.println("connectToProtDB error");
        }
    }
    /*
    getSequencesIter: Does a range query. Reture an iterator that yields IndexedSequences containing a single peptide and its mass
     */
    public static MongoSeqIter getSequencesIter(List<MassRange> rList, SearchParams sParam) throws Exception {

        try {
            connectToMassDB(sParam);
        } catch (Exception e) {
            System.out.println("MassDB connection error");
            return null;
        }

        int lowMass;
        int highMass;
        // Build long "or" query using all mass ranges
        BasicDBList or = new BasicDBList();
        for (MassRange mRange : rList) {
            lowMass = Math.round((mRange.getPrecMass() - mRange.getTolerance()) * 1000);
            highMass = Math.round((mRange.getPrecMass() + mRange.getTolerance()) * 1000);
            or.add(new BasicDBObject("_id", new BasicDBObject("$gte", lowMass).append("$lte", highMass)));
        }
        DBObject query = new BasicDBObject("$or", or);
        //System.out.println(query);

        DBCursor cursor = massDBCollection.find(query).batchSize(3000);
        MongoSeqIter msi = new MongoSeqIter(cursor);
        return msi;
    }

    /*
     getSequences
        
     Drop-in substitute for getSequences() method from DBIndexer.
     Takes MassRange list and sParam parameters objects as input - same as
     DBIndexer.getSequences()

     Handles iteration through a MongoDB query cursor.
     Returns an ArrayList of IndexedSequence objects - same as 
     DBIndexer.getSequences()
     */
    public static List<IndexedSequence> getSequences(List<MassRange> rList, SearchParams sParam) throws Exception {

        try {
            connectToMassDB(sParam);
        } catch (Exception e) {
            System.out.println("MassDB connection error");
//            logger.log(Level.SEVERE, "MassDB connection error");
            return null;
        }

        try {
            List<IndexedSequence> queryResults = new ArrayList<>();

            int intMass;
            String sequence;

            for (MassRange mRange : rList) {

                DBCursor cursor = getMassDBCursor(mRange).batchSize(3000); // try modifying batch size to tune performance

                try {
                    while (cursor.hasNext()) {

                        DBObject obj = cursor.next();

                        intMass = (int) obj.get("_id"); // gets intMass from current document

                        BasicDBList peptideSequences = (BasicDBList) obj.get("s");

                        for (Iterator<Object> it = peptideSequences.iterator(); it.hasNext();) {
                            sequence = (String) it.next();
                            queryResults.add(makeIndexedSequence(intMass, sequence));
                        }

                    }
                    System.out.println("Number of peptide sequence results from query: " + queryResults.size());

                } finally {
                    cursor.close();
                }
            }

            return queryResults;

        } catch (Exception e) {
            System.out.println("DB Query / getSequencesFromColl error");
            return null;
        }
    }

    /*
     makeIndexedSequence
    
     Takes an integer mass (exact mass rounded to 3 decimal points * 1000) 
     and a peptide sequence string as inputs.
    
     Returns an IndexedSequence object as output.
     */
    public static IndexedSequence makeIndexedSequence(int intMass, String sequence) throws Exception {
        try {

            float floatMass = (float) intMass / 1000; //'_id' is 'MASS'
            IndexedSequence indSeq = new IndexedSequence(floatMass, sequence, sequence.length(), "---", "---");

            return indSeq;

        } catch (Exception e) {
            System.out.println("DB Query / getSequencesFromColl error");
            return null;
        }
    }

    /*
     getMassDBCursor
    
     Takes a MassRange object as input.  Constructs and executes MongoDB
     range-based query on mass ("_id") using massDBCollection object.
    
     Returns an a MongoDB MassDB query cursor object as output.
     */
    private static DBCursor getMassDBCursor(MassRange mRange) throws Exception {

        try {

            int lowMass;
            int highMass;

            lowMass = Math.round((mRange.getPrecMass() - mRange.getTolerance()) * 1000);
            highMass = Math.round((mRange.getPrecMass() + mRange.getTolerance()) * 1000);
//            System.out.println("!!!!! LOWMASS\t"+String.valueOf(lowMass)+"\tHIGHMASS"+String.valueOf(highMass)); // remove
            BasicDBObject query = new BasicDBObject("_id", new BasicDBObject("$gte", lowMass).append("$lte", highMass));

            DBCursor cursor = massDBCollection.find(query);
//            System.out.println("==============" + mRange.getPrecMass() + " ==" + mRange.getTolerance() + " " + lowMass + " " + highMass);
            return cursor;

        } catch (Exception e) {
            System.out.println("MassDB Query / Cursor retrieval error");
            return null;
        }
    }

    /*
     getParents
    
     Takes a peptide sequence string and SearchParams object as inputs.  Uses
     MongoDB SeqDB and (optionally) ProtDB.
    
     Returns a list of parent proteins, each formatted in SQT L line format.
     */
    public static List<String> getParents(String peptideSequence, SearchParams sParam) throws Exception {

        List<String> parentProteinsOfPeptide = new ArrayList<>();

        if (sParam.isUsingSeqDB()) {
            try {
                connectToSeqDB(sParam);
                if (sParam.isUsingProtDB()) {
                    connectToProtDB(sParam);
                }
            } catch (Exception e) {
                System.out.println("SeqDB/ProtDB connection error");
                parentProteinsOfPeptide.add("");
                return parentProteinsOfPeptide; // return list with a single empty string (if not using SeqDB)
            }

            return getParentsFromColl(peptideSequence, sParam);
        } else {
            parentProteinsOfPeptide.add("");
            return parentProteinsOfPeptide; // return list with a single empty string (if not using SeqDB)
        }
    }

    /*
     getParentsFromColl
    
     Helper method for getParents.  Takes a peptide sequence string and 
     SearchParams object as inputs.
    
     Constructs and executes MongoDB findOne query using peptide sequence.
    
     Returns a list of parent proteins, each formatted in SQT L line format.
     */
    private static List<String> getParentsFromColl(String peptideSequence, SearchParams sParam) throws Exception {

        List<String> parentProteinsOfPeptide = new ArrayList<>();

        try {
            BasicDBObject query = new BasicDBObject("_id", peptideSequence);
            DBObject peptideDocument = seqDBCollection.findOne(query);

            if (peptideDocument == null) {
                parentProteinsOfPeptide.add(""); // return list with a single empty string (if no parent proteins found)
            } else {
                BasicDBList parentProteins = (BasicDBList) peptideDocument.get("p");

                if (parentProteins.isEmpty()) { // this shouldn't happen.  Shouldn't be peptides in SeqDB without parent proteins...
                    parentProteinsOfPeptide.add(""); // return list with a single empty string (if no parent proteins found)
                } else {
                    for (Iterator<Object> it = parentProteins.iterator(); it.hasNext();) {
                        DBObject parent = (DBObject) it.next();
                        parentProteinsOfPeptide.add(parseParentObjectSimple(peptideSequence, parent, sParam));
                    }
                }
            }

            return parentProteinsOfPeptide;

        } catch (Exception e) {
            System.out.println("SeqDB Query retrieval error");
            return null;
        }
    }

    /*
     parseParentObjectSimple
        
     Helper method for getParentsFromColl.  Takes a peptide sequence and a
     single parent DBObject as input (along with SearchParams object).
    
     Processes parent object and formats output in SQT L line format.
     Calls parseParentObjectDetailed if using ProtDB.
     */
    private static String parseParentObjectSimple(String peptideSequence, DBObject parent, SearchParams sParam) throws Exception {
        // example parent object:
        // { "_id" : "DYMAAGLYDRAEDMFSQLINEEDFR", "p" : [ { "i" : 20915500, "r" : "VSA", "l" : "LGR", "o" : 115 }, { "i" : 21556668, "r" : "VSA", "l" : "LGR", "o" : 115 } ] }
        try {

            String myParent = "";
            int parentID = (int) parent.get("i");
            String parentIDString;

            if (sParam.isUsingProtDB()) {

                parentIDString = parseParentObjectDetailed(parentID, sParam);

                if (parentIDString == null) {
                    // if parent protein ID is not found in ProtDB...
                    if (parent.containsField("d")) {
                        if ((boolean) parent.get("d") == true) {
                            myParent = "Reverse_";
                        }
                    }
                    parentIDString = String.valueOf(parentID);
                }
            } else {
                if (parent.containsField("d")) {
                    if ((boolean) parent.get("d") == true) {
                        myParent = "Reverse_";
                    }
                }
                parentIDString = String.valueOf(parentID);
            }

            myParent += parentIDString + "\t0\t"
                    + (String) parent.get("l") + "."
                    + peptideSequence + "." + (String) parent.get("r");

            return myParent;

        } catch (Exception e) {
            System.out.println("SeqDB parseParentObjectSimple error");
            return null;
        }
    }

    /*
     parseParentObjectDetailed
    
     Helper method for parseParentObjectSimple.  Takes an integer parent 
     protein ID and SearchParams object as inputs.
    
     Queries MongoDB ProtDB for FASTA defline (protein name, species etc.)
    
     Returns FASTA defline if found in ProtDB; otherwise returns string
     parent protein ID.
     */
    private static String parseParentObjectDetailed(int parentID, SearchParams sParam) throws Exception {
        try {

            BasicDBObject query = new BasicDBObject("_id", parentID);
            String proteinDefline;
            DBObject proteinDocument = protDBCollection.findOne(query);

            if (proteinDocument == null) {
                // if there is no result from the ProtDB query...
                return null;
            } else {
                // if there is a result from the query...
                proteinDefline = (String) proteinDocument.get("d");
                if (proteinDefline == null) {
                    // if there is no defline result from the query...
                    return String.valueOf(parentID);
                } else {
                    // if there is a defline key in the query result...
                    return proteinDefline;
                }
            }

        } catch (Exception e) {
            System.out.println("SeqDB parseParentObjectDetailed error");
            return null;
        }
    }

    public static void main(String[] args) throws Exception {

        try {

        } catch (Exception e) {
            System.out.println("general error");
        }
    }

}
