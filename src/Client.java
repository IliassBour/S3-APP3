import java.io.IOException;

public class Client {
    public static void main(String[] args) throws IOException {
        ApplicationClient app = new ApplicationClient();
        Transport transport = new Transport();
        Liaison liaison = new Liaison();

        app.setNext(transport);

        transport.setApplication(app);
        transport.setLiaison(liaison);

        liaison.setNext(transport);

        app.handle(null, null);
    }
}
