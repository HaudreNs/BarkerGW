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
        <username>HaudreN</username>
        <name>Kaloyan Nikolov</name>
        <password>936a185caaa266bb9cbe981e9e05cb78cd732b0b3280eb944412bb6f8f8f07af</password>
        <email>haudrennb@gmail.com </email>
    </register>
</Barker>
         */
        Log.logProvisioning("Provisioning::doRegister()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sName = "";
        String sPassword = "";
        String sEmail = "";
        String sUsername = "";

        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/username");
            sUsername = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/name");
            sName = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/email");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/password");
            sPassword = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

        }
        catch (Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }

        Log.logProvisioning("User with name: " + sName + " ,email: " + sEmail + " ,password: " + sPassword
                + " ,username: " + sUsername);

        if (sEmail.isEmpty())
        {
            Log.logProvisioning("Provisioning::doRegister missing email");
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }

        if (sUsername.isEmpty())
        {
            Log.logProvisioning("Provisioning::doRegister missing username");
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }

        if (!Tools.authenticateEmail(sEmail))
        {
            Log.logProvisioning("Provisioning::doRegister incorrect email format " + sEmail);
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            return sp;
        }
        if (sName.isEmpty())
        {
            Log.logProvisioning("Provisioning::doRegister missing name for email: " + sName);
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }
        if (sPassword.isEmpty())
        {
            Log.logProvisioning("Provisioning::doRegister missing password for email: " + sName);
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }
        // SHA-256 string length
        if (sPassword.length() != 64)
        {
            Log.logProvisioning("Provisioning::doRegister wrong password format for email: " + sName);
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            return sp;
        }
        
        sUsername = Tools.escapeString(sUsername);
        sEmail = Tools.escapeString(sEmail);
        sName = Tools.escapeString(sName);
        sPassword = Tools.escapeString(sPassword);


        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "";

            // TODO - make checks whether the email address exists already, whether password
            // and email are correct,
            // escape strings before inserting in database

            sSql = "SELECT 1 FROM barker.users WHERE user_email = '" + sEmail.trim() + "'";

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.USER_EMAIL_ALREADY_EXISTS);
                return sp;
            }
            
            sSql = "SELECT 1 FROM barker.users WHERE user_username = '" + sUsername.trim() + "'";

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.USER_ALREADY_EXISTS);
                return sp;
            }

            sSql = "INSERT INTO barker.users (user_email, user_name, user_password, user_username) VALUES" + "('"
                    + sEmail.trim() + "','" + sName.trim() + "', '" + sPassword.trim() + "' , '" + sUsername
                    + "') RETURNING user_id";

            Log.logProvisioning("SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);
            pSet = pStatement.executeQuery();
            if (pSet.next())
            {
                System.out.println("User created with id: " + pSet.getString("user_id"));
                String sResponse = "<Barker requestType=\"register\"> \r\n" + "    <statusCode>" + sp.getStatusCode()
                        + "</statusCode>\n" + "    <statusText>" + sp.getStatusText() + "</statusText>\n"
                        + "    <userId>" + pSet.getString("user_id") + "</userId>\n" + "</Barker> \n";

                sp.setResponseXML(sResponse);
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                return sp;
            }

        }
        catch (SQLException e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning(e.getMessage());
            return sp;
        }
        finally
        {
            if (pDB != null)
            {
                try
                {
                    pDB.close();
                }
                catch (SQLException e)
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
<Barker requestType="passwordReset">
    <passwordReset>
        <email>haudrennb@gmail.com</email>
        <password>d55e6a8c0f30870be24c37098273afb1dc877a5544f049f86ef0cdd7201b937c</password>
        <code>A5DFS2</code>
    </passwordReset>
</Barker>
         */
        Log.logProvisioning("Provisioning::doResetPassword()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sEmail = "";
        String sPassword = "";
        String sCode = "";

        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/email");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/password");
            sPassword = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/code");
            sCode = (String) pExp.evaluate(pDoc, XPathConstants.STRING);
        }
        catch (Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }

        if (sEmail.isEmpty())
        {
            Log.logProvisioning("Provisioning::doResetPassword empty email provided");
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }

        if (!Tools.authenticateEmail(sEmail))
        {
            Log.logProvisioning("Provisioning::doResetPassword incorrect email: " + sEmail);
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            return sp;
        }
        
        
        sEmail = Tools.escapeString(sEmail);
        sPassword = Tools.escapeString(sPassword);
        sCode = Tools.escapeString(sCode);

         
        // check if user exists
        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "SELECT 1 FROM " + Config.get("table-users") + " WHERE user_email = '" + sEmail.trim() + "'";

            Log.logProvisioning("SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            // user doesn't exist
            if (!pSet.next())
            {
                Log.logProvisioning("Provisioning::doResetPassword cannot find user with email " + sEmail + " in DB");
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                return sp;
            }
        }
        catch (Exception e)
        {
            Log.logProvisioning("Provisioning::doResetPassword error authenticating whether user exists");
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            if (pDB != null)
            {
                try
                {
                    pDB.close();
                }
                catch (SQLException e1)
                {
                    Log.logProvisioning(e1.getMessage());
                }
            }
            return sp;
        }

        // user needs a code sent to him - do just that
        if (sCode.isEmpty())
        {
            StringBuilder pCodeCreator = new StringBuilder();
            // create code
            String sCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

            int nChar;
            for (int i = 0; i < 6; ++i)
            {
                nChar = (int) (Math.random() * sCharacters.length());

                // 6 digit code
                pCodeCreator.append(sCharacters.charAt(nChar));
            }
            sCode = pCodeCreator.toString();

            // insert code in DB
            try
            {

                String sSql = "INSERT INTO " + Config.get("table-password-reset")
                        + "(user_email,password_reset_code, password_reset_is_used)" + " VALUES ('" + sEmail.trim()
                        + "','" + sCode + "', false) RETURNING 1 as created";

                Log.logProvisioning("SQL: " + sSql);

                PreparedStatement pStatement = pDB.prepareStatement(sSql);

                ResultSet pSet = pStatement.executeQuery();

                if (!pSet.next())
                {
                    Log.logProvisioning(
                            "Provisioning::doResetPassword error inserting code in DB for user " + sEmail + "\n ");
                    sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
                    return sp;
                }

            }
            catch (Exception e)
            {
                Log.logProvisioning("Provisioning::doResetPassword error inserting code in DB for user " + sEmail
                        + "\n " + e.getMessage());
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                return sp;
            }
            finally
            {
                if (pDB != null)
                {
                    try
                    {
                        pDB.close();
                    }
                    catch (SQLException e)
                    {
                        Log.logProvisioning(e.getMessage());
                    }
                }
            }
            // send email
            EmailHelper.sendAuthenticationEmail(sEmail, sCode);

            sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
            return sp;
        }

        // user already has received code. Check code against DB for user

        if (sPassword.length() != 64)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_PASSWORD);
            Log.logProvisioning("Provisioning::doResetPassword password is not SHA-256 encrypted");
            return sp;
        }

        try
        {

            String sSql = "UPDATE " + Config.get("table-password-reset")
                    + " SET password_reset_is_used = true WHERE user_email ='" + sEmail.trim() + "'"
                    + " AND password_reset_code ='" + sCode.trim() + "' and password_reset_is_used = false";

            Log.logProvisioning("Provisioning::doResetPassword SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            int nResult = pStatement.executeUpdate();

            // such request doesn't exist
            if (nResult < 1)
            {
                Log.logProvisioning(
                        "Provisioning::doResetPassword cannot find change password with that email and code or is already used for "
                                + sEmail);
                sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
                return sp;
            }

            sSql = "UPDATE " + Config.get("table-users") + " SET user_password = '" + sPassword.trim() + "'"
                    + " WHERE user_email = '" + sEmail.trim() + "'";
            Log.logProvisioning("Provisioning::doResetPassword SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            nResult = pStatement.executeUpdate();

            if (nResult == 0)
            {
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                Log.logProvisioning("Couldn't update password for user: " + sEmail);
                return sp;
            }

            sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
            return sp;
        }
        catch (Exception e)
        {
            Log.logProvisioning("Provisioning::doResetPassword error inserting code in DB for user " + sEmail + "\n "
                    + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
        }
        finally
        {
            if (pDB != null)
            {
                try
                {
                    pDB.close();
                }
                catch (SQLException e)
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
          <Barker requestType="login"> <login> <email>haudrennb@gmail.com</email>
          <password>8451ead0e04c63b538d89c8eb779567be38636863901248bfb5beed5dfadc7c1</
          password> </login> </Barker>
         */

        Log.logProvisioning("Provisioning::doLogIn()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sEmail = "";
        String sPassword = "";

        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/email");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/password");
            sPassword = (String) pExp.evaluate(pDoc, XPathConstants.STRING);
        }
        catch (Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }

        if (sEmail.isEmpty() || sPassword.isEmpty())
        {
            Log.logProvisioning("Provisioning::doLogIn missing email or password");
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getRequestStatus() + " email or password");
        }

        sEmail = Tools.escapeString(sEmail);
        sPassword = Tools.escapeString(sPassword);

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "";

            sSql = "SELECT * FROM barker.users WHERE user_email = '" + sEmail.trim() + "' AND user_password = '"
                    + sPassword.trim() + "'";

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                /*
                 * Response: <Barker requestType="login"> <statusCode>200</statusCode>
                 * <statusText>OK</statusText> <id>1</id> <username>HaudreN</username>
                 * <name>Kaloyan Nikolov</name> <email>haudrennb@gmail.com </email> </Barker>
                 */
                String sResponse = "<Barker requestType=\"login\">\n" 
                        + "           <statusCode>"+ Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                        + "           <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS) + "</statusText>\n"
                        + "           <id>" + pSet.getString("user_id") + "</id>\n" 
                        + "           <username>"+ pSet.getString("user_username") + "</username>\n" 
                        + "           <name>" + pSet.getString("user_name") + "</name>\n" 
                        + "           <email>" + pSet.getString("user_email") + "</email>\n" 
                        + "</Barker> \n";

                sp.setResponseXML(sResponse);

                return sp;

            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with such email or password");
                Log.logProvisioning("Provisioning::doLogIn cannot find user with email" + sEmail);
                return sp;
            }

            // TODO - create counter in DB for bad Requests. In case 5 are made create field
            // user_is_locked
            // where and email is send trough a queue to change password to unlock user
        }
        catch (Exception e)
        {
            Log.logProvisioning("Provisioning::doLogIn" + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            return sp;
        }

    }

    public SessionParameters doAddFriend(SessionParameters sp)
    {
        /*
<Barker requestType="addFriend">
    <addFriend>
        <fromAddress>haudrennb@gmail.com</fromAddress>
        <toAddress>appbarkeroriginal@gmail.com</toAddress>
    </addFriend>
</Barker>
         */

        Log.logProvisioning("Provisioning::doAddFriend()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sFromEmail = "";
        String sToEmail = "";

        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/fromAddress");
            sFromEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/toAddress");
            sToEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);
        }
        catch (Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }

        if (sFromEmail.isEmpty() || sToEmail.isEmpty())
        {
            Log.logProvisioning("Provisioning::doAddFriend missing fromEmail or toEmail");
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getRequestStatus() + " fromEmail or toEmail");
        }
        
        sFromEmail = Tools.escapeString(sFromEmail);
        sToEmail = Tools.escapeString(sToEmail);

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "";

            String sFromNames = "";
            String sFromUsername = "";
            sSql = "SELECT user_name,user_username FROM " + Config.get("table-users") + " WHERE user_email = '"
                    + sFromEmail + "'";

            Log.logProvisioning("Provisioning::doAddFriend SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                sFromNames = pSet.getString("user_name");
                sFromUsername = pSet.getString("user_username");
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with such email " + sToEmail);
                Log.logProvisioning("Provisioning::doAddFriend cannot find user with email" + sToEmail);
                return sp;
            }
            String sToNames = "";
            String sToUsername = "";
            sSql = "SELECT user_name,user_username FROM " + Config.get("table-users") + " WHERE user_email = '"
                    + sToEmail + "'";
            Log.logProvisioning("Provisioning::doAddFriend SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                sToNames = pSet.getString("user_name");
                sToUsername = pSet.getString("user_username");
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with such email " + sToEmail);
                Log.logProvisioning("Provisioning::doAddFriend cannot find user with email" + sToEmail);
                return sp;
            }

            sSql = "SELECT 1 FROM " + Config.get("table-friend-requests") + " WHERE (friend_from = '" + sFromEmail
                    + "' AND friend_to = '" + sToEmail + "')" + "OR (friend_from = '" + sToEmail + "' AND friend_to = '"
                    + sFromEmail + "')";

            Log.logProvisioning("Provisioning::doAddFriend SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.FRIEND_REQUEST_ALREADY_EXISTS);
                Log.logProvisioning("Friend request found for users" + sFromEmail + " and " + sToEmail);
                return sp;
            }

            sSql = "INSERT INTO " + Config.get("table-friend-requests")
                    + "(friend_from,friend_to,friend_is_accepted,friend_from_name,friend_from_username"
                    + ",friend_to_name,friend_to_username) " + "VALUES (" + "'" + sFromEmail + "'" + ", '" + sToEmail
                    + "',0, '" + sFromNames + "','" + sFromUsername + "','" + sToNames + "','" + sToUsername
                    + "') RETURNING friend_id";

            Log.logProvisioning("Provisioning::doAddFriend SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (pSet.next())
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
        catch (Exception e)
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
<Barker requestType="acceptFriend">
    <acceptFriend>
        <fromAddress>haudrennb@gmail.com</fromAddress>
        <toAddress>knnikolov@abv.bg</toAddress>
        <isAccepted>yes</isAccepted>
    </acceptFriend>
</Barker>
         */

        Log.logProvisioning("Provisioning::doAcceptFriend()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sFromEmail = "";
        String sToEmail = "";
        String sIsAccepted = "";

        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/fromAddress");
            sFromEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/toAddress");
            sToEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/isAccepted");
            sIsAccepted = (String) pExp.evaluate(pDoc, XPathConstants.STRING);
        }
        catch (Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }

        if (sFromEmail.isEmpty() || sToEmail.isEmpty())
        {
            Log.logProvisioning("Provisioning::doAcceptFriend missing fromEmail or toEmail");
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getRequestStatus() + " fromEmail or toEmail");
        }
        
        sIsAccepted = sIsAccepted.trim();
        sFromEmail = Tools.escapeString(sFromEmail);
        sToEmail = Tools.escapeString(sToEmail);

        if (sIsAccepted.isEmpty() || (!sIsAccepted.equals("yes") && !sIsAccepted.equals("no")))
        {
            Log.logProvisioning("Provisioning::doAcceptFriend wrong isAccepted value or isAccepted missing");
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            return sp;
        }

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "";

            // TODO - make checks whether the email address exists already, whether password
            // and email are correct,
            // escape strings before inserting in database

            sSql = "SELECT 1 FROM " + Config.get("table-users") + " WHERE user_email = '" + sFromEmail + "'";
            Log.logProvisioning("Provisioning::doAcceptFriend SQL: " + sSql);
            Log.logProvisioning("Provisioning::doAcceptFriend SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            if (!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with email " + sFromEmail);
                Log.logProvisioning("Provisioning::doAcceptFriend() cannot find user with email" + sFromEmail);
                return sp;
            }

            sSql = "SELECT 1 FROM " + Config.get("table-users") + " WHERE user_email = '" + sToEmail + "'";
            Log.logProvisioning("Provisioning::doAcceptFriend SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with such email " + sToEmail);
                Log.logProvisioning("Provisioning::doAcceptFriend cannot find user with email" + sToEmail);
                return sp;
            }

            sSql = "SELECT friend_id FROM " + Config.get("table-friend-requests") + " WHERE (friend_from = '"
                    + sFromEmail + "' AND friend_to = '" + sToEmail + "')" + " OR (friend_from = '" + sToEmail
                    + "' AND friend_to = '" + sFromEmail + "')";
            Log.logProvisioning("Provisioning::doAcceptFriend SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();
            String sId = "";
            if (pSet.next())
            {
                sId = pSet.getString("friend_id");
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_FRIEND_REQUEST);
                Log.logProvisioning("Provisioning::doAcceptFriend Friend request not found for users" + sFromEmail
                        + " and " + sToEmail);
                return sp;
            }

            if (sIsAccepted.equals("yes"))
            {
                sSql = "UPDATE " + Config.get("table-friend-requests")
                        + " SET friend_is_accepted = 1 WHERE friend_id = " + sId;

            }
            else if (sIsAccepted.equals("no"))
            {
                sSql = "DELETE FROM " + Config.get("table-friend-requests") + " WHERE friend_id = " + sId;

            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
                Log.logProvisioning("Provisioning::doAcceptFriend Internal error on isAccepted check");
                return sp;
            }

            Log.logProvisioning("Provisioning::doAcceptFriend SQL: " + sSql);
            pStatement = pDB.prepareStatement(sSql);

            int nResult = pStatement.executeUpdate();

            if (nResult == 0)
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_FRIEND_REQUEST);
                Log.logProvisioning("Provisioning::doAcceptFriend cannot accept or delete request");
                return sp;
            }

            Log.logProvisioning("Provisioning::doAcceptFriend SQL: " + sSql);

            sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
            return sp;

        }
        catch (Exception e)
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
        <onlyAccepted>yes</onlyAccepted>
    </getFriends>
