import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * ApplicationServeur est la couche Application du Serveur
 * @author Iliass Bouraba et Pedro Maria Scoccimarro
 * @version 1.0
 */
public class ApplicationServeur implements Couche {
    private Couche nextCouche;

    /**
     * Initialise la prochaine couche utilisé
     * @param couche est la prochaine couche de la chaîne de responsabilité
     */
    @Override
    public void SetNext(Couche couche) {
        nextCouche = couche;
    }

    /**
     * Sauvegarde le fichier dans le répertoire out
     * @param typeRequest correspond au nom du fichier
     * @param message correspond aux données reçus à sauvegarder
     */
    @Override
    public void Handle(String typeRequest, byte[] message) {
        File fichier = new File("out/" + typeRequest);
        String contenu = new String(message, StandardCharsets.UTF_8);
        try {
            FileWriter writer = new FileWriter(fichier);

            writer.write(contenu);

            writer.close();

            System.out.println("Le fichier a été sauvegarder par le serveur");
        } catch (IOException e) {
            e.printStackTrace();
        }

        nextCouche.Handle("ProchainFichierServeur", null);
    }

    public static void main(String[] args) throws IOException {
        boolean exit = false;
        ApplicationServeur appServeur = new ApplicationServeur();
        Transport coucheTransport = new Transport();
        LiaisonDeDonnees coucheLiaisonDeDonnee = new LiaisonDeDonnees(coucheTransport, "Serveur");
        appServeur.SetNext(coucheTransport);
        coucheTransport.setApplication(appServeur);
        coucheTransport.setLiaison(coucheLiaisonDeDonnee);


        Scanner keyboard = new Scanner(System.in);
        System.out.println("----- WELCOME TO SERVERS.INC -----\nTo start the server type: 'Open'\nTo close server type: 'Close'\n----------------------------------\n");

        while(!exit){
            String response = keyboard.nextLine();
            switch(response){
                case "Open":
                    if(!coucheLiaisonDeDonnee.isAlive()){
                        coucheLiaisonDeDonnee.start();
                    }
                    break;
                case "Close":
                    exit = true;
                    System.out.println("Close");
                    coucheLiaisonDeDonnee.interrupt();
                    break;
            }
        }
    }
}
