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
import java.util.List;
import java.util.Vector;  

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
          
          sSql = "SELECT 1 FROM barker.users WHERE user_email = '" + sEmail + "'";
          
          PreparedStatement pStatement = pDB.prepareStatement(sSql);
          
          ResultSet pSet = pStatement.executeQuery();
          
          if(pSet.next())
          {
              sp.setRequestStatus(Constants.RequestServerStatus.USER_ALREADY_EXISTS);
              return sp;
          }
          
          
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

          
          pStatement = pDB.prepareStatement(sSql);
          pSet = pStatement.executeQuery();
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

    public SessionParameters doLogIn(SessionParameters sp)
    {
        
        /*
         * <Barker requestType="login">
                <login>
                    <email>haudrennb@gmail.com</email>
                    <password>8451ead0e04c63b538d89c8eb779567be38636863901248bfb5beed5dfadc7c1</password>
                </login>
            </Barker>
         */
        
        Log.logProvisioning("Provisioning::doLogIn()");
        
        Document pDoc= null;
        DocumentBuilder pBuilder = null;
        
        String sEmail = "";
        String sPassword = "";
        
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
        }
        catch(Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }
        
        if(sEmail.isEmpty() || sPassword.isEmpty())
        {
            Log.logProvisioning("Provisioning::doLogIn missing email or password");
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getRequestStatus() + " email or password");
        }
        
        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"), Config.get("database-password"));
            
            
            String sSql = "";
            
            //TODO - make checks whether the email address exists already, whether password and email are correct,
            //escape strings before inserting in database
            
            sSql = "SELECT 1 FROM barker.users WHERE user_email = '" + sEmail + "'";
            
            PreparedStatement pStatement = pDB.prepareStatement(sSql);
            
            ResultSet pSet = pStatement.executeQuery();
            
            if(!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with such email");
                Log.logProvisioning("Provisioning::doLogIn cannot find user with email" + sEmail);
                return sp;
            }
            
            sSql += " AND user_password = '" + sPassword + "'";
            
            pStatement = pDB.prepareStatement(sSql);
            
            pSet = pStatement.executeQuery();
            
            if(pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
                return sp;
            }
            //TODO - create counter in DB for bad Requests. In case 5 are made create field user_is_locked
            //where and email is send trough a queue to change password to unlock user
        }
        catch(Exception e)
        {
            Log.logProvisioning("Provisioning::doLogIn" + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            return sp;
        }
        
        
        return sp;
    }

    public SessionParameters doAddFriend(SessionParameters sp)
    {
        /*
         * <Barker requestType="addFriend">
                <addFriend>
                    <fromAddress>haudrennb@gmail.com</fromAddress>
                    <toAddress>barker@gmail.com</toAddress>
                </addFriend>
            </Barker>
         */
        
        Log.logProvisioning("Provisioning::doAddFriend()");
        
        Document pDoc= null;
        DocumentBuilder pBuilder = null;
        
        String sFromEmail = "";
        String sToEmail = "";
        
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();        
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse( new InputSource( new StringReader( sp.getRequestXML() )) );
            
            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();            
            XPathExpression pExp = null;
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/fromAddress");
            sFromEmail = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/toAddress");
            sToEmail = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
        }
        catch(Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }
        
        if(sFromEmail.isEmpty() || sToEmail.isEmpty())
        {
            Log.logProvisioning("Provisioning::doAddFriend missing fromEmail or toEmail");
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getRequestStatus() + " fromEmail or toEmail");
        }
        
        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"), Config.get("database-password"));
            
            
            String sSql = "";

            sSql = "SELECT 1 FROM barker.users WHERE user_email = '" + sFromEmail + "'";
            
            PreparedStatement pStatement = pDB.prepareStatement(sSql);
            
            ResultSet pSet = pStatement.executeQuery();
            
            if(!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with email " + sFromEmail);
                Log.logProvisioning("Provisioning::doAddFriend cannot find user with email" + sFromEmail);
                return sp;
            }
            
            sSql = "SELECT 1 FROM barker.users WHERE user_email = '" + sToEmail + "'";
            
            pStatement = pDB.prepareStatement(sSql);
            
            pSet = pStatement.executeQuery();
            
            if(!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with such email " + sToEmail);
                Log.logProvisioning("Provisioning::doAddFriend cannot find user with email" + sToEmail);
                return sp;
            }
            
            sSql = "SELECT 1 FROM barker.friend.requests WHERE (friend_from = '" + sFromEmail + "' AND friend_to = '" + sToEmail + "')"
                    + "OR (friend_from = '" + sToEmail + "' AND friend_to = '" + sFromEmail + "'";
            
            pStatement = pDB.prepareStatement(sSql);
            
            pSet = pStatement.executeQuery();
            
            if(pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.FRIEND_REQUEST_ALREADY_EXISTS);
                Log.logProvisioning("Friend request found for users" + sFromEmail + " and " + sToEmail);
                return sp;
            }
            
            sSql = "INSERT INTO barker.friendRequests(friend_from,friend_to,friend_is_accepted) "
                    + "VALUES ("
                    +"'" +sFromEmail + "'" + "', '" + sToEmail + "',0) RETURNING friend_id";
            
            
            pStatement = pDB.prepareStatement(sSql);
            
            pSet = pStatement.executeQuery();
            
            
            if(pSet.next())
            {
                Log.logProvisioning("Provisioning::doAddFriend Created new friendRequest for users " + sFromEmail + " " 
            + sToEmail + " with id " + pSet.getString("friend_id"));
                sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
                return sp;
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                Log.logProvisioning("Provisioning::doAddFriend couldn't receive friend id for friend request");
            }
        }
        catch(Exception e)
        {
            Log.logProvisioning("Provisioning::doAddFriend" + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            return sp;
        }
        
        
        return sp;
            
    
    }

    public SessionParameters doAcceptFriend(SessionParameters sp)
    {
        /*
         * <Barker requestType="acceptFriend">
                <acceptFriend>
                    <fromAddress>haudrennb@gmail.com</fromAddress>
                    <toAddress>barker@gmail.com</toAddress>
                    <isAccepted>yes</isAccepted>
                </acceptFriend>
            </Barker>
         */
        
        Log.logProvisioning("Provisioning::doAcceptFriend()");
        
        Document pDoc= null;
        DocumentBuilder pBuilder = null;
        
        String sFromEmail = "";
        String sToEmail = "";
        String sIsAccepted = "";
        
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();        
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse( new InputSource( new StringReader( sp.getRequestXML() )) );
            
            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();            
            XPathExpression pExp = null;
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/fromAddress");
            sFromEmail = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/toAddress");
            sToEmail = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/isAccepted");
            sIsAccepted = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
        }
        catch(Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }
        
        if(sFromEmail.isEmpty() || sToEmail.isEmpty())
        {
            Log.logProvisioning("Provisioning::doAcceptFriend missing fromEmail or toEmail");
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getRequestStatus() + " fromEmail or toEmail");
        }
        
        if(sIsAccepted.isEmpty() || (!sIsAccepted.equals("yes") && !sIsAccepted.equals("no")))
        {
            Log.logProvisioning("Provisioning::doAcceptFriend wrong isAccepted value or isAccepted missing");
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
            
            sSql = "SELECT 1 FROM barker.users WHERE user_email = '" + sFromEmail + "'";
            
            PreparedStatement pStatement = pDB.prepareStatement(sSql);
            
            ResultSet pSet = pStatement.executeQuery();
            
            if(!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with email " + sFromEmail);
                Log.logProvisioning("Provisioning::doAcceptFriend() cannot find user with email" + sFromEmail);
                return sp;
            }
            
            sSql = "SELECT 1 FROM barker.users WHERE user_email = '" + sToEmail + "'";
            
            pStatement = pDB.prepareStatement(sSql);
            
            pSet = pStatement.executeQuery();
            
            if(!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with such email " + sToEmail);
                Log.logProvisioning("Provisioning::doAcceptFriend cannot find user with email" + sToEmail);
                return sp;
            }
            
            sSql = "SELECT 1 FROM barker.friend.requests WHERE (friend_from = '" + sFromEmail + "' AND friend_to = '" + sToEmail + "')"
                    + "OR (friend_from = '" + sToEmail + "' AND friend_to = '" + sFromEmail + "'";
            
            pStatement = pDB.prepareStatement(sSql);
            
            pSet = pStatement.executeQuery();
            
            if(pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_FRIEND_REQUEST);
                Log.logProvisioning("Provisioning::doAcceptFriend Friend request not found for users" + sFromEmail + " and " + sToEmail);
                return sp;
            }

            if(sIsAccepted.equals("yes"))
            {
                sSql = "UPDATE barker.friendRequests SET friend_is_accepted = 1 WHERE friend_from = '" + sFromEmail + "'"
                        + " AND friend_to = '" + sToEmail + "'";
                
            }
            else if(sIsAccepted.equals("no"))
            {
                sSql = "DELETE FROM barker.friendRequests WHERE friend_from = '" + sFromEmail + "'"
                        + " AND friend_to = '" + sToEmail + "'";
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
                Log.logProvisioning("Provisioning::doAcceptFriend Internal error on isAccepted check");
                return sp;
            }
            
            
            pStatement = pDB.prepareStatement(sSql);
            
            pSet = pStatement.executeQuery();
            
            sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
            return sp;
            
        }
        catch(Exception e)
        {
            Log.logProvisioning("Provisioning::doAcceptFriend" + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            return sp;
        }
        
    }

    public SessionParameters doGetFriends(SessionParameters sp)
    {
        
        /*
        <Barker requestType="getFriends">
            <getFriends>
                <email>haudrennb@gmail.com</email>
            </getFriends>
        </Barker>
     */

        
        Log.logProvisioning("Provisioning::doGetFriends()");
        
        Document pDoc= null;
        DocumentBuilder pBuilder = null;
        
        String sEmail = "";
        String sOnlyAccepted = "";
        
        String sResponse = "\"<Barker requestType=\"getFriends\"> \r\n\"";
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
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/onlyAccepted");
            sOnlyAccepted = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
        }
        catch(Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }
        
        if(sEmail.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getStatusText() + " email");
            Log.logProvisioning("Provisioning::doGetFriends() Empty email for request");
        }
        
        if(sOnlyAccepted.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getStatusText() + " onlyAccepted");
            Log.logProvisioning("Provisioning::doGetFriends() Empty onlyAccepted for user " + sEmail);
            return sp;
        }
        if(!sOnlyAccepted.equals("yes") && !sOnlyAccepted.equals("no"))
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            Log.logProvisioning("Provisioning::doGetFriends() wrong value for onlyAccepted for user" + sOnlyAccepted);
            return sp;
        }
        
        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"), Config.get("database-password"));
            
            
            String sSql = "";
            
            sSql = "SELECT 1 FROM barker.users WHERE user_email = '" + sEmail + "'";
            
            PreparedStatement pStatement = pDB.prepareStatement(sSql);
            
            ResultSet pSet = pStatement.executeQuery();
            
            if(!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with email " + sEmail);
                Log.logProvisioning("Provisioning::doGetFriends() cannot find user with email" + sEmail);
                return sp;
            }
            
            sSql = "SELECT * FROM barker.friendRequests WHERE friend_from = '" + sEmail + "' AND friend_is_accepted = 1";
            
            pStatement = pDB.prepareStatement(sSql);
            pSet = pStatement.executeQuery();
            
            String sFriendUsername = "";
            String sFriendName = "";
            
            while(pSet.next())
            {
                sFriendUsername = pSet.getString("friend_to_username");
                sFriendName = pSet.getString("friend_to_name");
                
                sResponse += "  <friend> \n"
                            +"      <username>" + sFriendUsername + "</username> \n"
                            +"      <name>" + sFriendName + "</name> \n"
                            +"      <isAccepted>yes</isAccepted>" 
                            +"  </friend> \n";
            }
            
            if(sOnlyAccepted.equals("yes"))
            {
                sSql = "SELECT * FROM barker.friendRequest WHERE friend_to = '" + sEmail + "' AND friend_is_accepted = 1";
                pStatement = pDB.prepareStatement(sSql);
                pSet = pStatement.executeQuery();
                
                while(pSet.next())
                {
                    sFriendUsername = pSet.getString("friend_to_username");
                    sFriendName = pSet.getString("friend_to_name");
                    
                    sResponse += "  <friend> \n"
                            +"      <username>" + sFriendUsername + "</username> \n"
                            +"      <name>" + sFriendName + "</name> \n"
                            +"      <isAccepted>yes</isAccepted>" 
                            +"  </friend> \n";
                }

            }
            else
            {
                String sFriendIsAccepted = "";
                sSql = "SELECT * FROM barker.friendRequest WHERE friend_to = '" + sEmail + "'";
                pStatement = pDB.prepareStatement(sSql);
                pSet = pStatement.executeQuery();
                
                while(pSet.next())
                {
                    sFriendUsername = pSet.getString("friend_to_username");
                    sFriendName = pSet.getString("friend_to_name");
                    sFriendIsAccepted = pSet.getString("friend_is_accepted");
                    
                    sResponse += "  <friend> \n"
                            +"      <username>" + sFriendUsername + "</username> \n"
                            +"      <name>" + sFriendName + "</name> \n";
                    
                    if(sFriendIsAccepted.equals("1")) sResponse += "      <isAccepted>yes</isAccepted>";
                            
                    else sResponse += "      <isAccepted>no</isAccepted>";
                    
                            
                    sResponse +="  </friend> \n";
                }
            }
        }
        catch(Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning("Provisioning::doGetFriends Error: " + e.getMessage());
            return sp;
        }
        
        
        /*
        <Barker requestType="getFriends">
            <getFriends>
                <friend>
                    <username></username>
                    <name></name>
                    <isAccepted>yes</isAccepted>
                </friend>
            </getFriends>
        </Barker>
     */
        
        sResponse += "\"</Barker>";
        //TODO ADD 200 OK
        sp.setStatusCode(Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS));
        sp.setResponseXML(sResponse);
        return sp;
    }

    public SessionParameters doCreateWalk(SessionParameters sp)
    {
        /*
        <Barker requestType="findWalk">
            <findWalk>
                <fromAddress>haudrennb@gmail.com</fromAddress>
                <fromNames>Kaloyan Nikolov</fromNames>
                <fromUsername>HaudreN</fromUsername>
                <location>Razsadnika</location>
            </findWalk>
        </Barker>
     */

        Log.logProvisioning("Provisioning::doCreateWalk()");
        
        Document pDoc= null;
        DocumentBuilder pBuilder = null;
        
        String sEmail = "";
        String sNames = "";
        String sLocation = "";
        String sUsername = "";
        
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();        
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse( new InputSource( new StringReader( sp.getRequestXML() )) );
            
            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();            
            XPathExpression pExp = null;
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/fromAddress");
            sEmail = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/fromNames");
            sNames = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/fromUsername");
            sUsername = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/location");
            sLocation = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
        }
        catch(Exception e)
        {
            Log.logProvisioning("Provisioning::doCreateWalk" + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }
        
        if(sEmail.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getStatusText() + " email");
            Log.logProvisioning("Provisioning::doCreateWalk Missing parameter email");
            return sp;
        }
        if(sNames.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getStatusText() + " names");
            Log.logProvisioning("Provisioning::doCreateWalk Missing parameter names for user " + sEmail);
            return sp;
        }
        if(sUsername.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getStatusText() + " username");
            Log.logProvisioning("Provisioning::doCreateWalk Missing parameter names for user " + sEmail);
            return sp;
        }
        if(sLocation.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getStatusText() + " location");
            Log.logProvisioning("Provisioning::doCreateWalk Missing parameter location for user " + sEmail);
            return sp;
        }
        
        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"), Config.get("database-password"));
            
            
            String sSql = "";
            
            sSql = "INSERT INTO barker.walks (walk_user_email,walk_user_name,walk_user_username,walk_user_location, walk_created_dt)"
                    + "VALUES (?,?,?,?, now()) RETURNING walk_id";
            
            PreparedStatement pStatement = pDB.prepareStatement(sSql);
            
            pStatement.setString(1,sEmail);
            pStatement.setString(2, sNames);
            pStatement.setString(3,sUsername);
            pStatement.setString(4, sLocation);
            
            ResultSet pSet = pStatement.executeQuery();
            String sWalkId = "";
            if(!pSet.next())
            {
                sWalkId = pSet.getString("walk_id");
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                Log.logProvisioning("Provisioning::doCreateWalk couldn't retreive walk id for user " + sEmail);
                return sp;
            }
            
            //TODO create XML including walk_id
            
            sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
            return sp;
            
        }
        catch(Exception e)
        {
            Log.logProvisioning("Provisioning::doCreateWalk Error: " + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            return sp;
            }
        
        
    }

    public SessionParameters doGetWalks(SessionParameters sp)
    {
        
        /*
        <Barker requestType="getWalks">
            <getWalks>
                <email>haudrennb@gmail.com</email>
            </getWalks>
        </Barker>
     */
        Log.logProvisioning("Provisioning::doGetWalks()");
        
        Document pDoc= null;
        DocumentBuilder pBuilder = null;
        
        String sEmail = "";

        String sResponse ="<Barker requestType=\"getWalks\">";
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

        }
        catch(Exception e)
        {
            Log.logProvisioning("Provisioning::doGetWalks" + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }
        
        if(sEmail.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            Log.logProvisioning("Provisioning::doGetWalks() empty email");
            return sp;
        }
        
        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"), Config.get("database-password"));
            
            
            String sSql = "SELECT friend_to FROM barker.friendRequests WHERE friend_from = '" + sEmail + "' "
                    + "AND friend_is_accepted = 1";
            
            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            List<String> vsFriendsEmails = new Vector<String>();
            
            ResultSet pSet = pStatement.executeQuery();
            while(pSet.next())
            {
                vsFriendsEmails.add(pSet.getString("friend_to"));
            }
            
            sSql = "SELECT friend_from FROM barker.friendRequests WHERE friend_to = '" + sEmail + "' "
                    + "AND friend_is_accepted = 1";
            
            pStatement = pDB.prepareStatement(sSql);
            
            pSet = pStatement.executeQuery();
            
            while(pSet.next())
            {
                vsFriendsEmails.add(pSet.getString("friend_from"));
            }
            pSet.close();

            for(int i=0;i<vsFriendsEmails.size();++i)
            {
                sSql = "SELECT * FROM barker.walks WHERE walk_user_email = '" + vsFriendsEmails.get(i) + "'";
                
                pDB.prepareStatement(sSql);
                
                pSet = pStatement.executeQuery();
                
                while(pSet.next())
                {
                    sResponse += "  <walk> \n"
                                +"      <id>" + pSet.getString("walk_id") + "</id>"
                                +"      <username>" + pSet.getString("walk_user_username") + "</username>\n"
                                +"      <name>" + pSet.getString("walk_user_name") + "</name> \n"
                                +"      <location>" + pSet.getString("walk_user_location") + "</location> \n"
                                +"      <time>" + pSet.getString("walk_created_dt") + "</time>"
                                +"  </walk>";
                }
            }
            
            sResponse += "</Barker>";
            
            //TODO ADD 200OK
            sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
            sp.setResponseXML(sResponse);
            return sp;
            
        }
        catch(Exception e)
        {
            Log.logProvisioning("Provisioning::doCreateWalk Error: " + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            return sp;
            }
        
        
    }
    
}
