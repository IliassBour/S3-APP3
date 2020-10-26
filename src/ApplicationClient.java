import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ApplicationClient implements Couche {
    private Couche nextCouche;
    private BufferedReader reader;
    @Override
    public void SetNext(Couche couche) {
        nextCouche = couche;
    }

    @Override
    public void Handle(String typeRequest, byte[] message) {
        Scanner scanner = new Scanner(System.in);
        if(typeRequest == null) {
            System.out.println("Écrire l'adresse IP du serveur : ");
            String adresseIp = scanner.next();

            nextCouche.Handle("Adresse", adresseIp.getBytes());
        } else if (typeRequest.equals("PaquetPerdu")) {
            String erreur = new String(message, StandardCharsets.UTF_8);
            System.err.println(erreur);

            Transport transport = new Transport();
            transport.setApplication(this);
            transport.setLiaison(new LiaisonDeDonnees(transport));
            nextCouche = transport;

            this.Handle(null, null);
        } else {
            System.out.println("Écrire l'emplacement du fichier : ");
            String nomFichier = scanner.next();

            File fichier = new File(nomFichier);
            String contenu = "";
            String ligne;
            try {
                FileReader reader1 = new FileReader(fichier);
                reader = new BufferedReader(reader1);

                while ((ligne = reader.readLine()) != null) {
                    System.out.println(ligne);
                    contenu += ligne + "\n";
                }

                reader.close();
                contenu = contenu.substring(0, contenu.length()-1);

                System.out.println("You did it");
                nextCouche.Handle(nomFichier, contenu.getBytes());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        ApplicationClient appClient = new ApplicationClient();
        Transport coucheTransport = new Transport();
        LiaisonDeDonnees coucheLiaisonDeDonnee = new LiaisonDeDonnees(coucheTransport);
        appClient.SetNext(coucheTransport);
        coucheTransport.setApplication(appClient);
        coucheTransport.setLiaison(coucheLiaisonDeDonnee);

        appClient.Handle(null, null);
    }
}
