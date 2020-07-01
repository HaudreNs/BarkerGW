package barkerGW;


public class BarkerGW
{

    public static void main(String[] args)
    { 
        //Before running the project right click on the project name -> Build Path -> Configure Build Path -> Libraries -> Add JARs
        
        Thread tRequestServer = new Thread( new RequestServer() );
        tRequestServer.start();
        
    }

}
