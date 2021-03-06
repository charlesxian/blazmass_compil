package blazmass;

/**
 *
 * @author rpark
 */
import blazmass.dbindex.*;
import blazmass.dbindex.DBIndexer.IndexerMode;
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Pattern;
import blazmass.io.*;
import blazmass.mod.DiffModification;
import blazmass.model.*;
import java.text.DecimalFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import mongoconnect.MongoSeqIter;
import mongoconnect.MongoConnect;
import util.MathUtil;

/**
 *
 * The blazmass search implementation
 *
 */
public class Blazmass {

    private static final String program = "Blazmass";
    private static final String version = "0.9993";

    //extensions
    public static final String SQT_EXT = "sqt";
    public static final String LOG_EXT = "log";

    //precompiled regex for fasta line
    private final Pattern FASTA_REGEX = Pattern.compile("^[\\S]*$");
    // defines variables//
    // private static final int[] iIonVal = null;
    private static final float FLOAT_ZERO = 0.000001f;
    private final int XCORR_WINDOW = 75;
    private final int MAX_PEPTIDE_SIZE = 512;
    private final double NEIGHBOR_LEFT = 50.0;
    private final double NEIGHBOR_RIGHT = 4.0;
    /*
     * clears +NEIGHBOR_RIGHT amu of precursor ions
     */
    private final double NEIGHBOR = 15.0; /*
     * clears +/- NEIGHBOR amu of precursor ion
     */

    private double cStartTime;
    int iSizepiDiffSearchSites;
    int[] piDiffSearchSites = new int[MAX_PEPTIDE_SIZE];
    private long scan1;
    private long scan2;
    private double dTotalIntensity;
    private int iDoXCorrCount;
    private double dLowestXCorr;
    private float dXCorr_Mean;
    private float dXCorr_Square_Sum;
    private int iLowestPeak;
    private int iHighestPeak;
    long[] liTotAACount = new long[1];
    int[] iIonVal25 = new int[9];
    int[] iIonVal50 = new int[9];
    private static final Logger logger = Logger.getLogger(Blazmass.class.getName());
    private String hostname;

    private final IonIntensitiesCache ionIntensitiesCache = new IonIntensitiesCache();
    public HighResMassProcessor hprocessor;

