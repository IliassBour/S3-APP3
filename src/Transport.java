import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Tranport est la couche Transport pour le client et le serveur
 * @author Iliass Bouraba et Pedro Maria Scoccimarro
 * @version 1.0
 */
public class Transport implements Couche {
    private Couche nextCouche;
    private Couche application;
    private LiaisonDeDonnees liaison;
    private ArrayList<byte[]> paquets = new ArrayList<>();
    private int erreur = 0;

    /**
     * Initialise la prochaine couche utilisé
     * @param couche est la prochaine couche de la chaîne de responsabilité
     */
    @Override
    public void SetNext(Couche couche) {
        nextCouche = couche;
    }

    /**
     * Initialise la couche LiaisonDeDonnees de cette couche
     * @param liaison est la couche de liaison de donnée
     */
    public void setLiaison(LiaisonDeDonnees liaison) {
        this.liaison = liaison;
    }

    /**
     * Initialise la couche Application de cette couche
     * @param application est la couche d'application
     */
    public void setApplication(Couche application) {
        this.application = application;
    }

    /**
     * Gére les données reçus en paramètre selon le type de requête envoyé
     * @param typeRequest correspond à la requête
     * @param message correspond aux données reçus
     */
    @Override
    public void Handle(String typeRequest, byte[] message) {
        //Envoie de l'adresse IP à la couche LiaisonDeDonnees
        if(typeRequest.equals("Adresse")) {
            SetNext(liaison);
            nextCouche.Handle(typeRequest, message);
        } //Gére le cas où il y a eu une erreur de CRC à la couche liaison de données du côté du Serveur
        else if (typeRequest.equals("ErreurCRC")) {
            erreur++;
            if(erreur > 3) {
                paquets = new ArrayList<>();
            }

            SetNext(liaison);
            nextCouche.Handle("Envoi", accuserReceptionErreur(message));
        } //Reçois un paquet de paquet de la couche LiaisonDeDonnees pour le Serveur et le Client
        else if (typeRequest.equals("Recu")) {
            lireTrame(message);
        } //Demande à la couche ApplicationClient de lire un nouveau fichier
        else if (typeRequest.equals("LireFichier")) {
            paquets = new ArrayList<>();
            SetNext(application);
            nextCouche.Handle(typeRequest,null);
        } //Demande à la couche LiaisonDeDonnees du côté du Serveur d'envoyer un nouveau fichier
        else if (typeRequest.equals("ProchainFichierServeur")) {
            paquets = new ArrayList<>();

            SetNext(liaison);
            nextCouche.Handle(typeRequest, message);
        } //Demande à la couche LiaisonDeDonnees du côté Client d'envoyer une transmission avec une erreur
        else if (typeRequest.equals("TestErreurCRC")) {
            String msg = "Hello World!";
            paquets = creerTrame(msg.getBytes(), new String(message));

            SetNext(liaison);
            try {
                nextCouche.Handle("TestErreurCRC", retransmission(paquets.get(0)));//Liaison donnée
            } catch (TransmissionErrorException e) {
                SetNext(application);
                nextCouche.Handle("PaquetPerdu", e.getMessage().getBytes());//send to application client
            }

        } //Création des paquets à partir des données reçu de la couche ApplicationClient
        else {
            paquets = creerTrame(message, typeRequest);

            //Envoi du premier paquet à la couche LiaisonDeDonnees du côté Client
            SetNext(liaison);
            nextCouche.Handle("Envoi", paquets.get(0));
        }
    }

