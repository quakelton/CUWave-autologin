package hk.idv.elton.cuwaveautologin;

import java.io.IOException;

public interface LoginClient {

    public void login(String username, String password, String fqdn, String cmd, String login) throws IOException, LoginException;
    public void logout() throws IOException, LoginException;
    public boolean allowAuto();

}
