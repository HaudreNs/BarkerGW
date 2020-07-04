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
        System.out.println("Provisioning::doRegister()");
        
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
            e.printStackTrace();
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
        System.out.println("User with name: " + sName + " ,email: " + sEmail + " ,password: " + sPassword 
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
        
      String sConnection = "jdbc:postgresql://127.0.0.1:5432/postgres";
      Connection pDB = null;
      try
      {
          pDB = DriverManager.getConnection(sConnection, "postgres", "password");
          
          
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
              
          System.out.println("SQL: " + sSql);

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
          e.printStackTrace();
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
                  // TODO Auto-generated catch block
                  e.printStackTrace();
              }
          }
      }
        
        
        sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
        return sp;
    }

    public SessionParameters doResetPassword(SessionParameters sp)
    {
        // TODO Auto-generated method stub
        
        
        return sp;
    }

}
