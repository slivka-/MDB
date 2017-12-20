package pl.jrj.mdb;

import java.util.UUID;
import javax.ejb.Stateless;

@Stateless
public class MdbManager implements IMdbManager
{
    @Override
    public String sessionId(String album)
    {
        if("108222".equals(album))
            return UUID.randomUUID().toString();
        else
            return null;
    }
}
