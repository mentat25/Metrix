package nki.core;

// Metrix - A server / client interface for Illumina Sequencing Metrics.
// Copyright (C) 2014 Bernd van der Veen

// This program comes with ABSOLUTELY NO WARRANTY;
// This is free software, and you are welcome to redistribute it
// under certain conditions; for more information please see LICENSE.txt

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.Properties;
import java.util.logging.Level;
import java.io.IOException;
import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import nki.util.LoggerWrapper;
import nki.parsers.illumina.*;
import nki.io.DataStore;
import nki.constants.Constants;
import nki.objects.Summary;
import nki.parsers.xml.XmlDriver;
import nki.parsers.metrix.PostProcessing;

  /**
   * Class that determines state, progress and parsing interfaces.
   * Sequencing runs are parsed and stored in a Metrix Summary object
   * which in turn is serialized into the database.
   *
   */

public class MetrixLogic {

  // Instantiate Logger
  private static final LoggerWrapper metrixLogger = LoggerWrapper.getInstance();

  // Call inits
  private Summary summary = null;
  private int state;
  private static final Properties configFile = new Properties();
  public boolean quickLoad = false;
  
  public MetrixLogic() {

  }
   /**
   * ProcessMetrics is the main parsing class. 
   * 
   * @param Path Sequencing directory
   * @param int Initial run state
   * @param DataStore Datastore instance
   */
  
