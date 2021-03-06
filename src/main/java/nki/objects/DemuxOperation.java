// Metrix - A server / client interface for Illumina Sequencing Metrics.
// Copyright (C) 2014 Bernd van der Veen

// This program comes with ABSOLUTELY NO WARRANTY;
// This is free software, and you are welcome to redistribute it
// under certain conditions; for more information please see LICENSE.txt

package nki.objects;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import nki.util.LoggerWrapper;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DemuxOperation extends PostProcess {

  public static final long serialVersionUID = 42L;
  private String bclToFastQPath;
  private String hiseqHeader = "FCID,Lane,SampleID,SampleReferenceGenome,Index,Descr,Control,Recipe,Operator,SampleProject";
  private ArrayList<String> hiseqHeaderLookup = new ArrayList<>();
  private String arguments;
  private String baseRunDir;
  private String baseOutputDir;
  private String baseWorkingDir;
  private String loggingPath;
  private String makePath;
  private String makeArguments;
  private String splitBy = "SampleProject"; // Split by project or lane
  private ArrayList<File> samplesheetLocations = new ArrayList<>();
  private String baseMask;  
  private List<String[]> fullSamplesheet = new ArrayList<>();
  private HashMap<Object, ArrayList<String>> sampleSheets = new HashMap<>(); // Store samplesheets by splitBy type.
  private HashMap<String, Integer> miseqLookup = new HashMap<>();
  private HashMap<String, String> baseMaskMap = new HashMap<>();
  public HashMap<String, String> ssBaseMaskMap = new HashMap<>();
  
  public DemuxOperation(Node parentNode) {
    NamedNodeMap parentAttr = parentNode.getAttributes();
    // Prepare header lookup array.
    hiseqHeaderLookup.addAll(Arrays.asList(hiseqHeader.split(",", -1)));

    // Set attribute values of inherited PostProcess
    this.setOrder(Integer.parseInt(parentAttr.getNamedItem("execOrder").getNodeValue()));
    this.setSubOrder(Integer.parseInt(parentAttr.getNamedItem("execOrder").getNodeValue()));
    this.setId(parentAttr.getNamedItem("id").getNodeValue());
    this.setTitle(parentAttr.getNamedItem("title").getNodeValue());
    NodeList foProps = parentNode.getChildNodes();

    for (int i = 0; i < foProps.getLength(); i++) {
      // Iterate over node properties
      Node p = foProps.item(i);
      if (p.getNodeName().equalsIgnoreCase("BclToFastQPath")) {
        this.setBclToFastQPath(p.getTextContent());
      }
      else if (p.getNodeName().equalsIgnoreCase("Arguments")) {
        this.setArguments(p.getTextContent());
      }
      else if (p.getNodeName().equalsIgnoreCase("BaseMask")) {
        this.setBaseMask(p.getTextContent());
      }
      else if (p.getNodeName().equalsIgnoreCase("BaseRunDir")) {
        this.setBaseRunDir(p.getTextContent());
      }
      else if (p.getNodeName().equalsIgnoreCase("BaseOutputDir")) {
        this.setBaseOutputDir(p.getTextContent());
      }
      else if (p.getNodeName().equalsIgnoreCase("BaseWorkingDir")) {
        this.setBaseWorkingDir(p.getTextContent());
      }
      else if (p.getNodeName().equalsIgnoreCase("MakePath")) {
        this.setMakePath(p.getTextContent());
      }
      else if (p.getNodeName().equalsIgnoreCase("MakeArguments")) {
        this.setMakeArguments(p.getTextContent());
      }
      else if (p.getNodeName().equalsIgnoreCase("LoggingPath")) {
        this.setLoggingPath(p.getTextContent());
      }
      else if (p.getNodeName().equalsIgnoreCase("SplitByHiSeqSamplesheetColumn")) {
        this.setSplitBy(p.getTextContent());
      }
    }
  }

  public void setBclToFastQPath(String sp) {
    this.bclToFastQPath = sp;
  }

  public String getBclToFastQPath() {
    return this.bclToFastQPath;
  }

  public void setArguments(String arg) {
    this.arguments = arg;
  }

  public String getArguments() {
    return this.arguments;
  }

  public void setBaseRunDir(String baseRunDir) {
    this.baseRunDir = baseRunDir;
  }

  public String getBaseRunDir() {
    return this.baseRunDir;
  }
  
  public void setBaseOutputDir(String baseOutputDir) {
    this.baseOutputDir = baseOutputDir;
  }

  public String getBaseOutputDir() {
    return this.baseOutputDir;
  }

  public void setBaseWorkingDir(String baseWorkingDir) {
    this.baseWorkingDir = baseWorkingDir;
  }
  
  public String getBaseWorkingDir() {
    return this.baseWorkingDir;
  }
  
  public void setBaseMask(String baseMask) {
    this.baseMask = baseMask;
  }

  public String getBaseMask() {
    return this.baseMask;
  }    
    
  public File getSampleSheetPath(){
      if(baseRunDir != null || !baseRunDir.equals("")){
        return new File(getBaseRunDir() + "/SampleSheet.csv");
      }else{
        return new File("./SampleSheet.csv");
      }
  }
  
  public void setLoggingPath(String loggingPath){
    this.loggingPath = loggingPath;
  }

  public String getLoggingPath() {
    return this.loggingPath;
  }
  
  public void setMakePath(String makePath) {
    this.makePath = makePath;
  }

  public String getMakePath() {
    return this.makePath;
  }
  
  public void setMakeArguments(String makeArguments) {
    this.makeArguments = makeArguments;
  }

  public String getMakeArguments() {
    return this.makeArguments;
  }  
  
  public void setSplitBy(String splitBy){
      if(hiseqHeaderLookup.indexOf(splitBy) > -1){
        this.splitBy = splitBy;
      }else{
        this.splitBy = "SampleProject";
      }
  }
  
  public String getSplitBy(){
      if(this.splitBy == null || this.splitBy.equals("")){
          return "SampleProject";
      }else{
          return this.splitBy;
      }
  }
  
  private String getIdxBaseMask(int newBaseMaskLength){
      String pattern = "I(\\d+)";
      // Global run basemask is used as match string.
      Pattern r = Pattern.compile(pattern);
      Matcher m = r.matcher(this.getBaseMask());
      String replace = "I"+newBaseMaskLength;
      
      String res = m.replaceAll(replace);
      
      if(res.equals(this.getBaseMask()) || replace.length() == 1){
          LoggerWrapper.log.log(Level.INFO, "No basemask match was made for: {0} using pattern: {1}", new Object[]{this.getBaseMask(), pattern});
          return this.getBaseMask();
      }

      LoggerWrapper.log.log(Level.INFO, "Basemask was set to: "+ res);
      return res;
  }
  
  private int lookupHiSeqHeaderForMiSeq(String header){
      // Initialize
      // "FCID,Lane,SampleID,SampleReferenceGenome,Index,Descr,Control,Recipe,Operator,SampleProject"
      miseqLookup.put("FCID", -1);
      miseqLookup.put("Lane", -1);
      miseqLookup.put("SampleID", 1);
      miseqLookup.put("SampleReferenceGenome", -1);
      miseqLookup.put("Index", 5);
      miseqLookup.put("Descr", -1);
      miseqLookup.put("Control", -1);
      miseqLookup.put("Recipe", -1);
      miseqLookup.put("Operator", -1);
      miseqLookup.put("SampleProject", 6);

      LoggerWrapper.log.log(Level.INFO, "Translating set HiSeq header {0} to match MiSeq samplesheet and found it in column {1}.", new Object[]{header, miseqLookup.get(header)});
      
      if(miseqLookup.get(header) != null){
        return miseqLookup.get(header);
      }else{
        return -1;
      }
  }
  
  public ArrayList<File> getSamplesheetLocations(){
      return this.samplesheetLocations;
  }
  
  public boolean generateSampleSheets() throws FileNotFoundException, IOException{
    LoggerWrapper.log.log(Level.INFO, "Initializing Samplesheet processing for demultiplexing...");
    String[] nextLine;
    
    if(!this.getSampleSheetPath().exists()){
        throw new FileNotFoundException();
    }
    
    LoggerWrapper.log.log(Level.INFO, "Splitting samplesheets using the {0} column.", this.getSplitBy());
    LoggerWrapper.log.log(Level.INFO, "Global basemask: {0}", this.getBaseMask());
    // Read full CSV.
    BufferedReader br = new BufferedReader(new FileReader(this.getSampleSheetPath()));
    String line = null;
    while ((line = br.readLine()) != null) {
       nextLine = line.split(",", -1);
       fullSamplesheet.add(nextLine);
    }
  
    // Determine header first (type of SampleSheet)
    String firstLineWord = fullSamplesheet.get(0)[0];
    
    switch (firstLineWord) {
        // MiSeq format
        // Grab all contents and transform to Hiseq format.
        case "[Header]":
            transformMiSeq();
            break;
        // HiSeq format - Split by selected splitBy type.
        case "FCID":
            transformHiSeq();
            // Skip header line.
            break;
        default:
            LoggerWrapper.log.log(Level.FINE, "Unknown type - '{0}'", firstLineWord);
            return false;
    }
    
    return true;
  }
  
  public void transformMiSeq(){
      // Group by project (zero based)
      int lineIdx;
      boolean addToSet = false;
      boolean foundData = false;
      // MiSeq only has one lane. Add to 1.
      if(getSplitBy().equalsIgnoreCase("Lane")){
          // Just transform, everything is present in one lane.
          lineIdx = -1;
      }else{
          lineIdx = lookupHiSeqHeaderForMiSeq(getSplitBy());
      }
      
      ArrayList<String> ssContents;
      
      for(String[] line : fullSamplesheet){
          if(line[0].equals("[Data]")){
            foundData = true;
            continue;
          }
          if(foundData){
              addToSet = true;
          }
          
          if(addToSet){
            if(line.length < 8){
                LoggerWrapper.log.log(Level.FINER, "Line {0} does not have enough columns to be a valid samplesheet.", line);
            }else{
                Object splitValue;
                // Split by selected value.
                splitValue = lineIdx == -1 ? 1 : line[lineIdx];
                LoggerWrapper.log.log(Level.FINER, "Working with column value {0}",splitValue);
                
                if(!line[0].equals("Sample_ID")){
                    if(sampleSheets.get(splitValue) == null){
                        ssContents = new ArrayList<>();
                        // Add default HiSeq header for demultiplexable samplesheets.
                        ssContents.add(hiseqHeader);
                    }else{
                        ssContents = sampleSheets.get(splitValue);
                    }
                    
                    ssContents.add(",1,"+line[1].replaceAll("[ ,:\\.;!@#$%&*\\^\\(\\)]", "_")+",,"+line[5]+","+line[7]+",,,Metrix,"+line[6]);
                    baseMaskMap.put((String) splitValue, getIdxBaseMask(line[5].length()));
                    sampleSheets.put(splitValue, ssContents);
                }
            }
          }
      }
      
      printSamplesheets();      
  }
  
  public void transformHiSeq(){
      LoggerWrapper.log.log(Level.FINE, "Starting HiSeq Transformation and splitting into multiple samplesheets.");
      // Default to project based splitting (SampleProject)
      int lineIdx = hiseqHeaderLookup.indexOf(getSplitBy()); 
      
      ArrayList<String> ssContents;
      
      for(String[] line : fullSamplesheet){
          ssContents = null;
          if(line[0] != null && line[0].equals("FCID")){
              continue;
          }
          if(sampleSheets.get(line[lineIdx]) == null){
              ssContents = new ArrayList<>();
              // Add default HiSeq header.
              ssContents.add(hiseqHeader);
          }else{
              ssContents = sampleSheets.get(line[lineIdx]);
          }
          line[2] = line[2].replaceAll("[ :\\.;!@#$%&*\\^\\(\\)]", "_");
          if(line[lineIdx] != null || !line[lineIdx].equals("") && !line[0].equals("FCID")){
            // Add the line to the contents of the samplesheet.
            StringBuilder builder = new StringBuilder();
            int lineSize = line.length;
            int idx = 0;
            for(String s : line) {
                builder.append(s);
                // Processing the index sequence column
                if(idx == 4){
                    // Check and possibly store new basemask in baseMaskMap
                    if(s != null){
                        if(s.length() > 0){
                            LoggerWrapper.log.log(Level.FINE, "Comparing length of index sequence against previously set basemask for potential adjustments (Using length: {0})", s.length());
                            baseMaskMap.put(line[lineIdx], getIdxBaseMask(s.length()));
                        }else{
                            LoggerWrapper.log.log(Level.FINE, "Comparing length of index sequence against previously set basemask. Length is 0. Using original basemask.");
                            baseMaskMap.put(line[lineIdx], this.getBaseMask());
                        }
                    }else{
                        LoggerWrapper.log.log(Level.FINE, "Index sequence has not been set in the used samplesheet. Using original basemask.");
                        baseMaskMap.put(line[lineIdx], this.getBaseMask());
                    }
                }
                if(idx < lineSize-1 ){
                     builder.append(",");
                }
                idx++;
            }
            ssContents.add(builder.toString());
          }
          sampleSheets.put(line[lineIdx], ssContents);
      }

      printSamplesheets();
    }
  
  private void printSamplesheets(){
      File ssBaseDirectory = new File(getBaseOutputDir()+"/SampleSheets/");
      
      // Check if SampleSheets Directory exists if not, create it.
      if(!ssBaseDirectory.exists()){
          LoggerWrapper.log.log(Level.FINE, "SampleSheets folder does not exist, creating...");
          ssBaseDirectory.mkdirs();
      }
      
      ArrayList<String> ssContents;
      // Foreach samplesheet key generated new samplesheet with contents.
      for(Object key : sampleSheets.keySet()){
           LoggerWrapper.log.log(Level.FINE, "Creating samplesheet for: {0}", key);
           ssContents = sampleSheets.get(key);
           try{
                String filename = "";
                if(getSplitBy().equalsIgnoreCase("lane")){
                    filename = ssBaseDirectory.toString()+"/L"+key.toString()+".csv";
                }else{
                    filename = ssBaseDirectory.toString()+"/"+key.toString()+".csv";
                }
                File samplesheetOut = new File(filename);
                samplesheetLocations.add(samplesheetOut);
                ssBaseMaskMap.put(samplesheetOut.getAbsolutePath(), (String) baseMaskMap.get((String) key));
                
                PrintWriter out = new PrintWriter(new FileWriter(samplesheetOut));
                
                // Write each string in the array on a separate line
                StringBuilder builder = new StringBuilder();
                int lineSize = ssContents.size();
                int idx = 0;
                
                for(String s : ssContents) {
                    builder.append(s);
                    if(idx < lineSize-1){
                        builder.append("\n");
                    }
                    idx++;
                }
                
                if(Character.valueOf(builder.charAt(0)).equals(",")){
                    builder.deleteCharAt(0);
                }
                out.println(builder.toString());
               
                out.close();
           }catch(IOException ioe){
               LoggerWrapper.log.log(Level.WARNING, "Error while create and or writing to samplesheet. {0}", ioe);
           }
        }
      
  }
}

