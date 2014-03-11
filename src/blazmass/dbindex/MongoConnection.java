/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package blazmass.dbindex;

import blazmass.dbindex.DBIndexStoreMongoDb;
import blazmass.dbindex.MassRange;
import blazmass.dbindex.MergeIntervals;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Harshil
 */
public class MongoConnection {

    /**
     * @param args the command line arguments
     */
    public String host_name = "localhost", db_name = "rnaseqDB", collection_name = "rnaseqK562";
    List listOfPeptide = new ArrayList();
    DBCollection coll = null;
    MongoClient mongoClient = null;
    DB db = null;

    public MongoConnection()
    {
        try 
        {
            mongoClient = new MongoClient(host_name, 27017);
            System.out.println("Connected to MongoD");
            db = mongoClient.getDB(db_name);
            coll = db.getCollection(collection_name);
        } 
        catch (UnknownHostException ex) 
        {
            Logger.getLogger(MongoConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public List getListOfPeptides(List<MassRange> ranges) {
        //convert mass ranges to intverals and merge overlapping mass ranges
        ArrayList<Interval> intervals = new ArrayList<Interval>();
        for (MassRange range : ranges)
        {
            Interval ith = Interval.massRangeToInterval(range);
            intervals.add(ith);
        }
        List<Interval> mergedIntervals = MergeIntervals.mergeIntervals(intervals);
        for (Interval massInterval : mergedIntervals) 
        {
            float minMass = massInterval.getStart() * 1000;
            float maxMass = massInterval.getEnd() * 1000;
            addPeptideDetails(minMass, maxMass);
        }
        return listOfPeptide;
    }

    public void addPeptideDetails(float minMass, float maxMass) {
        //Write the query
        BasicDBObject searchQuery = new BasicDBObject();
        searchQuery.put("MASS", new BasicDBObject("$gt", minMass).append("$lt", maxMass));

        DBCursor cursor = (DBCursor) coll.find(searchQuery);
        DBObject temp = null;
        while (cursor.hasNext()) 
        {
            temp = cursor.next();
            System.out.println(temp);
            PeptideDetail peptideDetail = new PeptideDetail();
            peptideDetail.setMass((int) temp.get("MASS"));
            peptideDetail.setSequence((String) temp.get("SEQ"));
            peptideDetail.setLenght((int) temp.get("LEN"));
            System.out.println("Length->\t" + temp.get("PARENTS"));
            BasicDBList subDetails = (BasicDBList) temp.get("PARENTS");
            Iterator itr = subDetails.iterator();
            BasicDBObject currentObject = new BasicDBObject();
            while (itr.hasNext()) 
            {
                currentObject = (BasicDBObject) itr.next();
                peptideDetail.setLeftResidue((String) currentObject.get("LR"));
                peptideDetail.setRightResidue((String) currentObject.get("RR"));
                peptideDetail.setProteinId((String) currentObject.get("PROT_ID"));
                peptideDetail.setOffset((int) currentObject.get("OFFSET"));
            }
            listOfPeptide.add(peptideDetail);
        }
    }

    public static void main(String[] args) {
        MongoConnection mg = new MongoConnection();
        mg.addPeptideDetails(25422, 25555);
    }
}
