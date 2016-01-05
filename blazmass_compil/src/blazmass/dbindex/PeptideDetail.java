/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package blazmass.dbindex;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Harshil
 */
public class PeptideDetail {

    private String sequence;
    private float mass;
    private int lenght;
    private List leftResidue = new ArrayList();
    private List rightResidue = new ArrayList();
    private List proteinId = new ArrayList();
    private List offset = new ArrayList();

    /**
     * @return the sequence
     */
    public String getSequence() {
        return sequence;
    }

    /**
     * @param sequence the sequence to set
     */
    public void setSequence(String sequence) {
        this.sequence = sequence;
    }

    /**
     * @return the mass
     */
    public float getMass() {
        return mass;
    }

    /**
     * @param mass the mass to set
     */
    public void setMass(float mass) {
        this.mass = mass;
    }

    /**
     * @return the lenght
     */
    public int getLenght() {
        return lenght;
    }

    /**
     * @param lenght the lenght to set
     */
    public void setLenght(int lenght) {
        this.lenght = lenght;
    }

    /**
     * @return the leftResidue
     */
    public List getLeftResidue() {
        return leftResidue;
    }

    /**
     * @param leftResidue the leftResidue to set
     */
    public void setLeftResidue(String leftResidue) {
        this.leftResidue.add(leftResidue);
    }

    /**
     * @return the rightResidue
     */
    public List getRightResidue() {
        return rightResidue;
    }

    /**
     * @param rightResidue the rightResidue to set
     */
    public void setRightResidue(String rightResidue) {
        this.rightResidue.add(rightResidue);
    }

    /**
     * @return the proteinId
     */
    public List getProteinId() {
        return proteinId;
    }

    /**
     * @param proteinId the proteinId to set
     */
    public void setProteinId(String proteinId) {
        this.proteinId.add(proteinId);
    }

    /**
     * @return the offset
     */
    public List getOffset() {
        return offset;
    }

    /**
     * @param offset the offset to set
     */
    public void setOffset(int offset) {
        this.offset.add(offset);
    }
}
