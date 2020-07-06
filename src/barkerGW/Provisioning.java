package barkerGW;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.text.ParseException;
import java.text.SimpleDateFormat;  
import java.util.Date;  

public class Provisioning
{

    public SessionParameters doRegister(SessionParameters sp)
    {
        
        /*
            <Barker requestType="register">
                <register>
                    <name>[alphanum]</name>
                    <password>[SHA256 encryption]</password>
                    <email>[email] </email>
                    <birthDate>[optional date]</birthDate>
                </register>
            </Barker>
         */
        Log.logProvisioning("Provisioning::doRegister()");
        
        Document pDoc= null;
        DocumentBuilder pBuilder = null;

        String sName = "";
        String sPassword = "";
        String sEmail = "";
        String sBirthDate = "";
        
        
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();        
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse( new InputSource( new StringReader( sp.getRequestXML() )) );
            
            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();            
            XPathExpression pExp = null;
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/name");
            sName = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/email");
            sEmail = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/password");
            sPassword = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "birthDate");
            sBirthDate = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            
        }
        catch(Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }
//        catch (ParserConfigurationException e)
//        {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        } catch (SAXException e)
//        {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        } catch (IOException e)
//        {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        } catch (XPathExpressionException e)
//        {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
        Log.logProvisioning("User with name: " + sName + " ,email: " + sEmail + " ,password: " + sPassword 
                + " ,birthDate: " + sBirthDate);

        if(!sBirthDate.isEmpty())
        {
            try
            {
                Date pTmpDate= new SimpleDateFormat("dd/MM/yyyy").parse(sBirthDate);
                
            } catch (Exception e)
            {
                //OK, data is not correct, just don't insert it
                sBirthDate = "";
            } 
        }
        
        if(sEmail.isEmpty())
        {
            Log.logProvisioning("Provisioning::doRegister missing email");
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }
        
        if(!Tools.authenticateEmail(sEmail))
        {
            Log.logProvisioning("Provisioning::doRegister incorrect email format " + sEmail);
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            return sp;
        }
        if(sName.isEmpty())
        {
            Log.logProvisioning("Provisioning::doRegister missing name for email: " + sName);
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }
        if(sPassword.isEmpty())
        {
            Log.logProvisioning("Provisioning::doRegister missing password for email: " + sName);
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }
        //SHA-256 string length
        if(sPassword.length() != 64)
        {
            Log.logProvisioning("Provisioning::doRegister wrong password format for email: " + sName);
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            return sp;
        }
        

      String sConnection = Config.get("database-connection-string");
      Connection pDB = null;
      try
      {
          pDB = DriverManager.getConnection(sConnection, Config.get("database-username"), Config.get("database-password"));
          
          
          String sSql = "";
          
          //TODO - make checks whether the email address exists already, whether password and email are correct,
          //escape strings before inserting in database
          if(sBirthDate.isEmpty())
          { 
               sSql = "INSERT INTO barker.users (user_email, user_name, user_password) VALUES"
                       + "('" + sEmail + "','" + sName + "', '" + sPassword + "') RETURNING user_id";
          }
          else
          {
              sSql = "INSERT INTO barker.users (user_email, user_name, user_password,user_birth_date ) VALUES "
                      + "('" + sEmail + "','" + sName + "', '" + sPassword + "' , " + sBirthDate + ") RETURNING user_id";
          }
              
          Log.logProvisioning("SQL: " + sSql);

          PreparedStatement pStatement = pDB.prepareStatement(sSql);
          
          ResultSet pSet = pStatement.executeQuery();
          
          if(pSet.next())
          {
              System.out.println("User created with id: " + pSet.getString("user_id"));
              String sResponse = "<Barker requestType=\"register\"> \r\n"
                               + "    <statusCode>" + sp.getStatusCode() + "</statusCode>\n" 
                               + "    <statusText>" + sp.getStatusText() + "</statusText>\n"
                               + "    <userId>" + pSet.getString("user_id") + "</userId>\n"
                               + "</Barker>";
              
              sp.setResponseXML(sResponse);
          }
          else
          {
              sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
              return sp;
          }


          
      } catch (SQLException e)
      {
          sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
          Log.logProvisioning(e.getMessage());
          return sp;
      }
      finally
      {
          if(pDB != null)
          {
              try
              {
                  pDB.close();
              } catch (SQLException e)
              {
                  Log.logProvisioning(e.getMessage());
              }
          }
      }
        
        
        sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
        return sp;
    }

    public SessionParameters doResetPassword(SessionParameters sp)
    {

        /*
         * <Barker requestType="passwordReset">
                <passwordReset>
                    <email>haudrennb@gmail.com</email>
                    <password>8451ead0e04c63b538d89c8eb779567be38636863901248bfb5beed5dfadc7c1</password>
                    <code>A5DFS2</code>
                </passwordReset>
            </Barker>
         */
        Log.logProvisioning("Provisioning::doResetPassword()");
        
        Document pDoc= null;
        DocumentBuilder pBuilder = null;
        
        String sEmail = "";
        String sPassword = "";
        String sCode = "";
        
        
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();        
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse( new InputSource( new StringReader( sp.getRequestXML() )) );
            
            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();            
            XPathExpression pExp = null;
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/email");
            sEmail = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/password");
            sPassword = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/code");
            sCode = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
        }
        catch(Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }
        
        if(sEmail.isEmpty())
        {
            Log.logProvisioning("Provisioning::doResetPassword empty email provided");
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }
        
        if(!Tools.authenticateEmail(sEmail))
        {
            Log.logProvisioning("Provisioning::doResetPassword incorrect email: " + sEmail);
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            return sp;
        }
        
        //check if user exists
        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"), Config.get("database-password"));
            
            
            String sSql = "SELECT 1 FROM " + Config.get("table-users") + " WHERE user_email = '" + sEmail + "'";
            
            Log.logProvisioning("SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);
            
            ResultSet pSet = pStatement.executeQuery();
            
            //user doesn't exist
            if(!pSet.next())
            {
                Log.logProvisioning("Provisioning::doResetPassword cannot find user with email " + sEmail + " in DB");
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                return sp;
            }
        }
        catch(Exception e)
        {
            Log.logProvisioning("Provisioning::doResetPassword error authenticating whether user exists");
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
        }
        finally
        {
            if(pDB != null)
            {
                try
                {
                    pDB.close();
                } catch (SQLException e)
                {
                    Log.logProvisioning(e.getMessage());
                }
            }
        }
        
        //user needs a code sent to him - do just that
        if(sCode.isEmpty())
        {
            StringBuilder pCodeCreator = new StringBuilder();
            //create code
            String sCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            
            int nChar;
            for(int i=0;i<6;++i)
            {
                nChar = (int) (Math.random() * sCharacters.length());
                
                //6 digit code
                pCodeCreator.append(sCharacters.charAt(nChar));
            }
            sCode = pCodeCreator.toString();
            
            //insert code in DB
            try
            {
                if(pDB == null)pDB = DriverManager.getConnection(sConnection, Config.get("database-username"), Config.get("database-password"));
                
                
                String sSql = "INSERT INTO " + Config.get("table-password-reset") + "(user_email,password_reset_code) VALUES (?,?)";
                
                Log.logProvisioning("SQL: " + sSql);

                PreparedStatement pStatement = pDB.prepareStatement(sSql);
                
                pStatement.setString(1, sEmail);
                pStatement.setString(2,sCode);
                
                pStatement.executeQuery();
                

            }
            catch(Exception e)
            {
                Log.logProvisioning("Provisioning::doResetPassword error inserting code in DB for user " + sEmail);
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            }
            finally
            {
                if(pDB != null)
                {
                    try
                    {
                        pDB.close();
                    } catch (SQLException e)
                    {
                        Log.logProvisioning(e.getMessage());
                    }
                }
            }
            //send email
            EmailHelper.sendAuthenticationEmail(sEmail, sCode);
            
            sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
            return sp;
        }
        
        //user already has received code. Check code against DB for user
        
        try
        {
            if(pDB == null)pDB = DriverManager.getConnection(sConnection, Config.get("database-username"), Config.get("database-password"));
            
            
            String sSql = "SELECT 1 FROM " + Config.get("table-password-reset") + "WHERE user_email ='" + sEmail + "'"
                    + " AND password_reset_code ='" + sCode + "'"; 
            
            Log.logProvisioning("SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);
            

            ResultSet pSet = pStatement.executeQuery();
            
            //such request doesn't exist
            if(!pSet.next())
            {
                Log.logProvisioning("Provisioning::doResetPassword cannot find change password with that email and code for " + sEmail);
                sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
                return sp;
            }

            sSql = "UPDATE " + Config.get("table-users") + " SET user_password = '" + sPassword + "'"
                    + " WHERE user_email = '" + sEmail + "'";
            
            pStatement = pDB.prepareStatement(sSql);
            

            pSet = pStatement.executeQuery();

            sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
            return sp;
        }
        catch(Exception e)
        {
            Log.logProvisioning("Provisioning::doResetPassword error inserting code in DB for user " + sEmail);
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
        }
        finally
        {
            if(pDB != null)
            {
                try
                {
                    pDB.close();
                } catch (SQLException e)
                {
                    Log.logProvisioning(e.getMessage());
                }
            }
        }
        
        return sp;
    }

}
