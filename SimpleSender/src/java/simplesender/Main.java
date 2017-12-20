package simplesender;

import java.util.ArrayList;
import javax.annotation.Resource;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

public class Main
{   
    @Resource(mappedName="jms/ConnectionFactory")
    private static ConnectionFactory connectionFactory;
    
    @Resource(mappedName = "jms/MyQueue")
    private static Queue queue;
    
    private static final ArrayList<String> MESSAGES = new ArrayList<String>(){{
        add("start");
        add("inc");
        add("inc");
        add("dec");
        add("val");
        add("err");
        add("start");
        add("tfujstary");
        add("err");
        add("inc/50");
        add("dec/h");
        add("dec/20");
        add("val");
        add("err");
        add("stop");
        add("stop");
        add("err");
    }};

    public static void main(String[] args) throws JMSException, InterruptedException
    {
        try(Connection conn = connectionFactory.createConnection())
        {
            try(Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE))
            {
                try(MessageProducer prod = session.createProducer(queue))
                {
                    for(String msg: MESSAGES)
                    {
                        Message m = session.createTextMessage(msg);
                        System.out.println("Sending msg: "+msg);
                        prod.send(m);
                        Thread.sleep(500);
                    }
                }
            }
        }
    }
}
