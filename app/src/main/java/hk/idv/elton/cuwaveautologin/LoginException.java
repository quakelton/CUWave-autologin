package hk.idv.elton.cuwaveautologin;

public class LoginException extends Exception {

    private static final long serialVersionUID = 1L;

    public LoginException(String strRes) {
        super(strRes);
    }
}
