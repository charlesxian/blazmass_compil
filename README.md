# Blazmass+ComPIL

### Blazmass, a fast and sensitive proteomics search engine
### ComPIL (Comprehensive Protein Identification Library), a set of peptide and protein databases for metaproteomics

----

See related *Metaproteomics* repository at [Su Lab/metaproteomics](https://bitbucket.org/sulab/metaproteomics)

----

### File formats

MS2 and SQT are plaintext file formats detailed in the following publication:

[McDonald, W. H. et al. MS1, MS2, and SQT-three unified, compact, and easily parsed file formats for the storage of shotgun proteomic spectra and identifications. Rapid Commun. Mass Spectrom. 18, 2162–2168 (2004).](http://dx.doi.org/10.1002/rcm.1603)

MS2 files can be generated from instrument RAW files using a tool such as [RawExtractor](http://fields.scripps.edu/) or [RawConverter](http://fields.scripps.edu/)

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
...
```
----
### Running locally

*tested on CentOS 6, RHEL 7, Linux Mint/Ubuntu 15.10*

**Requirements**

* Java 1.7 (Oracle or OpenJDK)
* [MongoDB 3.0+](http://www.mongodb.org/)
    * MongoDB databases can be running locally (`localhost`), remotely as a single node (typically using TCP port 27017), or sharded behind a `mongos` process (typically port 27018)
* Databases
    * see metaproteomics repository for [build_compil](https://bitbucket.org/sulab/metaproteomics)
    * or see [here](https://hpccloud.scripps.edu/index.php/s/55zVzx1QVaxstqe) for demo databases

**Instructions**

* Download [Blazmass+ComPIL](https://github.com/sandipchatterjee/blazmass_compil/releases)
* Untar / extract using `tar -xvf blazmass_compil.tar.gz`
* Modify parameters as desired in directory `example/blazmass.params` -- specifically, database connection information
* Try a test search using `example/run.sh`

----

*Blazmass written by [Robin Park, John Yates III, et al.](http://fields.scripps.edu)*

*ComPIL/MongoDB integration by [Sandip Chatterjee](http://www.scripps.edu/wolan) & [Greg Stupp](http://sulab.org/)*


