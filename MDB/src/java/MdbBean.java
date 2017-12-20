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
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import pl.jrj.mdb.IMdbManager;
/**
 * Message driven bean, provides counter
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
    //=========================================================================
    //server logger for displaying errors
    private static final Logger LOGGER = Logger.getLogger(MdbBean.class.getName());
    //=========================================================================
    //DataVault class, holds counter values between calls
    private final DataVault DATA_VAULT = DataVault.getInstance();
         
    /**
     * Recieves messages sent to bean
     * @param message 
     */
    @Override
    public void onMessage(Message message)
    {      
        try
        {
            //checks if message recieved is text message
            if (message instanceof TextMessage)
            {
                //gets conten of message
                String msg = ((TextMessage)message).getText();
                switch (msg)
                {
                    //handling of simple message types
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
                        //if message is not simple, pass it for evaluation
                        DATA_VAULT.advancedMessage(msg);
                        break;
                }
            }
            else
            {
                //if recieved message is not TextMessage, increment error
                DATA_VAULT.addError();
            }
        }
        catch (JMSException ex)
        {
            LOGGER.log(Level.SEVERE, ex.toString());
        }
    }     
}

/**
 * Singleton class, holds bean values between calls
 * @author Michał Śliwa
 */
class DataVault
{   
    //=======================STATIC PART==================
    //logger for displaying errors
    private static final Logger LOGGER = Logger.getLogger(DataVault.class.getName());
    //static instance
    private static DataVault instance = null;
    
    /**
     * Returns single instance of DataVault class
     * Ensures only one instance is present at any given time
     * Thread safe
     * @return instance of DataVault class
     */
    public static DataVault getInstance()
    {
        //check if instance is null or created instance failed to register
        if (instance == null || instance.sessionId == null)
        {
            //synchronized block for thread safety
            synchronized (DataVault.class)
            {
                //double check if instance is null 
                //or created instance failed to register
                if (instance == null || instance.sessionId == null)
                    //create new instance
                    instance = new DataVault();
            }
        }
        //returns current instance
        return instance;
    }
    //====================================================    
    
    //my album number
    private final String ALBUM = "108222";
    //MdbManager deployment descriptor
    private final String DB_MANAGER = "java:global/mdb-project/MdbManager!"+
                                      "pl.jrj.mdb.IMdbManager";
    //connection factory deployment decriptor
    private final String CONNECTION_FACTORY = "jms/ConnectionFactory";
    //topic deployment decriptor
    private final String TOPIC = "jms/MyTopic";
    
    private ConnectionFactory connFactory = null;
    private Topic myTopic = null;
    
    //current state of bean
    private BeanState state; 
    //value counter
    private int counter;
    //error counter
    private int error;
    //session id
    private String sessionId;
    
    /**
     * Private constructor, to ensure instatiation through getInstance()
     */
    private DataVault()
    {       
        this.counter = 0;
        this.error = 0;
        this.state = BeanState.STANDBY;
        try
        {
            //create context
            Context ctx = new InitialContext();
            //lookup for MdbManager
            IMdbManager dbManager = (IMdbManager)ctx.lookup(DB_MANAGER);
            //lookup for connection factory
            connFactory = (ConnectionFactory)ctx.lookup(CONNECTION_FACTORY);
            //lookup for topic
            myTopic = (Topic)ctx.lookup(TOPIC);
            
            //get new session id
            this.sessionId = dbManager.sessionId(ALBUM);
            if(this.sessionId == null)
                LOGGER.log(Level.SEVERE, "!RECIEVED NULL FOR SESSION ID!");
        }
        catch (NamingException ex)
        {
            //log any errors
            LOGGER.log(Level.SEVERE, ex.toString());
            //set sessionId to null, indicates broken instance
            this.sessionId = null;
        }
    }
    
    /**
     * Sets bean to counting mode
     */
    public void startCounting()
    {
        //if mode is standby and session id is not null, start counting
        if (this.sessionId != null && this.state == BeanState.STANDBY)
            this.state = BeanState.COUNTING;
        else
            this.error++;
    }
    
    /**
     * Sets bean to standby mode
     */
    public void stopCounting()
    {
        //if bean is in counting state, change to standby
        if (this.state == BeanState.COUNTING)
            this.state = BeanState.STANDBY;
        else
            this.error++;
    }
    
    /**
     * Increments counter
     */
    public void increment()
    {
        //if bean is in counting state, increment counter
        if (this.state == BeanState.COUNTING)
            this.counter++;
        else
            this.error++;
    }
    
    /**
     * Decrements counter
     */
    public void decrement()
    {
        //if bean is in counting state, decrement counter
        if (this.state == BeanState.COUNTING)
            this.counter--;
        else
            this.error++;
    }
    
    /**
     * Increments error counter
     */
    public void addError()
    {
        this.error++;
    }
    
    /**
     * Processes advanced messages
     * @param message message recieved by bean
     */
    public void advancedMessage(String message)
    {
        //split message by "/" sign
        String[] parts = message.split("\\/");
        //check if message is in two parts, if not, increment error
        if(parts.length == 2)
        {
            switch(parts[0])
            {
                case "inc":
                    //check if value can be parsed, if not, increment error
                    if(canParseInt(parts[1]))
                        //add value to counter
                        this.counter += Integer.parseInt(parts[1]);
                    else
                        this.error++;
                    break;
                case "dec":
                    //check if value can be parsed, if not, increment error
                    if(canParseInt(parts[1]))
                        //subtract value from counter
                        this.counter -= Integer.parseInt(parts[1]);
                    else
                        this.error++;
                    break;
                default:
                    //if instruction is unrecognized, increment error
                    this.error++;
                    break;
            }
        }
        else
        {
            this.error++;
        }
    }
    
    /**
     * Send counter value to topic
     */
    public void counterValue()
    {
        sendToTopic(this.counter);
    }
    
    /**
     * Send error value to topic
     */
    public void errorValue()
    {
        sendToTopic(this.error);
    }
    
    private void sendToTopic(int value)
    {
        //if sessionId is not null, try to send value, 
        //otherwise increment error
        if (this.sessionId != null)
        {
            //create connection
            try (Connection conn = this.connFactory.createConnection())
            {
                //create session
                try (Session s = conn.createSession())
                {
                    //create message producer
                    try(MessageProducer prod = s.createProducer(this.myTopic))
                    {
                        //initialize content of sessage
                        String content;
                        content = String.format("%s/%d", this.sessionId,value);
                        //create new message
                        Message m = s.createTextMessage(content);
                        //send message to topic
                        prod.send(m);
                    }
                }
            }
            catch (JMSException ex)
            {
                LOGGER.log(Level.SEVERE, ex.toString());
            }
        }
        else
        {
            this.error++;
        } 
    }
    
    /**
     * Checks if string can be parsed to int
     * @param val string to parse
     * @return true if can be parsed otherwise false
     */
    private Boolean canParseInt(String val)
    {
        try
        {
            //try to parse string to integer
            Integer.parseInt(val);
            //return true
            return true;
        }
        catch(NumberFormatException ex)
        {
            //on exception return false
            return false;
        }
    }
}

/**
 * Enum representing states of the bean
 * @author Michał Śliwa
 */
enum BeanState
{
    STANDBY, COUNTING;
}