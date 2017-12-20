import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import pl.jrj.mdb.IMdbManager;
/**
 * @author Michał Śliwa
 */
@MessageDriven(mappedName= "jms/MyQueue", activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationType",
            propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "acknowledgeMode",
            propertyValue = "Auto-acknowledge")
})
public class MdbBean implements MessageListener
{
    //=================================================================================
    private static final Logger LOGGER = Logger.getLogger(MdbBean.class.getName());
    //=================================================================================   
    private final DataVault DATA_VAULT = DataVault.getInstance();
           
    @Override
    public void onMessage(Message message)
    {      
        try
        {
            if(message instanceof TextMessage)
            {
                String msg = ((TextMessage)message).getText();
                switch(msg)
                {
                    case "start":
                        DATA_VAULT.startCounting();
                        break;
                    case "stop":
                        DATA_VAULT.stopCounting();
                        break;
                    case "val":
                        DATA_VAULT.counterValue();
                        break;
                    case "err":
                        DATA_VAULT.errorValue();
                        break;
                    case "inc":
                        DATA_VAULT.increment();
                        break;
                    case "dec":
                        DATA_VAULT.decrement();
                        break;
                    default:
                        DATA_VAULT.advancedMessage(msg);
                        break;
                }
            }
            else
            {
                DATA_VAULT.addError();
            }
        }
        catch (JMSException ex)
        {
            LOGGER.log(Level.SEVERE, ex.toString());
        }
    }     
}

class DataVault
{   
    //=======================STATIC PART==================
    private static final Logger LOGGER = Logger.getLogger(DataVault.class.getName());
    private static DataVault instance = null;
    
    public static DataVault getInstance()
    {
        if (instance == null || instance.sessionId == null)
        {
            synchronized (DataVault.class)
            {
                if (instance == null || instance.sessionId == null)
                    instance = new DataVault();
            }
        }
        return instance;
    }
    //====================================================    
       
    private final String ALBUM = "108222";
    private final String DB_MANAGER = "java:global/mdb-project/MdbManager!"+
                                      "pl.jrj.mdb.IMdbManager";
    private final String CONNECTION_FACTORY = "jms/ConnectionFactory";
    private final String TOPIC = "jms/MyTopic";
    
    private ConnectionFactory connFactory = null;
    private Topic myTopic = null;
    
    public BeanState state;   
    public int counter;
    public int error;
    public String sessionId;
    
    
    private DataVault()
    {       
        this.counter = 0;
        this.error = 0;
        this.state = BeanState.STANDBY;
        try
        {
            Context ctx = new InitialContext();
            IMdbManager dbManager = (IMdbManager)ctx.lookup(DB_MANAGER);           
            connFactory = (ConnectionFactory)ctx.lookup(CONNECTION_FACTORY);
            myTopic = (Topic)ctx.lookup(TOPIC);
            
            this.sessionId = dbManager.sessionId(ALBUM);
        }
        catch(NamingException ex)
        {
            LOGGER.log(Level.SEVERE, ex.toString());
            this.sessionId = null;
        }
    }
    
    public void startCounting()
    {
        if(this.sessionId != null && this.state == BeanState.STANDBY)
            this.state = BeanState.COUNTING;
        else
            this.error++;
    }
    
    public void stopCounting()
    {
        if(this.state == BeanState.COUNTING)
            this.state = BeanState.STANDBY;
        else
            this.error++;
    }
    
    public void increment()
    {
        if (this.state == BeanState.COUNTING)
            this.counter++;
        else
            this.error++;
    }
    
    public void decrement()
    {
        if (this.state == BeanState.COUNTING)
            this.counter--;
        else
            this.error++;
    }
    
    public void addError()
    {
        this.error++;
    }
    
    public void advancedMessage(String message)
    {
        String[] parts = message.split("\\/");
        switch(parts[0])
        {
            case "inc":
                if(canParseInt(parts[1]))
                    this.counter += Integer.parseInt(parts[1]);
                else
                    this.error++;
                break;
            case "dec":
                if(canParseInt(parts[1]))
                    this.counter -= Integer.parseInt(parts[1]);
                else
                    this.error++;
                break;
            default:
                this.error++;
                break;
        }
    }
    
    public void counterValue()
    {
        if(sessionId != null)
        {
            try(Connection conn = connFactory.createConnection())
            {
                try(Session s = conn.createSession())
                {
                    try(MessageProducer prod = s.createProducer(myTopic))
                    {
                        String content;
                        content = String.format("%s/%d", sessionId,counter);
                        Message m = s.createTextMessage(content);
                        prod.send(m);
                    }
                }
            }
            catch(JMSException ex)
            {
                LOGGER.log(Level.SEVERE, ex.toString());
            }
        }
        else
        {
            this.error++;
        }
    }
    
    public void errorValue()
    {
        if(sessionId != null)
        {
            try(Connection conn = connFactory.createConnection())
            {
                try(Session s = conn.createSession())
                {
                    try(MessageProducer prod = s.createProducer(myTopic))
                    {
                        String content;
                        content = String.format("%s/%d", sessionId,error);
                        Message m = s.createTextMessage(content);
                        prod.send(m);
                    }
                }
            }
            catch(JMSException ex)
            {
                LOGGER.log(Level.SEVERE, ex.toString());
            }
        }
        else
        {
            this.error++;
        }
    }
    
    private Boolean canParseInt(String val)
    {
        try
        {
            Integer.parseInt(val);
            return true;
        }
        catch(NumberFormatException ex)
        {
            return false;
        }
    }
}

enum BeanState
{
    STANDBY, COUNTING;
}