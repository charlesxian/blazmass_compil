/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package mongoconnect;

/**
 *
 * @author sandip
 */
public class ResidueInfo {
    
    private String residueLeft;
    private String residueRight;

    public ResidueInfo(String residueLeft, String residueRight) {
        this.residueLeft = residueLeft;
        this.residueRight = residueRight;
    }

    public String getResidueLeft() {
        return residueLeft;
    }

    public String getResidueRight() {
        return residueRight;
    }

    
    
    @Override
    public String toString() {
        return "ResidueInfo{" + "residueLeft=" + residueLeft + ", residueRight=" + residueRight + '}';
    }
    
    
    
}