    /**
     * Crée les paquets de 200 octets de données ou moins
     * @param message les données à séparer en paquets
     * @param titre le titre du fichier des données à séparer en paquets
     * @return un ArrayList de tous les paquets à envoyer au Serveur
     */
    private ArrayList<byte[]> creerTrame(byte[] message, String titre) {
        float tailleFichier = message.length;
        titre = titre.substring(titre.lastIndexOf('/')+1);
        titre = titre.substring(titre.lastIndexOf('\\')+1);

        //Boucle pour séparer le fichier en paquet de 200 octets ou moins
        for(int index = 0; index < Math.ceil(tailleFichier/200)+1; index++) {
            String numeroPaquet, taillePaquet, dernierPaquet; //String pour les nombres binaire des variables

            //Création de la partie de l'entête pour le numéro du paquet
            numeroPaquet = "Numero:"+index;
            numeroPaquet = remplissage(numeroPaquet, 16);

            //Création de la partie de l'entête pour le denier du paquet
            dernierPaquet = "Dernier:"+(int) Math.ceil(tailleFichier/200);
            dernierPaquet = remplissage(dernierPaquet, 17);

            //Création de la partie de l'entête pour la transmision
            String transmission = "Envoie:1";//8 octect

            //Création de la partie de l'entête pour la taille du paquet
            if(index == 0) {
                // les octets pour le titre + 52 octects pour l'entête
                taillePaquet = "Taille:"+(titre.getBytes().length + 51);
            } else if(index == Math.ceil(tailleFichier/200)) {
                // les octets pour les données restantes du fichier + 52 octects pour l'entête
                taillePaquet = "Taille:"+((int) tailleFichier - ((index-1) * 200) + 51);
            } else {
                // 200 octets pour les données + 52 octects pour l'entête
                taillePaquet = "Taille:251"; //10 octect
            }
            taillePaquet = remplissage(taillePaquet, 10);


            //Insère le paquet dans l'ArrayList
            ByteArrayOutputStream paquet = new ByteArrayOutputStream();
            try {
                paquet.write(numeroPaquet.getBytes());
                paquet.write(dernierPaquet.getBytes());
                paquet.write(transmission.getBytes());
                paquet.write(taillePaquet.getBytes());

                if(index == 0) {
                    paquet.write(titre.getBytes());
                } else if(index == Math.ceil(tailleFichier/200)) {
                    paquet.write(Arrays.copyOfRange(message, (index-1)*200, (int) tailleFichier));
                } else {
                    paquet.write(Arrays.copyOfRange(message, (index-1)*200, index*200));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            paquets.add(paquet.toByteArray());
        }

        return paquets;
    }

    /**
     * Lit différent champs de l'entête pour déterminer l'action à faire avec le paquet
     * @param message est les données du paquet
     */
    private void lireTrame(byte[] message) {
        byte[] fichier;

        //Vérifie si la transmission est de type Envoi pour le paquet
        if(verificationEnvoie(message)) {
            //Vérifie si le numéro du paquet est la suite des numéros de paquets recus
            if(verificationNumeroPaquet(message)) {
                //Ajoute le paquet dans l'ArrayList des paquets du Serveur
                paquets.add(message);

                //Vérifie s'il s'agit du dernier numéro de paquet
                if(verificationDernier(message)) {
                    fichier = creationFichier();
                    String nomFichier = new String(Arrays.copyOfRange(paquets.get(0), 51, paquets.get(0).length),
                            StandardCharsets.UTF_8);

                    //Envoie les données assemblé à la couche ApplicationServeur
                    SetNext(application);
                    nextCouche.Handle(nomFichier, fichier);
                }//Le cas où ce n'est pas le denier paquet
                else {
                    fichier = accuserReception(message);

                    //Envoie du paquet d'accuser de Reception à la couche LiaisonDeDonnes du côté Serveur
                    SetNext(liaison);
                    nextCouche.Handle("Envoi", fichier);
                }
            } //Le cas où il n'est la suite de paquets reçus
            else {
                erreur++;
                if(erreur > 3) {
                    paquets = new ArrayList<>();
                }

                //Vérifie s'il s'agit du premier paquet
                if(numeroPaquet(message) == 0) {
                    fichier = accuserReceptionErreur(message);

                    /* Envoie un accusé de reception de type Erreur du paquet message
                       à LiaisonDeDonnes du côté Serveur
                     */
                    SetNext(liaison);
                    nextCouche.Handle("Envoi", fichier);
                } //Le cas où ce n'est pas le premier paquet
                else {
                    String numeroPaquet = "Numero:"+(numeroPaquet(message)-1);
                    numeroPaquet = remplissage(numeroPaquet, 16);
                    int positionNumero = 0;
                    for(int index = 0; index < 16; index++) {
                        message[index] = (byte) numeroPaquet.charAt(positionNumero);
                        positionNumero++;
                    }
                    fichier = accuserReceptionErreur(message);

                    /* Envoie un accusé de reception de type Erreur du paquet précedent
                       du paquet message à LiaisonDeDonnes du côté Serveur
                     */
                    SetNext(liaison);
                    nextCouche.Handle("Envoi", fichier);//send to liaison serveur
                }
            }
        }  //Vérifie si le paquet est un accusé de reception de type Reçu
        else if(verificationReception(message)) {
            fichier = paquets.get(numeroPaquet(message)+1);

            //Envoi du paquet suivant à LiaisonDeDonnes du côté Client
            SetNext(liaison);
            nextCouche.Handle("Envoi", fichier);
        }  //Le cas où le paquet est perdu
        else {
            try {
                fichier = retransmission(message);

                //Envoie la retransmission du paquet
                SetNext(liaison);
                nextCouche.Handle("Envoi", fichier);//send to liaison client
            } catch (TransmissionErrorException e) {
                //Envoi une demande de nouvelle connexion à la couche ApplicationClient
                SetNext(application);
                nextCouche.Handle("ConnexionPerdu", e.getMessage().getBytes());
            }
        }
    }

    /**
     * Remplit de caractère un champs de l'entête pour qu'il soit de la taille envoyer en paramètre
     * @param chaine est le champs de l'entête avant le remplissage
     * @param taille est la taille finale du champs de l'entête
     * @return le champs de l'entête après le remplissage
     */
    private String remplissage(String chaine, int taille) {
        while(chaine.getBytes().length < taille) {
            chaine += "-";
        }

        return chaine;
    }

    /**
     * Trouve le numéro du paquet dans l'entête de celui-ci
     * @param message est les données du paquet
     * @return le numéro du paquet
     */
    private int numeroPaquet(byte[] message) {
        String numPaquet = new String(Arrays.copyOfRange(message, 7, 16), StandardCharsets.UTF_8);

        try {
            numPaquet = numPaquet.substring(0, numPaquet.indexOf('-'));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Integer.parseInt(numPaquet);
    }

    /**
     * Vérifie si le type de transmission du paquet est de type Envoie
     * @param message est les données du paquet
     * @return true si le type de transmission est de type Envoie aussi non false est retourné
     */
    private boolean verificationEnvoie(byte[] message) {
        boolean verif = false;
        String trans = new String(Arrays.copyOfRange(message, 33, 39), StandardCharsets.UTF_8);

        if(trans.equals("Envoie")) {
            verif = true;
        }

        return verif;
    }

    /**
     * Vérifie si le type de transmission du paquet est de type Envoie.
     * Donc, si le paquet est l'accuser de reception du serveur.
     * @param message est les données du paquet
     * @return true si le type de transmission est de type Recu aussi non false est retourné
     */
    private boolean verificationReception(byte[] message) {
        boolean verif = false;
        String trans = new String(Arrays.copyOfRange(message, 33, 37), StandardCharsets.UTF_8);

        if(trans.equals("Recu")) {
            verif = true;
        }

        return verif;
    }

    /**
     * Vérifie s'il y a eu une perte de paquets lors de la transmission
     * @param message est les données du paquet
     * @return true s'il n'y a pas eu de perte et false dans le cas contraire
     */
    private boolean verificationNumeroPaquet(byte[] message) {
        boolean verif = false;

        //Vérifie si la couche Transport du côté Serveur à déjà reçu des paquets
        if(paquets.size() >= 1) {
            int numPaquet = numeroPaquet(paquets.get(paquets.size()-1));
            int numPaquetNext = numeroPaquet(message);

            if(numPaquet + 1 == numPaquetNext) {
                verif = true;
            }
        }//Le cas où il n'en a pas reçu
        else {
            int numPaquet = numeroPaquet(message);

            if(numPaquet == 0) {
                verif = true;
            }
        }

        return verif;
    }

    /**
     * Vérifie si le numéro du paquet correspond au denier numéro de paquet
     * @param message est les données du paquet
     * @return
     */
    private boolean verificationDernier(byte[] message) {
        boolean verif = false;

        int numPaquet = numeroPaquet(message);
        String dernier = new String(Arrays.copyOfRange(message, 24, 32), StandardCharsets.UTF_8);

        try {
            dernier = dernier.substring(0, dernier.indexOf('-'));
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(numPaquet == Integer.parseInt(dernier)) {
            verif = true;
        }

        return verif;
    }

    /**
     * Retransmet le paquet correspondant au numéro du paquet de l'accusé de reception de type Erreur
     * et vérifie que le nombre d'erreur n'est pas plus grand 3
     * @param message est les données du paquet de l'accusé de reception de type Erreur
     * @return les données du paquet à retransmettre
     * @throws TransmissionErrorException quand le nombre d'erreur est plus grand 3
     */
    private byte[] retransmission(byte[] message) throws TransmissionErrorException {
        int numeroPaquet = numeroPaquet(message);
        String transmission = new String(Arrays.copyOfRange(message, 40, 41), StandardCharsets.UTF_8);
        byte[] paquet;

        paquet = paquets.get(numeroPaquet);

        erreur++;
        if(erreur < 4) {
            String recu = "Envoie:"+erreur;
            int position = 0;

            for(int index = 0; index < 51; index++) {
                paquet[index] = message[index];
                if(index >= 33 && index <= 40) {
                    paquet[index] = (byte) recu.charAt(position);
                    position++;
                }
            }
        } else {
            throw new TransmissionErrorException("Connexion perdue");
        }

        return paquet;
    }

    /**
     * Créé un accusé de reception pour le paquet reçu
     * @param message est les données du paquet reçu
     * @return les données de l'accusé de reception
     */
    private byte[] accuserReception(byte[] message) {
        byte[] paquet = new byte[51];
        int position = 0;
        String recu = "Recu----";

        for(int index = 0; index < 51; index++) {
            paquet[index] = message[index];
            if(index >= 33 && index <= 40) {
                paquet[index] = (byte) recu.charAt(position);
                position++;
            }
        }

        return paquet;
    }

    /**
     * Créé un accusé de reception de type erreur pour le paquet reçu
     * @param message est les données du paquet reçu
     * @return les données de l'accusé de reception de type erreur
     */
    private byte[] accuserReceptionErreur(byte[] message) {
        byte[] paquet = new byte[51];
        String trans = new String(Arrays.copyOfRange(message, 40, 41), StandardCharsets.UTF_8);
        String erreur = "Erreur:"+trans;
        int positionTrans = 0;

        for(int index = 0; index < 51; index++) {
            paquet[index] = message[index];

            if(index >= 33 && index <= 40) {
                paquet[index] = (byte) erreur.charAt(positionTrans);
                positionTrans++;
            }
        }

        return paquet;
    }

    /**
     * Assemble les données des paquets pour reconstituer le fichier
     * @return les données du fichier assemblé
     */
    private byte[] creationFichier() {
        byte[] fichier;
        ByteArrayOutputStream paquet = new ByteArrayOutputStream();
        for(int index = 1 ; index < paquets.size(); index++) {
            try {
                if(index < paquets.size() - 1) {
                    paquet.write(Arrays.copyOfRange(paquets.get(index), 51, 251));
                } else {
                    paquet.write(Arrays.copyOfRange(paquets.get(index), 51, 251));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        fichier = paquet.toByteArray();

        return fichier;
    }
}
