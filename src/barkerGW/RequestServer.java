package barkerGW;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

                m_socket.setSoTimeout( 3 * 1000);
                //get request
                String sRequest = readRequest();
                if(sRequest.isEmpty())
                {
                    m_socket.close();
                    sp.setStatusCode(Constants.requestStatusToCode(Constants.RequestServerStatus.BAD_XML));
                    sendResponse(sp);
                    continue;
                }
                sp.setRequestXML(sRequest);
                //parse request
                sp = parseRequest(sp);
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
        // TODO Auto-generated method stub
        return null;
    }


    private SessionParameters parseRequest(SessionParameters sp)
    {
        
        
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
        
        
        return sRequest;
    }

}
