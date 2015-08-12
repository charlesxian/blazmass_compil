/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package blazmass.io;

/**
 *
 * @author rpark
 */
import java.io.*;
import java.util.*;
import blazmass.AssignMass;
import blazmass.dbindex.DBIndexer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Robin Park
 * @version $Id: SearchParamReader.java,v 1.56 2013/10/25 16:42:44 rpark2 Exp $
 */
public class SearchParamReader {

    private String path;
    private String fileName;
    private SearchParams param;
    //private boolean isModification;
    private Hashtable<String, String> ht = new Hashtable<>();
    private final Logger logger;
    public static final String DEFAULT_PARAM_FILE_NAME = "blazmass.params";

    public static void main(String args[]) throws Exception {

        if (args.length < 2) {
            System.out.println("Usage: java SearchParamReader path param_filename");
            return;
        }
        SearchParamReader p = new SearchParamReader(args[0], args[1]);
        SearchParams param = p.getParam();

    }

    public SearchParamReader(String path, String fileName) throws IOException {
        this.logger = Logger.getLogger(SearchParamReader.class.getName());
        this.path = path;
        this.fileName = fileName;
        init();
    }

    public void init() throws IOException {
        path = path + File.separator + fileName;
        if (!new File(path).exists()) {
            throw new IOException("Could not locate params file at path: " + path);
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path));
            String eachLine;

            //Hashtable<String> ht = new Hashtable<String>();
            param = SearchParams.getInstance();
            //StringBuffer sb = new StringBuffer();
            //br.skip(2000);
            while ((eachLine = br.readLine()) != null) {
                if (eachLine.startsWith("#")) {
                    continue;
                }
                String[] strArr = eachLine.split("=");
                if (strArr.length < 2) {
                    continue;
                }

                ht.put(strArr[0].trim(), strArr[1].split(";")[0].trim());
            }
            String sqtSuffix = trimValue(getParam("sqt_suffix"));
            if (sqtSuffix == null) {
                param.setSqtSuffix("");
            } else {
                param.setSqtSuffix(sqtSuffix);
            }

            param.setDatabaseName(trimValue(getParam("database_name")));
            param.setIndexDatabaseName(trimValue(getParam("index_database_name")));

            int useIndex = trimValueAsInt(getParam("use_index"), 1);
            param.setUseIndex(useIndex == 1);

            int indexType = trimValueAsInt(getParam("index_type"), 1);
            param.setIndexType(indexType == 1 ? DBIndexer.IndexType.INDEX_NORMAL : DBIndexer.IndexType.INDEX_LARGE);

            int inMemoryIndexI = trimValueAsInt(getParam("index_inmemory"), 1);
            boolean inMemoryIndex = inMemoryIndexI == 1;
            param.setInMemoryIndex(inMemoryIndex);

            int indexFactor = trimValueAsInt(getParam("index_factor"), 6);
            param.setIndexFactor(indexFactor);

            param.setParametersFile(path);

            String pepTolerance = getParam("ppm_peptide_mass_tolerance");
            if (null == pepTolerance) {
                System.out.println("Error: ppm_peptide_mass_tolerance is missing");
            }

            param.setPeptideMassTolerance(trimValueAsFloat(pepTolerance));
            param.setFragmentIonTolerance(trimValueAsFloat(getParam("ppm_fragment_ion_tolerance")));
            param.setFragmentIonToleranceInt((int) Double.parseDouble(getParam("ppm_fragment_ion_tolerance").trim()));

            String highRes = getParam("ppm_fragment_ion_tolerance_high");
            if (null != highRes && "1".equals(highRes.trim())) {
                param.setHighResolution(true);
            } else {
                param.setHighResolution(false);
            }

