package barkerGW;


public class BarkerGW
{

    public static void main(String[] args)
    { 

                //Before running the project right click on the project name -> Build Path -> Configure Build Path -> Libraries -> Add JARs
        //load Config file
        String sPath = System.getProperty("user.home") + "/Desktop";
        //TODO - load correct value for Config location from here
        if(!Config.load(sPath + "/projectsOthers/" + "BarkerGW/config/Barker.config"))
        {
            System.out.println("Fatal error - Cannot load Configuration file.");
            return;
        }
        
        //load Username change queue
        Thread tUsernameChangeQueue = new Thread( new UsernameChangeQueue());
        tUsernameChangeQueue.start();
        
        //load current_walks remove queue. Every walk is active for one hour
        Thread tCurrentWalksDeleteQueue = new Thread(new CurrentWalksDeleteQueue());
        tCurrentWalksDeleteQueue.start();
        
        //load Request Server
        Thread tRequestServer = new Thread( new RequestServer() );
        tRequestServer.start();
        

        
    }

}
