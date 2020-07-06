package barkerGW;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Log
{
    static private boolean m_bRequestServerLogExists = false;
    static private boolean m_bProvisioningLogExists = false;
  //TODO - load correct value for Config location from here
    static private final String sPath = System.getProperty("user.home") + "/Desktop/projectsOthers/BarkerGW/log";
    
    static private DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
    static private LocalDateTime now = null;
    
    static public void logRequestServer(String sMessage)
    {
        if(!m_bRequestServerLogExists)
        {
            createLog("requestServer.log");
        }
        
        try
        {
            FileWriter pWriter = new FileWriter(sPath + "requestServer.log");
            
             now = LocalDateTime.now();  
            
            sMessage = "[" + dtf.format(now) + "]" + sMessage;
            
            System.out.println(sMessage);
                
            pWriter.append(sMessage);
            pWriter.close();
            
        } catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    static public void logProvisioning(String sMessage)
    {
        if(!m_bProvisioningLogExists)
        {
            createLog("provisioning.log");
        }
        
        try
        {
            FileWriter pWriter = new FileWriter(sPath + "provisioning.log");
            
             now = LocalDateTime.now();  
            
            sMessage = "[" + dtf.format(now) + "]" + sMessage;
            
            System.out.println(sMessage);
                
            pWriter.append(sMessage);
            pWriter.close();
            
        } catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void createLog(String logName)
    {
        

        File myObj = new File(sPath + logName);
        try
        {
            if (myObj.createNewFile()) 
            {
              System.out.println("File created: " + myObj.getName());
            } 
            else 
            {
              System.out.println("File already exists.");
            }
            m_bRequestServerLogExists = true;
        } catch (IOException e)
        {
            e.printStackTrace();
        }        
    }

}
