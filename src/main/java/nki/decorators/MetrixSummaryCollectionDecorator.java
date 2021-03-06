package nki.decorators;

import java.util.ListIterator;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import nki.constants.Constants;
import nki.core.MetrixContainer;
import nki.objects.Summary;
import nki.objects.SummaryCollection;
import nki.util.LoggerWrapper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Decorator for a SummaryCollection object.
 *
 * @author Bernd van der Veen
 * @date 14/07/14
 * @since version
 */
public class MetrixSummaryCollectionDecorator {
  private SummaryCollection sc;
  private String expectedType = Constants.COM_TYPE_SIMPLE;
  
  public MetrixSummaryCollectionDecorator(SummaryCollection sc) {
    this.sc = sc;
  }

  public void setExpectedType(String expectedType){
      if(expectedType.equals(Constants.COM_TYPE_DETAIL)){
        this.expectedType = Constants.COM_TYPE_DETAIL;
      }else{
        this.expectedType = Constants.COM_TYPE_SIMPLE;
      }
  }
  
  public void initializeMetrix(){
      LoggerWrapper.log.log(Level.INFO, "Starting full initialization of Metrix... ");
      for(ListIterator<Summary> iter = sc.getSummaryCollection().listIterator(); iter.hasNext();){
          Summary sum = iter.next();
          LoggerWrapper.log.log(Level.INFO, "Processing {0}", sum.getRunId());
          MetrixContainer mc = new MetrixContainer(sum, false);
          mc = null; // Cleanup MC.
          LoggerWrapper.log.log(Level.INFO, "Done processing ...");
      }
      LoggerWrapper.log.log(Level.INFO, "Finished full initialization.");
  }
  
  public JSONObject toJSON(){
      JSONObject json = new JSONObject();
      JSONArray jsonCollection = new JSONArray();
      boolean isRemote = false;
      
      //for(Summary sum : sc.getSummaryCollection()){
      for(ListIterator<Summary> iter = sc.getSummaryCollection().listIterator(); iter.hasNext();){
          Summary sum = iter.next();
          LoggerWrapper.log.log(Level.INFO, "Processing {0}", sum.getRunId());
          JSONObject metrixJson = new JSONObject();
          
          if(this.expectedType.equals(Constants.COM_TYPE_SIMPLE)){
            JSONObject summary;
            if(sum.getState() == Constants.STATE_FINISHED || sum.getState() == Constants.STATE_HANG){
                summary = new MetrixSummaryDecorator(sum).toJSON();
            }else{
                MetrixContainer mc = new MetrixContainer(sum, isRemote);
                summary = new MetrixSummaryDecorator(mc.getSummary()).toJSON();
            }
            metrixJson.put("summary", summary);
          }else if(this.expectedType.equals(Constants.COM_TYPE_DETAIL)){
            Summary procSum = sum;
            
            if((sum.getState() == Constants.STATE_FINISHED || sum.getState() == Constants.STATE_HANG) && sum.hasIntensityDistRaw()){
                procSum = sum;
            }else{
                MetrixContainer mc = new MetrixContainer(sum, isRemote);
                procSum = mc.getSummary();
                mc = null;
            }

            JSONObject summary = new MetrixSummaryDecorator(procSum).toJSON();
            JSONObject tileMetrics = new MetrixTileMetricsDecorator(procSum.getClusterDensity(),
                                                                    procSum.getClusterDensityPF(),
                                                                    procSum.getPhasingMap(),
                                                                    procSum.getPrephasingMap(),
                                                                    procSum.getReads()
                                     ).toJSON();
            
            JSONObject qualityMetrics = new MetrixQualityMetricsDecorator(procSum).toJSON();
            JSONObject errorMetrics = new MetrixErrorMetricsDecorator(procSum.getErrorDist()).toJSON();
            JSONObject indexMetrics = new MetrixIndexMetricsDecorator(procSum.getSampleInfo()).toJSON();
            JSONObject extractionMetrics = new MetrixExtractionMetricsDecorator(procSum.getIntensityDistRaw(), procSum.getFWHMDist()).toJSON();
            JSONObject intensityMetrics = new MetrixIntensityMetricsDecorator(procSum.getIntensityDistAvg(),
                                                                              procSum.getIntensityDistCCAvg()
                                          ).toJSON();
          
            metrixJson.put("summary", summary);
            metrixJson.put("tileMetrics", tileMetrics);
            metrixJson.put("qualityMetrics", qualityMetrics);
            metrixJson.put("errorMetrics", errorMetrics);
            metrixJson.put("indexMetrics", indexMetrics);
            metrixJson.put("extractionMetrics", extractionMetrics);
            metrixJson.put("intensityMetrics", intensityMetrics);
            
            LoggerWrapper.log.log(Level.FINEST, "Emptying MetrixContainer");
            procSum = null;
            iter.remove();
            LoggerWrapper.log.log(Level.FINER, "Removed summary from list.");
          }else{
              metrixJson.put("Unknown request type. ", new JSONObject());
          }
          
          jsonCollection.add(metrixJson);
          
      }
      // Add statistics to json object.
      json.put("summaries", jsonCollection);
      sc = null; // Destroy the summarycollection.
      return json;
  }
  
