package com.ecovues;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import oracle.imaging.BasicUserToken;
import oracle.imaging.Document;
import oracle.imaging.DocumentService;
import oracle.imaging.NameId;
import oracle.imaging.ServicesFactory;
import oracle.imaging.UserToken;

import org.apache.log4j.Logger;

import org.json.simple.JSONObject;

import org.sql2o.tools.NamedParameterStatement;

@Path("documents")
public class ImagingUpdateAPIService {
    public ImagingUpdateAPIService() {
    }
    private static final Logger logger = Logger.getLogger(ImagingUpdateAPIService.class);

    @GET
    @Path("/login")
    public String getData() {

        // Provide method implementation.
        // TODO

        return "Login Successful";
    }
    public ConfigData configs() throws Exception {
        String username="";
        String password="";
        String imagingUrl="";
        Connection ebsconn = JdbcHelper.getJDBCConnectionFromDataSource(true);
        
        ConfigData config = new ConfigData(username,password,imagingUrl);
        String query = "select * from ecoui_configs";//xxcv_img_configs


        NamedParameterStatement p = new NamedParameterStatement(ebsconn, query, true);

        ResultSet rs = null;

        try {
            rs = p.executeQuery();
           
            while(rs.next()){  
          
                
           if(rs.getString("configs_name").equalsIgnoreCase("ImagingUrl"))
            imagingUrl =  rs.getString("configs_value");
                               
                               if(rs.getString("configs_name").equalsIgnoreCase("ImagingUsername"))
                                  username= rs.getString("configs_value");
                               
                               if(rs.getString("configs_name").equalsIgnoreCase("ImagingPassword"))
                                  password = rs.getString("configs_value");
                
                 config = new ConfigData(username,password,imagingUrl);
            }
            p.close();
            rs.close();
        } catch (SQLException e) {
            
            
        } finally {
            
            ebsconn.close();
        }


        return config;
    }
    class ConfigData {
        String userName, password, imagingUrl;

        public ConfigData(String userName, String password, String imagingUrl) {
            this.userName = userName;
            this.password = password;
            this.imagingUrl = imagingUrl;
        }
    }
    @GET
    @Path("/updateEmployeeNumber")
    @Produces("application/json")
    public Response updateEmployeeNumber() throws Exception {
    logger.info("Entering updateEmployeeNumber method");
    ConfigData config = configs();
    
    String username   = config.userName;
    String password   = config.password;
    String imagingUrl = config.imagingUrl;
    
    logger.info("imagingUrl: "+imagingUrl);
    logger.info("username: "+username);
                  
    JSONObject output=new JSONObject();
    String documentUrl="";
    String status="Success";
    String errorMessage="";
        int docsupdated=0;
        int docsfailed=0;
        int nextID_from_seq=0;
                 
    try {
        
      UserToken credentials = new BasicUserToken(username, password);
      ServicesFactory servicesFactory =
          ServicesFactory.login(credentials, Locale.US, imagingUrl+"/imaging/ws");
    try{
        DocumentService documentService = servicesFactory.getDocumentService();
        NameId appNameId = null;
        List<NameId> appList = documentService.listTargetApplications(Document.Ability.CREATEDOCUMENT);
        
            Connection ebsconn = JdbcHelper.getJDBCConnectionFromDataSource(true);
            
            
        
                
          
            String docGUIId="";
            String docsdeleted="";
        
            
           
            
            try {
                String query="select * from employee_num_update_v where status='PENDING_UPDATE'";
                
                NamedParameterStatement p = new NamedParameterStatement(ebsconn, query, true);

                ResultSet rs = null;
                rs = p.executeQuery();
                
                //Get seq number
                String sql = "select EMPLOYEENUM_UPDATE_STATUS_SEQ.nextval from DUAL";
                PreparedStatement ps = ebsconn.prepareStatement(sql);
                ResultSet rseq = ps.executeQuery();
                
                if(rseq.next())
                     nextID_from_seq = rseq.getInt(1);
                
                //Update document
                     String docid="";
                while(rs.next()){
                    docid=rs.getString("did");
                    try{
                            List fieldValues = new ArrayList();
                             logger.info("Before updating docid: "+docid+","+new Date());
                             fieldValues.add(new Document.FieldValue("Employee Number", rs.getString("employee_num")));
                             docGUIId = documentService.updateDocument(docid, null, fieldValues,true);
                               logger.info("After: "+new Date());
                        logger.info("Update is successfull: "+docid);
                        docsupdated = docsupdated+1;
                                updateLogTable(docid,"Success","Updated Employee Number to "+rs.getString("employee_num"),nextID_from_seq);                                   
                    }
                    catch (Exception e) 
                    {
                                    
                                updateLogTable(docid,"Failed",e.getMessage(),nextID_from_seq);  
                                docsfailed = docsfailed+1;
                    }
                }
                p.close();
                rs.close();
            } catch (SQLException e) {
                logger.info("SQLException in View: "+e.getMessage());
                
            } finally {
                
                ebsconn.close();
            }
                                    
        }finally{
        
        if (servicesFactory != null){
        servicesFactory.logout();
        }
        }
      
    } catch (Exception e) {
      logger.error("Error while wciPostDocument doc: "+e.getMessage());
      status="Failed";
      errorMessage=e.getMessage();
    }
    output.put("Number of documents updated", String.valueOf(docsupdated));
    output.put("Number of documents failed", String.valueOf(docsfailed));
    output.put("Sequence Number", nextID_from_seq);
      //  output.put("LOG", sessionLog(nextID_from_seq));
    return Response.status(200).entity(output.toString()).header("Access-Control-Allow-Origin",
                                                                  "*").header("Access-Control-Allow-Methods",
                                                                              "GET, POST, DELETE, PUT").build();
    }
    