    public Blazmass() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            // byte[] ipAddr = addr.getAddress();
            hostname = addr.getHostName();

        } catch (UnknownHostException e) {
            hostname = "unknown";
            logger.log(Level.SEVERE, "Could not get host name to write out to result, ", e);
        }
    }

    private static void usage_exit() {
        System.out.println("Usage: java blazmass ms2_params_directory_path num_threads");
        System.out.println("Usage: java blazmass ms2_params_directory_path ms2_file_name param_file_name num_threads");
        System.out.println("Usage: java blazmass -i params_directory_path");
        System.out.println("Usage: java blazmass -i params_directory_path blazmass.params");
        System.out.println("Usage: java blazmass");
        System.out.println("Usage: java blazmass -h");
        System.exit(1);
    }

    private void runFolder(String path) {
        System.out.println("zzzzzzzzzzzzz  runFolder  zzzzzzzzzzzz");
        String param = blazmass.io.SearchParamReader.DEFAULT_PARAM_FILE_NAME;

        //setup fasta database index
        //String path = args[0];
        File paramFile = new File(path + File.separator + param);// szParamsFile

        //File ms2File = new File(ms2File);
        if (!paramFile.exists()) {
            System.out.println("No param file, blazmass.params, at this folder");
            return;
        }

        SearchParamReader pr;
        try {
            pr = new SearchParamReader(path, param);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error getting params from " + param + ", cannot search", ex);
            return;
        }
        SearchParams sParam = pr.getSearchParams();
        if (sParam.isHighResolution()) {
            System.out.println("zzzzzzzzzzzzzHIGH RES PARAMSzzzzzzzzzzzz");
            this.hprocessor = new HighResMassProcessor();
        }
        final IndexerMode indexerMode = sParam.isUseIndex() ? IndexerMode.SEARCH_INDEXED : IndexerMode.SEARCH_UNINDEXED;
        final DBIndexer indexer = new DBIndexer(sParam, indexerMode);
        if (!sParam.isUsingMongoDB()) {
            try {
                indexer.init();
            } catch (DBIndexerException ex) {
                logger.log(Level.SEVERE, "Error initializing the indexer in the search mode, cannot search", ex);
                return;
            }
        }

        File f = new File(path);
        if (!f.isDirectory()) {
            usage_exit();
        }
        File[] flist = f.listFiles();

        for (File each : flist) {
            if (!each.getName().endsWith(".ms2")) {
                continue;
            }

            //logger.info(each.getName() + " " + each.getName().endsWith(".ms2"));
            run(path, each, sParam, indexer);
        }

        indexer.close();
    }

    public static void main(String[] args) throws Exception {//      

        /*
         System.out.println("Usage: java blazmass ms2_params_directory_path num_threads");
         System.out.println("Usage: java blazmass ms2_params_directory_path ms2_file_name param_file_name num_threads");
         System.out.println("Usage: java blazmass -i params_directory_path");
         System.out.println("Usage: java blazmass -i params_directory_path blazmass.params");
         System.out.println("Usage: java blazmass -m params_directory_path");
         System.out.println("Usage: java blazmass");
         System.out.println("Usage: java blazmass -h");*/
        System.out.println("Blazmass version " + version);
        final Blazmass bmass = new Blazmass();

        if (args.length == 2 && args[0].contains("-i")) {
            DBIndexer.runDBIndexer(args[1]);
            return;
        }

        if (args.length == 2 && args[0].contains("-m")) {
            DBIndexer.mergeLargeDBIndex(args[1]);
            return;
        }

        if (args.length == 3 && args[0].contains("-i")) {
            DBIndexer.runDBIndexer(args[1], args[2]);
            return;
        }

        if (args.length == 1 && args[0].contains("-h")) {
            usage_exit();
            return;
        }

        if (args.length == 2) {
            //dirName, threads
            String threads = args[1];
            if (threads.trim().equals("1")) {
//                bmass.runFolder(args[0]);  //temp commented out by Sandip
                WorkerManager.run(args[0], threads);
            } else {
                WorkerManager.run(args[0], threads);
            }
        } else if (args.length <= 0) {
            bmass.runFolder("."); //dirName, 1 thread
        } else if (args.length == 4) {// args.length > 1
            // path, filename, param file, threads
            String threads = args[3];
            if (threads.trim().equals("1")) {
//                bmass.runFile(args[0], args[1], args[2]); //temp commented out by Sandip
                WorkerManager.run(args[0], args[1], args[2], threads);
            } else {
                WorkerManager.run(args[0], args[1], args[2], threads);
            }

        } else {
            usage_exit();
        }

    }

    public void run(String path, File ms2File, SearchParams sParam, DBIndexer indexer) {
        int scanCount = 0;
        String base = ms2File.getName().substring(0, ms2File.getName().length() - 4);
        String sqtOut = base + sParam.getSqtSuffix() + "." + SQT_EXT;
        String sqtPath = path + File.separator + sqtOut;

        final String logOut = path + File.separator + base + "." + LOG_EXT;
        FileWriter logWriter = null;
        try {
            logWriter = new FileWriter(logOut);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Could not initialize log writer for file: " + logOut, ex);
        }

        BufferedWriter resultWriter = null;
        MongoConnect mongoConnection = new mongoconnect.MongoConnect(sParam);
        try {
            final MS2ScanReader ms2Reader = new MS2ScanReader(ms2File.getAbsolutePath());
            final int totalScans = ms2Reader.getNumScansIdx();
            System.out.println(totalScans);
            String totalScansStr;
            if (totalScans == -1) {
                totalScansStr = "Scan count:";
            } else {
                totalScansStr = Integer.toString(totalScans);
            }

            resultWriter = new BufferedWriter(new FileWriter(new File(sqtPath)));
            System.out.println("Writing output SQT to: " + sqtPath);
            resultWriter.write(header(sParam).toString());
            resultWriter.flush();

            while (ms2Reader.hasNext()) {
                scanCount++;
                final MS2Scan scan = ms2Reader.next();

                if (sParam.isHighResolution()) {
                    runScanHigh(scan, sParam, indexer, resultWriter, mongoConnection);
                } else {
                    runScan(scan, sParam, indexer, resultWriter, mongoConnection);
                }

                if (logWriter != null) {
                    logWriter.append(totalScansStr).append("\t").append(Integer.toString(scanCount)).append("\tScan num:\t").append(Integer.toString(scan.getIsScan1())).append("\n");
                    if (scanCount % 10 == 0) {
                        logWriter.flush();
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "I/O error, could not run search for ms2 file " + ms2File.getAbsolutePath(), e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error, could not run search for ms2 file " + ms2File.getAbsolutePath(), e);
        } finally {
            if (resultWriter != null) {
                try {
                    resultWriter.close();
                } catch (IOException ex) {
                    Logger.getLogger(Blazmass.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if (logWriter != null) {
                try {
                    logWriter.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
            mongoConnection.disconnect();
        }
    }

    void runScanHigh(MS2Scan scan, SearchParams sParam, DBIndexer indexer, BufferedWriter resultWriter, MongoConnect mongoConnection) throws IOException {
        //high resolution

        try {
            scan1 = scan.getIsScan1();
            scan2 = scan.getIsScan2();

            final List<Integer> chargeStates = scan.getChargeStates();
            final List<Float> precMasses = scan.getPrecursorMasses();
            final int chargeStatesSize = chargeStates.size();

            //for each charge-state
            for (int chargeStateI = 0; chargeStateI < chargeStatesSize; ++chargeStateI) {
                // final int[] scoreHistogram = new int[Constants.SCORE_BIN_SIZE];
                float[] scoreArray;

                final int chargeState = chargeStates.get(chargeStateI);
                final float precursorMass = precMasses.get(chargeStateI);

                if (precursorMass < sParam.getMinPrecursorMass() || precursorMass > sParam.getMaxPrecursorMass() || chargeState > sParam.getMaxChargeState()) {
                    System.out.println(precursorMass + " " + sParam.getMinPrecursorMass() + " " + sParam.getMaxPrecursorMass());
                    System.out.println("charge state: " + chargeState);
                    System.out.println("max charge state: " + sParam.getMaxChargeState());
                    System.out.println("precursorMass or charge state is out of range");
                    continue;
                }

                System.out.println("scan==" + scan1 + "\t" + (System.currentTimeMillis() - this.cStartTime));
                this.cStartTime = System.currentTimeMillis();

                //pDbEntry = new PDbEntry();
                // iTotalNumProteins=0;                
                float maxIntensity = 0.0f;
                dTotalIntensity = 88.0;
                iDoXCorrCount = 0;
                dLowestXCorr = FLOAT_ZERO;
                dXCorr_Mean = 0;
                dXCorr_Square_Sum = 0;

                //int precursorMassBin = (int)((precursorMass + 50)*sParam.getFragmentIonToleranceBinScale());
                System.out.println("this hprocessor" + this.hprocessor);
                scoreArray = new float[this.hprocessor.getBinSize(sParam)];
                //double[] precursorMassArr = new double[2];
                float[] precursorMassArr = new float[chargeState];
                int[] precursorMassBinIndexArr = new int[chargeState];

                //remove precursor mass
                //if (sParam.getRemovePrecursorPeak() == 1) {
                for (int ii = 0; ii < precursorMassArr.length; ii++) {
                    precursorMassArr[ii] = (precursorMass + ii * sParam.getHparent()) / (ii + 1);
                    precursorMassBinIndexArr[ii] = this.hprocessor.getBinIndex(precursorMassArr[ii], sParam);
                }

                final List<Float> masses = scan.getMasses();
                final List<Float> intensities = scan.getIntensities();
                final int massesSize = masses.size();

                if (massesSize < sParam.getMinFragPeakNum()) {
                    continue;
                }
                int binIndex = 0;

                for (int mi = 0; mi < massesSize; ++mi) {

                    float mass = masses.get(mi);
                    float intensity = intensities.get(mi);

                    if (intensity <= FLOAT_ZERO) {
                        continue;
                    }

                    //if (mass > precursorMass + 50.0) continue;
                    binIndex = this.hprocessor.getBinIndex(mass, sParam);

                    //check precursor
                    boolean isSkip = false;
                    for (int pindex : precursorMassBinIndexArr) {
                        if (pindex == binIndex) {
                            isSkip = true;
                        }
                    }

                    if (isSkip) {
                        continue;
                    }

                    intensity = (float) Math.sqrt((int) intensity);
                    if (intensity <= scoreArray[binIndex]) {
                        continue;
                    }

                    scoreArray[binIndex] = intensity;

                    if (scoreArray[binIndex] > maxIntensity) {
                        maxIntensity = scoreArray[binIndex];
                    }

                }

                if (maxIntensity <= 0) {
                    System.out.println("no peaks");
                    return;
                }

                float maxInt = normalize(scoreArray, maxIntensity);
                //System.out.println("max int==" + maxInt);

                // for(int i=0;i<scoreArray.length;i++)
                // System.out.println("high before\t" + i + "\t" + this.hprocessor.printMappingRange(i) + "\t" + scoreArray[i]);
                scoreArray = generateCorrHigh(scoreArray, maxInt, binIndex, sParam);
                //scoreArray = generateCorr(scoreArray, maxInt, scoreArray.length);
                //for(int i=0;i<scoreArray.length;i++)
                //     for(int i=0;i<900;i++)
                ///     System.out.println("high after\t" + i + "\t" + this.hprocessor.printMappingRange(i) + "\t" + scoreArray[i]);

                //    System.out.println("terminate==========================");
                //    System.exit(0);
                float[] backgroundArr = new float[scoreArray.length];

                float[] signalArray = scoreArray;
                //float[] signalArray = generateBlazScore(backgroundArr, scoreArray, maxInt, binIndex, sParam);

                //   for(int ii=0;ii<backgroundArr.length;ii++)
                //        if(signalArray[ii]<=0) continue;
                //        else System.out.println(this.hprocessor.printMappingRange(ii)  + " " + ii + " " + signalArray[ii] + " " + backgroundArr[ii]);
                //for(int i=100;i<scoreArray.length;i++)
                //System.out.print(" " + scoreArray[i]);
                // for(int i=0;i<scoreArray.length;i++) 
                //    System.out.println(i + "\t" + scoreArray[i]);
                PeptideResult[] pArr = new PeptideResult[sParam.getNumPeptideOutputLnes()];
                for (int i = 0; i < pArr.length; i++) {
                    pArr[i] = new PeptideResult();
                }

                int numMatched = runSearchHigh(indexer, sParam, signalArray, backgroundArr, chargeState, precursorMass, pArr, masses);

                // System.exit(0);
                // if(numMatched>0)
                //     correlation(numMatched, scoreHistogram, pArr);
                //System.out.println("=========" + outputResult(indexer, hostname, dTotalIntensity, sParam, numMatched, chargeState, precursorMass, pArr).toString());
                resultWriter.write(outputResult(indexer, hostname, dTotalIntensity, sParam, numMatched, chargeState, precursorMass, pArr, mongoConnection).toString());
                
                resultWriter.flush();

            } //end for each charge state

            //TODO more specific error handling here and other places
            //throw specific checked exceptions, such as BlazmassSearchException
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error reading ms2 data and searching, scan: " + scan.toString(), e);
            //throw (new RuntimeException(e));
        }
    }

    /**
     * Per scan search entry point
     *
     * @param scan MS2SScan to search
     * @param sParam blazmass params to use
     * @param indexer indexer with search database sequence index
     * @param resultWriter buffered output stream
     * @throws IOException exception thrown when search failed TODO should be
     * replaced by custom exception!
     */
    void runScan(MS2Scan scan, SearchParams sParam, DBIndexer indexer, BufferedWriter resultWriter, MongoConnect mongoConnection) throws Exception {

            scan1 = scan.getIsScan1();
            scan2 = scan.getIsScan2();

            final List<Integer> chargeStates = scan.getChargeStates();
            final List<Float> precMasses = scan.getPrecursorMasses();
            final int chargeStatesSize = chargeStates.size();

            //for each charge-state
            //for (int chargeStateI = 0; chargeStateI < chargeStatesSize; ++chargeStateI) {
            for (int cs = 0; cs < chargeStatesSize; cs++) {
                final int[] scoreHistogram = new int[Constants.SCORE_BIN_SIZE];
                float[] scoreArray;

                final int chargeState = chargeStates.get(cs);
                final float precursorMass = precMasses.get(cs);

                if (precursorMass < sParam.getMinPrecursorMass() || precursorMass > sParam.getMaxPrecursorMass() || chargeState > sParam.getMaxChargeState()) {
                    //System.out.println(precursorMass + " " + sParam.getMinPrecursorMass() + " " + sParam.getMaxPrecursorMass());
                    //System.out.println("precursorMass or charge state is out of range");
                    //System.out.println("charge state: " + chargeState);
                    //System.out.println("max charge state: " + sParam.getMaxChargeState());
                    continue;
                }

                this.cStartTime = System.currentTimeMillis();

                //pDbEntry = new PDbEntry();
                // iTotalNumProteins=0;                
                float dHighestIntensity = 0.0f;
                dTotalIntensity = 88.0;
                iDoXCorrCount = 0;
                dLowestXCorr = FLOAT_ZERO;
                dXCorr_Mean = 0;
                dXCorr_Square_Sum = 0;

                int ion_excluded;

                //String line;
                //int precursorMassBin = Math.round (precursorMass + 50);
                int precursorMassBin = MathUtil.round(precursorMass) + 50;

                //int precursorMassBin = (int) Math.floor(precursorMass + 50); //robin change it to ath.round later.
                //System.out.println("precursorMass " + precursorMass + " " + precursorMassBin);
                scoreArray = new float[precursorMassBin];
                //System.out.println("==" + sParam.getRemovePrecursorPeak());
                //System.out.println("==" + ((precursorMass + (chargeState - 1) * sParam.getHparent()) / (double)(chargeState)));

                double[] dPrecursorMasses = new double[chargeState];
                double dPrecursorMass = 0;
                if (sParam.getRemovePrecursorPeak() == 1) {
                    dPrecursorMass
                            = (precursorMass + (chargeState - 1) * sParam.getHparent()) / (double) (chargeState);
                } else if (sParam.getRemovePrecursorPeak() == 2) {
                    for (int ii = 0; ii < chargeState; ii++) {
                        dPrecursorMasses[ii]
                                = (precursorMass + ii * sParam.getHparent()) / (double) (ii + 1);
                    }
                }

                final List<Float> masses = scan.getMasses();
                final List<Float> intensities = scan.getIntensities();
                final int massesSize = masses.size();

                if (massesSize < sParam.getMinFragPeakNum()) {
                    continue;
                }
                int highestIon = 0;
                for (int mi = 0; mi < massesSize; ++mi) {

                    float mass = masses.get(mi);
                    float intensity = intensities.get(mi);

                    // System.out.println("====" + mass + " " + intensity + " " + FLOAT_ZERO);
                    if (intensity <= FLOAT_ZERO) {
                        continue;
                    }
                    if (mass > precursorMass + 50.0) {
                        continue;
                    }

                    highestIon = AssignMass.getBinnedValue(mass, 0);

                    //System.out.println("=================\t" + mass + "\t" + " " + highestIon); 
                    intensity = (float) Math.sqrt(intensity);

                    if (highestIon < precursorMassBin
                            && intensity > scoreArray[highestIon]) {
                        if (sParam.getRemovePrecursorPeak() == 2) {
                            ion_excluded = 0;
                            for (int ii = 0; ii < chargeState; ii++) {
                                if (mass > dPrecursorMasses[ii]
                                        - (NEIGHBOR_LEFT / ((double) (ii + 1)))
                                        && mass < dPrecursorMasses[ii]
                                        + NEIGHBOR_RIGHT) {
                                    ion_excluded = 1;
                                }
                            }
                            if (ion_excluded == 0) {
                                scoreArray[highestIon] = intensity;

                                if (scoreArray[highestIon] > dHighestIntensity) {
                                    dHighestIntensity = scoreArray[highestIon];
                                }
                            }
                        } else if ((sParam.getRemovePrecursorPeak() != 1)
                                || Math.abs(mass - dPrecursorMass) > NEIGHBOR) {

                            scoreArray[highestIon] = intensity;
                            // logger.info(fmass + " " + dPrecursorMass + " " + NEIGHBOR + " " + fIntensity);

                            if (scoreArray[highestIon] > dHighestIntensity) {
                                dHighestIntensity = scoreArray[highestIon];
                            }
                        }

                        //     System.out.println(dHighestIntensity);
                    }
                }

                float maxInt = normalize(scoreArray, dHighestIntensity);

                scoreArray = generateCorr(scoreArray, maxInt, highestIon);

                // Make sure not an empty spectrum
                if (dTotalIntensity > FLOAT_ZERO) {
                    PeptideResult[] pArr = new PeptideResult[sParam.getNumPeptideOutputLnes()];
                    for (int i = 0; i < pArr.length; i++) {
                        pArr[i] = new PeptideResult();
                    }

                    // ***************  Run search for each charge state ****************
                    System.out.println("Scan: " + scan1 + "\tCharge: " + chargeState);
                    
                    int numMatched = runSearch(indexer, sParam, scoreArray, chargeState, precursorMass, scoreHistogram, pArr, mongoConnection);
                    if (numMatched > 0) {
                        correlation(numMatched, scoreHistogram, pArr);
                    }
                    String temp = outputResult(indexer, hostname, dTotalIntensity, sParam, numMatched, chargeState, precursorMass, pArr, mongoConnection).toString();
                    
                    resultWriter.write(temp);
                    
                    resultWriter.flush(); //do not flush after every scan, it may block and slow down threads, take advantage of file buffer
                }
            } //end for each charge state
    }

    // Open database and perform search
    private StringBuffer outputResult(DBIndexer indexer, String szHostName,
            double dTotalIntensity,
            SearchParams sParam,
            int liNumMatchedPeptides, int chargeState,
            double precursorMass,
            PeptideResult[] pArr,
            MongoConnect mongoConnection) {

        //clock_t cEndTime = clock();
        float searchTime = 0;
        double cEndTime = System.currentTimeMillis();

        searchTime = (float) (cEndTime - cStartTime);

        StringBuffer sb = new StringBuffer();
        sb.append("S").append("\t").append(scan1).append("\t").append(scan2).append("\t")
                .append(chargeState).append("\t").append(MathUtil.round(searchTime)).append("\t")
                .append(szHostName).append("\t").append(precursorMass).append("\t")
                .append(dTotalIntensity).append("\t").append(dLowestXCorr).append("\t").append(liNumMatchedPeptides).append("\n");

        float maxXCorr = pArr[0].getxCorr();

        int count = 1;
        for (PeptideResult each : pArr) {

            if (each.getxCorr() < 0) {
                continue;
            }

            IndexedSequence iseq = each.getIndexedSeq();
            float deltCN = 1 - each.getxCorr() / maxXCorr;

            sb.append("M").append("\t").append(count).append("\t").append(count).append("\t").append(each.getPeptideMass())
                    .append("\t").append(deltCN).append("\t").append(each.getxCorr()).append("\t").append(each.getzScore())
                    .append("\t").append(each.getMatchedIon()).append("\t").append(each.getTotalIon())
                    .append("\t").append(iseq.getSimpleSequence()).append("\tU\n");
        
            try {
                if (sParam.isUsingMongoDB()) {
                    List<String> parentLines;
                    //custom handling of 'L' lines for MongoDB here...
                    if (sParam.isUsingSeqDB()) {
                        if (!iseq.isReverse){
                            parentLines = mongoConnection.getParents(iseq.getSequence(), sParam, false);
                        }
                        else {
                            // GSS: This doesn't make sense?
                            String revSequence = new StringBuilder(iseq.getSequence()).reverse().toString();
                            parentLines = mongoConnection.getParents(revSequence, sParam, true);
                        }
                        if (parentLines == null) {
                            System.err.println("Error - found no parent proteins for peptide " + iseq.getSequence());
                            sb.append("L\t").append("\n");
                        } else {
                            for (String parentLine : parentLines) {
                                sb.append("L\t").append(parentLine).append("\n");
                            }
                        }
                    } else {
                        // if not using SeqDB, no protein / locus information to add in L lines
                        // (print empty L line)
                        sb.append("L\t").append("\n");
                    }
                } else {
                    // if NOT using MongoDB...
                    List<IndexedProtein> iproteins = indexer.getProteins(iseq);

                    if (iproteins.size() > 0) {
                        for (IndexedProtein iprotein : iproteins) {
                            //                resultWriter.write("L\t" + iprotein.getAccession());
                            sb.append("L\t").append(iprotein.getAccession()).append("\t0\t").append(iseq.getWholeSequence()).append("\n");
                        }
                    } else {
                        List proteinId = iseq.getProteinDescArray();
                        for (int i = 0; i < proteinId.size(); i++) {
                            sb.append("L\t").append(proteinId.get(i)).append("\t0\t").append(iseq.getWholeSequence()).append("\n");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            count++;
        }

        //System.out.println("-======>>" + sb.toString()); 
        return sb;

        //resultWriter.write(sb.toString());
    }
    
   /**
     * Add a new peptide result to the list of peptide results
     *
     * @param pr new PeptideResult
     * @param pArr list of PeptideResults
     */
    private void addResult(PeptideResult pr, PeptideResult[] pArr) {
        addResult(pr, pArr, false);
    }

    private void addResult(PeptideResult pr, PeptideResult[] pArr, boolean verbose) {
        if (pr == null) {
            return;
        }

        PeptideResult prtmp = pArr[pArr.length - 1];
        if (prtmp != null) {
            if (prtmp.getxCorr() < pr.getxCorr()) {
                pArr[pArr.length - 1] = pr;
                Arrays.sort(pArr);
            }
        } else {
            pArr[pArr.length - 1] = pr;
            Arrays.sort(pArr);
        }

        if (verbose) {
            System.out.println("---");
            for (PeptideResult p : pArr) {
                System.out.println(p.getxCorr());
            }
            System.out.println("---");
        }
    }

    /**
     * Search a single charge state for one scan (all isotopes)
     *
     * @param indexer who knows
     * @param sParam blazmass params to use
     * @param scoreArray who knows
     * @param chargeState charge state set by run runScan
     * @param precursorMass
     * @param pArr (probably) empty array of PeptideResults, initialized by
     * runScan
     * @throws IOException exception thrown when search failed TODO should be
     * replaced by custom exception!
     * @return numMatched number of matched peptides times two, for some reason
     */
    private int runSearch(DBIndexer indexer, SearchParams sParam, float[] scoreArray, int chargeState, float precursorMass, 
            int[] scoreHistogram, PeptideResult[] pArr, MongoConnect mongoConnection) throws Exception {
        int numMatched = 0;
        PeptideResult pr;
        float massTolerance = sParam.getRelativePeptideMassTolerance();
        float ppmTolerance = this.getPpm(precursorMass, massTolerance);
        List<MassRange> rList = new ArrayList<>();
        // If getIsotopes() is 1, it should search isotopic peaks
        // If 0, it will only search the monoisotopic peak
        int isotopeNum = sParam.getIsotopes() * chargeState + 1;
        if (sParam.isPrecursorHighResolution()) {
            for (int i = 0; i < isotopeNum; i++) {
                rList.add(new MassRange(precursorMass - i * AssignMass.DIFFMASSC12C13, ppmTolerance));
            }
        } else {
            rList.add(new MassRange(precursorMass, ppmTolerance));
        }
        
        /* N15 mode
        */
        if (sParam.isSearchN15Isotopes()){
            float diffMass = (AssignMass.DIFFMASSC12C13 + AssignMass.DIFFMASSN14N15) / 2;
            for (int i = 1; i < isotopeNum; i++) {
                rList.add(new MassRange(precursorMass + i * diffMass, ppmTolerance));
            }
        }
        
        System.out.println("Working on no mod: " + precursorMass);
        if (sParam.isUsingMongoDB()) {
            MongoSeqIter msi = mongoConnection.getSequencesIter(rList, sParam);
            while (msi.hasNext()) {
                IndexedSequence indSeq = msi.next();
                pr = calcScore(indSeq, scoreArray, chargeState, scoreHistogram, sParam);
                //System.out.println("!"+indSeq.getSequence() + "\t" + indSeq.getMass() + "\t" + pr.getxCorr());
                numMatched += 2;
                addResult(pr, pArr);
                if (sParam.doReversePeptides){
                    System.out.println("Not implemented in diffmods");
                    System.exit(1);
                    String revSequence = new StringBuilder(indSeq.getSequence()).reverse().toString();
                    IndexedSequence revSeq = new IndexedSequence(indSeq.getMass(), revSequence, revSequence.length(), "---", "---");
                    revSeq.isReverse = true;
                    pr = calcScore(revSeq, scoreArray, chargeState, scoreHistogram, sParam);
                    //System.out.println("!"+revSeq.getSequence() + "\t" + indSeq.getMass() + "\t" + pr.getxCorr());
                    numMatched += 2;
                    addResult(pr, pArr);
                }
            }
            System.out.println("Number of peptides scored: " + msi.count);
        } else { //not using mongodb
            System.out.println("Too bad...");
            System.exit(1);
        }
        /////////////////////////
        // 4. modification search!!!
        /////////////////////////
        if (sParam.isDiffSearch()) {
            // Allow search for more than one different ptm on the same residue (at different times)
            // ONLY ONE PTM PER PEPTIDE!!
            HashMap<Character, ArrayList<Float>> diffModMap = sParam.diffModMap;
            Set<Float> modMasses = sParam.modMasses;
            for (float eachModMass : modMasses) {
                System.out.println("Working on mod mass: " + eachModMass);
                rList.clear();
                // All AA's that could have this PTM's mass (eachModMass)
                Set<Character> modMassResidues = new HashSet<>();
                for (Character key : diffModMap.keySet()) {
                    if (diffModMap.get(key).contains(eachModMass)) {
                        modMassResidues.add(key);
                    }
                }
                //System.out.println("AAs with this mod mass: " + modMassResidues);
                // Set params diffModMass using these masses
                // diffModMass index is the (int) value of the amino acid, value is the mass
                // replaces DiffModification.diffMod
                double[] diffModMass = new double[256];
                for (Character modResidue : modMassResidues) {
                    diffModMass[modResidue] = eachModMass;
                }
                //System.out.println("diffmodmass" + Arrays.toString(diffMod));
                float modPrecursorMass = precursorMass - eachModMass;
                ppmTolerance = this.getPpm(modPrecursorMass, massTolerance);

                if (sParam.isPrecursorHighResolution()) {
                    for (int i = 0; i < isotopeNum; i++) {
                        rList.add(new MassRange(modPrecursorMass - i * AssignMass.DIFFMASSC12C13, ppmTolerance));
                    }
                } else {
                    rList.add(new MassRange(modPrecursorMass, ppmTolerance));
                }

                //System.out.println(precursorMass);
                //System.out.println(rList);
                MongoSeqIter msi = mongoConnection.getSequencesIter(rList, sParam);
                while (msi.hasNext()) {
                    IndexedSequence iSeq = msi.next();
                    String seq = iSeq.getSequence();
                    int[] modIndexHash = new int[iSeq.getSequenceLen()];

                    //for each residue in modMassResidues (the list of AAs with this mod)
                    //    for each instance of residue in peptide sequence
                    //        generate modIndex and score
                    for (Character eachResidue : modMassResidues) {
                        for (int j = 0; j < seq.length(); j++) {
                            char ch = seq.charAt(j);
                            if (eachResidue == ch) {
                                modIndexHash[j] = 1;
                                pr = calcModScore(iSeq, scoreArray, chargeState, modIndexHash, diffModMass, scoreHistogram, sParam);
                                modIndexHash[j] = 0;
                                pr.setIsModified(true);
                                pr.addPeptideMass(eachModMass);
                                //System.out.println(pr.getIndexedSeq());

                                numMatched += 2;
                                PeptideResult prtmp = pArr[pArr.length - 1];
                                if (prtmp.getxCorr() < pr.getxCorr()) {
                                    pArr[pArr.length - 1] = pr;
                                    Arrays.sort(pArr);
                                }
                                //System.out.println(pr.getxCorr());
                                //System.out.println(pr.getIndexedSeq().getModSequence());

                            }

                        }
                    }
                }
                System.out.println("Number of peptides scored: " + msi.count);
                System.out.println("Number of scores calced: " + numMatched);
            }
        }
        // N-term and C-term searches
        /*
        cursor = massdb.find({ "$or" : [ { "_id" : { "$gte" : 1000000 , "$lte" : 1016662}}]})
        seq = list(chain(*[x['s'] for x in cursor]))
        seq = [s for s in seq if s.startswith('M')]
        for seq_doc in seqdb.find({'$and':[{'_id': {'$in': seq}}, {'$or': [{'p.l':'---'},{'p.r':'---'}]}]}):
            pass
        */
        if (pArr[0].getIndexedSeq() != null) {
            System.out.println(pArr[0].getIndexedSeq().toString() + "\tXcorr: " + pArr[0].getxCorr());
        }

        return numMatched;
    }

    private int runSearchHigh(DBIndexer indexer, SearchParams sParam, float[] signalArr, float[] backgroundArr, int chargeState, float precursorMass,
            PeptideResult[] pArr, List<Float> masses) throws Exception {

        System.out.println("run search high is currently broken******");
        System.exit(1);
        return 5;
    }
/*
        int isotopeNum = chargeState * 2 + 1; //robin move it to config file later
        List<IndexedSequence> pepList = null;
        int numMatched = 0;
        int intMass;
        String sequence;
        float massTolerance = sParam.getRelativePeptideMassTolerance();

        float ppmTolerance = this.getPpm(precursorMass, massTolerance);

        List<MassRange> rList = new ArrayList<>();

        Set<Integer> theoNonZeroInd = new HashSet<>();

        for (int i = 0; i < isotopeNum; i++) {
            rList.add(new MassRange(precursorMass - i * AssignMass.DIFFMASSC12C13, ppmTolerance));
        }

        PeptideResult pr;
        if (sParam.isUsingMongoDB()) {
            DBCursor cursor = mongoconnect.Mongoconnect.getSequencesIter(rList, sParam);
            while (cursor.hasNext()) {
                DBObject obj = cursor.next();
                intMass = (int) obj.get("_id"); // gets intMass from current document
                BasicDBList peptideSequences = (BasicDBList) obj.get("s");
                for (Object pepSeq : peptideSequences) {
                    sequence = (String) pepSeq;
                    IndexedSequence indSeq = new IndexedSequence((float) intMass / 1000, sequence, sequence.length(), "---", "---");
                    pr = calcScoreHigh(indSeq, signalArr, backgroundArr, chargeState, sParam, masses, theoNonZeroInd);
                    //System.out.println("!"+indSeq.getSequence() + "\t" + indSeq.getMass() + "\t" + pr.getxCorr());
                    if (pr == null) {
                        continue;
                    }
                    numMatched += 2;
                    PeptideResult prtmp = pArr[pArr.length - 1];
                    if (null != prtmp) {
                        if (prtmp.getxCorr() < pr.getxCorr()) {
                            pArr[pArr.length - 1] = pr;
                            Arrays.sort(pArr);
                        }
                    }
                }
            }
        } else { //not using mongodb
            pepList = indexer.getSequences(rList);
            if (pepList != null && pepList.size() > 0) {
                for (IndexedSequence iSeq : pepList) {

                    pr = calcScoreHigh(iSeq, signalArr, backgroundArr, chargeState, sParam, masses, theoNonZeroInd);
                    //System.out.println("!!"+iSeq.getSequence() + "\t" + iSeq.getMass() + "\t" + pr.getxCorr());
                    if (pr == null) {
                        continue;
                    }
                    numMatched += 2;
                    PeptideResult prtmp = pArr[pArr.length - 1];
                    if (null != prtmp) {
                        if (prtmp.getxCorr() < pr.getxCorr()) {
                            pArr[pArr.length - 1] = pr;
                            Arrays.sort(pArr);
                        }
                    }
                }
            }
        }

        /////////////////////////
        // 4. modification search
        /////////////////////////
        if (false) {
            System.out.println("mod search");
            //List<ModResidue> mList = sParam.getModList();
            //System.out.println("=====GSS1=====" + pepList);
            List<List<Double>> modGList = sParam.getModGroupList();

            //System.out.println("m list==========" + modGList);
            for (Iterator<List<Double>> ml = modGList.iterator(); ml.hasNext();) {
                List<Double> eachModGroup = ml.next();

                float modPrecursorMass = precursorMass;

                double modSumCandidate = 0;

                for (Iterator<Double> sml = eachModGroup.iterator(); sml.hasNext();) {
                    double d = sml.next();
                    modPrecursorMass -= d;
                    modSumCandidate += d;
                    // System.out.println("mass=========" + d + " " + modSum);
                }

                // System.out.println("mod sum can=========" + modSumCandidate);
                ppmTolerance = this.getPpm(modPrecursorMass, massTolerance);
                //System.out.println("2=========" + modPrecursorMass + " " + ppmTolerance);

                rList.clear();
                for (int i = 0; i < isotopeNum; i++) {
                    //pepList = indexer.getSequences(modPrecursorMass - i * AssignMass.DIFFMASSC12C13, ppmTolerance);

                    //System.out.println( "pep query " + (precursorMass - i * AssignMass.DIFFMASSC12C13) + " " + ppmTolerance);                    
                    rList.add(new MassRange(modPrecursorMass - i * AssignMass.DIFFMASSC12C13, ppmTolerance));
                }

                if (sParam.isUsingMongoDB()) {
                    pepList = mongoconnect.Mongoconnect.getSequences(rList, sParam);
                } else {
                    pepList = indexer.getSequences(rList);
                }
                //System.out.println("****pepList*********" + pepList);

                //for (int i = 0; i < isotopeNum; i++) {
                // System.out.println("4444=========" + eachModGroup + " " + precursorMass + " " + modPrecursorMass + " " + rList.size());
                //pepList = indexer.getSequences(modPrecursorMass - i * AssignMass.DIFFMASSC12C13, ppmTolerance);                    
                // System.out.println(pepList);
                //  System.out.print("=");        
                if (null != pepList && pepList.size() > 0) {
                    for (Iterator<IndexedSequence> itr = pepList.iterator(); itr.hasNext();) {
                        IndexedSequence iSeq = itr.next();

                        //System.out.println("X");
//            System.out.println("proteein==========\t" + indexer.getProteins(iSeq));
                        String seq = iSeq.getSequence();
                        //float calcMass = 0;
                        List<Integer> modIndexList = new ArrayList();

                        // if(!"EPQVDVSDDSDNEAVEQELTEEQR".equals(seq)) continue;
                        //System.out.println("mod sum======" + modSumCandidate + " " + modIndexList);                        
                        for (int j = 0; j < seq.length(); j++) {
                            char ch = seq.charAt(j);
                            //calcMass += AssignMass.getMass(ch);

                            if (DiffModification.isDiffMod(ch)) {
                                modIndexList.add(j);
                            }
                        }

                        if (modIndexList.size() <= 0) {
                            continue;
                        }

                        // Create the initial vector
                        ICombinatoricsVector<Integer> initialVector = Factory.createVector(modIndexList);
   //ICombinatoricsVector<Integer> initialVector = Factory.createVector(new Integer[] { 1, 1, 2, 2 });
                        //new String[] { "red", "black", "white", "green", "blue" } );

                        // Create a simple combination generator to generate 3-combinations of the initial vector
                        Generator<Integer> gen = Factory.createSimpleCombinationGenerator(initialVector, eachModGroup.size());

                        for (ICombinatoricsVector<Integer> combination : gen) {
                             //System.out.println("c b ===" + combination);

                            //combination.getSize();
                            double modSum = 0;

                            int[] modIndexHash = new int[seq.length()];
                            int mcount = 0;
                            for (Iterator<Integer> eachItr = combination.iterator(); eachItr.hasNext();) {
                                int modIndex = eachItr.next();
                                modSum += DiffModification.getDiffModMass(seq.charAt(modIndex));
                                modIndexHash[modIndex] = 1;
                                mcount++;
                            }

                            //wrote candidate
                            if (modSumCandidate != modSum) {
                                continue;
                            }
                            //System.out.println("m sum===" + seq + " " + combination  + " " + modSum + " " + (modSumCandidate==modSum));

                            //PeptideResult pr = calcModScore(iSeq, precursorMass, scoreArray, chargeState, scoreHistogram, eachModGroup, modIndexHash, sParam);
                            //System.out.println("m\t" +combination.getSize() + " " +iSeq.getSequence());
                            //System.out.println("xxxx" + iSeq.getModSequence());
                            //pr = calcModScoreHigh(iSeq, signalArr, backgroundArr, chargeState, modIndexHash, sParam, masses, theoNonZeroInd);
                            pr = null;
                            pr.setIsModified(true);
                            pr.addPeptideMass(modSum);

                            numMatched += 2;
                            PeptideResult prtmp = pArr[pArr.length - 1];

                            if (prtmp.getxCorr() < pr.getxCorr()) {
                                pArr[pArr.length - 1] = pr;
                                Arrays.sort(pArr);
                            }

                        }
                    }

                }
            }
        }

        System.out.println("=====GSS3=====" + pArr[0].getIndexedSeq());
        // System.out.println("time==>" + (System.currentTimeMillis() - midTime) );

        return numMatched;

    }
        */

    private void correlation(int numMatched, int[] pXCorrHistogram, PeptideResult[] pArr) {

        //float  dx, dy;
        float dXCorrTemp = 0.0f;

        dXCorr_Mean = dXCorr_Mean / numMatched;

        //System.out.println("=================" + numMatched);
        //System.exit(0);
        float dXCorr_Variance = (dXCorr_Square_Sum / numMatched) - (dXCorr_Mean * dXCorr_Mean);
        float dXCorrSigma = (float) Math.sqrt(dXCorr_Variance);

        int scoreCount = 0;

        /*
         float dsumx = 0;
         float dsumx2 = 0;
         float dsumy = 0;
         float dsumxy = 0;
         float dsumy2 = 0;*/
        for (int i = 0; i < Constants.SCORE_BIN_SIZE; i++) {

            dXCorrTemp = (i - 50) / 10.0f + 0.05f;

            if ((dXCorrTemp > dXCorr_Mean + 2.5 * dXCorrSigma) && (pXCorrHistogram[i] >= 10)) {

                scoreCount++;
                /*
                
                 dx = dXCorrTemp;
                 dy = (float) Math.log10(10.0f * pXCorrHistogram[i]);
                 dsumx += dx;
                 dsumy += dy;
                 dsumx2 += (dx * dx);
                 dsumy2 += (dy * dy);
                 dsumxy += (dx * dy);*/
            }
        }

        /*
         float dlogA = (dsumx2 * dsumy - dsumx * dsumxy) / (scoreCount * dsumx2 - dsumx * dsumx);
         float dB = (dsumx * dsumy - scoreCount * dsumxy) / (scoreCount * dsumx2 - dsumx * dsumx);
         float dR2 = ((scoreCount * dsumxy - dsumx * dsumy) * (scoreCount * dsumxy - dsumx * dsumy))
         / ((scoreCount * dsumx2 - dsumx * dsumx) * (scoreCount * dsumy2 - dsumy * dsumy));
         float dlogB = (float) Math.log(dB);*/
//        logger.info("============77========" + dXCorr_Mean + " " + dXCorr_Variance + " " + dXCorr_Sigma);        
//        logger.info("============77========" + iDoXCorrCount);
        for (PeptideResult each : pArr) {
            if (null == each) {
                continue;
            }
            //logger.info(each);
            each.setzScore((each.getxCorr() - dXCorr_Mean) / dXCorrSigma);
            each.setzScore((each.getxCorr() - dXCorr_Mean) / dXCorrSigma);
            each.setMatchedIon(scoreCount);

        }
    }

    private PeptideResult calcScore(IndexedSequence iSeq, float[] scoreArray, int chargeState, int[] scoreHistogram, SearchParams sParam) {

        //logger.info("=======>>" + iSeq.getSequence());
        String pepSeq = iSeq.getSequence();

        List<FragIonModel> l = new ArrayList<>();
        for (int each : sParam.getIonToUse()) {

            FragIonModel fmodel = null;
            float[] fragArr = null;
            switch (each) {
                /*a*/ case 0:
                    fragArr = AssignMass.getFragIonArr(pepSeq, each);
                    fmodel = new FragIonModel(each, "a", sParam.getIonSeries()[each], fragArr, true);
                    break;
                /*b*/ case 1:
                    fragArr = AssignMass.getFragIonArr(pepSeq, each);
                    fmodel = new FragIonModel(each, "b", sParam.getIonSeries()[each], fragArr, true);
                    //System.out.println(Arrays.toString(fragArr));
                    break;

                /*c*/ case 2:
                    break;

                /*x*/ case 6:
                    break;
                /*y*/ case 7:
                    fragArr = AssignMass.getFragIonArrRev(pepSeq, each);
                    fmodel = new FragIonModel(each, "y", sParam.getIonSeries()[each], fragArr, false);
                    //System.out.println(Arrays.toString(fragArr));
                    break;

                /*z*/ case 8:
                    break;

            }

            l.add(fmodel);
        }
        //return null;
        return calcEachIon(l, scoreArray, chargeState, iSeq, scoreHistogram, sParam);
    }

    private PeptideResult calcScoreHigh(IndexedSequence iSeq, float[] signalArr, float[] backgroundArr, int chargeState, SearchParams sParam, List<Float> masses, Set<Integer> theoNonZeroInd) {

        String pepSeq = iSeq.getSequence();

        // List<FragIonModel> l = new ArrayList<FragIonModel>();
        final int CS_FRAG = 3;
        //
        //int massIndex = (int)(mass*sParam.getFragmentIonToleranceBinScale());
        theoNonZeroInd.clear();
        int[] theorArr = new int[signalArr.length];

        //   System.out.println(scoreArray.length);
        for (int each : sParam.getIonToUse()) {

            //long start = System.currentTimeMillis();
            float[] fragArr = null;
            switch (each) {
                /*a*/ case 0:
                /*b*/ case 1:
                /*c*/ case 2:
                    fragArr = AssignMass.getFragIonArr(pepSeq, each);
                    break;

                /*x*/ case 6:
                /*y*/ case 7:
                /*z*/ case 8:
                    fragArr = AssignMass.getFragIonArrRev(pepSeq, each);
                    break;

            }

            //        System.out.println("1\t" + (System.currentTimeMillis()-start));
            HighResMassProcessor.assignTheoMass(theorArr, fragArr, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
//            HighResMassProcessor.assignTheoMassTest(theorArr, fragArr, sParam, 1, this.hprocessor, String.valueOf(each));                

            if (chargeState >= CS_FRAG) {
                HighResMassProcessor.assignTheoMass(theorArr, fragArr, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                //              HighResMassProcessor.assignTheoMassTest(theorArr, fragArr, sParam, 2, this.hprocessor, String.valueOf(each) + "CS2");                

            }

            switch (each) {
                /*a*/ case 0:
                    if (sParam.getNeutralLossAions() > 0) { //a ion

                        for (float f : fragArr) {

                            if (sParam.isNeturalLossIsotope()) {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                            } else {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, 10, theoNonZeroInd);
                            }
                            //                         HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.NH3, sParam, 1, this.hprocessor, String.valueOf(each)+"NH3" );                

                            if (chargeState >= CS_FRAG) {
                                if (sParam.isNeturalLossIsotope()) {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                } else {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, 10, theoNonZeroInd);
                                }
                                //                           HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.NH3, sParam, 2, this.hprocessor, String.valueOf(each)+"NH3 CS2" );                
                            }
                        }
                    }
                    break;
                /*b*/ case 1:

                    if (sParam.getNeutralLossBions() > 0) { //b ion

                        for (float f : fragArr) {

                            //     System.out.println("----------" + f + " " + (f-AssignMass.NH3));
                            if (sParam.isNeturalLossIsotope()) {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.H2O, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.CO, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                            } else {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, 10, theoNonZeroInd);
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.H2O, sParam, 1, this.hprocessor, 10, theoNonZeroInd);
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.CO, sParam, 1, this.hprocessor, 10, theoNonZeroInd);
                            }

                            //                     HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.NH3, sParam, 1, this.hprocessor, String.valueOf(each)+"NH3" );                
                            //                     HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.H2O, sParam, 1, this.hprocessor, String.valueOf(each)+"H2O" );                
                            //                     HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.CO, sParam, 1, this.hprocessor, String.valueOf(each)+"CO" );                
                            if (chargeState >= CS_FRAG) {

                                if (sParam.isNeturalLossIsotope()) {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.H2O, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.CO, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                } else {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, 10, theoNonZeroInd);
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.H2O, sParam, 2, this.hprocessor, 10, theoNonZeroInd);
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.CO, sParam, 2, this.hprocessor, 10, theoNonZeroInd);
                                }

                                //                      HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.NH3, sParam, 2, this.hprocessor, String.valueOf(each)+"NH3 CS2" );                
                                //                      HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.H2O, sParam, 2, this.hprocessor, String.valueOf(each)+"H2O CS2" );                
                                //                       HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.CO, sParam, 2, this.hprocessor, String.valueOf(each)+"CO CS2" );   
                            }

                        }
                    }

                    break;

                /*c*/ case 2:
                    break;

                /*x*/ case 6:
                    break;

                /*y*/ case 7:
                    if (sParam.getNeutralLossYions() > 0) { //y ion

                        for (float f : fragArr) {

                            if (sParam.isNeturalLossIsotope()) {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                            } else {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, 10, theoNonZeroInd);
                            }
                            //                  HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.NH3, sParam, 1, this.hprocessor, String.valueOf(each) + "NH3");                                             

                            if (chargeState >= CS_FRAG) {
                                if (sParam.isNeturalLossIsotope()) {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                } else {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, 10, theoNonZeroInd);
                                }
                                //                   HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.NH3, sParam, 2, this.hprocessor, String.valueOf(each) + "NH3 CS2");                                             
                            }
                        }
                    }

                    break;

                /*z*/ case 8:
                    break;

            }

            //    System.out.println("2\t" + (System.currentTimeMillis()-start));
        }

        //return null;
        return calcEachIonHigh(theorArr, signalArr, backgroundArr, chargeState, iSeq, sParam, masses, theoNonZeroInd);
    }

    private PeptideResult calcModScore(IndexedSequence iSeq, float[] scoreArray, int chargeState, int[] modIndexHash, double[] diffModMass, int[] scoreHistogram, SearchParams sParam) {

        String pepSeq = iSeq.getSequence();
        IndexedSequence newISeq = iSeq.getCopy();

        List<FragIonModel> l = new ArrayList<FragIonModel>();

        for (int each : sParam.getIonToUse()) {

            FragIonModel fmodel = null;
            float[] fragArr = null;

            switch (each) {
                /*a*/ case 0:
                    fragArr = AssignMass.getFragIonArrMod(pepSeq, each, modIndexHash, diffModMass);
                    fmodel = new FragIonModel(each, "a", sParam.getIonSeries()[each], fragArr, true);
                    break;
                /*b*/ case 1:
                    fragArr = AssignMass.getFragIonArrMod(pepSeq, each, modIndexHash, diffModMass);
                    fmodel = new FragIonModel(each, "b", sParam.getIonSeries()[each], fragArr, true);

                    break;

                /*c*/ case 2:
                    break;

                /*x*/ case 6:
                    break;
                /*y*/ case 7:
                    fragArr = AssignMass.getFragIonArrRevMod(pepSeq, each, modIndexHash, diffModMass);
                    fmodel = new FragIonModel(each, "y", sParam.getIonSeries()[each], fragArr, false);

                    break;

                /*z*/ case 8:
                    break;

            }

            l.add(fmodel);
        }

        int size = pepSeq.length();
        StringBuffer modSeq = new StringBuffer();
        for (int i = 0; i < size; i++) {
            char ch = pepSeq.charAt(i);
            modSeq.append(ch);
            if (modIndexHash[i] > 0) {
                double modMass = diffModMass[ch];
                modSeq.append("(").append(new DecimalFormat("0.0000").format(modMass)).append(")");
            }
        }
        newISeq.setIsModified(true);
        newISeq.setModSequence(modSeq.toString());

        return calcEachIon(l, scoreArray, chargeState, newISeq, scoreHistogram, sParam);
    }

    private PeptideResult calcModScoreHigh(IndexedSequence iSeq,
            //float precursorMass,
            float[] signalArr,
            float[] backgroundArr,
            int chargeState,
            // List<Double> eachModGroup,
            int[] modIndexHash,
            SearchParams sParam,
            List<Float> masses,
            double[] diffModMass,
            Set<Integer> theoNonZeroInd) {

        final int CS_FRAG = 3;

        String pepSeq = iSeq.getSequence();
        IndexedSequence newISeq = iSeq.getCopy();
        theoNonZeroInd.clear();
        int[] theorArr = new int[signalArr.length];

        //float[] revFragArr = null;
        List<FragIonModel> l = new ArrayList<FragIonModel>();

        for (int each : sParam.getIonToUse()) {

            //  FragIonModel fmodel = null;
            float[] fragArr = null;

            switch (each) {
                /*a*/ case 0:
                /*b*/ case 1:
                /*c*/ case 2:
                    fragArr = AssignMass.getFragIonArrMod(pepSeq, each, modIndexHash, diffModMass);
                    break;

                /*x*/ case 6:
                /*y*/ case 7:
                /*z*/ case 8:
                    fragArr = AssignMass.getFragIonArrRevMod(pepSeq, each, modIndexHash, diffModMass);
                    break;

            }

            HighResMassProcessor.assignTheoMass(theorArr, fragArr, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);

            if (chargeState >= CS_FRAG) {
                HighResMassProcessor.assignTheoMass(theorArr, fragArr, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
            }

            // l.add(fmodel);
            switch (each) {
                /*a*/ case 0:
                    if (sParam.getNeutralLossAions() > 0) { //a ion

                        for (float f : fragArr) {

                            if (sParam.isNeturalLossIsotope()) {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                            } else {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, 10, theoNonZeroInd);
                            }

                            if (chargeState >= CS_FRAG) {
                                if (sParam.isNeturalLossIsotope()) {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                } else {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, 10, theoNonZeroInd);
                                }
                                //   HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.NH3, sParam, 2, this.hprocessor, String.valueOf(each)+"NH3 CS2" );                
                            }
                        }
                    }
                    break;
                /*b*/ case 1:

                    if (sParam.getNeutralLossBions() > 0) { //b ion

                        for (float f : fragArr) {

                            //     System.out.println("----------" + f + " " + (f-AssignMass.NH3));
                            if (sParam.isNeturalLossIsotope()) {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.H2O, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.CO, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                            } else {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, 10, theoNonZeroInd);
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.H2O, sParam, 1, this.hprocessor, 10, theoNonZeroInd);
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.CO, sParam, 1, this.hprocessor, 10, theoNonZeroInd);
                            }

                            //                     HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.NH3, sParam, 1, this.hprocessor, String.valueOf(each)+"NH3" );                
                            //                     HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.H2O, sParam, 1, this.hprocessor, String.valueOf(each)+"H2O" );                
                            //                     HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.CO, sParam, 1, this.hprocessor, String.valueOf(each)+"CO" );                
                            if (chargeState >= CS_FRAG) {

                                if (sParam.isNeturalLossIsotope()) {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.H2O, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.CO, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                } else {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, 10, theoNonZeroInd);
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.H2O, sParam, 2, this.hprocessor, 10, theoNonZeroInd);
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.CO, sParam, 2, this.hprocessor, 10, theoNonZeroInd);
                                }

                                //                      HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.NH3, sParam, 2, this.hprocessor, String.valueOf(each)+"NH3 CS2" );                
                                //                      HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.H2O, sParam, 2, this.hprocessor, String.valueOf(each)+"H2O CS2" );                
                                //                       HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.CO, sParam, 2, this.hprocessor, String.valueOf(each)+"CO CS2" );   
                            }

                        }
                    }

                    break;

                /*c*/ case 2:
                    break;

                /*x*/ case 6:
                    break;

                /*y*/ case 7:
                    if (sParam.getNeutralLossYions() > 0) { //y ion

                        for (float f : fragArr) {

                            if (sParam.isNeturalLossIsotope()) {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                            } else {
                                HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 1, this.hprocessor, 10, theoNonZeroInd);
                            }
                            //                  HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.NH3, sParam, 1, this.hprocessor, String.valueOf(each) + "NH3");                                             

                            if (chargeState >= CS_FRAG) {
                                if (sParam.isNeturalLossIsotope()) {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, sParam.getWeight(each), theoNonZeroInd);
                                } else {
                                    HighResMassProcessor.assignTheoMass(theorArr, f - AssignMass.NH3, sParam, 2, this.hprocessor, 10, theoNonZeroInd);
                                }
                                //                   HighResMassProcessor.assignTheoMassTest(theorArr, f-AssignMass.NH3, sParam, 2, this.hprocessor, String.valueOf(each) + "NH3 CS2");                                             
                            }
                        }
                    }

                    break;

                /*z*/ case 8:
                    break;

            }

        }

        int size = pepSeq.length();
        StringBuffer modSeq = new StringBuffer();
        for (int i = 0; i < size; i++) {
            char ch = pepSeq.charAt(i);
            modSeq.append(ch);

            if (modIndexHash[i] > 0) {
                double modMass = DiffModification.getDiffModMass(ch);
                modSeq.append("(").append(modMass).append(")");
            }
        }

        newISeq.setIsModified(true);