            String mongoParam = getParam("use_mongodb").trim();
            String seqDBParam = getParam("use_SeqDB").trim();
            String protDBParam = getParam("use_ProtDB").trim();
            if (mongoParam == null || Integer.parseInt(mongoParam) == 0) {
                param.setUsingMongoDB(false);
            } else {
                param.setUsingMongoDB(true);
                String massDBServer = getParam("MassDB_server").trim();
                int massDBPort = Integer.parseInt(getParam("MassDB_port").trim());
                String massDBname = getParam("MassDB_dbname").trim();
                String massDBCollection = getParam("MassDB_collection").trim();
                param.setMassDBServer(massDBServer);
                param.setMassDBPort(massDBPort);
                param.setMassDBName(massDBname);
                param.setMassDBCollection(massDBCollection);
                param.setDatabaseName(massDBname + "." + massDBCollection + " [MongoDB]");

                if (seqDBParam != null && Integer.parseInt(seqDBParam) == 1) {
                    //use SeqDB
                    param.setUsingSeqDB(true);
                    String seqDBServer = getParam("SeqDB_server");
                    int seqDBPort = Integer.parseInt(getParam("SeqDB_port"));
                    String seqDBName = getParam("SeqDB_dbname");
                    String seqDBCollection = getParam("SeqDB_collection");
                    param.setSeqDBServer(seqDBServer);
                    param.setSeqDBPort(seqDBPort);
                    param.setSeqDBName(seqDBName);
                    param.setSeqDBCollection(seqDBCollection);

                    if (protDBParam != null && Integer.parseInt(protDBParam) == 1) {
                        //use ProtDB
                        param.setUsingProtDB(true);
                        String protDBServer = getParam("ProtDB_server");
                        int protDBPort = Integer.parseInt(getParam("ProtDB_port"));
                        String protDBName = getParam("ProtDB_dbname");
                        String protDBCollection = getParam("ProtDB_collection");
                        param.setProtDBServer(protDBServer);
                        param.setProtDBPort(protDBPort);
                        param.setProtDBName(protDBName);
                        param.setProtDBCollection(protDBCollection);
                    } else {
                        param.setUsingProtDB(false);
                        System.out.println("Not using ProtDB MongoDB database -- SQT output will be incomplete");
                    }
                } else {
                    param.setUsingSeqDB(false);
                    System.out.println("Not using SeqDB MongoDB database -- SQT output will be incomplete");
                }
            }

            String massTypeParent = getParam("mass_type_parent").trim();

            if ("1".equals(massTypeParent)) {
                param.setUseMonoParent(true);
            } else {
                param.setUseMonoParent(false);
            }
            param.setMassTypeParent(trimValueAsInt(massTypeParent));  //; 0=average masses, 1=monoisotopic masses

            String massTypeFrag = getParam("mass_type_fragment").trim();

            // System.out.println("=====---=====" + massTypeParent);
            // System.out.println("=====---=====" + massTypeFrag);
            if ("1".equals(massTypeFrag)) {
                param.setUseMonoFragment(true);
            } else {
                param.setUseMonoFragment(false);
            }
            param.setMassTypeFragment(trimValueAsInt(massTypeFrag));  //; 0=average masses, 1=monoisotopic masses

            param.setNumPeptideOutputLnes(trimValueAsInt(getParam("num_output_lines")));
            param.setRemovePrecursorPeak(trimValueAsInt(getParam("remove_precursor_peak")));
            String ionSeries = getParam("ion_series");
            String arr[] = ionSeries.split(" ");

            //System.out.println("===========" + arr[0] + arr[1] + " " + arr[2]);
            param.setNeutralLossAions(Integer.parseInt(arr[0]));
            param.setNeutralLossBions(Integer.parseInt(arr[1]));
            param.setNeutralLossYions(Integer.parseInt(arr[2]));

            int[] ions = new int[9];
            float[] weightArr = new float[12];

            for (int i = 0; i < 9; i++) {
                weightArr[i] = Float.parseFloat(arr[i + 3]);
            }

            for (int i = 0; i < 3; i++) {
                weightArr[i + 9] = Float.parseFloat(arr[i]);
            }

            param.setWeightArr(weightArr);

            ions[0] = (int) (10 * Double.parseDouble(arr[3])); //a
            ions[1] = (int) (10 * Double.parseDouble(arr[4])); //b
            ions[2] = (int) (10 * Double.parseDouble(arr[5])); //c
            ions[3] = (int) (10 * Double.parseDouble(arr[6]));
            ions[4] = (int) (10 * Double.parseDouble(arr[7]));
            ions[5] = (int) (10 * Double.parseDouble(arr[8]));
            ions[6] = (int) (10 * Double.parseDouble(arr[9]));  //x
            ions[7] = (int) (10 * Double.parseDouble(arr[10])); //y
            ions[8] = (int) (10 * Double.parseDouble(arr[11])); //z

            int numIonUsed = 0;
            for (int eachIon : ions) {
                if (eachIon > 0) {
                    numIonUsed++;
                }
            }

            param.setNumIonSeriesUsed(numIonUsed);
            param.setIonSeries(ions);
            param.setMaxNumDiffMod(trimValueAsInt(getParam("max_num_differential_AA_per_mod")));

            Object obj = getParam("peptide_mass_tolerance");
            if (null != obj) {
                param.setPeptideMassTolerance(trimValueAsFloat(obj.toString()));
            }