    public void updateLogTable(String docid, String status, String message, int nextID_from_seq){
        Connection ebsconn = JdbcHelper.getJDBCConnectionFromDataSource(true);
       String query="INSERT INTO EMPLOYEENUM_UPDATE_STATUS " + "VALUES ('"+docid+"','"+ status+"','"+message+"',"+nextID_from_seq+")";
       logger.info("Log insert query: "+query);

        // insert the data
        try {
            Statement statement = ebsconn.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            
            logger.info("Message while inserting into log table docid: "+docid+","+e.getMessage());
        }
        finally{
            try {
                ebsconn.close();
            } catch (SQLException e) {
            }
        }
    }
    
    @GET
            @Path("/getLog")
            @Produces("application/json")
            public Response getLog(
            @DefaultValue("status") @QueryParam("status") String status, @DefaultValue("1000") @QueryParam("limit") String limit, @DefaultValue("sequence_num") @QueryParam("sequence") String sequence
            , @DefaultValue("did") @QueryParam("did") String did) throws Exception {
            
            logger.info("Entering into a method apInvoiceList");
            Connection ebsconn = JdbcHelper.getJDBCConnectionFromDataSource(true);
            String outputStr = "";
            
            if(!status.equals("status")) {
                status = "'"+status+"'";
            }
                if(!did.equals("did")) {
                    did = "'"+did+"'";
                }
            String query = "select 'Did:'||did||', Status:'||status||', Message:'||message||', Sequence:'||sequence_num as log_data from EMPLOYEENUM_UPDATE_STATUS where status="+status+" and did="+did+" and sequence_num="+sequence+" and rownum<="+limit;
                logger.info("query: "+query);
           
                StringBuffer buffer = new StringBuffer();
            try {
               
                NamedParameterStatement p = new NamedParameterStatement(ebsconn, query, true);
                ResultSet rs = null;
                rs = p.executeQuery();
                while (rs.next()) {
                    buffer.append(rs.getString("log_data") + "\n");
                 //   System.out.println(rs.getString("log_data"));
                }
//                org.json.JSONObject jsonArray = new org.json.JSONObject();
//                jsonArray = JdbcHelper.convertToJSON(rs);
//                outputStr = jsonArray.toString();
                p.close();
                rs.close();
                
            } catch (Exception ex) {
                logger.info("Error"+ex.getMessage());
                outputStr = ex.getMessage();
            } finally {
                ebsconn.close();
            }

            logger.info("Exiting apInvoiceList");
            return     Response.status(200).entity(buffer.toString()).header("Access-Control-Allow-Origin",
                                                                  "*").header("Access-Control-Allow-Methods",
                                                                              "GET, POST, DELETE, PUT").build();
            }
    
      
}
