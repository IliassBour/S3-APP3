import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ApplicationServeur implements Couche {
    private Couche nextCouche;
    private File fichier;
    @Override
    public void SetNext(Couche couche) {
        nextCouche = couche;
    }

    @Override
    public void Handle(String typeRequest, byte[] message) {
        fichier = new File(typeRequest);
        String contenu = new String(message, StandardCharsets.UTF_8);
        try {
            FileWriter writer = new FileWriter(fichier);

            writer.write(contenu);

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        nextCouche.Handle("ProchainFichierServeur", null);
    }
}
