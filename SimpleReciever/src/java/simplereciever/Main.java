package simplereciever;

import javax.annotation.Resource;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * @author Michał Śliwa
 */
public class Main 
{
    @Resource(mappedName="jms/ConnectionFactory")
    private static ConnectionFactory connectionFactory;
    
    @Resource(mappedName = "jms/MyTopic")
    private static Topic topic;
    
    public static void main(String[] args)throws JMSException
    {
        try(Connection conn = connectionFactory.createConnection())
        {
            try(Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE))
            {
                try(MessageConsumer cons = session.createConsumer(topic))
                {
                    conn.start();
                    System.out.println("-----------LISTENER START-----------");
                    while(true)
                    {
                        TextMessage msg = (TextMessage)cons.receive();
                        System.out.println("RECIEVED: "+msg.getText());
                    }
                }
            }
        }
    }
}
