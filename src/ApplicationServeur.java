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
        fichier = new File("out/"+typeRequest);
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

    public static void main(String[] args) throws IOException {
        ApplicationServeur appServeur = new ApplicationServeur();
        Transport coucheTransport = new Transport();
        LiaisonDeDonnees coucheLiaisonDeDonnee = new LiaisonDeDonnees(coucheTransport);
        appServeur.SetNext(coucheTransport);
        coucheTransport.setApplication(appServeur);
        coucheTransport.setLiaison(coucheLiaisonDeDonnee);

        coucheLiaisonDeDonnee.start();

        //coucheLiaisonDeDonnee.Handle("Recu", null);
    }
}
