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
        System.out.println("RequestServer start time: " + dtf.format(now));  
        
        try
        {
            ServerSocket pServerSocket = new ServerSocket( 12345, 3, InetAddress.getByName( "127.0.0.1" ) );
            
            while(true)
            {
                //accept client
                m_socket = pServerSocket.accept();
                SessionParameters sp = new SessionParameters();
                //TODO get timeout from conf in the form seconds * 1000

                m_socket.setSoTimeout( 30000);
                //get request
                String sRequest = readRequest();
                if(sRequest.isEmpty())
                {
                    System.out.println("Cannot parse empty request");
                    sp.setRequestStatus(Constants.RequestServerStatus.BAD_XML);
                    sendResponse(sp);
                    m_socket.close();
                    continue;
                }
               System.out.println(sRequest);
               
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
            System.out.println("Request Server stopped after terminal error");
            e.printStackTrace();
        }   


    }


    private SessionParameters executeCommand(SessionParameters sp)
    {
        Provisioning pProvisioning = new Provisioning();
        
        if(sp.getRequestType() == Constants.RequestType.REGISTER) sp = pProvisioning.doRegister(sp);
        else if(sp.getRequestType() == Constants.RequestType.PASSWORD_RESET)sp = pProvisioning.doResetPassword(sp);
        // TODO Auto-generated method stub
        System.out.println("After completing Provisioning Request status is " + sp.getStatusCode() + " " + sp.getStatusText());
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
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SAXException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (XPathExpressionException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
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
            
            sResponse = "<Barker requestType=\"register\"> \r\n"
                      + "    <statusCode>" + sp.getStatusCode() + "</statusCode>\n" 
                      + "    <statusText>" + sp.getStatusText() + "</statusText>\n"
                      + "</Barker>";
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
            // TODO Auto-generated catch block
            e.printStackTrace();
            try
            {
                if(pWriter != null) pWriter.close();
            }
            catch (IOException e2)
            {
                // TODO Auto-generated catch block
                e2.printStackTrace();
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
        
        String sRequest = "";
        String sResult = "";
        int nContentLength = 0;
        
        // Read headers
        while( true )
        {
            String sLine =  pBufferedReader.readLine();
            sRequest+=sLine+"\n";

            if ( sLine.toLowerCase().startsWith( "content-length:" ) )
            {
                String sContentLength = "";
                for( int i = 15; i < sLine.length(); i++ )
                {
                    if( Character.isDigit( sLine.charAt( i ) ) )
                    {
                        sContentLength+=sLine.charAt( i );
                    }
                }
                
                if( !sContentLength.isEmpty() ) nContentLength = Integer.parseInt( sContentLength );
            }

            if ( sLine.length() == 0 )
            {
                break;
            }           
        }
        
        for( int i=0; i<nContentLength; i++ )
        {
            int x = pBufferedReader.read();
                
            // BEGIN Is this correct?!!??!!?
            if ( x >= 256 )
                i++;
            // END
            
            if ( x == -1 )
                break;
            
            sResult += (char)x;
        }
        
        sRequest+=sResult;        
        
        
        return sResult;
    }

}
