import javax.jms.Message;
import javax.jms.MessageListener;
import pl.jrj.mdb.IMdbManager;
/**
 *
 * @author Michał Śliwa
 */
public class MdbBean implements MessageListener
{
    private IMdbManager mdbManagerInstance = null;
    
    @Override
    public void onMessage(Message message)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
