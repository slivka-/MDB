package pl.jrj.mdb;
import javax.ejb.Remote;

@Remote
public interface IMdbManager
{
    public String sessionId(String album);
}
