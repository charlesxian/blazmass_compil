# Blazmass+ComPIL

### Blazmass, a fast and sensitive proteomics search engine
### ComPIL (Comprehensive Protein Identification Library), a set of peptide and protein databases for metaproteomics

----

*See related **Metaproteomics** repository at [Su Lab/metaproteomics](https://bitbucket.org/sulab/metaproteomics)*

----

#### Amazon Machine Images

**We have fully functional Amazon Machine Images (AMIs) for testing Blazmass and ComPIL**

**Instructions**

***All AMIs are in the US West (Oregon) region***

* Start an EC2 instance for ComPIL using public AMI ID `ami-81eaeeb1`
    * *Recommended Instance Type: **r3.4xlarge***
    * *Recommended Volume Type: **General Purpose (SSD)** with volume size of at least 700GB*
    * **Configure Security Group to allow inbound TCP connections from any IP address on port 27017 *(required for use)* **
* Start an EC2 instance for Blazmass using public AMI ID `ami-37fafe07`  
    * *Recommended: **r3.4xlarge, r3.8xlarge, or m4.10xlarge***
* Run Blazmass+ComPIL
    * Log in to Blazmass instance via SSH
    * run `cd test_data`
    * run `nohup ./run_test_search [COMPIL_IP] 121614_SC_sampleH1sol_25ug_pepstd_HCD_FTMS_MS2_07_11.ms2 > nohup.out &` where **[COMPIL_IP]** is the IP address of the ComPIL EC2 instance you just started
    * The above command will run Blazmass in the background on a test dataset with 32 search threads and output progress to file `nohup.out` (which you can follow using `tail -f nohup.out`)
    * Searched data will be saved in `121614_SC_sampleH1sol_25ug_pepstd_HCD_FTMS_MS2_07_11.sqt`

----

#### Running locally

*tested on CentOS 6 and RHEL 7*

**Requirements**

* Java 1.7 (Oracle or OpenJDK)
* [MongoDB 2.6+](http://www.mongodb.org/)
    * MongoDB databases can be running locally (connect to `localhost`), remotely as a single node (typically using TCP port 27017), or behind a `mongos` process (typically port 27018)

**Instructions**

* Download [Blazmass+ComPIL](https://bitbucket.org/sandipchatterjee/blazmass_compil/downloads/blazmass_compil.tar.gz)
* Untar / extract using `tar -xvf blazmass_compil.tar.gz`
* Modify parameters as desired in directory `blazmass_compil/blazmass_search/blazmass.params` -- specifically, database connection information
* Try a test search using `blazmass_compil/test_data/run_test_search`
    * Use a command like the following to run in the background: `nohup ./run_test_search [COMPIL_IP] 121614_SC_sampleH1sol_25ug_pepstd_HCD_FTMS_MS2_07_11.ms2 > nohup.out &` where **[COMPIL_IP]** is the IP address of the ComPIL (MongoDB) installation

----

*Blazmass written by [Robin Park, John Yates III, et al.](http://fields.scripps.edu)*

*Initial ComPIL (MongoDB) integration by [Sandip Chatterjee, Dennis Wolan](http://www.scripps.edu/wolan)*

*Maintenance and improvements by [Greg Stupp, Andrew Su](http://sulab.org/)*