            String varMassTol = getParam("var_peptide_mass_tolerance");
            if (null != varMassTol) {
                param.setVariableTolerance(true);
                param.setVariablePeptideMassTolerance(trimValueAsFloat(varMassTol.trim()));
            }

            String amuMassTol = getParam("amu_peptide_mass_tolerance");
            if (null != amuMassTol) {
                param.setPeptideMassTolerance(trimValueAsFloat(amuMassTol));
            }

            String ppmMassTol = getParam("ppm_peptide_mass_tolerance");
            if (null != ppmMassTol) {
                param.setUsePPM(true);
                param.setRelativePeptideMassTolerance(trimValueAsFloat(ppmMassTol) / 1000f);
            }

            param.setIsotopes(trimValueAsInt(getParam("isotopes")));
            String n15enrich = getParam("n15_enrichment");
            if (null != n15enrich) {
                param.setN15Enrichment(trimValueAsFloat(n15enrich.trim()));
            }

            param.setMatchPeakTolerance(trimValueAsFloat(getParam("match_peak_tolerance")));
            param.setNumPeptideOutputLnes(trimValueAsInt(getParam("num_output_lines")));
            param.setRemovePrecursorPeak(trimValueAsInt(getParam("remove_precursor_peak")));
            //param.setAddCterminus(trimValueAsFloat(getParam("add_C_terminus")));
            //param.setAddNterminus(trimValueAsFloat(getParam("add_N_terminus")));
            AssignMass.setcTerm(trimValueAsFloat(getParam("add_C_terminus")));
            if (AssignMass.getcTerm() > 0) {
                SearchParams.addStaticParam("cterm", AssignMass.getcTerm());
            }

            AssignMass.setnTerm(trimValueAsFloat(getParam("add_N_terminus")));
            if (AssignMass.getnTerm() > 0) {
                SearchParams.addStaticParam("nterm", AssignMass.getnTerm());
            }

            AssignMass amassPar = new AssignMass(param.isUseMonoParent());
            param.setHplusparent(amassPar.getHplus());
            param.setHparent(amassPar.getH());
            param.setoHparent(amassPar.getOh());

            String minPrecursor = getParam("min_precursor_mass");
            if (null != minPrecursor) {
                param.setMinPrecursorMass(Float.parseFloat(minPrecursor.trim()));
            }

            String maxPrecursor = getParam("max_precursor_mass");
            if (null != maxPrecursor) {
                param.setMaxPrecursorMass(Float.parseFloat(maxPrecursor.trim()));
            }
            String minFragPeakNum = getParam("min_frag_num");
            if (null != minFragPeakNum) {
                param.setMinFragPeakNum(Integer.parseInt(minFragPeakNum));
            }

            AssignMass amassFrag = new AssignMass(param.isUseMonoFragment());

            System.out.println("============" + param.isUseMonoParent() + " " + param.isUseMonoFragment());

            //param.setYionfragment(param.getAddCterminus() + amassPar.getOh() + amassPar.getH() + amassPar.getH());
            //param.setBionfragment(param.getAddNterminus() + amassPar.getH());
            AssignMass.setBionfragment(AssignMass.getnTerm() + amassPar.getH());
            AssignMass.setYionfragment(AssignMass.getcTerm() + amassPar.getOh() + amassPar.getH() + amassPar.getH());

            //System.out.println("============" + (amassPar.getOh() + amassPar.getH() + amassPar.getH()));
            //System.out.println(AssignMass.getcTerm() + "============" + (amassPar.getOh() + "\t" +  amassPar.getH() + "\t" +  amassPar.getH()));
            param.setBinWidth(amassPar.getBinWidth());

            //amassPar.addMass('G', 1.1f);
            float f = Float.parseFloat(getParam("add_C_terminus"));
            amassPar.setcTerm(f);
            //amassFrag.setcTerm(f);

            f = Float.parseFloat(getParam("add_N_terminus"));
            amassPar.setnTerm(f);
            //amassFrag.setnTerm(f);

