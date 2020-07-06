package barkerGW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tools
{

    public static boolean authenticateEmail(String sEmail)
    {
        //match letter/number or _ at least 2 times followed by @ then a-z at least 2 times then .
        // at least 1 time and finish it with a-z at least 2 times(. at least once because of domains like co.uk)
        //example email knnikolov@mail.co.uk

        String sRegex = "^(.+)@(.+)$";
        Pattern pEmailPattern = Pattern.compile(sRegex);
        Matcher pEmailMatcher = pEmailPattern.matcher(sEmail);
        //true if complete match false otherwise
        return pEmailMatcher.matches();        
    }

}
