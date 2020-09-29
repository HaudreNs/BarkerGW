package barkerGW;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UsernameChangeQueue implements Runnable{

	@Override
	public void run() {
		
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
        LocalDateTime now = LocalDateTime.now();  
        Log.logRequestServer("UsernameChangeQueue start time: " + dtf.format(now));  

			
	       String sConnection = Config.get("database-connection-string");
	       Connection pDB = null, pDB2 = null;
	       PreparedStatement pStatement = null, pStatement2 = null;
	       ResultSet pSet = null;

		try
		{
	       pDB = DriverManager.getConnection(sConnection, Config.get("database-username"), Config.get("database-password"));
	       pDB2 = DriverManager.getConnection(sConnection, Config.get("database-username"), Config.get("database-password"));

		}
		catch(Exception e)
		{
			Log.logProvisioning(e.getMessage());
		}
		String sSql = "";
		String sOldUsername = "";
		String sNewUsername = "";
		String sId = "";
		while(true)
		{
			try
			{
				
				sSql = "SELECT * FROM " + Config.get("table-username-change") + " LIMIT 100";
//				Log.logProvisioning("UsernameChangeQueue SQL: " + sSql);

				pStatement = pDB.prepareStatement(sSql);
				
				pSet = pStatement.executeQuery();
				
				while(pSet.next())
				{
					sOldUsername = pSet.getString("username_old");
					sNewUsername = pSet.getString("username_new");
					sId = pSet.getString("id");
					
				 	   sSql = "UPDATE " + Config.get("table-accommodations") + " SET accommodation_user_username = '" + sNewUsername + "' WHERE accommodation_user_username = '" + sOldUsername + "'";
						Log.logProvisioning("UsernameChangeQueue SQL: " + sSql);
				 	   pStatement2 = pDB2.prepareStatement(sSql);
				       pStatement2.executeUpdate();
				        
				 	   sSql = "UPDATE " + Config.get("table-comments") + " SET user_username = '" + sNewUsername + "' WHERE user_username = '" + sOldUsername + "'";
						Log.logProvisioning("UsernameChangeQueue SQL: " + sSql);
				 	   pStatement2 = pDB.prepareStatement(sSql);
				       pStatement2.executeUpdate();
				        
				 	   sSql = "UPDATE " + Config.get("table-subjects") + " SET subject_user_username = '" + sNewUsername + "' WHERE subject_user_username = '" + sOldUsername + "'";
						Log.logProvisioning("UsernameChangeQueue SQL: " + sSql);
				 	   pStatement2 = pDB2.prepareStatement(sSql);
				       pStatement2.executeUpdate();
				        
				 	   sSql = "UPDATE " + Config.get("table-friend-requests") + " SET friend_from = '" + sNewUsername + "' WHERE friend_from = '" + sOldUsername + "'";
						Log.logProvisioning("UsernameChangeQueue SQL: " + sSql);
				 	   pStatement2 = pDB2.prepareStatement(sSql);
				       pStatement2.executeUpdate();
				        
				 	   sSql = "UPDATE " + Config.get("table-friend-requests") + " SET friend_to = '" + sNewUsername + "' WHERE friend_to = '" + sOldUsername + "'";
				 	   Log.logProvisioning("UsernameChangeQueue SQL: " + sSql);
				 	   pStatement2 = pDB2.prepareStatement(sSql);
				       pStatement2.executeUpdate();
				       
				 	   sSql = "UPDATE " + Config.get("table-messages") + " SET message_from_user = '" + sNewUsername + "' WHERE message_from_user = '" + sOldUsername + "'";
						Log.logProvisioning("UsernameChangeQueue SQL: " + sSql);
				 	   pStatement2 = pDB2.prepareStatement(sSql);
				       pStatement2.executeUpdate();
				        
				 	   sSql = "UPDATE " + Config.get("table-messages") + " SET message_to_user = '" + sNewUsername + "' WHERE message_to_user = '" + sOldUsername + "'";
				 	   Log.logProvisioning("UsernameChangeQueue SQL: " + sSql);
				 	   pStatement2 = pDB2.prepareStatement(sSql);
				       pStatement2.executeUpdate();
				       
				       
				       sSql = "DELETE FROM " + Config.get("table-username-change") + " WHERE id = " +  sId;
						Log.logProvisioning("UsernameChangeQueue SQL: " + sSql);
					 	pStatement2 = pDB2.prepareStatement(sSql);
					    pStatement2.executeUpdate();
				    
				}
		           

			}
			catch(Exception e)
			{
				Log.logProvisioning("UsernameChangeQueue error" + e.getMessage());
			}
			
            try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			
		}

	}

}
