
package mongoconnect;

import blazmass.dbindex.IndexedSequence;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * Custom iterator class that returns single peptides at a time as an IndexedSequence from a mongo DBCursor
 * @author gstupp
 */
public class MongoSeqIter {

    private int intMass;
    private String sequence;
    private ListIterator<String> peptideSeq;
    private DBCursor cursor;
    private DBObject obj; //sequence mongo object {'_id"- mass, 's'- list of sequences}
    public int count = 0;
    private IndexedSequence indSeq = null;
    private IndexedSequence nextSeq = null;
    
    
    // constructor
    public MongoSeqIter(DBCursor cursor) {
        this.cursor = cursor;
        count = 0;
    }

    public IndexedSequence next() throws NoSuchElementException{
        if (nextSeq != null){
            indSeq = nextSeq;
            nextSeq = null;
            return indSeq; 
        }
        if (peptideSeq != null && peptideSeq.hasNext()){
            sequence = peptideSeq.next();
            indSeq = new IndexedSequence((float) intMass / 1000, sequence, sequence.length(), "---", "---");
            count++;
            return indSeq;
        }else {
            try {
                obj = cursor.next();
            } catch (NoSuchElementException e) {
                cursor.close();
                throw new NoSuchElementException("No more");
            }
            //System.out.println("obj-" + obj);
            intMass = (int) obj.get("_id"); // gets intMass from current document
            peptideSeq = ((List<String>) obj.get("s")).listIterator();
            sequence = peptideSeq.next();
            indSeq = new IndexedSequence((float) intMass / 1000, sequence, sequence.length(), "---", "---");
            count++;
            return indSeq;
        }

    }
    
    public boolean hasNext() {
        if (nextSeq == null) {
            try {
                nextSeq = next();
            } catch (Exception e){
                cursor.close();
                return false;
            }
            return true;
        } else{
            return true;
        }
        
    }

}