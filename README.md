# Blazmass+ComPIL

### Blazmass, a fast and sensitive proteomics search engine
### ComPIL (Comprehensive Protein Identification Library), a set of peptide and protein databases for metaproteomics

----

See related *Metaproteomics* repository at [Su Lab/metaproteomics](https://bitbucket.org/sulab/metaproteomics)

----

### File formats

MS2 and SQT are plaintext file formats detailed in the following publication:

[McDonald, W. H. et al. MS1, MS2, and SQT-three unified, compact, and easily parsed file formats for the storage of shotgun proteomic spectra and identifications. Rapid Commun. Mass Spectrom. 18, 2162–2168 (2004).](http://dx.doi.org/10.1002/rcm.1603)

MS2 files can be generated from instrument RAW files using a tool such as [RawExtractor](http://fields.scripps.edu/)

#### Input file format

Blazmass takes an MS2 file as input. MS2 files contain MS/MS precursor ion, charge, and fragment information:

```
MS2 Format
S       000040  000040  960.22797
I       RetTime 0.25
I       PrecursorInt    6606.3
I       IonInjectionTime        150.000
I       ActivationType  HCD
I       PrecursorFile   MSMS_sample.ms1
I       PrecursorScan   34
I       InstrumentType  FTMS
Z       4       3837.89004
109.4537 168.2 0
111.1992 175.5 0
112.6070 188.2 0
136.0749 575.7 0
143.1249 190.1 0
152.1059 178.3 0
...
```

#### Output file format

Blazmass outputs search results in the SQT file format, which contains unfiltered proteomic scoring information, including the best scoring peptide matches for each scan, parent proteins for each matched peptide, and other search-related information.

```
SQT Format
S       10210   [information for scan #10210]
M       1       [best scoring peptide match]
L       [parent protein for peptide match 1]
L       [parent protein for peptide match 1]
L       [parent protein for peptide match 1]
M       2       [second-best scoring peptide match]
L       [parent protein for peptide match 2]
L       [parent protein for peptide match 2]
M       3       [third-best scoring peptide match]
L       [parent protein for peptide match 3]
M       4       [fourth-best scoring peptide match]
L       [parent protein for peptide match 4]
L       [parent protein for peptide match 4]
L       [parent protein for peptide match 4]
M       5       [fifth-best scoring peptide match]
L       [parent protein for peptide match 5]
L       [parent protein for peptide match 5]
...
```

----

### Amazon Machine Images

**We have fully functional Amazon Machine Images (AMIs) for testing Blazmass and ComPIL**

**Requirements**

* An [Amazon Web Services](http://aws.amazon.com) account
* An [SSH client](https://en.wikipedia.org/wiki/Comparison_of_SSH_clients)

**Instructions**

***All AMIs are in the US West (Oregon) region***

* Start an EC2 instance for ComPIL using public AMI ID `ami-853a3db5` (search the "Community AMIs" tab when launching a new EC2 instance)
    * Recommended Instance Type: **r3.4xlarge** (cost estimate: ~$1.50/hour)
    * Recommended Volume Type: **General Purpose (SSD)** with volume size of at least 800GB
    * Configure Security Group to allow inbound TCP connections from any IP address on port 27017 **(required for use)**
* Start an EC2 instance for Blazmass using public AMI ID `ami-1b383f2b`
    * Recommended: **r3.4xlarge, r3.8xlarge, or m4.10xlarge** (cost estimate: <$3/hour)
* Run Blazmass+ComPIL
    * Log in to Blazmass instance via SSH
    * run `cd blazmass_search`
    * run `nohup ./run_search [COMPIL_IP] sample_data.ms2 > nohup.out &` where **[COMPIL_IP]** is the IP address of the ComPIL EC2 instance you just started
    * The above command will run Blazmass in the background on a test dataset with 32 search threads and output progress to file `nohup.out` (which you can follow using `tail -f nohup.out`)
        * This test dataset is a portion of a human gut microbiome proteomic MudPIT dataset
    * Searched data will be saved in `sample_data.sqt`
    * Test dataset should take ~2 hours to run (tested with 32 search threads), which should cost around $10-20 with the instance types recommended above (far less if using spot instances)

----

### Running locally

*tested on CentOS 6 and RHEL 7*

**Requirements**

* Java 1.7 (Oracle or OpenJDK)
* [MongoDB 2.6+](http://www.mongodb.org/)
    * MongoDB databases can be running locally (connect to `localhost`), remotely as a single node (typically using TCP port 27017), or sharded behind a `mongos` process (typically port 27018)
* Preexisting ComPIL databases -- *see metaproteomics repository for [build_compil](https://bitbucket.org/sulab/metaproteomics)*
    * MassDB -- ***required***
    * SeqDB -- optional, but needed for identifying parent protein IDs for peptide matches
    * ProtDB -- optional, but needed for mapping protein IDs to FASTA deflines and other protein information

**Instructions**

* Download [Blazmass+ComPIL](https://github.com/sandipchatterjee/blazmass_compil/releases)
* Untar / extract using `tar -xvf blazmass_compil.tar.gz`
* Modify parameters as desired in directory `blazmass_compil/blazmass_search_dist/blazmass.params` -- specifically, database connection information
* Try a test search using `blazmass_compil/blazmass_search/run_search`
    * Use a command like the following to run in the background: `nohup ./run_search [COMPIL_IP] sample_data.ms2 > nohup.out &` where **[COMPIL_IP]** is the IP address of the ComPIL (MongoDB) installation

----

*Blazmass written by [Robin Park, John Yates III, et al.](http://fields.scripps.edu)*

*Initial ComPIL (MongoDB) integration by [Sandip Chatterjee, Dennis Wolan](http://www.scripps.edu/wolan)*

*Maintenance and improvements by [Greg Stupp, Andrew Su](http://sulab.org/)*


