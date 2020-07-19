package barkerGW;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

public class RequestServer implements Runnable
{
    private Socket m_socket;


    @Override
    public void run()
    {
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
        LocalDateTime now = LocalDateTime.now();  
        Log.logRequestServer("RequestServer start time: " + dtf.format(now));  
        ServerSocket pServerSocket = null;
        
        try
        {
            String sIp = Config.get("request-server-ip");
            int nPort = Integer.parseInt(Config.get("request-server-port")); 
            int nTimeOut = Integer.parseInt(Config.get("request-server-timeout"));
            int nBackLog = Integer.parseInt(Config.get("request-server-backlog"));
            nTimeOut *= 1000;
            pServerSocket = new ServerSocket( nPort, nBackLog, InetAddress.getByName(sIp) );
            
            while(true)
            {
                //accept client
                m_socket = pServerSocket.accept();
                SessionParameters sp = new SessionParameters();
                
                m_socket.setSoTimeout( nTimeOut);
                //get request
                String sRequest = readRequest();
                if(sRequest.isEmpty())
                {
                    Log.logRequestServer("cannot parse empty request");
                    sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
                    sendResponse(sp);
                    m_socket.close();
                    continue;
                }
                Log.logRequestServer(sRequest);
               
                sp.setRequestXML(sRequest);
                //parse request
                sp = getRequestType(sp);
                
                
                if(sp.getStatusCode() != Constants.requestStatusToCode(Constants.RequestServerStatus.SUCCESS))
                {
                    //send response with error message and code
                    sendResponse(sp);
                }
                
                
                //send to provisioning to do the work and create response
                sp = executeCommand(sp);

                //send response
                sendResponse(sp);
                //close socket
                m_socket.close();
            }
        } 
        catch (Exception e)
        {
            Log.logRequestServer("Request Server stopped after terminal error" + e.getMessage());
            try
            {
                pServerSocket.close();
            } catch (IOException e1)
            {
                Log.logRequestServer(e1.getMessage());
            }
        }   


    }


    private SessionParameters executeCommand(SessionParameters sp)
    {
        Provisioning pProvisioning = new Provisioning();
        
        if(sp.getRequestType() == Constants.RequestType.REGISTER) sp = pProvisioning.doRegister(sp);
        else if(sp.getRequestType() == Constants.RequestType.PASSWORD_RESET)sp = pProvisioning.doResetPassword(sp);
        else if(sp.getRequestType() == Constants.RequestType.LOG_IN) sp = pProvisioning.doLogIn(sp);
        else if(sp.getRequestType() == Constants.RequestType.ADD_FRIEND) sp = pProvisioning.doAddFriend(sp);
        else if(sp.getRequestType() == Constants.RequestType.ACCEPT_FRIEND) sp = pProvisioning.doAcceptFriend(sp);
        else if(sp.getRequestType() == Constants.RequestType.GET_FRIENDS) sp = pProvisioning.doGetFriends(sp);
        else if(sp.getRequestType() == Constants.RequestType.CREATE_WALK) sp = pProvisioning.doCreateWalk(sp);
        else if(sp.getRequestType() == Constants.RequestType.VIEW_WALKS) sp = pProvisioning.doGetWalks(sp);
        else if(sp.getRequestType() == Constants.RequestType.GET_FORUM_SUBJECTS) sp = pProvisioning.doGetForumSubjects(sp);
        else if(sp.getRequestType() == Constants.RequestType.VIEW_FORUM_SUBJECT) sp = pProvisioning.doViewSubject(sp);
        else if(sp.getRequestType() == Constants.RequestType.CREATE_FORUM_SUBJECT) sp = pProvisioning.doCreateSubject(sp);
        else if(sp.getRequestType() == Constants.RequestType.CREATE_SUBJECT_COMMENT) sp = pProvisioning.doCreateSubjectComment(sp);
        else if(sp.getRequestType() == Constants.RequestType.GET_ACCOMMODATIONS) sp = pProvisioning.doGetAccommodations(sp);
        else if(sp.getRequestType() == Constants.RequestType.CREATE_ACCOMMODATION) sp = pProvisioning.doCreateAccommodation(sp);
        else if(sp.getRequestType() == Constants.RequestType.RATE_ACCOMMODATION) sp = pProvisioning.doRateAccommodation(sp);
        else if(sp.getRequestType() == Constants.RequestType.CREATE_ACCOMMODATION_COMMENT) sp = pProvisioning.doCreateAccommodationComment(sp);
        else if(sp.getRequestType() == Constants.RequestType.VIEW_PROFILE) sp = pProvisioning.doViewProfile(sp);
        // TODO Auto-generated method stub
        Log.logRequestServer("After completing Provisioning Request status is " + sp.getStatusCode() + " " + sp.getStatusText());
        return sp;
    }


