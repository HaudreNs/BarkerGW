package barkerGW;

public class SessionParameters
{
    
    private String m_sRequestXML = "";
    private String m_sResponseXML = "";
    private int m_nStatusCode = 0;
    private String m_sStatusText = "";
    private Constants.RequestServerStatus m_eRequestStatus = Constants.RequestServerStatus.SUCCESS;
    private Constants.RequestType m_eRequestType = Constants.RequestType.UNKNOWN ;

    public String getRequestXML()
    {
        return m_sRequestXML;
    }
    public String getResponseXML()
    {
        return m_sResponseXML;
    }
    public int getStatusCode()
    {
        return m_nStatusCode;
    }
    public String getStatusText()
    {
        return m_sStatusText;
    }
    public void setRequestXML(String requestXML)
    {
        m_sRequestXML = requestXML;
    }
    public void setResponseXML(String responseXML)
    {
        m_sResponseXML = responseXML;
    }
    public void setStatusCode(int statusCode)
    {
        m_nStatusCode = statusCode;
    }
    public void setStatusText(String statusText)
    {
        m_sStatusText = statusText;
    }

    public Constants.RequestServerStatus getRequestStatus()
    {
        return m_eRequestStatus;
    }
    public void setRequestStatus(Constants.RequestServerStatus eRequestStatus)
    {
        m_eRequestStatus = eRequestStatus;
    }
    public Constants.RequestType getRequestType()
    {
        return m_eRequestType;
    }
    public void setRequestType(Constants.RequestType requestType)
    {
        m_eRequestType = requestType;
    }
}
