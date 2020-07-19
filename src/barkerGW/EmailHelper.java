package barkerGW;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailHelper
{
    static private String m_sEmail = Config.get("email-address");
    static private String m_sPassword = Config.get("email-password");
    
    public static boolean sendAuthenticationEmail(String sRecepient, String sCode)
    {
        if(sRecepient.isEmpty())
        {
            Log.logProvisioning("EmailHelper::sendAuthenticationEmail cannot send email to empty recepient");
            return false;
        }
        
        Properties pEmailProperties = new Properties();
        
        pEmailProperties.put("mail.smtp.host", "smtp.gmail.com");
        pEmailProperties.put("mail.smtp.port", "587");
        pEmailProperties.put("mail.smtp.auth", "true");
        pEmailProperties.put("mail.smtp.starttls.enable", "true");
        
        Session pSession = Session.getInstance(pEmailProperties, new Authenticator() {
                
            @Override
            protected PasswordAuthentication getPasswordAuthentication()
            {
                Log.logProvisioning("Entered getPasswordAuthentication");
                return new PasswordAuthentication(m_sEmail, m_sPassword);
            }
        });
        
        Log.logProvisioning("Received session");
        
        Message pMessage = getMessage(pSession,sRecepient,sCode);
        Log.logProvisioning("Got message");
        
        //not created successfully
        if(pMessage == null)
        {
            Log.logProvisioning("EmailHelper::sendAuthenticationEmail Failed to create email");
            return false;
        }
        
        try
        {
            Log.logProvisioning("Entered send transport");
            Transport.send(pMessage);
            Log.logProvisioning("EmailHelper::sendAuthenticationEmail sent code " + sCode + " to user " + sRecepient);
            
        } catch (MessagingException e)
        {
            Log.logProvisioning("EmailHelper::sendAuthenticationEmail failed to send code to user " + sRecepient 
                    + " Error: " + e.getMessage() );
        }
        
        
        return true;
    }

    private static Message getMessage(Session pSession, String sRecepient, String sCode)
    {
        Log.logProvisioning("Entered getMessage ");

        Message pMessage = new MimeMessage(pSession);
        
        Log.logProvisioning("Created getMessage ");
        
        try
        {
            pMessage.setFrom(new InternetAddress(m_sEmail));
            pMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(sRecepient));
            //TODO - maybe get subject and message from DB(This way HTML can be used"
            pMessage.setSubject("Barker Password Resset");
            pMessage.setText("Your code for Barker is: " + sCode + "\n Please insert the code in the application");
            Log.logProvisioning("Finished getting message");
            
        } catch (Exception e)
        {
            Log.logProvisioning("EmailHelper::getMessage Error" + e.getMessage());
            return null;
        } 


        return pMessage;
    }


}