    private SessionParameters getRequestType(SessionParameters sp)
    {
        Document pDoc= null;
        DocumentBuilder pBuilder = null;
        try
        {
            DocumentBuilderFactory pFactory = DocumentBuilderFactory.newInstance();        
            pBuilder = pFactory.newDocumentBuilder();
            pDoc = pBuilder.parse( new InputSource( new StringReader( sp.getRequestXML() )) );
            
            XPathFactory pXpathFactory = XPathFactory.newInstance();
            XPath pXpath = pXpathFactory.newXPath();            
            XPathExpression pExp = null;
            pExp = pXpath.compile("Barker/@requestType");
            String sRequestType = (String)pExp.evaluate( pDoc, XPathConstants.STRING );
            

            
            Constants.RequestType eRequestType = Constants.textToRequestType(sRequestType);
            System.out.println("Request type parameter. Request type is: " 
                    + Constants.requestTypeToText(eRequestType));
            
            
            sp.setRequestType(eRequestType);
            if(sp.getRequestType() == Constants.RequestType.UNKNOWN)
            {
                sp.setStatusCode(Constants.requestStatusToCode(Constants.RequestServerStatus.MISSING_PARAMETER));
                sp.setStatusText("Missing request type parameter");
                
            }
            else
            {
                sp.setRequestStatus(Constants.RequestServerStatus.SUCCESS);
            }
            

        } catch (ParserConfigurationException e)
        {
            Log.logRequestServer(e.getMessage());

        } catch (SAXException e)
        {
            Log.logRequestServer(e.getMessage());
        } catch (IOException e)
        {
            Log.logRequestServer(e.getMessage());
        } catch (XPathExpressionException e)
        {
            Log.logRequestServer(e.getMessage());
        }
        
        
        return sp;
    }


    private void sendResponse(SessionParameters sp)
    {

        OutputStreamWriter pWriter = null;

        String sResponse = "";

        if(!sp.getResponseXML().isEmpty())
        {
            sResponse = sp.getResponseXML();
        }
        else
        {
            if(sp.getStatusText().isEmpty()) sp.setStatusText(Constants.statusCodeToText(sp.getStatusCode()));
            /*Default is 
             * <Barker requestType="register">
                    <statusCode>200</statusCode> 
                    <statusText>OK</statusText>
               </Barker>
             */
            
            sResponse = "<Barker requestType=\"" + sp.getRequestTypeText() + "\"> \r\n"
                      + "    <statusCode>" + sp.getStatusCode() + "</statusCode>\n" 
                      + "    <statusText>" + sp.getStatusText() + "</statusText>\n"
                      + "</Barker>";
            
            Log.logRequestServer(sResponse);

        }
        try
        {
            
            pWriter = new OutputStreamWriter( m_socket.getOutputStream(), "UTF-8" );
            pWriter.write( "HTTP/1.0 200 OK\r\n" );
            pWriter.write( "Content-Type: text/xml;charset=UTF-8\r\n" );
            pWriter.write( "Content-Length: " + sResponse.length() + "\r\n" );
            pWriter.write( "Connection: close\r\n" );
            pWriter.write( "\r\n" );
            pWriter.write(sResponse);
            
            pWriter.flush();
            pWriter.close();

        } 
        catch (IOException e)
        {
            Log.logRequestServer(e.getMessage());
            try
            {
                if(pWriter != null) pWriter.close();
            }
            catch (IOException e2)
            {
                Log.logRequestServer(e2.getMessage());
            }
        }

    }

    /*Content length is required header for each request. In case it is missing request is not read. 
     * This way no data loss can occur
     */
    private String readRequest() throws IOException
    {
        InputStreamReader pInputStreamReader = new InputStreamReader( m_socket.getInputStream(), "utf-8");
        BufferedReader pBufferedReader = new BufferedReader( pInputStreamReader );
        
        String sResult = "";
        
        // Read headers
        try
        {
            while( true )
            {
                String sLine =  pBufferedReader.readLine();
                                
                if(sLine == null) break;
                   
                sResult += sLine+ "\r\n";
                if(sLine.indexOf("</Barker>") != -1)
                {
                    break;
                }
            }
        }
        catch(Exception e)
        {
            return "Bad request";
        }

        int nStartXML = sResult.indexOf("<Barker ");
        int nEndXML = sResult.indexOf("</Barker>");
        
        if(nStartXML == -1 || nEndXML == -1)
        {
            return "Bad request";
        }
        nEndXML +=  "</Barker>".length();
        
        Log.logProvisioning("Received request: " + sResult);
        
        
        return sResult.substring(nStartXML, nEndXML);
    }

}
