import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * ApplicationClient est la couche Application du Client
 * @author Iliass Bouraba et Pedro Maria Scoccimarro
 * @version 1.0
 */
public class ApplicationClient implements Couche {
    private Couche nextCouche;
    private BufferedReader reader;

    /**
     * Initialise la prochaine couche utilisé
     * @param couche est la prochaine couche de la chaîne de responsabilité
     */
    @Override
    public void SetNext(Couche couche) {
        nextCouche = couche;
    }

    /**
     * Gére les données reçus en paramètre selon le type de requête envoyé
     * @param typeRequest correspond à la requête
     * @param message correspond aux données reçus
     */
    @Override
    public void Handle(String typeRequest, byte[] message) {
        Scanner scanner = new Scanner(System.in);

        //Demande de l'adresse IP du serveur
        if(typeRequest == null) {
            System.out.println("Écrire l'adresse IP du serveur : ");
            String adresseIp = scanner.next();

            nextCouche.Handle("Adresse", adresseIp.getBytes());

        } //Gestion le cas de connexion perdu
        else if (typeRequest.equals("ConnexionPerdu")) {
            String erreur = new String(message, StandardCharsets.UTF_8);
            System.err.println(erreur);

            Transport transport = new Transport();
            transport.setApplication(this);
            transport.setLiaison(new LiaisonDeDonnees(transport, "Client"));
            nextCouche = transport;

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.Handle(null, null);
        } //Demande le fichier à sauvergarder par le serveur
        else {
            System.out.println("\nÉcrire l'emplacement du fichier : ");
            String nomFichier = scanner.next();

            File fichier = new File(nomFichier);
            String contenu = "";
            String ligne;
            try {
                FileReader reader1 = new FileReader(fichier);
                reader = new BufferedReader(reader1);

                System.out.println("\nContenu du fichier :");
                while ((ligne = reader.readLine()) != null) {
                    System.out.println(ligne);
                    contenu += ligne + "\n";
                }

                reader.close();
                contenu = contenu.substring(0, contenu.length()-1);

                System.out.println("\nEnvoie du fichier");

                nextCouche.Handle(nomFichier, contenu.getBytes());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Fonction pour envoyer des transmission avec des erreurs
     * @param transport est la couche à laquelle la transmission commence
     */
    public void EnvoiErreur(Transport transport){
        String msg = "HelloWorld.txt";
        transport.Handle("TestErreurCRC", msg.getBytes());
    }

    public static void main(String[] args) throws IOException {
        ApplicationClient appClient = new ApplicationClient();
        Transport coucheTransport = new Transport();
        LiaisonDeDonnees coucheLiaisonDeDonnee = new LiaisonDeDonnees(coucheTransport, "Client");
        appClient.SetNext(coucheTransport);
        coucheTransport.setApplication(appClient);
        coucheTransport.setLiaison(coucheLiaisonDeDonnee);

        if(args.length == 0) {
            appClient.Handle(null, null);
        } else {
            appClient.EnvoiErreur(coucheTransport);
        }
    }
}