</Barker>
         */

        Log.logProvisioning("Provisioning::doGetFriends()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sEmail = "";
        String sOnlyAccepted = "";

        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/email");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/onlyAccepted");
            sOnlyAccepted = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

        }
        catch (Exception e)
        {
            Log.logProvisioning(e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }

        if (sEmail.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getStatusText() + " email");
            Log.logProvisioning("Provisioning::doGetFriends() Empty email for request");
        }

        sEmail = Tools.escapeString(sEmail);
        
        if (sOnlyAccepted.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getStatusText() + " onlyAccepted");
            Log.logProvisioning("Provisioning::doGetFriends() Empty onlyAccepted for user " + sEmail);
            return sp;
        }
        sOnlyAccepted = sOnlyAccepted.trim();
        if (!sOnlyAccepted.equals("yes") && !sOnlyAccepted.equals("no"))
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            Log.logProvisioning("Provisioning::doGetFriends() wrong value for onlyAccepted for user" + sOnlyAccepted);
            return sp;
        }

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "";

            sSql = "SELECT 1 FROM " + Config.get("table-users") + " WHERE user_email = '" + sEmail + "'";

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            if (!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                sp.setStatusText("No customer found with email " + sEmail);
                Log.logProvisioning("Provisioning::doGetFriends() cannot find user with email" + sEmail);
                return sp;
            }

            sSql = "SELECT * FROM " + Config.get("table-friend-requests") + " WHERE friend_from = '" + sEmail
                    + "' AND friend_is_accepted = 1";

            pStatement = pDB.prepareStatement(sSql);
            pSet = pStatement.executeQuery();

            String sFriendUsername = "";
            String sFriendName = "";
            String sFriendEmail = "";

            while (pSet.next())
            {
                sFriendUsername = pSet.getString("friend_to_username");
                sFriendName = pSet.getString("friend_to_name");
                sFriendEmail = pSet.getString("friend_to");

                sResponse += "  <friend> \n" + "      <username>" + sFriendUsername + "</username> \n" + "      <name>"
                        + sFriendName + "</name> \n" + "      <email>" + sFriendEmail + "</email> \n"
                        + "      <isAccepted>yes</isAccepted> \n" + "  </friend> \n";
            }

            if (sOnlyAccepted.equals("yes"))
            {
                sSql = "SELECT * FROM " + Config.get("table-friend-requests") + " WHERE friend_to = '" + sEmail
                        + "' AND friend_is_accepted = 1";
                pStatement = pDB.prepareStatement(sSql);
                pSet = pStatement.executeQuery();

                while (pSet.next())
                {
                    sFriendUsername = pSet.getString("friend_from_username");
                    sFriendName = pSet.getString("friend_from_name");
                    sFriendEmail = pSet.getString("friend_from");

                    sResponse += "  <friend> \n" + "      <username>" + sFriendUsername + "</username> \n"
                            + "      <name>" + sFriendName + "</name> \n" + "      <email>" + sFriendEmail
                            + "</email> \n" + "      <isAccepted>yes</isAccepted> \n" + "  </friend> \n";
                }

            }
            else
            {
                String sFriendIsAccepted = "";
                sSql = "SELECT * FROM " + Config.get("table-friend-requests") + " WHERE friend_to = '" + sEmail + "'";
                pStatement = pDB.prepareStatement(sSql);
                pSet = pStatement.executeQuery();

                while (pSet.next())
                {
                    sFriendUsername = pSet.getString("friend_to_username");
                    sFriendName = pSet.getString("friend_to_name");
                    sFriendIsAccepted = pSet.getString("friend_is_accepted");
                    sFriendEmail = pSet.getString("friend_from");

                    sResponse += "  <friend> \n" + "      <username>" + sFriendUsername + "</username> \n"
                            + "      <name>" + sFriendName + "</name> \n";

                    if (sFriendIsAccepted.equals("1"))
                        sResponse += "      <isAccepted>yes</isAccepted> \n";

                    else
                        sResponse += "      <email>" + sFriendEmail + "</email> \n"
                                + "      <isAccepted>no</isAccepted> \n";

                    sResponse += "  </friend> \n";
                }
            }
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning("Provisioning::doGetFriends Error: " + e.getMessage());
            return sp;
        }

        /*
          <Barker requestType="getFriends"> <getFriends> <friend> <username></username>
          <name></name> <isAccepted>yes</isAccepted> </friend> </getFriends> </Barker>
         */

        sResponse = "<Barker requestType=\"getFriends\"> \r\n" + "	<statusCode>"
                + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode> \n"
                + "	<statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                + "</statusText> \n" + sResponse + "</Barker> \n";
        // TODO ADD 200 OK
        sp.setStatusCode(Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS));
        sp.setResponseXML(sResponse);
        return sp;
    }

    public SessionParameters doCreateWalk(SessionParameters sp)
    {
        /*
<Barker requestType="createWalk">
    <createWalk>
        <email>haudrennb@gmail.com</email>
        <location>Razsadnika</location>
        <message>helloWorld</message>
    </createWalk>
</Barker>
         */

        Log.logProvisioning("Provisioning::doCreateWalk()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sEmail = "";
        String sNames = "";
        String sLocation = "";
        String sUsername = "";
        String sMessage = "";

        String sResponse = "";

        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/email");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/location");
            sLocation = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/message");
            sMessage = (String) pExp.evaluate(pDoc, XPathConstants.STRING);
        }
        catch (Exception e)
        {
            Log.logProvisioning("Provisioning::doCreateWalk" + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }

        if (sEmail.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getStatusText() + " email");
            Log.logProvisioning("Provisioning::doCreateWalk Missing parameter email");
            return sp;
        }
        if (sLocation.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            sp.setStatusText(sp.getStatusText() + " location");
            Log.logProvisioning("Provisioning::doCreateWalk Missing parameter email");
            return sp;
        }

        sLocation = Tools.escapeString(sLocation);
        sEmail = Tools.escapeString(sEmail);
        sMessage = Tools.escapeString(sMessage);



        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "";

            sSql = "SELECT user_username FROM " + Config.get("table-users") + " WHERE user_email = '" + sEmail + "'";

            Log.logProvisioning("Provisioning::doCreateWalk() SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                sUsername = pSet.getString("user_username");
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                Log.logProvisioning("Provisioning::doCreateWalk() cannot find user with email: " + sEmail);
            }

            sSql = "INSERT INTO " + Config.get("table-walks")
                    + " (walk_user_email,walk_user_username,walk_user_location, walk_message, walk_created_dt)"
                    + "VALUES ('" + sEmail + "','" + sUsername + "','" + sLocation + "','" + sMessage
                    + "', now()) RETURNING walk_id";

            Log.logProvisioning("Provisioning::doCreateWalk() SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                sResponse = "   <walk> \n" + "     <id>" + pSet.getString("walk_id") + "</id> \n" + "   </walk> \n";
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                Log.logProvisioning("Provisioning::doCreateWalk couldn't retreive walk id for user " + sEmail);
                return sp;
            }

            sResponse = "<Barker requestType=\"createWalk\">\n" + "   <statusCode>200</statusCode> \n"
                    + "   <statusText>OK</statusText> \n" + sResponse + "</Barker> \n";

            sp.setResponseXML(sResponse);
            return sp;

        }
        catch (Exception e)
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

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sEmail = "";

        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/email");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

        }
        catch (Exception e)
        {
            Log.logProvisioning("Provisioning::doGetWalks" + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            return sp;
        }

        if (sEmail.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            Log.logProvisioning("Provisioning::doGetWalks() empty email");
            return sp;
        }

        sEmail = Tools.escapeString(sEmail);

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "SELECT friend_to FROM " + Config.get("table-friend-requests") + " WHERE friend_from = '"
                    + sEmail + "' " + "AND friend_is_accepted = 1";

            Log.logProvisioning("Provisioning::doGetWalks() SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            List<String> vsFriendsEmails = new Vector<String>();

            ResultSet pSet = pStatement.executeQuery();
            while (pSet.next())
            {
                vsFriendsEmails.add(pSet.getString("friend_to"));
            }

            sSql = "SELECT friend_from FROM " + Config.get("table-friend-requests") + " WHERE friend_to = '" + sEmail
                    + "' " + "AND friend_is_accepted = 1";

            Log.logProvisioning("Provisioning::doGetWalks() SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            while (pSet.next())
            {
                vsFriendsEmails.add(pSet.getString("friend_from"));
            }

            for (int i = 0; i < vsFriendsEmails.size(); ++i)
            {
                sSql = "SELECT * FROM " + Config.get("table-walks") + " WHERE walk_user_email = '"
                        + vsFriendsEmails.get(i) + "'";

                Log.logProvisioning("Provisioning::doGetWalks() SQL: " + sSql);

                pStatement = pDB.prepareStatement(sSql);

                pSet = pStatement.executeQuery();

                String sTimePassed = "";

                while (pSet.next())
                {
                    sTimePassed = pSet.getString("walk_created_dt");
                    if (sTimePassed.length() > 5)
                    {
                        sTimePassed = sTimePassed.substring(3, 5);
                    }
                    sResponse += "  <walk> \n" + "      <id>" + pSet.getInt("walk_id") + "</id>" + "      <username>"
                            + pSet.getString("walk_user_username") + "</username>\n"
//                                +"      <name>" + pSet.getString("walk_user_name") + "</name> \n"
                            + "      <location>" + pSet.getString("walk_user_location") + "</location> \n"
                            + "		<message>" + pSet.getString("walk_message") + "</message>\n" + "      <time>"
                            + sTimePassed + "</time>" + "  </walk>";
                }
            }
            sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\">\n"
                    + "   <statusCode>200</statusCode> \n" + "   <statusText>OK</statusText> \n" + sResponse
                    + "</Barker> \n";

            sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
            sp.setResponseXML(sResponse);
            return sp;

        }
        catch (Exception e)
        {
            Log.logProvisioning("Provisioning::doGetWalks Error: " + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            return sp;
        }

    }

    public SessionParameters doGetForumSubjects(SessionParameters sp)
    {
        /*
<Barker requestType="getForumSubjects">
    <getForumSubjects>
        <fromSubject>1</fromSubject>
        <toSubject>20</toSubject>
    </getForumSubjects>
</Barker>
         */
        Log.logProvisioning("Provisioning::doGetForumSubjects()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        int nFromSubject = 0;
        int nToSubject = 0;

        double nTmp = 0;

        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/fromSubject");
            nTmp = (double) pExp.evaluate(pDoc, XPathConstants.NUMBER);

            // downcasting as number accepts double
            nFromSubject = (int) nTmp;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/toSubject");
            nTmp = (double) pExp.evaluate(pDoc, XPathConstants.NUMBER);

            nToSubject = (int) nTmp;
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning("Cannot convert properly xml to get subject" + e.getMessage());
            return sp;
        }

        if (nFromSubject <= 0 || nToSubject <= 0 || nToSubject < nFromSubject)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            Log.logProvisioning(
                    "Provisioning::doGetForumSubjects bad from or to values: " + nFromSubject + " " + nToSubject);
            return sp;
        }

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "";

            sSql = "SELECT * FROM " + Config.get("table-subjects") + " WHERE subject_id BETWEEN " + nFromSubject
                    + " AND " + nToSubject;
            Log.logProvisioning("Provisioning::doGetForumSubjects() SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();
            while (pSet.next())
            {
                sResponse += "   <subject> \n" + "       <id>" + pSet.getString("subject_id") + "</id> \n"
                        + "       <name>" + pSet.getString("subject_name") + "</name> \n" + "       <customerUsername>"
                        + pSet.getString("subject_user_username") + "</customerUsername> \n" + "   </subject> \n";
            }
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning("Error selecting subjects from database " + e.getMessage());
            return sp;
        }

        sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                + "</statusText>\n" + sResponse + "</Barker> \n";

        sp.setResponseXML(sResponse);

        return sp;
    }

    public SessionParameters doViewSubject(SessionParameters sp)
    {
        /*
<Barker requestType="viewForumSubject">
    <viewForumSubject>
        <id>1</id>
    </viewForumSubject>
</Barker>
         */

        Log.logProvisioning("Provisioning::doViewSubject()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        int nId = 0;
        double nTmp = 0;
        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/id");
            nTmp = (double) pExp.evaluate(pDoc, XPathConstants.NUMBER);

            nId = (int) nTmp;
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning(
                    "Provisioning::doViewSubject Cannot convert properly xml to get subject" + e.getMessage());
            return sp;
        }

        if (nId <= 0)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            Log.logProvisioning("Provisioning::doViewSubject wrong id ");
            return sp;
        }

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "";

            sSql = "SELECT * FROM " + Config.get("table-subjects") + " WHERE subject_id = " + nId;
            Log.logProvisioning("Provisioning::doViewSubject SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                    + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                    + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                    + "</statusText>\n";

            if (pSet.next())
            {
                sResponse += "      <subject id = \"" + nId + "\"> \n";
                sResponse += "          <name>" + pSet.getString("subject_name") + "</name> \n";
                sResponse += "          <description>" + pSet.getString("subject_description") + "</description> \n";
                sResponse += "          <user>" + pSet.getString("subject_user_username") + "</user> \n";
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_SUBJECT);
                Log.logProvisioning("Provisioning::doViewSubject subject does not exist " + nId);
                return sp;
            }

            sSql = "SELECT * FROM " + Config.get("table-comments") + " WHERE subject_id = " + nId;

            Log.logProvisioning("Provisioning::doViewSubject SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            while (pSet.next())
            {
                sResponse += "          <comment id = \"" + pSet.getString("comment_id") + "\"> \n";
                sResponse += "              <text>" + pSet.getString("comment") + "</text> \n";
                sResponse += "              <user>" + pSet.getString("user_username") + "</user> \n";
                sResponse += "          </comment> \n";
            }

            sResponse += "      </subject> \n" + "</Barker> \n";

            sp.setResponseXML(sResponse);
            return sp;
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning("Provisioning::doViewSubject Error selecting subjects from database " + e.getMessage());
            return sp;
        }

    }

    public SessionParameters doCreateSubject(SessionParameters sp)
    {
        /*
<Barker requestType="createForumSubject">
    <createForumSubject>
        <userEmail>haudrennb@gmail.com</userEmail>
        <subject>Barker Subject</subject>
        <text>First Barker Subject</text>
    </createForumSubject>
</Barker>
         */

        Log.logProvisioning("Provisioning::doCreateSubject()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sEmail = "";
        String sSubject = "";
        String sText = "";

        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/userEmail");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/subject");
            sSubject = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/text");
            sText = (String) pExp.evaluate(pDoc, XPathConstants.STRING);
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning(
                    "Provisioning::doCreateSubject Cannot convert properly xml to get subject" + e.getMessage());
            return sp;
        }
        if (sEmail.isEmpty() || sSubject.isEmpty() || sText.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            Log.logProvisioning("Provisioning::doCreateSubject missing parameter");
            return sp;
        }
        
        sEmail = Tools.escapeString(sEmail);
        sSubject = Tools.escapeString(sSubject);
        sText = Tools.escapeString(sText);


        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "SELECT user_username FROM " + Config.get("table-users") + " WHERE user_email = '" + sEmail
                    + "'";
            Log.logProvisioning("Provisioning::doCreateSubject SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            String sUsername = "";
            if (pSet.next())
            {
                sUsername = pSet.getString("user_username");
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                Log.logProvisioning("Provisioning::doCreateSubject cannot find user with email " + sEmail);
                return sp;
            }

            sSql = "INSERT INTO " + Config.get("table-subjects")
                    + "(subject_name, subject_description, subject_user_username)" + " VALUES ('" + sSubject + "', '"
                    + sText + "','" + sUsername + "') RETURNING subject_id ";
            Log.logProvisioning("Provisioning::doCreateSubject SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                sResponse = "    <subject> \n" + "     <id>" + pSet.getString("subject_id") + "</id> \n" + "    </subject> \n";
            }

        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning("Provisioning::doViewSubject Error in database " + e.getMessage());
            return sp;
        }
        sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                + "</statusText>\n" + sResponse + "</Barker> \n";

        sp.setResponseXML(sResponse);

        return sp;
    }

    public SessionParameters doCreateSubjectComment(SessionParameters sp)
    {
        /*
<Barker requestType="createSubjectComment">
    <createSubjectComment>
        <userEmail>haudrennb@gmail.com</userEmail>
        <subjectId>1</subjectId>
        <text>First Barker Subject</text>
    </createSubjectComment>
</Barker>
         */

        Log.logProvisioning("Provisioning::doCreateComment()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sEmail = "";
        double nTmpSubjectId = 0;
        int nSubjectId = 0;
        String sText = "";

        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/userEmail");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/subjectId");
            nTmpSubjectId = (double) pExp.evaluate(pDoc, XPathConstants.NUMBER);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/text");
            sText = (String) pExp.evaluate(pDoc, XPathConstants.STRING);
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning(
                    "Provisioning::doCreateComment Cannot convert properly xml to get subject" + e.getMessage());
            return sp;
        }

        // downcasting as number expects double
        nSubjectId = (int) nTmpSubjectId;

        if (sEmail.isEmpty() || nSubjectId <= 0 || sText.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            Log.logProvisioning("Provisioning::doCreateComment missing parameter");
            return sp;
        }
        
        sEmail = Tools.escapeString(sEmail);
        sText = Tools.escapeString(sText);


        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "SELECT user_username FROM " + Config.get("table-users") + " WHERE user_email = '" + sEmail
                    + "'";
            Log.logProvisioning("Provisioning::doCreateComment SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            String sUsername = "";
            if (pSet.next())
            {
                sUsername = pSet.getString("user_username");
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                Log.logProvisioning("Provisioning::doCreateComment cannot find user with email " + sEmail);
                return sp;
            }

            sSql = "SELECT 1 FROM " + Config.get("table-subjects") + " WHERE subject_id = " + nSubjectId;
            Log.logProvisioning("Provisioning::doCreateComment SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_SUBJECT);
                Log.logProvisioning("Cannot find subject with id " + nSubjectId);
                return sp;
            }

            sSql = "INSERT INTO " + Config.get("table-comments") + " (subject_id, comment, user_username)" + " VALUES ("
                    + nSubjectId + ", '" + sText + "','" + sUsername + "') RETURNING comment_id";
            Log.logProvisioning("Provisioning::doCreateComment SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();
            if (pSet.next())
            {
                sResponse = "    <comment> \n" + "       <id>" + pSet.getString("comment_id") + "</id> \n" + "    </comment> \n";
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                Log.logProvisioning("Provisioning::doCreateComment couldn't retreive commend id ");
                return sp;
            }

        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning("Provisioning::doCreateComment Error in database " + e.getMessage());
            return sp;
        }

        sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                + "</statusText>\n" + sResponse + "</Barker> \n";

        sp.setResponseXML(sResponse);

        return sp;
    }

    public SessionParameters doGetAccommodations(SessionParameters sp)
    {
        /*
<Barker requestType="getAccommodations">
    <getAccommodations>
        <fromAccommodation>1</fromAccommodation>
        <showAccommodations>20</showAccommodations>
        <accommodationType>all</accommodationType>
    </getAccommodations>
</Barker>
         */
        Log.logProvisioning("Provisioning::doGetAccommodations()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        int nToAccommodation = 0;
        int nFromAccommodation = 0;

        String sAccommodationType = "";
        double nTmp = 0;

        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/fromAccommodation");
            nTmp = (double) pExp.evaluate(pDoc, XPathConstants.NUMBER);
            nFromAccommodation = (int) nTmp;
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/showAccommodations");
            nTmp = (double) pExp.evaluate(pDoc, XPathConstants.NUMBER);
            nToAccommodation = (int) nTmp;
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/accommodationType");
            sAccommodationType = (String) pExp.evaluate(pDoc, XPathConstants.STRING);
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning("Cannot convert properly xml to get subject" + e.getMessage());
            return sp;
        }
        
        sAccommodationType = sAccommodationType.trim();

        if (nToAccommodation <= 0 || nFromAccommodation <= 0)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            Log.logProvisioning("Provisioning::doGetAccommodations bad from or to values: " + nFromAccommodation + " "
                    + nToAccommodation);
            return sp;
        }
        if(!sAccommodationType.equals("all") && !sAccommodationType.equals("hotel") && !sAccommodationType.equals("vet") && !sAccommodationType.equals("establishment"))
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            Log.logProvisioning("Provisioning::doGetAccommodations bad value for accommodation type " + sAccommodationType);
            return sp;
        }

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "";

            if(!sAccommodationType.equals("all"))
            {
                sSql = "SELECT * FROM " + Config.get("table-accommodations") + " WHERE accommodation_type = '" + sAccommodationType + "'";
                
                if(nFromAccommodation != 1) sSql += " AND accommodation_id > " + nFromAccommodation;
                
                sSql += " LIMIT " + nToAccommodation;
            }
            else
            {
                sSql = "SELECT * FROM " + Config.get("table-accommodations");
                
                if(nFromAccommodation != 1) sSql += " WHERE accommodation_id > " + nFromAccommodation;
                
                sSql += " LIMIT " + nToAccommodation;
            }

            Log.logProvisioning("Provisioning::getAccommodations SQL: " + sSql);
            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();
            while (pSet.next())
            {
                sResponse += "   <accommodation> \n" + "       <id>" + pSet.getString("accommodation_id") + "</id> \n"
                        + "       <name>" + pSet.getString("accommodation_name") + "</name> \n" + "       <rating>"
                        + pSet.getString("accommodation_rating") + "</rating> \n" + "       <voted>"
                        + pSet.getString("accommodation_voted_count") + "</voted> \n" + "       <description>"
                        + pSet.getString("accommodation_description") + "</description> \n" + "       <username>"
                        + pSet.getString("accommodation_user_username") + "</username> \n" + "   </accommodation> \n";

            }

        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning("Error selecting accommodations from database " + e.getMessage());
            return sp;
        }

        sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                + "</statusText>\n" + sResponse + "</Barker> \n";

        sp.setResponseXML(sResponse);

        return sp;

    }

    public SessionParameters doCreateAccommodation(SessionParameters sp)
    {
        /*
<Barker requestType="createAccommodation">
    <createAccommodation>
        <accommodationName>First Accommodation</accommodationName>
        <accommodationDescription>Accommodation Description</accommodationDescription>
        <accommodationType>vet</accommodationType>
        <userEmail>haudrennb@gmail.com</userEmail>
    </createAccommodation>
</Barker>
         */

        Log.logProvisioning("Provisioning::doCreateAccommodation()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sEmail = "";
        String sDescription = "";
        String sName = "";
        String sAccommodationType = "";

        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/userEmail");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/accommodationDescription");
            sDescription = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/accommodationName");
            sName = (String) pExp.evaluate(pDoc, XPathConstants.STRING);
            
            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/accommodationType");
            sAccommodationType = (String) pExp.evaluate(pDoc, XPathConstants.STRING);
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning(
                    "Provisioning::doCreateAccommodation Cannot convert properly xml to get subject" + e.getMessage());
            return sp;
        }
        
        sAccommodationType = sAccommodationType.trim();
        if (sEmail.isEmpty() || sDescription.isEmpty() || sName.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            Log.logProvisioning("Provisioning::doCreateAccommodation missing parameter");
            return sp;
        }
        
        if(!sAccommodationType.equals("hotel") && !sAccommodationType.equals("vet") && !sAccommodationType.equals("establishment"))
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            Log.logProvisioning("Provisioning::doGetAccommodations bad value for accommodation type " + sAccommodationType);
            return sp;
        }
        
        sEmail = Tools.escapeString(sEmail);
        sDescription = Tools.escapeString(sDescription);
        sName = Tools.escapeString(sName);


        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "SELECT user_username FROM " + Config.get("table-users") + " WHERE user_email = '" + sEmail
                    + "'";
            Log.logProvisioning("Provisioning::doCreateAccommodation SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            String sUsername = "";
            if (pSet.next())
            {
                sUsername = pSet.getString("user_username");
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                Log.logProvisioning("Provisioning::doCreateAccommodation cannot find user with email " + sEmail);
                return sp;
            }

            sSql = "INSERT INTO " + Config.get("table-accommodations")
                    + " (accommodation_name, accommodation_description, accommodation_user_username, accommodation_type, accommodation_rating, accommodation_voted_count)" + "VALUES ('"
                    + sName + "' , '" + sDescription + "' , '" + sUsername + "','" + sAccommodationType + "',0,0) RETURNING accommodation_id";
            Log.logProvisioning("Provisioning::doCreateAccommodation SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();
            if (pSet.next())
            {
                sResponse = "    <accommodation> \n" + "       <id>" + pSet.getString("accommodation_id") + "</id> \n"
                        + "    </accommodation> \n";
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                Log.logProvisioning("Provisioning::doCreateAccommodation couldn't retreive accommodation id ");
                return sp;
            }

        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning("Provisioning::doCreateAccommodation Error in database " + e.getMessage());
            return sp;
        }

        sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                + "</statusText>\n" + sResponse + "</Barker> \n";

        sp.setResponseXML(sResponse);

        return sp;
    }

    public SessionParameters doRateAccommodation(SessionParameters sp)
    {
        /*
         * <Barker requestType="rateAccommodation"> <rateAccommodation>
         * <accommodationId>1</accommodationId> <rate>6</rate>
         * <userEmail>haudrennb@gmail.com</userEmail> </rateAccommodation> </Barker>
         */

        Log.logProvisioning("Provisioning::doRateAccommodation()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sEmail = "";
        int nId = 0;
        double nTmp = 0;
        int nRate = 0;

        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/userEmail");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/accommodationId");
            nTmp = (double) pExp.evaluate(pDoc, XPathConstants.NUMBER);
            nId = (int) nTmp;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/rate");
            nTmp = (double) pExp.evaluate(pDoc, XPathConstants.NUMBER);
            nRate = (int) nTmp;
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning(
                    "Provisioning::doRateAccommodation Cannot convert properly xml to get subject" + e.getMessage());
            return sp;
        }
        if (sEmail.isEmpty() || nRate <= 0 || nId <= 0)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            Log.logProvisioning("Provisioning::doRateAccommodation missing parameter");
            return sp;
        }
        if (nRate > 10)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            Log.logProvisioning("Provisioning::doRateAccommodation rate exceeds maximum");
        }
        
        sEmail = Tools.escapeString(sEmail);


        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            // check if the user giving rating exists exists
            String sSql = "SELECT 1 FROM " + Config.get("table-users") + " WHERE user_email = '" + sEmail + "'";
            Log.logProvisioning("Provisioning::doRateAccommodation SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            if (!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                Log.logProvisioning("Provisioning::doRateAccommodation cannot find user with email " + sEmail);
                return sp;
            }

            sSql = "SELECT 1 FROM " + Config.get("table-accommodation-votes") + " WHERE accommodation_id = " + nId
                    + " AND user_email = '" + sEmail + "'";
            Log.logProvisioning("Provisioning::doRateAccommodation SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);
            pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                Log.logProvisioning("User " + sEmail + " already voted for accommodation with id " + nId);
                sp.setRequestStatus(Constants.RequestServerStatus.ACCOMMODATION_ALREADY_RATED);
                return sp;
            }

            sSql = "SELECT accommodation_rating, accommodation_voted_count FROM " + Config.get("table-accommodations")
                    + " WHERE accommodation_id = " + nId;
            Log.logProvisioning("Provisioning::doRateAccommodation SQL: " + sSql);

            double nDBRating = 0;
            int nDBRatedBy = 0;

            pStatement = pDB.prepareStatement(sSql);
            pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                nDBRating = pSet.getDouble("accommodation_rating");
                nDBRatedBy = pSet.getInt("accommodation_voted_count");
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                Log.logProvisioning("Provisioning::doRateAccommodation couldn't retreive accommodation id ");
                return sp;
            }

            // in case nobody has voted yet ResultSet returns default values 0
            // if there are votes already calculate the new rating by the formula avNew =
            // AverageOld + ((value - AverageOld)/sizeNew)
            ++nDBRatedBy;

            double nRatingNew = nDBRating + ((nRate - nDBRating) / nDBRatedBy);

            sSql = "UPDATE " + Config.get("table-accommodations") + " SET accommodation_rating = " + nRatingNew
                    + " ,accommodation_voted_count = " + nDBRatedBy + " WHERE accommodation_id = " + nId;
            Log.logProvisioning("Provisioning::doRateAccommodation SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);
            int nUpdated = pStatement.executeUpdate();

            // not successfully updated
            if (nUpdated == 0)
            {
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                Log.logProvisioning("Couldn't successfully update value for accommodation " + nId);
                return sp;
            }

            sSql = "INSERT INTO " + Config.get("table-accommodation-votes") + "(accommodation_id, user_email) VALUES ("
                    + nId + ", '" + sEmail + "') RETURNING id";
            Log.logProvisioning("Provisioning::doRateAccommodation SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);
            pSet = pStatement.executeQuery();

            if (!pSet.next())
            {
                Log.logProvisioning("Couldn't insert vote");
            }

            sResponse = "    <accommodation> \n" + "       <id>" + nId + "</id> \n" + "       <rating>" + nRatingNew
                    + "</rating> \n" + "       <ratedBy>" + nDBRatedBy + "</ratedBy> \n" + "    </accommodation> \n";

        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning("Provisioning::doRateAccommodation Error in database " + e.getMessage());
            return sp;
        }

        sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                + "</statusText>\n" + sResponse + "</Barker> \n";

        sp.setResponseXML(sResponse);

        return sp;
    }

    public SessionParameters doCreateAccommodationComment(SessionParameters sp)
    {
        /*
         * <Barker requestType="createAccommodationComment">
         * <createAccommodationComment> <email>haudrennb@gmail.com</email>
         * <accommodationId>1</accommodationId> <text>First Barker Accommodation</text>
         * </createAccommodationComment> </Barker>
         */

        Log.logProvisioning("Provisioning::doCreateAccommodationComment()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sEmail = "";
        int nAccommodationId = 0;
        double nTmp = 0;
        String sText = "";

        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/email");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/accommodationId");
            nTmp = (double) pExp.evaluate(pDoc, XPathConstants.NUMBER);
            nAccommodationId = (int) nTmp;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/text");
            sText = (String) pExp.evaluate(pDoc, XPathConstants.STRING);
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning(
                    "Provisioning::doCreateAccommodationComment Cannot convert properly xml to get accommodation comment"
                            + e.getMessage());
            return sp;
        }
        if (sEmail.isEmpty() || nAccommodationId <= 0 || sText.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }
        
        sEmail = Tools.escapeString(sEmail);
        sText = Tools.escapeString(sText);


        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "SELECT user_username FROM " + Config.get("table-users") + " WHERE user_email = '" + sEmail
                    + "'";
            Log.logProvisioning("Provisioning::doCreateAccommodationComment SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            String sUsername = "";
            if (pSet.next())
            {
                sUsername = pSet.getString("user_username");
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                Log.logProvisioning("Provisioning::doCreateAccommodationComment cannot find user with email " + sEmail);
                return sp;
            }

            sSql = "SELECT 1 FROM " + Config.get("table-accommodations") + " WHERE accommodation_id = "
                    + nAccommodationId;
            Log.logProvisioning("Provisioning::doCreateAccommodationComment SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_SUBJECT);
                Log.logProvisioning("Provisioning::doCreateAccommodationComment Cannot find accommodation with id "
                        + nAccommodationId);
                return sp;
            }

            sSql = "INSERT INTO " + Config.get("table-accommodation-comments")
                    + " (accommodation_id, comment_text, user_username)" + " VALUES (" + nAccommodationId + ", '"
                    + sText + "','" + sUsername + "') RETURNING comment_id";
            Log.logProvisioning("Provisioning::doCreateComment SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                sResponse = "    <comment>" + "       <id>" + pSet.getString("comment_id") + "</id>" + "    </comment>";
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                Log.logProvisioning("Provisioning::doCreateAccommodationComment couldn't retreive commend id ");
                return sp;
            }

        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning("Provisioning::doCreateAccommodationComment Error in database " + e.getMessage());
            return sp;
        }

        sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                + "</statusText>\n" + sResponse + "</Barker>";

        sp.setResponseXML(sResponse);

        return sp;

    }

    public SessionParameters doViewProfile(SessionParameters sp)
    {

        /*
         * <Barker requestType="viewProfile"> <viewProfile>
         * <email>haudrennb@gmail.com</email> <username>HaudreN</username>
         * <requestFromEmail>barkerapporiginal@gmail.com</requestFromEmail>
         * </viewProfile> </Barker>
         */

        Log.logProvisioning("Provisioning::doViewProfile()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sEmail = "";
        String sUsername = "";
        String sEmailRequestFrom = "";
        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/email");
            sEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/username");
            sUsername = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/requestFromEmail");
            sEmailRequestFrom = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning(
                    "Provisioning::doViewProfile Cannot convert properly xml to get email" + e.getMessage());
            return sp;
        }
        if (sEmail.isEmpty() && sUsername.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            Log.logProvisioning("Provisioning::doViewProfile missing parameter");
            return sp;
        }

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));
            String sSql = "";
            if (!sEmail.isEmpty())
            {
                sEmail = Tools.escapeString(sEmail);
                sSql = "SELECT * FROM " + Config.get("table-users") + " WHERE user_email = '" + sEmail + "'";
            }
            else
            {
                sUsername = Tools.escapeString(sUsername);
                sSql = "SELECT * FROM " + Config.get("table-users") + " WHERE user_username = '" + sUsername + "'";
            }
            Log.logProvisioning("Provisioning::doViewProfile SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                sResponse = "   <user> \n" + "       <id>" + pSet.getString("user_id") + "</id> \n" + "       <email>"
                        + pSet.getString("user_email") + "</email> \n" + "       <name>" + pSet.getString("user_name")
                        + "</name> \n" + "       <username>" + pSet.getString("user_username") + "</username> \n";

                if (!sEmailRequestFrom.isEmpty())
                {
                    sEmailRequestFrom = Tools.escapeString(sEmailRequestFrom);
                    sSql = "SELECT friend_is_accepted FROM " + Config.get("table-friend-requests")
                            + " WHERE (friend_from = '" + sEmailRequestFrom + "' AND friend_to = '"
                            + pSet.getString("user_email") + "') OR (friend_from = '" + pSet.getString("user_email")
                            + "' AND friend_to = '" + sEmailRequestFrom + "')";

                    pStatement = pDB.prepareStatement(sSql);

                    pSet = pStatement.executeQuery();

                    if (pSet.next())
                    {
                        sResponse += "<friendAccepted>" + pSet.getString("friend_is_accepted") + "</friendAccepted> \n";
                    }
                }
                sResponse += "   </user> \n";

            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                Log.logProvisioning("Provisioning::doViewProfile cannot find user with email " + sEmail);
                return sp;
            }

        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning("Provisioning::doViewProfile Error in database " + e.getMessage());
            return sp;
        }

        sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                + "</statusText>\n" + sResponse + "</Barker> \n";

        sp.setResponseXML(sResponse);
        return sp;

    }

    public SessionParameters doViewAccommodation(SessionParameters sp)
    {
        /*
<Barker requestType="viewAccommodation">
    <viewAccommodation>
        <id>1</id>
    </viewAccommodation>
</Barker>
         */

        Log.logProvisioning("Provisioning::doViewAccommodation()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        int nId = 0;
        double nTmp = 0;
        String sResponse = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/id");
            nTmp = (double) pExp.evaluate(pDoc, XPathConstants.NUMBER);

            nId = (int) nTmp;
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning(
                    "Provisioning::doViewAccommodation Cannot convert properly xml to get subject" + e.getMessage());
            return sp;
        }

        if (nId <= 0)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
            Log.logProvisioning("Provisioning::doViewAccommodation wrong id ");
            return sp;
        }

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            String sSql = "";

            sSql = "SELECT * FROM " + Config.get("table-accommodations") + " WHERE accommodation_id = " + nId;
            Log.logProvisioning("Provisioning::doViewAccommodation SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                    + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                    + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                    + "</statusText>\n";

            if (pSet.next())
            {
                sResponse += "      <accommodation id = \"" + nId + "\"> \n";
                sResponse += "          <name>" + pSet.getString("accommodation_name") + "</name> \n";
                sResponse += "          <description>" + pSet.getString("accommodation_description")
                        + "</description> \n";
                sResponse += "			<rating>" + pSet.getString("accommodation_rating") + "</rating> \n";
                sResponse += "			<votedBy>" + pSet.getString("accommodation_voted_count") + "</votedBy> \n";
                sResponse += "          <user>" + pSet.getString("accommodation_user_username") + "</user> \n";
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_ACCOMMODATION);
                Log.logProvisioning("Provisioning::doViewSubject subject does not exist " + nId);
                return sp;
            }

            sSql = "SELECT * FROM " + Config.get("table-accommodation-comments") + " WHERE accommodation_id = " + nId;

            Log.logProvisioning("Provisioning::doViewAccommodation SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            while (pSet.next())
            {
                sResponse += "          <comment id = \"" + pSet.getString("comment_id") + "\"> \n";
                sResponse += "              <text>" + pSet.getString("comment_text") + "</text> \n";
                sResponse += "              <user>" + pSet.getString("user_username") + "</user> \n";
                sResponse += "          </comment> \n";
            }

            sResponse += "      </accommodation> \n" + "</Barker> \n";

            sp.setResponseXML(sResponse);
            return sp;
        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            Log.logProvisioning(
                    "Provisioning::doViewAccommodation Error selecting subjects from database " + e.getMessage());
            return sp;
        }

    }

    public SessionParameters doChangeProfileParameters(SessionParameters sp)
    {
        /*
<Barker requestType="changeProfileParameters">
    <changeProfileParameters>
        <oldName>Kaloyan Nikolov</oldName>
        <oldEmail>haudrennb@gmail.com</oldEmail>
        <oldUsername>HaudreN</oldUsername>
        <password>936a185caaa266bb9cbe981e9e05cb78cd732b0b3280eb944412bb6f8f8f07af</password>
        <newName>Kalo
          Niko</newName>
        <newEmail>haudrennb1@gmail.com</newEmail>
        <newUsername>HaudreN1"</newUsername>
    </changeProfileParameters>
</Barker>
         */

        Log.logProvisioning("Provisioning::doChangeProfileParameters()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sOldName = "", sNewName = "", sOldEmail = "", sNewEmail = "", sOldUsername = "", sNewUsername = "",
                sPassword = "";
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/oldName");
            sOldName = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/newName");
            sNewName = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/oldEmail");
            sOldEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/newEmail");
            sNewEmail = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/oldUsername");
            sOldUsername = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/newUsername");
            sNewUsername = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/password");
            sPassword = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning("Provisioning::doChangeProfileParameters Cannot convert properly xml to get subject"
                    + e.getMessage());
            return sp;
        }

        if (sOldName.isEmpty() || sNewName.isEmpty() || sOldEmail.isEmpty() || sNewEmail.isEmpty()
                || sOldUsername.isEmpty() || sNewUsername.isEmpty() || sPassword.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            Log.logProvisioning("Not all needed fields could be found. Old Name: " + sOldName + " ,new name: "
                    + sNewName + " ,old email : " + sOldEmail + " ,new email: " + sNewEmail + " old username: "
                    + sOldUsername + " ,new username: " + sNewUsername + " password: " + sPassword);
            return sp;
        }
        
        sOldName = Tools.escapeString(sOldName);
        sNewName = Tools.escapeString(sNewName);
        sOldEmail = Tools.escapeString(sOldEmail);
        sNewEmail = Tools.escapeString(sNewEmail);
        sOldUsername = Tools.escapeString(sOldUsername);
        sNewUsername = Tools.escapeString(sNewUsername);
        sPassword = Tools.escapeString(sPassword);


        if (sPassword.length() != 64)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.BAD_PASSWORD);
            Log.logProvisioning("Provisioning::doChangeProfileParameters Password is in wrong format");
            return sp;
        }

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;

        String sSql = "";

        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            PreparedStatement pStatement;
            ResultSet pSet;
            if (!sOldEmail.equals(sNewEmail))
            {
                sSql = "SELECT 1 FROM barker.users WHERE user_email = '" + sNewEmail.trim() + "'";
                Log.logProvisioning("Provisioning::doChangeProfileParameters SQL: " + sSql);

                pStatement = pDB.prepareStatement(sSql);

                pSet = pStatement.executeQuery();

                if (pSet.next())
                {
                    sp.setRequestStatus(Constants.RequestServerStatus.USER_EMAIL_ALREADY_EXISTS);
                    return sp;
                }
            }

            if (!sOldUsername.equals(sNewUsername))
            {
                sSql = "SELECT 1 FROM " + Config.get("table-users") + " WHERE user_username = '" + sNewUsername.trim()
                        + "'";
                Log.logProvisioning("Provisioning::doChangeProfileParameters SQL: " + sSql);


                pStatement = pDB.prepareStatement(sSql);

                pSet = pStatement.executeQuery();

                if (pSet.next())
                {
                    sp.setRequestStatus(Constants.RequestServerStatus.USER_ALREADY_EXISTS);
                    return sp;
                }
            }

            sSql = "UPDATE " + Config.get("table-users") + " SET user_name = '" + sNewName + "' ,user_email = '"
                    + sNewEmail + "' ,user_username = '" + sNewUsername + "' WHERE user_email = '" + sOldEmail
                    + "' AND user_name = '" + sOldName + "' AND user_password = '" + sPassword
                    + "' AND user_username = '" + sOldUsername + "'";
            Log.logProvisioning("Provisioning::doChangeProfileParameters SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            int nResult = pStatement.executeUpdate();

            if (nResult == 0)
            {
                Log.logProvisioning("Provisioning::doChangeProfileParameters cannot find such user in DB");
                sp.setRequestStatus(Constants.RequestServerStatus.MISSING_USER);
                return sp;
            }

            if (!sOldUsername.equals(sNewUsername))
            {
                sSql = "INSERT INTO " + Config.get("table-username-change") + "(username_old, username_new) VALUES ('"
                        + sOldUsername + "', '" + sNewUsername + "')";
                Log.logProvisioning("Provisioning::doChangeProfileParameters SQL: " + sSql);

                pStatement = pDB.prepareStatement(sSql);

                pStatement.executeUpdate();
            }

        }
        catch (Exception e)
        {
            Log.logProvisioning("Provisioning::doChangeProfileParameters Error " + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            return sp;
        }

        sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
        return sp;
    }

    public SessionParameters doGetMessages(SessionParameters sp)
    {

        /*
<Barker requestType="getMessages">
    <getMessages>
        <user1>Barker</user1>
        <user2>HaudreN1"</user2>
        <afterID>1</afterID>
    </getMessages>
</Barker>
         */
        Log.logProvisioning("Provisioning::doGetMessages()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sUserFirst = "", sUserSecond = "", sAfterId = "";

        String sResponse = "";

        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/user1");
            sUserFirst = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/user2");
            sUserSecond = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/afterID");
            sAfterId = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning(
                    "Provisioning::doGetMessages Cannot convert properly xml to get subject" + e.getMessage());
            return sp;
        }

        if (sUserFirst.isEmpty() || sUserSecond.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }

        sUserFirst = Tools.escapeString(sUserFirst);
        sUserSecond = Tools.escapeString(sUserSecond);


        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;

        String sSql = "";
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            sSql = "SELECT * FROM " + Config.get("table-messages") + " WHERE ((message_from_user = '" + sUserFirst
                    + "' AND message_to_user = '" + sUserSecond + "') OR (message_from_user = '" + sUserSecond
                    + "' AND message_to_user = '" + sUserFirst + "')) ";

            if (!sAfterId.isEmpty())
                sSql += " AND message_id > " + sAfterId;

            sSql += "ORDER BY message_id";
            
            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            Log.logProvisioning("Provisioning::doGetMessages() SQL: " + sSql);

            ResultSet pSet = pStatement.executeQuery();

            while (pSet.next())
            {
                sResponse += "	<message>\n" 
                        + "    	<id>" + pSet.getString("message_id") + "</id>\n"
                        + "    	<fromUser>" + pSet.getString("message_from_user") + "</fromUser>\n"
                        + "    	<messageText>" + pSet.getString("message_text") + "</messageText>\n"
                        + "	</message> \n";
            }

        }
        catch (Exception e)
        {
            Log.logProvisioning("Provisioning::doGetMessages Error " + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            return sp;
        }

        sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                + "</statusText>\n" + sResponse + "</Barker> \n";

        sp.setResponseXML(sResponse);

        return sp;
    }

    public SessionParameters doAddMessage(SessionParameters sp)
    {
        /*
          <Barker requestType="addMessage"> 
               <addMessage>
                   <userFrom>Barker</userFrom>
                   <userTo>HaudreN</userTo> 
                   <messageText>helloWorld</messageText> 
               </addMessage>
          </Barker>
         */

        Log.logProvisioning("Provisioning::doAddMessage()");

        Document pDoc = null;
        DocumentBuilder pBuilder = null;

        String sUserFrom = "", sUserTo = "", sMessage = "";

        String sResponse = "";

        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse(new InputSource(new StringReader(sp.getRequestXML())));

            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();
            XPathExpression pExp = null;

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/userFrom");
            sUserFrom = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/userTo");
            sUserTo = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

            pExp = pXpath.compile("Barker/" + sp.getRequestTypeText() + "/messageText");
            sMessage = (String) pExp.evaluate(pDoc, XPathConstants.STRING);

        }
        catch (Exception e)
        {
            sp.setRequestStatus(Constants.RequestServerStatus.INTERNAL_ERROR);
            Log.logProvisioning(
                    "Provisioning::doAddMessage Cannot convert properly xml to get subject" + e.getMessage());
            return sp;
        }

        if (sUserFrom.isEmpty() || sUserTo.isEmpty() || sMessage.isEmpty())
        {
            sp.setRequestStatus(Constants.RequestServerStatus.MISSING_PARAMETER);
            return sp;
        }

        sUserFrom = Tools.escapeString(sUserFrom);
        sUserTo = Tools.escapeString(sUserTo);
        sMessage = Tools.escapeString(sMessage);
        

        String sConnection = Config.get("database-connection-string");
        Connection pDB = null;

        String sSql = "";
        try
        {
            pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
                    Config.get("database-password"));

            sSql = "SELECT 1 FROM " + Config.get("table-users") + " WHERE user_username = '" + sUserFrom + "'";
            Log.logProvisioning("Provisioning::doAddMessage() SQL: " + sSql);

            PreparedStatement pStatement = pDB.prepareStatement(sSql);

            ResultSet pSet = pStatement.executeQuery();

            if (!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
                return sp;
            }

            sSql = "SELECT 1 FROM " + Config.get("table-users") + " WHERE user_username = '" + sUserTo + "'";
            Log.logProvisioning("Provisioning::doAddMessage() SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (!pSet.next())
            {
                sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
                return sp;
            }

            sSql = "INSERT INTO " + Config.get("table-messages")
                    + "(message_from_user, message_to_user, message_text) VALUES ('" + sUserFrom + "' , '" + sUserTo
                    + "' , '" + sMessage + "') RETURNING message_id";
            Log.logProvisioning("Provisioning::doAddMessage() SQL: " + sSql);

            pStatement = pDB.prepareStatement(sSql);

            pSet = pStatement.executeQuery();

            if (pSet.next())
            {
                sResponse += "	<messageId>" + pSet.getString("message_id") + "</messageId> \n";
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
                return sp;
            }

        }
        catch (Exception e)
        {
            Log.logProvisioning("Provisioning::doAddMessage Error " + e.getMessage());
            sp.setRequestStatus(Constants.RequestServerStatus.DATABASE_ERROR);
            return sp;
        }

        sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n" + "    <statusCode>"
                + Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS) + "</statusCode>\n"
                + "    <statusText>" + Constants.requestStatusToText(Constants.RequestServerStatus.SUCCESS)
                + "</statusText>\n" + sResponse + "</Barker> \n";

        sp.setResponseXML(sResponse);

        return sp;
    }

}
