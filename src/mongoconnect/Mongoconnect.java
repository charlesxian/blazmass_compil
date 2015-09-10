/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mongoconnect;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import static com.mongodb.client.model.Filters.*;

import java.util.List;
import java.util.ArrayList;
import blazmass.dbindex.MassRange;
import blazmass.dbindex.IndexedSequence;
//import blazmass.io.SearchParamReader;
import blazmass.io.SearchParams;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.bson.Document;
import org.bson.conversions.Bson;
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
    private static MongoDatabase massDB = null;
    private static MongoDatabase seqDB = null;
    private static MongoDatabase protDB = null;
    private static MongoCollection<Document> massDBCollection = null;
    private static MongoCollection<Document> seqDBCollection = null;
    private static MongoCollection<Document> protDBCollection = null;
    private static final MongoClientOptions.Builder options = MongoClientOptions.builder()
                                    .connectTimeout(5000) //default is 0 (no timeout)
                                    .socketTimeout(30000) //default is 0 (no timeout)
                                    .serverSelectionTimeout(30000); //default 
    
    //private static final MongoClientOptions.Builder options = null;

    public Mongoconnect() {
    }

    public static void disconnect(){
        if (massDBMongoClient != null)
            massDBMongoClient.close();
        if (seqDBMongoClient != null)
           seqDBMongoClient.close();
        if (protDBMongoClient != null)
            protDBMongoClient.close();
        System.out.println("Closed mongo connection");
    }
    /*
     connectToMassDB
    
     Connects to MongoDB 'MassDB' with hostname, port, dbname, 
     collection name specified in blazmass.params file (sParam object).
        
     Sets variables massDBMongoClient, massDB, massDBCollection for use 
     throughout the class.  Should only make one of each object per thread.
     */
    private static void connectToMassDB(SearchParams sParam) {
        if (massDBMongoClient == null) {
            try {
                massDBMongoClient = new MongoClient(new MongoClientURI(sParam.getMassDBURI(), options));
                System.out.println("------Making new connection to MongoDB/MassDB at " + massDBMongoClient);
                massDB = massDBMongoClient.getDatabase(sParam.getMassDBName());
                massDBCollection = massDB.getCollection(sParam.getMassDBCollection());
                System.out.println(massDBCollection);
                if (massDBCollection.count() == 0 ){
                    System.out.println("Massdb is empty!");
                    System.exit(1); 
                }
            } catch (MongoException e) {
                System.out.println("connectToMassDB error");
                e.printStackTrace();
                System.exit(1);
            }
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
    private static void connectToSeqDB(SearchParams sParam) {
        if (seqDBMongoClient == null) {
            try {
                seqDBMongoClient = new MongoClient(new MongoClientURI(sParam.getSeqDBURI(), options));
                System.out.println("------Making new connection to MongoDB/SeqDB at " + seqDBMongoClient);
                seqDB = seqDBMongoClient.getDatabase(sParam.getSeqDBName());
                seqDBCollection = seqDB.getCollection(sParam.getSeqDBCollection());
                System.out.println(seqDBCollection);
            } catch (MongoException e) {
                System.out.println("connectToSeqDB error");
                e.printStackTrace();
                System.exit(1);
            }
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
    private static void connectToProtDB(SearchParams sParam) {
        if (protDBMongoClient == null) {
            try {
                protDBMongoClient = new MongoClient(new MongoClientURI(sParam.getProtDBURI(), options));
                System.out.println("------Making new connection to MongoDB/ProtDB at " + protDBMongoClient);
                protDB = protDBMongoClient.getDatabase(sParam.getProtDBName());
                protDBCollection = protDB.getCollection(sParam.getProtDBCollection());
                System.out.println(protDBCollection);
            } catch (MongoException e) {
                System.out.println("connectToProtDB error");
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
    
    /*
    getSequencesIter: Does a range query. Reture an iterator that yields IndexedSequences containing a single peptide and its mass
     */
    public static MongoSeqIter getSequencesIter(List<MassRange> rList, SearchParams sParam) {
        
        connectToMassDB(sParam);

        int lowMass;
        int highMass;
        // Build long "or" query using all mass ranges
        List<Bson> orList = new ArrayList();
        for (MassRange mRange : rList) {
            lowMass = Math.round((mRange.getPrecMass() - mRange.getTolerance()) * 1000);
            highMass = Math.round((mRange.getPrecMass() + mRange.getTolerance()) * 1000);
            Bson x = and(gte("_id", lowMass), lte("_id", highMass));
            orList.add(x);
        }
        Bson query = or(orList);
        FindIterable<Document> cursor = massDBCollection.find(query).batchSize(3000);
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
    /*public static List<IndexedSequence> getSequences(List<MassRange> rList, SearchParams sParam) throws Exception {

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
*/
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
    /*private static DBCursor getMassDBCursor(MassRange mRange) throws Exception {

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
    }*/

    /*
     getParents
    
     Takes a peptide sequence string and SearchParams object as inputs.  Uses
     MongoDB SeqDB and (optionally) ProtDB.
    
     Returns a list of parent proteins, each formatted in SQT L line format.
     */
    public static List<String> getParents(String peptideSequence, SearchParams sParam, boolean isReversePeptide) throws Exception {

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

            return getParentsFromColl(peptideSequence, sParam, isReversePeptide);
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
    private static List<String> getParentsFromColl(String peptideSequence, SearchParams sParam, boolean isReversePeptide) throws Exception {
        
        List<String> parentProteinsOfPeptide = new ArrayList<>();

        try {
            Document peptideDocument = seqDBCollection.find(eq("_id", peptideSequence)).first();

            if (peptideDocument == null) {
                parentProteinsOfPeptide.add(""); // return list with a single empty string (if no parent proteins found)
            } else {
                ArrayList<Document> parentProteins = (ArrayList<Document>) peptideDocument.get("p");

                if (parentProteins.isEmpty()) { // this shouldn't happen.  Shouldn't be peptides in SeqDB without parent proteins...
                    parentProteinsOfPeptide.add(""); // return list with a single empty string (if no parent proteins found)
                } else {
                    for (Iterator<Document> it = parentProteins.iterator(); it.hasNext();) {
                        Document parent = it.next();
                        parentProteinsOfPeptide.add(parseParentObjectSimple(peptideSequence, parent, sParam, isReversePeptide));
                    }
                }
            }
            return parentProteinsOfPeptide;
        } catch (Exception e) {
            System.out.println("SeqDB Query retrieval error");
            e.printStackTrace();
            return null;
        }
    }

    /*
     parseParentObjectSimple
        
     Helper method for getParentsFromColl.  Takes a peptide sequence and a
     single parent DBObject as input (along with SearchParams object).
    
     Processes parent object and formats output in SQT L line format.
     Calls parseParentObjectDetailed if using ProtDB.
    
    if isReversePeptide, prepend "Reverse_" on the protID no matter what
     */
    private static String parseParentObjectSimple(String peptideSequence, Document parent, SearchParams sParam, boolean isReversePeptide) throws Exception {
        // example parent object:
        // { "_id" : "DYMAAGLYDRAEDMFSQLINEEDFR", "p" : [ { "i" : 20915500, "r" : "VSA", "l" : "LGR", "o" : 115 }, { "i" : 21556668, "r" : "VSA", "l" : "LGR", "o" : 115 } ] }
        try {
            String myParent;
            int parentID = ((Number) parent.get("i")).intValue();
            String parentIDString;

            if (sParam.isUsingProtDB())
                parentIDString = parseParentObjectDetailed(parentID, sParam);
            else
                parentIDString = String.valueOf(parentID);
            
            if ((parent.containsKey("d") && (boolean) parent.get("d") == true) || isReversePeptide)
                myParent = "Reverse_";
            else
                myParent = "";
            
            myParent += parentIDString + "\t0\t"
                    + (String) parent.get("l") + "."
                    + peptideSequence + "." + (String) parent.get("r");
            
            // For future information. DTASelect seems perfectly happy to accept the peptide string
            // without the left and right 3 residues, like below:
            // myParent += parentIDString + "\t0\t" + peptideSequence;
            
            return myParent;

        } catch (Exception e) {
            System.out.println("SeqDB parseParentObjectSimple error");
            e.printStackTrace();
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
            String proteinDefline;
            Document proteinDocument = protDBCollection.find(eq("_id", parentID)).first();

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
