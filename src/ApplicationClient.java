import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ApplicationClient implements Couche {
    private Couche nextCouche;
    private BufferedReader reader;
    @Override
    public void setNext(Couche couche) {
        nextCouche = couche;
    }

    @Override
    public void handle(String typeRequest, byte[] message) {
        Scanner scanner = new Scanner(System.in);
        if(typeRequest == null) {
            System.out.println("Écrire l'adresse IP du serveur : ");
            String adresseIp = scanner.next();

            nextCouche.handle("Adresse", adresseIp.getBytes());
        } else if (typeRequest.equals("PaquetPerdu")) {
            String erreur = new String(message, StandardCharsets.UTF_8);
            System.err.println(erreur);

            Transport transport = new Transport();
            transport.setApplication(this);
            //transport.setLiaison(new Liaison());
            nextCouche = transport;

            this.handle(null, null);
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
                    contenu += ligne + "\n";
                }

                reader.close();
                contenu = contenu.substring(0, contenu.length()-1);
                nextCouche.handle(nomFichier, contenu.getBytes());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}