  public boolean processMetrics(Path runDir, int st, DataStore ds) {
    boolean success = false;
    boolean finishBool = false;

    if (st == -1) {
      finishBool = true;
      this.state = Constants.STATE_FINISHED;
    }
    else {
      this.state = st;
    }

    String extractionMetrics = "";
    String tileMetrics = "";
    String qualityMetrics = "";
    String path = runDir.toString();

    if (!path.matches("(.*)InterOp(.*)")) {
      extractionMetrics = path + "/InterOp/" + Constants.EXTRACTION_METRICS;
      tileMetrics = path + "/InterOp/" + Constants.TILE_METRICS;
      qualityMetrics = path + "/InterOp/" + Constants.QMETRICS_METRICS;
    }
    else {
      extractionMetrics = path + "/" + Constants.EXTRACTION_METRICS;
      tileMetrics = path + "/" + Constants.TILE_METRICS;
      qualityMetrics = path + "/" + Constants.QMETRICS_METRICS;
    }

    // Retrieve Summary if exists else create new instance.
    this.checkSummary(path);

    // Check if run has finished uncaught.
    if (finishBool && this.state != Constants.STATE_FINISHED) {
      this.checkFinished(path);
    }

    // Check if run hasnt halted.
    if (this.checkTimeout(path) && !path.matches("(.*)/InterOp(.*)")) {
      summary.setState(Constants.STATE_HANG);
      saveEntry(path);
    }

    // Save entry for init phase of run.
    if (state == Constants.STATE_INIT) {
      // Set basic run info
      summary.setState(Constants.STATE_INIT);
      summary.setLastUpdated();
      summary.setRunDirectory(path);
      XmlDriver xmd = null;
      try {
        if (!summary.getXmlInfo()) {
          xmd = new XmlDriver(path, summary);
          if (xmd.parseRunInfo()) {
            summary = xmd.getSummary();
          }
          else {
            summary.setXmlInfo(false);
          }
        }
      }
      catch (SAXException SAX) {
        metrixLogger.log.severe("Error parsing XML with SAX. " + SAX.toString());
      }
      catch (IOException Ex) {
        metrixLogger.log.severe("IOException Error. " + Ex.toString());
      }
      catch (ParserConfigurationException PXE) {
        metrixLogger.log.severe("Parser Configuration Exception. " + PXE.toString());
      }
      finally {
        xmd.closeAll();
      }
      
      if(!this.quickLoad){
        saveEntry(path);
      }
      return false;
    }
    
    if(!this.quickLoad){
        summary.setRunDirectory(path);

        // Instantiate processing modules.
        ExtractionMetrics em = new ExtractionMetrics(extractionMetrics, state);
        TileMetrics tm = new TileMetrics(tileMetrics, state);
        QualityMetrics qm = new QualityMetrics(qualityMetrics, state);

        // Fail conditions:
        // 	- More than 20 parsing attempts have been made which resulted in errors.
        // 	- The current cycle of run is over 20
        // 	- All involved InterOp files (EM, TM, QM) have not been updated for over 24 hours (86400000ms)
        // 	- Run is not awaiting turning.
        if (summary.getParseError() >= 20 && summary.getCurrentCycle() > 20) {
          if ((em.getLastModifiedSourceDiff() >= Constants.ACTIVE_TIMEOUT) &&
              (tm.getLastModifiedSourceDiff() >= Constants.ACTIVE_TIMEOUT) &&
              (qm.getLastModifiedSourceDiff() >= Constants.ACTIVE_TIMEOUT)
              ) {
            if (!summary.getPairedTurnCheck()) {    // Check if this is not a run waiting for turning.
              summary.setState(Constants.STATE_HANG);
              saveEntry(path);
              metrixLogger.log.info("Run " + summary.getRunId() + " has failed to complete within the allotted time frame.");
              return false;
            }
          }
        }

        try {
          if (summary.getState() != Constants.STATE_FINISHED) {
            summary.setState(state);
          }

          if (!summary.getXmlInfo()) {
            XmlDriver xmd = new XmlDriver(runDir + "", summary);
            if (xmd.parseRunInfo()) {
              summary = xmd.getSummary();
            }
            else {
              summary.setXmlInfo(false);
            }
          }

          int currentCycle = em.getLastCycle();

          if (summary.getRunType().equals("Paired End") && currentCycle == summary.getTurnCycle()) {
            summary.setState(Constants.STATE_TURN);
            state = Constants.STATE_TURN;
          }

          // If run == paired end. Check for FC turn at (numReads read 1 + index 1). Set state accordingly.
          if (summary.getPairedTurnCheck()) {
            // State has not been set yet and user has not yet been notified for the turning of the flowcell.
            if (!summary.getHasNotifyTurned()) {
              summary.setState(Constants.STATE_TURN);
              metrixLogger.log.info("Flowcell of run: " + summary.getRunId() + " has to be turned. Current cycle: " + currentCycle);
              summary.setHasNotifyTurned(true);
            }
          }

          summary.setCurrentCycle(currentCycle);
          summary.setLastUpdated();

          // Catch event for when the flowcell has turned and the sequencer has continued sequencing the other side.
          if ((summary.getRunType() == "Paired End") && (currentCycle > summary.getTurnCycle()) && (summary.getState() == Constants.STATE_TURN)) {
            summary.setHasTurned(true);
            summary.setState(Constants.STATE_RUNNING);
          }
          else {
            summary.setState(state);
          }

          saveEntry(path);  // Store summary entry in SQL database

          metrixLogger.log.info("Finished processing: " + runDir.getFileName());

          success = true;
        }
        catch (NullPointerException NPE) {
          NPE.printStackTrace();
        }
        catch (SAXException SAX) {
          metrixLogger.log.severe("Error parsing XML with SAX. " + SAX.toString());
        }
        catch (IOException Ex) {
          metrixLogger.log.severe("IOException Error. " + Ex.toString());
        }
        catch (ParserConfigurationException PXE) {
          metrixLogger.log.severe("Parser Configuration Exception. " + PXE.toString());
        }
        finally {
          em.closeSourceStream();
          qm.closeSourceStream();
          tm.closeSourceStream();
        }
    
    }else{
        metrixLogger.log.info("Quick loading finished run : " + path);
    }

    return success;
  }

  private void checkSummary(String path) {
    // Check if run path is present in the database already. If so, retrieve; else instantiate new summary object;
    boolean scrape = false;
    if (summary == null) {
      scrape = true;
    }

    if (scrape || !summary.getRunDirectory().equals(path)) {
      try {
        DataStore _ds = new DataStore();
        if (_ds.checkSummaryByRunId(path)) {
          if(!this.quickLoad){
            summary = _ds.getSummaryByRunName(path);
          }
        }
        else {
          // Summary isnt present in database
          summary = new Summary();
        }
        _ds.closeAll();
        if (_ds != null) {
          _ds = null;
        }
      }
      catch (Exception SEx) {  // SQL Exception - Generic catch
        metrixLogger.log.severe("Error checking for summary by runId in database. " + SEx.toString());
      }
    }
  }

