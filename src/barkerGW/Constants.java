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
       ,GET_WALKS
       ,CREATE_WALK
       ,GET_FORUM_SUBJECTS
       ,CREATE_FORUM_SUBJECT
       ,CREATE_SUBJECT_COMMENT
       ,VIEW_FORUM_SUBJECT
       ,GET_ACCOMMODATIONS
       ,CREATE_ACCOMMODATION
       ,RATE_ACCOMMODATION
       ,CREATE_ACCOMMODATION_COMMENT
       ,VIEW_ACCOMMODATION
       ,VIEW_PROFILE
       ,CHANGE_PROFILE_PARAMETERS
       ,GET_MESSAGES
       ,ADD_MESSAGE
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
       ,USER_ALREADY_EXISTS
       ,USER_EMAIL_ALREADY_EXISTS
       ,MISSING_FRIEND_REQUEST
       ,FRIEND_REQUEST_ALREADY_EXISTS
       ,MISSING_SUBJECT
       ,BAD_PASSWORD
       ,MISSING_ACCOMMODATION
       ,ACCOMMODATION_ALREADY_RATED
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
        case CREATE_WALK: return "createWalk";
        case GET_WALKS: return "getWalks";
        case GET_FORUM_SUBJECTS: return "getForumSubjects";
        case VIEW_FORUM_SUBJECT: return "viewForumSubject";
        case CREATE_FORUM_SUBJECT: return "createForumSubject";
        case CREATE_SUBJECT_COMMENT: return "createSubjectComment";
        case GET_ACCOMMODATIONS: return "getAccommodations";
        case CREATE_ACCOMMODATION: return "createAccommodation";
        case RATE_ACCOMMODATION: return "rateAccommodation";
        case CREATE_ACCOMMODATION_COMMENT: return "createAccommodationComment";
        case VIEW_ACCOMMODATION: return "viewAccommodation";
        case VIEW_PROFILE: return "viewProfile";
        case CHANGE_PROFILE_PARAMETERS: return "changeProfileParameters";
        case GET_MESSAGES: return "getMessages";
        case ADD_MESSAGE: return "addMessage";
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
        else if(sType.equals("getWalks")) return RequestType.GET_WALKS;
        else if(sType.equals("createWalk")) return RequestType.CREATE_WALK;
        else if(sType.equals("getForumSubjects")) return RequestType.GET_FORUM_SUBJECTS;
        else if(sType.equals("viewForumSubject")) return RequestType.VIEW_FORUM_SUBJECT;
        else if(sType.equals("createForumSubject")) return RequestType.CREATE_FORUM_SUBJECT;
        else if(sType.equals("createSubjectComment")) return RequestType.CREATE_SUBJECT_COMMENT;
        else if(sType.equals("getAccommodations")) return RequestType.GET_ACCOMMODATIONS;
        else if(sType.equals("createAccommodation")) return RequestType.CREATE_ACCOMMODATION;
        else if(sType.equals("rateAccommodation")) return RequestType.RATE_ACCOMMODATION;
        else if(sType.equals("createAccommodationComment")) return RequestType.CREATE_ACCOMMODATION_COMMENT;
        else if(sType.equals("viewAccommodation")) return RequestType.VIEW_ACCOMMODATION;
        else if(sType.equals("viewProfile")) return RequestType.VIEW_PROFILE;
        else if(sType.equals("changeProfileParameters")) return RequestType.CHANGE_PROFILE_PARAMETERS;
        else if(sType.equals("getMessages")) return RequestType.GET_MESSAGES;
        else if(sType.equals("addMessage")) return RequestType.ADD_MESSAGE;
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
            case USER_ALREADY_EXISTS: return 402;
            case USER_EMAIL_ALREADY_EXISTS: return 409;
            case MISSING_FRIEND_REQUEST: return 403;
            case FRIEND_REQUEST_ALREADY_EXISTS: return 405;
            case BAD_PASSWORD: return 406;
            case MISSING_SUBJECT: return 407;
            case MISSING_ACCOMMODATION: return 408;
            case ACCOMMODATION_ALREADY_RATED: return 207;
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
            case USER_ALREADY_EXISTS: return "User already exists";
            case USER_EMAIL_ALREADY_EXISTS: return "User email already exists";
            case MISSING_FRIEND_REQUEST: return "Missing friend request";
            case MISSING_SUBJECT: return "Missing subject";
            case FRIEND_REQUEST_ALREADY_EXISTS: return "Friend request already exists";
            case BAD_PASSWORD: return "Wrong password";
            case MISSING_ACCOMMODATION: return "Missing accommodation";
            case ACCOMMODATION_ALREADY_RATED: return " Accommodation already rated by user";
            default: return "Unknown status";
        }
    }
    
    public static String statusCodeToText(int nStatus)
    {
        switch(nStatus)
        {
            case 200: return "OK";
            case 206: return "Partial Success";
            case 207: return "Accommodation already rated by user";
            case 500: return "Internal Error";
            case 503: return "Database Error";
            case 400: return "Bad Request";
            case 460: return "Missing Parameter";
            case 402: return "User already exists";
            case 409: return "User email already exists";
            case 403: return "Missing friend request";
            case 405: return "Friend request already exists";
            case 406: return "Wrong password";
            case 407: return "Missing subject";
            case 408: return "Missing accommodation";
            default: return "Unknown status";
        }
    }
    

}