            f = Float.parseFloat(getParam("add_G_Glycine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('G', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('G', f);
            }

            f = Float.parseFloat(getParam("add_A_Alanine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('A', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('A', f);
            }

            f = Float.parseFloat(getParam("add_S_Serine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('S', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('S', f);
            }

            f = Float.parseFloat(getParam("add_P_Proline"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('P', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('P', f);
            }
            //amassFrag.addMass('P', f );

            f = Float.parseFloat(getParam("add_V_Valine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('V', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('V', f);
            }
            //amassFrag.addMass('V', f );

            f = Float.parseFloat(getParam("add_T_Threonine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('T', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('T', f);
            }
            //amassFrag.addMass('T', f );

            //System.out.println("====" + AssignMass.getMass('C') + " " + param.getN15Enrichment());
            //System.out.println("====>>" + f);        
            f = Float.parseFloat(getParam("add_C_Cysteine"));
            if (param.getN15Enrichment() > 0) {
                f = f + param.getN15Enrichment() * (f - 57.02146f);
            }
            //amassPar.addMass('C', f*param.getN15Enrichment());

            amassPar.addMass('C', f);

            /*
             if (f > 56.9f) {                
             System.out.println("===" + param.getN15Enrichment() + " " +  f); 
             f = f + param.getN15Enrichment() * (f - 57.02146f);
                
             System.out.println("===" + param.getN15Enrichment() + " " +  f); 
             }*/
            //amassFrag.addMass('C', f );
            f = Float.parseFloat(getParam("add_L_Leucine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('L', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('L', f);
            }
            //amassFrag.addMass('L', f );

            f = Float.parseFloat(getParam("add_I_Isoleucine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('I', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('I', f);
            }
            //amassFrag.addMass('I', f );

            f = Float.parseFloat(getParam("add_X_LorI"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('X', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('X', f);
            }
            //amassFrag.addMass('X', f );

            f = Float.parseFloat(getParam("add_N_Asparagine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('N', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('N', f);
            }
            //amassFrag.addMass('N', f );

            f = Float.parseFloat(getParam("add_O_Ornithine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('O', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('O', f);
            }
            //amassFrag.addMass('O', f );

            f = Float.parseFloat(getParam("add_B_avg_NandD"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('B', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('B', f);
            }
            //amassFrag.addMass('B', f );

            f = Float.parseFloat(getParam("add_D_Aspartic_Acid"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('D', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('D', f);
            }
            //amassFrag.addMass('D', f );

            f = Float.parseFloat(getParam("add_Q_Glutamine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('Q', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('Q', f);
            }
            //amassFrag.addMass('Q', f );

            f = Float.parseFloat(getParam("add_K_Lysine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('K', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('K', f);
            }
            //amassFrag.addMass('K', f );

            f = Float.parseFloat(getParam("add_Z_avg_QandE"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('Z', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('Z', f);
            }
            //amassFrag.addMass('Z', f );

            f = Float.parseFloat(getParam("add_E_Glutamic_Acid"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('E', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('E', f);
            }
            //amassFrag.addMass('E', f );

            f = Float.parseFloat(getParam("add_M_Methionine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('M', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('M', f);
            }
            //amassFrag.addMass('M', f );

            f = Float.parseFloat(getParam("add_H_Histidine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('H', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('H', f);
            }
            //amassFrag.addMass('H', f );

            f = Float.parseFloat(getParam("add_F_Phenyalanine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('F', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('F', f);
            }
            //amassFrag.addMass('F', f );

            f = Float.parseFloat(getParam("add_R_Arginine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('R', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('R', f);
            }
            //amassFrag.addMass('R', f );

            f = Float.parseFloat(getParam("add_Y_Tyrosine"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('Y', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('Y', f);
            }
            //amassFrag.addMass('Y', f );

            f = Float.parseFloat(getParam("add_W_Tryptophan"));
            if (param.getN15Enrichment() > 0) {
                amassPar.addMass('W', f * param.getN15Enrichment());
            } else {
                amassPar.addMass('W', f);
            }
            //amassFrag.addMass('W', f );

            param.setPdAAMassParent(amassPar.getPdAAMass());
            param.setPdAAMassFragment(amassFrag.getPdAAMass());

            param.setMaxInternalCleavageSites(Integer.parseInt(getParam("max_num_internal_cleavage_sites")));
            param.setNumPeptideOutputLnes(Integer.parseInt(getParam("num_output_lines")));
            param.setMaxMissedCleavages(Integer.parseInt(getParam("miscleavage")));
            param.setEnzymeName(getParam("enzyme_name"));
            param.setEnzymeResidues(getParam("enzyme_residues"));
            param.setEnzymeBreakAA(getParam("enzyme_residues"));
            param.setEnzymeCut(getParam("enzyme_cut"));
            param.setEnzymeNocutResidues(getParam("enzyme_nocut_residues"));
            param.setEnzymeNoBreakAA(getParam("enzyme_nocut_residues"));

            if ("c".equals(getParam("enzyme_cut"))) {
                param.setEnzymeOffset(1);
            } else //if("n".equals( getParam("enzyme_cut") )                
            {
                param.setEnzymeOffset(0);
            }
            
            // Reverse peptides?
            // If true, the db does not contain reverse peptides and each
            // peptide returned from the query should be reversed
            String doReversePeptides = getParam("reverse_peptides");
            if (doReversePeptides != null) {
                param.doReversePeptides = Integer.parseInt(doReversePeptides) == 1;
            } else {
                param.doReversePeptides = false;
            }
            System.out.println("doReversePeptides: " + param.doReversePeptides);
            
            // Only search for diffmods?
            String only_diff_search = getParam("only_diff_search");
            if (only_diff_search != null) {
                param.onlyDiffMod = Integer.parseInt(only_diff_search) == 1;
            } else {
                param.onlyDiffMod = false;
            }
            System.out.println("onlyDiffMod: " + param.onlyDiffMod);

            if (null != getParam("diff_search_options") && !"".equals(getParam("diff_search_options"))) {
                
                String[] modArr = getParam("diff_search_options").trim().split(" ");
                
                HashMap<Character, ArrayList<Float>> diffModMap = new HashMap<>();
                for (char AA: "ARNDBCEQZGHILKMFPSTWYV".toCharArray()) {
                    diffModMap.put(AA, new ArrayList<Float>());
                }

                for (int i = 0; i < modArr.length; i += 2) {
                    float massShift = (float) Double.parseDouble(modArr[i]);
                    if (massShift != 0) {
                        param.setDiffSearch(true);
                        for (int j = 0; j < modArr[i + 1].length(); j++) {
                            diffModMap.get(modArr[i + 1].charAt(j)).add(massShift);
                        }
                    }
                }
                param.diffModMap = diffModMap;
                System.out.println("diffmods to apply:" + diffModMap);

                Set<Float> modMasses = new HashSet<>();
                for (Character key: diffModMap.keySet()) {
                    modMasses.addAll(diffModMap.get(key));
                }
                param.modMasses = modMasses;
                System.out.println("list of diffmod masses:" + modMasses);
                //System.exit(1);
            }
        } catch (IOException e) {
            System.out.println("Error reading param file " + e);
            throw new IOException(e.toString());
        } finally {
            if (null != br);
            br.close();
        }
    }

    public void pepProbeInit() {
    }

    public void gutentagInit() {
    }

    public SearchParams getSearchParams() {
        return param;
    }

    public int trimValueAsInt(String str, int defaultVal) {
        if (null == str) {
            return defaultVal;
        }
        int ret = defaultVal;
        try {
            ret = Integer.valueOf(trimValue(str));
        } catch (NumberFormatException e) {
            logger.log(Level.SEVERE, "Blazmass parameter invalid, expecting int, got: " + str);
        }
        return ret;
    }

    public int trimValueAsInt(String str) {
        return trimValueAsInt(str, 0);
    }

    public float trimValueAsFloat(String str) {
        float ret = 0;
        try {
            ret = Float.valueOf(trimValue(str));
        } catch (NumberFormatException e) {
            logger.log(Level.SEVERE, "Blazmass parameter invalid, expecting float, got: " + str);
        }
        return ret;
    }

    public double trimValueAsDouble(String str) {
        double ret = 0;
        try {
            ret = Double.valueOf(trimValue(str));
        } catch (NumberFormatException e) {
            logger.log(Level.SEVERE, "Blazmass parameter invalid, expecting double, got: " + str);
        }
        return ret;
    }

    public String trimValue(String str) {
        if (null == str) {
            return null;
        }

        int index = str.indexOf(';');
        if (index > 0) {
            str = str.substring(0, index);
        }

        return str.trim();
    }

    public Hashtable<String, String> getHashtable() {
        return ht;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Hashtable<String, String> getHt() {
        return ht;
    }

    public void setHt(Hashtable<String, String> ht) {
        this.ht = ht;
    }

    public SearchParams getParam() {
        return param;
    }

    public void setParam(SearchParams param) {
        this.param = param;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Get a param value from the internal map, log warning if not present
     *
     * @param paramName name of the param to get
     * @return the param value or empty string if not present
     */
    private String getParam(String paramName) {
        //System.out.println("== " + paramName  + "\t" + ht.get(paramName) + " " + ht.contains(paramName));

        String paramValue = ht.get(paramName);
        if (paramValue == null) {
            System.out.println("warning: missing parameter: " + paramName);
        }

        return paramValue;
    }
}