  public void finishRun(String path) {
    this.quickLoad = false;
    this.checkSummary(path);
    summary.setState(Constants.STATE_FINISHED);  // Set state to STATE_FINISED (2): Complete
    try {
      DataStore tmpDS = new DataStore();
      // Final processing run before finishing.
      processMetrics(Paths.get(path), -1, tmpDS);
      metrixLogger.log.info("Performing final parse of data to create distributions.");
      MetrixContainer mc = new MetrixContainer(summary, false, true);
      metrixLogger.log.info("Finished parsing " + summary.getRunId());
      mc=null;
      tmpDS.closeAll();
      if (tmpDS != null) {
        tmpDS = null;
      }
      summary.setHasFinished(true); // Run has finished
    }
    catch (IOException IE) {
      metrixLogger.log.severe("Error setting up database connection. " + IE.toString());
      metrixLogger.log.severe("Error stacktrace: " + IE);
    }
    metrixLogger.log.info("Run " + summary.getRunId() + " has finished.");
    saveEntry(path);

    // Check if run requires post processing and execute it
    runPostProcessing(summary);
  }

  public void saveEntry(String path) {
    int lastId = 0;
    try {
      DataStore _ds = new DataStore();
      if (!_ds.checkSummaryByRunId(path)) {
        try {
          lastId = DataStore.getMaxId();
          summary.setSumId(lastId + 1);
          _ds.appendedWrite(summary, path);
        }
        catch (Exception Ex) {
          metrixLogger.log.severe("Exception in write statement lastID = " + lastId + " Error: " + Ex.toString());
        }
      }
      else {
        // Run has been parsed before. Update instead of insert
        try {
          _ds.updateSummaryByRunName(summary, path);
        }
        catch (Exception SEx) {
          metrixLogger.log.severe("Exception in update statement " + SEx.toString());
        }
      }
      _ds.closeAll();
      if (_ds != null) {
        _ds = null;
      }
    }
    catch (Exception Ex) {
      metrixLogger.log.severe("Run ID Checking error." + Ex.toString());
    }
  }

  public boolean checkFinished(String path) {
    File RTAComplete = new File(path + "/RTAComplete.txt");
    this.checkSummary(path);

    if (RTAComplete.isFile() && summary.getState() != Constants.STATE_FINISHED) {
      this.finishRun(path);
      return true;
    }
    else {
      return false;
    }
  }

  public boolean checkPaired(String path, DataStore ds) {
    this.checkSummary(path);
    boolean check = false;

    if (summary.getPairedTurnCheck()) {
      // State has not been set yet and user has not yet been notified for the turning of the flowcell.
      int cCycle = summary.getCurrentCycle();
      summary.setState(Constants.STATE_TURN);
      metrixLogger.log.info("Flowcell of run: " + summary.getRunId() + " has to be turned. Current cycle: " + cCycle);
      summary.setHasNotifyTurned(true);
      check = true;
    }
    return check;
  }

  public boolean checkTimeout(String file) {
    File lastModCheck = new File(file + "/InterOp/");
    if(summary.getState() == Constants.STATE_FINISHED){
        metrixLogger.log.info("Run has finished. No need for update.");
        return false;
    }
    if (!lastModCheck.isDirectory()) {
      metrixLogger.log.warning("Directory " + file + " does not exist.");
      return false;
    }

    File[] files = lastModCheck.listFiles();

    Arrays.sort(files, new Comparator<File>() {
      public int compare(File f1, File f2) {
        return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
      }
    });

    long difference = (System.currentTimeMillis() - files[files.length - 1].lastModified());

    if (difference > 86400000 && (summary.getState() == Constants.STATE_RUNNING)) { // If no updates for 24 hours. (86400000 milliseconds)
      metrixLogger.log.info("Run (" + summary.getRunId() + ") has timed out. No data received for over 24 hours. Halting watch.");
      state = Constants.STATE_HANG;
      return true;
    }

    return false;
  }

  public void loadConfig() {
    try {
      String externalFileName = System.getProperty("properties");
      String absFile = (new File(externalFileName)).getAbsolutePath();

      InputStream fin = new FileInputStream(new File(absFile));
      configFile.load(fin);
      fin.close();
    }
    catch (IOException Ex) {
      LoggerWrapper.log.log(Level.SEVERE, "IOException when loading config: {0}", Ex.toString());
    }
  }

  public boolean runPostProcessing(Summary sum) {
    // Load properties file parameter for post processing
    loadConfig();
    String execPP = configFile.getProperty("EXEC_POSTPROCESSING", "FALSE");
    if(sum != null){
        if (execPP.equalsIgnoreCase("TRUE") ) {
          metrixLogger.log.log(Level.INFO, "Calling post processing module for: {0}", sum.getRunId());
          PostProcessing pp = new PostProcessing(sum);
          pp.run();

          return true;

        }
        else {
          metrixLogger.log.info("No postprocessing performed for: " + sum.getRunId());
          return false;
        }
    }else{
        return false;
    }
  }
}