//        System.out.println("mod seq-----------" + modSeq.toString());

        newISeq.setModSequence(modSeq.toString());

        return calcEachIonHigh(theorArr, signalArr, backgroundArr, chargeState, newISeq, sParam, masses, theoNonZeroInd);

    }

    private PeptideResult calcEachIonHigh(int[] theorArr, float[] signalArr, float[] backgroundArr, int chargeState, IndexedSequence iSeq, SearchParams sParam, List<Float> masses, Set<Integer> theoNonZeroInd) {

        int[] corr = new int[signalArr.length];
        final PeptideResult pepResult = new PeptideResult();

        //System.out.
        //K.AAMTEGDAVITAYR.C
        /*
         ArrayList<Peak> theorPeaks = new ArrayList<Peak>(200);
         int maxIndex = massCorr.length - 75;
         for(int i = 0; i < theorMass.length; i++) {
         if(theorMass[i] > 0 && i > 75 && i < maxIndex) {
         theorPeaks.add(new Peak(i, theorMass[i]));
         }
         }*/
        //for(int i=0;i<theorArr.length;i++)
        //     if(theorArr[i]>0)
        //     System.out.println("theor\t" + i + "\t" + theorArr[i]);
        float signalProduct = 0.0f;
        float backProduct = 0.0f;

        //@mlaval modification
        //for(int i=0;i<theorArr.length;i++) {
        for (Integer i : theoNonZeroInd) {

            //    if(theorArr[i]<=0) continue; // || signalArr[i]<=0) continue;
            //mlaval second change
            //    if(theorArr[i]<=0) continue;
            //int mass = (int)p.getM2z()*ACCURACYFACTOR;
            //double intensity = p.getIntensity();
            signalProduct += theorArr[i] * signalArr[i];
            backProduct += theorArr[i] * backgroundArr[i]; //massSum[mass]*intensity;

            // System.out.println(backProduct);
            //System.out.println(i + "\t===" + theorArr[i] + "\t" + signalArr[i] + "\t" + backgroundArr[i] + "\t" + (0.993377483f*signalArr[i]) + "\t" + (backgroundArr[i]/151) + "\t" + this.hprocessor.printMappingRange(i));
        }

        //double xcorr = (0.993377483f*sumProduct - moreSumProduct/151)/10000;
        float xcorr = (0.993377483f * signalProduct - backProduct / 151) / 10000;
        xcorr = xcorr < 0.00001 ? 0.00001f : xcorr;

        //System.out.println("-------" + signalProduct + " " + backProduct + " " + xcorr);
        int ionNum = iSeq.getSequence().length() - 1;
        iDoXCorrCount++;

        pepResult.setIndexedSeq(iSeq);
        pepResult.setPeptideMass(iSeq.getMass());
        //pepResult.setIon(7);  //robin why this is 7?
        //  pepResult.setIsDecoy(isDecoy);

        if (chargeState > 2) {
            pepResult.setTotalIon(2 * ionNum * sParam.getNumIonSeriesUsed());
        } else {
            pepResult.setTotalIon(ionNum * sParam.getNumIonSeriesUsed());
        }

        pepResult.setxCorr(xcorr);

        return pepResult;
    }

    private PeptideResult calcEachIon(List<FragIonModel> l, float[] scoreArray, int chargeState, IndexedSequence iSeq, int[] scoreHistogram, SearchParams sParam) {

        int[] iCorrel = new int[scoreArray.length];
        final PeptideResult pepResult = new PeptideResult();

        //System.out.println("===" + iSeq.getSequence());
        float dTmpXCorr = 0;

        for (Iterator<FragIonModel> itr = l.iterator(); itr.hasNext();) {

            final FragIonModel fmodel = itr.next();
            //System.out.println("==" + fmodel);
            int ionValue = fmodel.getWeight();
            int ion = fmodel.getIon();
            int tolBig = ionValue * 50;
            int tolMed = ionValue * 25;
            int tolSma = ionValue * 10;

            final float[] fragArr = fmodel.getFragArr();

            //System.out.println("====f model" + fmodel.getIonStr());
            //for (float eachIon : fragArr) {
            for (int i = 0; i < fragArr.length; i++) {
                float eachIon = fragArr[i];
                int ionNum;

                if (fmodel.isForwardIon()) {
                    ionNum = i + 1;
                } else {
                    ionNum = fragArr.length - i;
                }

                //  System.out.println(fmodel.getIonStr() + ionNum + "\t" + eachIon);
                int eachIntIon = AssignMass.getBinnedValue(eachIon, 0f);

                //System.out.println("=----------------=" + ionValue + " " +eachIon + " " +  eachIntIon + " " + iLowestPeak + " " + iHighestPeak);
                if ((eachIntIon > iLowestPeak - 2) && (eachIntIon < iHighestPeak + 30)) {
                    // System.out.println("low=========\t" + eachIon + " " + eachIntIon); 

                    if (iCorrel[eachIntIon] < tolBig) {

                        if (scoreArray[eachIntIon] > 0) {
                            pepResult.addMatchedIon(fmodel.getIonStr() + ionNum, eachIon);
                        }

                        dTmpXCorr += (tolBig - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                        //System.out.println("xcorr\t=========" + eachIntIon + "\t" + tolBig + "\t" + (tolBig - iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);

                        iCorrel[eachIntIon] = tolBig;
                    }


                    //final int nextIon = eachIntIon + 1;
                    final int nextIon = AssignMass.getBinnedValue(eachIon, 1.0f);
                    //System.out.println("low=========\t" + nextIon + " " + eachIntIon); 

                    if (nextIon < iCorrel.length) {//prevent java.lang.ArrayIndexOutOfBoundsException
                        if (iCorrel[nextIon] < tolMed) {
                            if (scoreArray[eachIntIon] > 0) {
                                pepResult.addMatchedIon(fmodel.getIonStr() + ionNum, eachIon + 1.0f);
                            }

                            //System.out.println("3=========" + eachIon + " " + scoreArray[eachIntIon]);
                            dTmpXCorr += (tolMed - iCorrel[nextIon]) * scoreArray[nextIon];
                            //System.out.println("xcorr\t=========" + nextIon + "\t" + tolMed + "\t" + (tolMed- iCorrel[nextIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[nextIon]);
                            iCorrel[nextIon] = tolMed;
                        }
                    }

                    //    System.out.println("----------------" + eachIon + " " + iCorrel[nextIon]);
                    //    System.exit(0);
                    //Put in neutral losses                   
                    if (ion == 0 && sParam.getNeutralLossAions() > 0) { //a ion

                        eachIntIon = AssignMass.getBinnedValue(eachIon, -AssignMass.NH3);
                        // System.out.println("low nh3=========\t" + (eachIon-AssignMass.NH3) + " " + eachIntIon); 

                        if (iCorrel[eachIntIon] < tolSma) {
                            if (scoreArray[eachIntIon] > 0) {
                                pepResult.addMatchedIon(fmodel.getIonStr() + ionNum + "-NH3", eachIon - AssignMass.NH3);
                            }

                            dTmpXCorr += (tolSma - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                            //	    System.out.println("xcorr\t=========" + eachIntIon + "\t" + tolSma + "\t" + (tolSma- iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);
                            iCorrel[eachIntIon] = tolSma;
                            // * a-NH3

                        }
                    } else if (ion == 1 && sParam.getNeutralLossBions() > 0) { //b ion

                        // System.out.println("low nh3=========\t" + (eachIon-AssignMass.NH3) + " " + eachIntIon); 
                        eachIntIon = AssignMass.getBinnedValue(eachIon, -AssignMass.NH3);
                        if (iCorrel[eachIntIon] < tolSma) {
                            if (scoreArray[eachIntIon] > 0) {
                                pepResult.addMatchedIon(fmodel.getIonStr() + ionNum + "-NH3", eachIon - AssignMass.NH3);
                            }

                            dTmpXCorr += (tolSma - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                            //    System.out.println("xcorr\t=========" + eachIntIon + "\t" + tolSma + "\t" + (tolSma- iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);
                            iCorrel[eachIntIon] = tolSma;
                            // * b-NH3

                        }

                        eachIntIon = AssignMass.getBinnedValue(eachIon, -AssignMass.H2O);
                        //System.out.println("low h2o=========\t" + (eachIon-AssignMass.H2O) + " " + eachIntIon); 
                        if (iCorrel[eachIntIon] < tolSma) {
                            if (scoreArray[eachIntIon] > 0) {
                                pepResult.addMatchedIon(fmodel.getIonStr() + ionNum + "-H2O", eachIon - AssignMass.H2O);
                            }

                            dTmpXCorr += (tolSma - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                            //   System.out.println("xcorr\t=========" + eachIntIon + "\t" + tolSma + "\t" + (tolSma- iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);
                            iCorrel[eachIntIon] = tolSma;

                        }
                        eachIntIon = AssignMass.getBinnedValue(eachIon, -AssignMass.CO);
                        //  System.out.println("low co=========\t" + (eachIon-AssignMass.CO) + " " + eachIntIon); 
                        if (iCorrel[eachIntIon] < tolSma) {
                            if (scoreArray[eachIntIon] > 0) {
                                pepResult.addMatchedIon(fmodel.getIonStr() + ionNum + "-CO", eachIon - AssignMass.CO);
                            }

                            dTmpXCorr += (tolSma - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                            //    System.out.println("xcorr\t=========" + eachIntIon + "\t" + tolSma + "\t" + (tolSma- iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);
                            iCorrel[eachIntIon] = tolSma;
                        }

                    } else if (ion == 7 && sParam.getNeutralLossYions() > 0) { //y ion
                        //logger.info(sParam.getNeutralLossAions() + " " + sParam.getNeutralLossBions() + " " + sParam.getNeutralLossYions() + " " + ion);
                        //logger.info("2-->>" + eachIntIon + "\t" + iCorrel[eachIntIon] + "\t" + iCorrel[eachIntIon-1] +"\t" + eachIon);                        
                        //    System.out.println("low nh3=========\t" + (eachIon-AssignMass.NH3) + " " + eachIntIon); 
                        eachIntIon = AssignMass.getBinnedValue(eachIon, -AssignMass.NH3);
                        if (iCorrel[eachIntIon] < tolSma) {

                            if (scoreArray[eachIntIon] > 0) {
                                pepResult.addMatchedIon(fmodel.getIonStr() + ionNum + "-NH3", eachIon - AssignMass.NH3);
                            }

                            dTmpXCorr += (tolSma - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                            //    System.out.println("xcorr\t=========" + eachIntIon + "\t" + tolSma + "\t" + (tolSma- iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);
                            iCorrel[eachIntIon] = tolSma;
                        }
                    }
                }

                if (chargeState > 2) {
                    //  System.out.println("!!====" + eachIon + "\t" + AssignMass.getH());                   
                    eachIon = (eachIon + AssignMass.getH()) / 2;
                    //  System.out.println("!!====" + eachIon + "\t" + AssignMass.getH());                   

                    eachIntIon = AssignMass.getBinnedValue(eachIon, 0.0f);

                    if ((eachIntIon > iLowestPeak - 2) && (eachIntIon < iHighestPeak + 16)) {

                        if (iCorrel[eachIntIon] < tolBig) {
                            if (scoreArray[eachIntIon] > 0) {
                                pepResult.addMatchedIon(fmodel.getIonStr() + ionNum, eachIon);
                            }

                            dTmpXCorr += (tolBig - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                            //    System.out.println("xcorr\t=========" + eachIntIon + "\t" + tolBig + "\t" + (tolBig- iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);                            
                            iCorrel[eachIntIon] = tolBig;
                        }

                        final int nextIon = AssignMass.getBinnedValue(eachIon, 0.5f);

                        if (nextIon < iCorrel.length) {
                            if (iCorrel[nextIon] < tolMed) {
                                if (scoreArray[eachIntIon] > 0) {
                                    pepResult.addMatchedIon(fmodel.getIonStr() + ionNum, eachIon);
                                }

                                dTmpXCorr += (tolMed - iCorrel[nextIon]) * scoreArray[nextIon];
                                //    System.out.println("xcorr\t=========" + nextIon + "\t" + tolMed + "\t" + (tolMed- iCorrel[nextIon]) + "\t" + iCorrel[nextIon] + "\t" + scoreArray[nextIon]);                                                            
                                iCorrel[nextIon] = tolMed;
                            }
                        }

                        if (ion == 0 && sParam.getNeutralLossAions() > 0) {
                            eachIntIon = AssignMass.getBinnedValue(eachIon, -AssignMass.NH3_CS2);
                            if (iCorrel[eachIntIon] < tolSma) {
                                if (scoreArray[eachIntIon] > 0) {
                                    pepResult.addMatchedIon(fmodel.getIonStr() + ionNum + "-NH3", eachIon - AssignMass.NH3_CS2);
                                }

                                dTmpXCorr += (tolSma - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                                //    System.out.println("xcorr\t=========" + eachIntIon + "\t" + tolSma + "\t" + (tolSma- iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);                                                                                            
                                iCorrel[eachIntIon] = tolSma;
                                //a-NH3
                            }

                        } else if (ion == 1 && sParam.getNeutralLossBions() > 0) {
                            eachIntIon = AssignMass.getBinnedValue(eachIon, -AssignMass.NH3_CS2);
                            if (iCorrel[eachIntIon] < tolSma) {
                                if (scoreArray[eachIntIon] > 0) {
                                    pepResult.addMatchedIon(fmodel.getIonStr() + ionNum + "-NH3", eachIon - AssignMass.NH3_CS2);
                                }

                                dTmpXCorr += (tolSma - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                                //    System.out.println("xcorr\t=========" + eachIntIon + "\t" + tolSma + "\t" + (tolSma- iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);                                                                                                                            
                                iCorrel[eachIntIon] = tolSma;
                                //b-NH3

                            }

                            eachIntIon = AssignMass.getBinnedValue(eachIon, -AssignMass.H2O_CS2);
                            if (iCorrel[eachIntIon] < tolSma) {

                                if (scoreArray[eachIntIon] > 0) {
                                    pepResult.addMatchedIon(fmodel.getIonStr() + ionNum + "-H2O", eachIon - AssignMass.H2O_CS2);
                                }

                                dTmpXCorr += (tolSma - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                                //    System.out.println("xcorr\t=========" + eachIntIon + "\t" + tolSma + "\t" + (tolSma- iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);                                                                                                                                                            
                                iCorrel[eachIntIon] = tolSma;
                                //b-H2O

                            }
                            eachIntIon = AssignMass.getBinnedValue(eachIon, -AssignMass.CO_CS2);

                            if (iCorrel[eachIntIon] < tolSma) {
                                if (scoreArray[eachIntIon] > 0) {
                                    pepResult.addMatchedIon(fmodel.getIonStr() + ionNum + "-CO", eachIon);
                                }

                                dTmpXCorr += (tolSma - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                                //    System.out.println(eachIon + "xcorr\t=========" + eachIntIon + "\t" + tolSma + "\t" + (tolSma- iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);                                                                                                                                                            
                                iCorrel[eachIntIon] = tolSma;
                                //b-CO

                            }

                        } else if (ion == 7 && sParam.getNeutralLossYions() > 0) {

                            eachIntIon = AssignMass.getBinnedValue(eachIon, -AssignMass.NH3_CS2);

                            if (iCorrel[eachIntIon] < tolSma) {

                                if (scoreArray[eachIntIon] > 0) {
                                    pepResult.addMatchedIon(fmodel.getIonStr() + ionNum + "-NH3", eachIon - AssignMass.NH3_CS2);
                                }

                                dTmpXCorr += (tolSma - iCorrel[eachIntIon]) * scoreArray[eachIntIon];
                                //    System.out.println("xcorr\t=========" + eachIntIon + "\t" + tolSma + "\t" + (tolSma- iCorrel[eachIntIon]) + "\t" + iCorrel[eachIntIon] + "\t" + scoreArray[eachIntIon]);                                                                                                                                                            
                                iCorrel[eachIntIon] = tolSma;
                                //y-NH3

                            }
                        }
                    }
                }

            } //end for each ion
        } //end for each fragionmodel

        dTmpXCorr = dTmpXCorr / 100000;
        //dTmpXCorrReverse = dTmpXCorrReverse / 100000.0;

        //dXCorr_Mean += (dTmpXCorr + dTmpXCorrReverse);
        dXCorr_Mean += dTmpXCorr;
        //dXCorr_Square_Sum += (dTmpXCorr * dTmpXCorr + dTmpXCorrReverse * dTmpXCorrReverse);
        dXCorr_Square_Sum += dTmpXCorr * dTmpXCorr; // + dTmpXCorrReverse * dTmpXCorrReverse);
        int k = (int) (dTmpXCorr * 10.0 + 50.0);
        scoreHistogram[k]++;
        // boolean bFoundDiff = false;
        boolean isDecoy = false;

        //if (dTmpXCorr >= dLowestXCorr) {
        //logger.info(iIsDecoy);
        int ionNum = iSeq.getSequence().length() - 1;
        iDoXCorrCount++;

        pepResult.setIndexedSeq(iSeq);
        pepResult.setPeptideMass(iSeq.getMass());

        pepResult.setIon(7);  //robin why this is 7?
        pepResult.setIsDecoy(isDecoy);

        if (chargeState > 2) {
            pepResult.setTotalIon(2 * ionNum * sParam.getNumIonSeriesUsed());
        } else {
            pepResult.setTotalIon(ionNum * sParam.getNumIonSeriesUsed());
        }

        if (dTmpXCorr < 0) {
            dTmpXCorr = 0;
        }

        pepResult.setxCorr(dTmpXCorr);
        return pepResult;

    }

    private float[] generateCorrHigh(float[] scoreArray, float maxInt, int highestIon, SearchParams sParam) {

        float[] normArr = new float[scoreArray.length];

        int win = sParam.getScoreWin();
        win = 30;

        int winSize = (int) (scoreArray.length) / win;
        for (int i = 0; i < win; i++) {
            float max = 0;
            for (int j = 0; j < winSize; j++) {
                int index = i * winSize + j;
                if (scoreArray[index] > max) {
                    max = scoreArray[index];
                }
            }

            for (int j = 0; j < winSize; j++) {
                int index = i * winSize + j;
                if (scoreArray[index] > 5) {
                    normArr[index] = scoreArray[index] * 50.0f / max; //  [2.5, 50]
                }
            }
        }

        int corrWin = 300;

        scoreArray[0] = 0;
        for (int i = 0; i < corrWin + 1; i++) {
            scoreArray[0] += normArr[i];
        }

        for (int i = 1; i < corrWin + 1; i++) {

            scoreArray[i] = scoreArray[i - 1]
                    + normArr[i + corrWin];
        }

        for (int i = corrWin + 1; i < normArr.length - corrWin; i++) {

            scoreArray[i] = scoreArray[i - 1]
                    + normArr[i + corrWin]
                    - normArr[i - corrWin - 1];

        }

        for (int i = normArr.length - corrWin; i < normArr.length; i++) {

            scoreArray[i] = scoreArray[i - 1]
                    - normArr[i - corrWin - 1];
        }

        for (int i = 0; i < normArr.length; i++) {

            normArr[i] = normArr[i] - scoreArray[i]
                    / (2 * corrWin + 1);
        }

        int i = 0;
        while (Math.abs(normArr[i]) < FLOAT_ZERO) { //TODO java.lang.ArrayIndexOutOfBoundsException: 1425
            i++;
        }
        iLowestPeak = i;
        i = normArr.length - 1;
        while (Math.abs(normArr[i]) < FLOAT_ZERO) {
            i--;
        }
        iHighestPeak = i;

        return normArr;
    }

    private float[] generateCorr(float[] scoreArray, float maxInt, int highestIon) {

        int numWindows = 10;
        float[] tempArr = new float[scoreArray.length];

        //int iWindowSize = Math.round(highestIon / numWindows);
        int winSize = MathUtil.round(highestIon / numWindows);
        for (int i = 0; i < numWindows; i++) {
            float dMaxWindowInten = 0.0f;

            for (int ii = 0; ii < winSize; ii++) /* find max inten. in window */ {

                if (scoreArray[i * winSize + ii] > dMaxWindowInten) {
                    dMaxWindowInten = scoreArray[i * winSize + ii];
                }
            }

            for (int ii = 0; ii < winSize; ii++) {

                if (scoreArray[i * winSize + ii] > 0.05f * maxInt) {
                    tempArr[i * winSize + ii]
                            = scoreArray[i * winSize + ii] * 50f / dMaxWindowInten;
                }

            }
        } //normalized to [0, 50]

        /*  
         for (int i = 0; i < scoreArray.length; i++) {
         if(tempArr[i]>0)
         System.out.println(i + "\t" + this.hprocessor.printMappingRange(i) + " ==hi " + tempArr[i]);
         }

         System.exit(0);
         */
        scoreArray[0] = 0;
        for (int i = 0; i < XCORR_WINDOW + 1; i++) {
            scoreArray[0] += tempArr[i];
        }

        for (int i = 1; i < XCORR_WINDOW + 1; i++) {

            scoreArray[i] = scoreArray[i - 1]
                    + tempArr[i + XCORR_WINDOW];
        }

        for (int i = XCORR_WINDOW + 1; i < tempArr.length - XCORR_WINDOW; i++) {

            scoreArray[i] = scoreArray[i - 1]
                    + tempArr[i + XCORR_WINDOW]
                    - tempArr[i - XCORR_WINDOW - 1];

        }

        for (int i = tempArr.length - XCORR_WINDOW; i < tempArr.length; i++) {

            scoreArray[i] = scoreArray[i - 1]
                    - tempArr[i - XCORR_WINDOW - 1];
        }

        for (int i = 0; i < tempArr.length; i++) {

            tempArr[i] = tempArr[i] - scoreArray[i]
                    / (2 * XCORR_WINDOW + 1);
        }

        int i = 0;
        while (Math.abs(tempArr[i]) < FLOAT_ZERO) { //TODO java.lang.ArrayIndexOutOfBoundsException: 1425
            i++;
        }
        iLowestPeak = i;
        i = tempArr.length - 1;
        while (Math.abs(tempArr[i]) < FLOAT_ZERO) {
            i--;
        }
        iHighestPeak = i;

        /*
         for (i = 0; i < tempArr.length; i++) {
         if(tempArr[i]>0)
         System.out.println("low\t" + i + "\t" + tempArr[i]);
         }*/
        return tempArr;
    }

    private float normalize(float[] arr, float highestInt) {
        float maxInt = 0.0f;
        float factor = 100.0f / highestInt;

        if (highestInt > FLOAT_ZERO) {
            for (int i = 0; i < arr.length; i++) {
                //arr[i] = (arr[i] / highestInt) * 100.0f;
                arr[i] = arr[i] * factor;

                if (maxInt < arr[i]) {
                    maxInt = arr[i];
                }
            }
        }
        return maxInt;
    }

    public StringBuffer header(SearchParams sParam) {
        StringBuffer sb = new StringBuffer();

        sb.append("H\tSQTGenerator\t").append(program).append("\n")
                .append("H\tBlazmassVersion\t").append(version).append("\n")
                .append("H\tDatabase\t").append(sParam.getDatabaseName()).append("\n");
        if (sParam.isUseMonoParent()) {
            sb.append("H\tPrecursorMasses\tMONO").append("\n");
        } else {
            sb.append("H\tPrecursorMasses\tAVG").append("\n");
        }
        if (sParam.isUseMonoFragment()) {
            sb.append("H\tFragmentMasses\tMONO").append("\n");
        } else {
            sb.append("H\tFragmentMasses\tAVG").append("\n");

        }

        return sb;
    }

    /*
     private String getValue(String eachLine) {
     String arr[] = eachLine.split(";");
     String tmparr[] = arr[0].split("=");
     return tmparr[1].trim();
     }*/
    public float getPpm(float precMass, float tolerance) {
        float ppm = tolerance / 1000;
        ppm += 1;
        ppm = precMass - precMass / ppm;

        return ppm;
    }

    /**
     * Cache result of ion multiplication by 3 intensities
     */
    private static class IonIntensitiesCache {

        private final IonIntensities[] cache = new IonIntensities[255];

        IonIntensities get(int ionValue) {
            IonIntensities ret = cache[ionValue];
            if (ret == null) {
                ret = new IonIntensities(ionValue);
                cache[ionValue] = ret;
            }
            return ret;
        }

    }

    private static class IonIntensities {

        int small;
        int medium;
        int big;

        IonIntensities(int ionValue) {
            small = ionValue * 10;
            medium = ionValue * 25;
            big = ionValue * 50;
        }

    }
}