  public String toCSV(){
      boolean isRemote = false;
      for(Summary sum : sc.getSummaryCollection()){
          JSONObject metrixJson = new JSONObject();
          MetrixContainer mc = new MetrixContainer(sum, isRemote);
          
      }
      return "";
  }
  
  public String toTab(){
      
      for(Summary sum : sc.getSummaryCollection()){
          
      }
      return "";
  }
  
  public Element toXML(){
    Document xmlDoc = null;
    Element root = null;
    try {
      // Build the XML document
      DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
      xmlDoc = docBuilder.newDocument();

      root = xmlDoc.createElement("SummaryCollection");
      xmlDoc.appendChild(root);
      
        for(ListIterator<Summary> iter = sc.getSummaryCollection().listIterator(); iter.hasNext();){
          Summary sum = iter.next();
          LoggerWrapper.log.log(Level.INFO, "Processing {0}", sum.getRunId());
          MetrixContainer mc = new MetrixContainer(sum, false);          
          
          Element sumXml = xmlDoc.createElement("Summary");
          sumXml.setAttribute("runId", sum.getRunId());
          if (this.expectedType.equals(Constants.COM_TYPE_SIMPLE)) {
            sumXml = new MetrixSummaryDecorator(mc.getSummary()).toXML(sumXml, xmlDoc);
            root.appendChild(sumXml);
          }
          else if (this.expectedType.equals(Constants.COM_TYPE_DETAIL)) {
            Element runinfo = xmlDoc.createElement("RunInfo");
            runinfo = new MetrixSummaryDecorator(mc.getSummary()).toXML(sumXml, xmlDoc);
            sumXml.appendChild(runinfo);
            
            Element tile = xmlDoc.createElement("tileMetrics");
            //tile = new MetrixTileMetricsDecorator(mc.getSummary()).toXML(sumXml, xmlDoc);
            sumXml.appendChild(tile);
            
            Element quality = xmlDoc.createElement("qualityMetrics");
            //quality = new MetrixQualityMetricsDecorator(mc.getSummary().getQScoreDist()).toXML();
            sumXml.appendChild(quality);
            
            Element error = xmlDoc.createElement("errorMetrics");
            //error = new MetrixErrorMetricsDecorator(mc.getSummary().getErrorDist()).toXML();
            sumXml.appendChild(error);
            
            Element index = xmlDoc.createElement("indexMetrics");
            index = new MetrixIndexMetricsDecorator(mc.getSummary().getSampleInfo()).toXML();
            sumXml.appendChild(index);
            
            Element extraction = xmlDoc.createElement("extractionMetrics");
            extraction = new MetrixExtractionMetricsDecorator(mc.getSummary().getIntensityDistRaw(), mc.getSummary().getFWHMDist()).toXML();
            sumXml.appendChild(extraction);
            
            Element intensity = xmlDoc.createElement("intensityMetrics");
            intensity = new MetrixIntensityMetricsDecorator(mc.getSummary().getIntensityDistAvg(), mc.getSummary().getIntensityDistCCAvg()).toXML();
            sumXml.appendChild(intensity);
            
            // Add to main summary element for each run.
            root.appendChild(sumXml);
          }
          else {
            sumXml = new MetrixSummaryDecorator(mc.getSummary()).toXML(sumXml, xmlDoc);
            root.appendChild(sumXml);
          }
          iter.remove();
          LoggerWrapper.log.log(Level.FINER, "Removed from list.");
      }
        return root; 
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
    return root;
  }
}
