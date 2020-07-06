package barkerGW;

public class Constants
{
    
    enum RequestType
    {
        REGISTER
       ,LOG_IN
       ,PASSWORD_RESET
       ,ADD_FRIEND
       ,ACCEPT_FRIEND
       ,GET_FRIENDS
       ,FIND_WALK
       ,CREATE_WALK
       ,GET_FORUM_SUBJECTS
       ,CREATE_FORUM_SUBJECT
       ,VIEW_FORUM_SUBJECT
       ,GET_ACOMMODATIONS
       ,CREATE_ACOMMODATION
       ,UNKNOWN
    }
    enum RequestServerStatus
    {
        SUCCESS
       ,PARTIAL_SUCCESS
       ,INTERNAL_ERROR
       ,DATABASE_ERROR
       ,BAD_XML
       ,MISSING_PARAMETER
       ,MISSING_USER
    }

    public static String requestTypeToText(RequestType eType )
    {
        switch(eType)
        {
        case REGISTER: return "register";
        case LOG_IN: return "login";
        case PASSWORD_RESET: return "passwordReset";
        case ADD_FRIEND: return "addFriend";
        case ACCEPT_FRIEND: return "acceptFriend";
        case GET_FRIENDS: return "getFriends";
        case FIND_WALK: return "findWalk";
        case CREATE_WALK: return "createWalk";
        case GET_FORUM_SUBJECTS: return "getForumSubjects";
        case VIEW_FORUM_SUBJECT: return "viewForumSubject";
        case GET_ACOMMODATIONS: return "getAccomodations";
        case CREATE_ACOMMODATION: return "createAcommodation";
        default: return "unknown";
        }
            
    }
    
    public static RequestType textToRequestType(String sType)
    {
        if(sType.equals("register")) return RequestType.REGISTER;
        else if(sType.equals("login")) return RequestType.LOG_IN;
        else if(sType.equals("passwordReset")) return RequestType.PASSWORD_RESET;
        else if(sType.equals("addFriend")) return RequestType.ADD_FRIEND;
        else if(sType.equals("acceptFriend")) return RequestType.ACCEPT_FRIEND;
        else if(sType.equals("getFriends")) return RequestType.GET_FRIENDS;
        else if(sType.equals("findWalk")) return RequestType.FIND_WALK;
        else if(sType.equals("createWalk")) return RequestType.CREATE_WALK;
        else if(sType.equals("getForumSubjects")) return RequestType.GET_FORUM_SUBJECTS;
        else if(sType.equals("viewForumSubject")) return RequestType.VIEW_FORUM_SUBJECT;
        else if(sType.equals("getAccomodations")) return RequestType.GET_ACOMMODATIONS;
        else if(sType.equals("createAcommodation")) return RequestType.CREATE_ACOMMODATION;
        else return RequestType.UNKNOWN;
    }
    
    /* Following mapping scheme is used:
     * 0 status unknown
     * 1xx information 
     * 2xx success
     * 3xx redirect
     * 4xx client error
     * 5xx server error
     */
    public static int requestStatusToCode(RequestServerStatus eStatus)
    {
        switch(eStatus)
        {
            case SUCCESS: return 200;
            case PARTIAL_SUCCESS: return 206;
            case INTERNAL_ERROR: return 500;
            case DATABASE_ERROR: return 503;
            case BAD_XML: return 400;
            case MISSING_PARAMETER: return 460;
            case MISSING_USER: return 401;
            default: return 0;
        }
    }
    
    /*
     * BAD_XML is used when method is unknown or the xml is not properly formed or false
     * MISSING_PARAMETER is used when xml is valid, but a mandatory field is missing
     */
    public static String requestStatusToText(RequestServerStatus eStatus)
    {
        switch(eStatus)
        {
            case SUCCESS: return "OK";
            case PARTIAL_SUCCESS: return "Partial Success";
            case INTERNAL_ERROR: return "Internal Error";
            case DATABASE_ERROR: return "Database Error";
            case BAD_XML: return "Bad Request";
            case MISSING_PARAMETER: return "Missing Parameter";
            case MISSING_USER: return "Missing User";
            default: return "Unknown status";
        }
    }
    
    public static String statusCodeToText(int nStatus)
    {
        switch(nStatus)
        {
            case 200: return "OK";
            case 206: return "Partial Success";
            case 500: return "Internal Error";
            case 503: return "Database Error";
            case 400: return "Bad Request";
            case 460: return "Missing Parameter";
            default: return "Unknown status";
        }
    }
    

}
