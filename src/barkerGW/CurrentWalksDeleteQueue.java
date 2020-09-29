package barkerGW;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CurrentWalksDeleteQueue implements Runnable {

	@Override
	public void run() {

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		Log.logRequestServer("UsernameChangeQueue start time: " + dtf.format(now));

		String sConnection = Config.get("database-connection-string");
	    Connection pDB = null, pDB2 = null;
	    PreparedStatement pStatement = null, pStatement2 = null;
		ResultSet pSet = null;

		try {
			pDB = DriverManager.getConnection(sConnection, Config.get("database-username"),
					Config.get("database-password"));
			pDB2 = DriverManager.getConnection(sConnection, Config.get("database-username"),
					Config.get("database-password"));
		} catch (Exception e) {
			Log.logProvisioning(e.getMessage());
		}

		String sSql = "";
		int nId = 0;

		while (true) {
			try {
				
				sSql = "SELECT walk_id FROM " + Config.get("table-walks") + " WHERE walk_created_dt > now() + interval '1 hour' LIMIT 100";
//				Log.logProvisioning("CurrentWalksDeleteQueue SQL: " + sSql);

				pStatement = pDB.prepareStatement(sSql);
				
				pSet = pStatement.executeQuery();
				
				while(pSet.next())
				{
					nId = pSet.getInt("walk_id");
					
					sSql = "DELETE FROM " + Config.get("table-walks") + " WHERE walk_id = " + nId;
//					Log.logProvisioning("CurrentWalksDeleteQueue SQL: " + sSql);
				 	pStatement2 = pDB2.prepareStatement(sSql);
				    pStatement2.executeUpdate();

				}
				
			} catch (Exception e) {
				Log.logProvisioning("CurrentWalksDeleteQueue error" + e.getMessage());
			}

			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
	}

}
