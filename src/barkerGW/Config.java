package barkerGW;

import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileNotFoundException;


public class Config
{
    private static Properties m_pProperties = null;
    
    public static boolean load(String sFileName)
    {
        if(m_pProperties == null)
        {
            m_pProperties = new Properties();
            
            try
            {
                FileInputStream pInput = new FileInputStream(sFileName);
                m_pProperties.load(pInput);
            } catch (Exception e)
            {
                Log.logRequestServer(e.getMessage());
                return false;
            } 
        }        
        return true;
    }
    
    public static String get(String sKey)
    {
        return get(sKey, "");
    }
    
    public static String get(String sKey,String sDefault)
    {
        return m_pProperties.getProperty(sKey, sDefault);
    }

}
