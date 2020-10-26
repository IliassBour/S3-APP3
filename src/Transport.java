import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class Transport implements Couche {
    private Couche nextCouche;
    private Couche application;
    private Couche liaison;
    private ArrayList<byte[]> paquets = new ArrayList<>();

    @Override
    public void setNext(Couche couche) {
        nextCouche = couche;
    }

    public void setLiaison(Couche liaison) {
        this.liaison = liaison;
    }

    public void setApplication(Couche application) {
        this.application = application;
    }

    @Override
    public void handle(String typeRequest, byte[] message) {
        if(typeRequest.equals("Adresse")) {
            setNext(liaison);
            nextCouche.handle(typeRequest, message);
        } else if (typeRequest.equals("ErreurCRC")) {
            setNext(liaison);
            nextCouche.handle("ENVOI", paquets.get(numeroPaquet(message)+1));
        } else if (typeRequest.equals("RECU")) {
            lireTrame(message);
        } else if(typeRequest.equals("LireFichier")) {
            setNext(application);
            nextCouche.handle("LireFichier",null);
        } else {
            paquets = creerTrame(message, typeRequest);

            setNext(liaison);
            nextCouche.handle("ENVOI", paquets.get(0));//Liaison donnée
        }
    }

    private ArrayList<byte[]> creerTrame(byte[] message, String titre) {
        float tailleFichier = message.length;

        //Boucle pour séparer le fichier en paquet de 200 octets ou moins
        for(int index = 0; index < Math.ceil(tailleFichier/200)+1; index++) {
            String numeroPaquet, taillePaquet, dernierPaquet; //String pour les nombres binaire des variables

            //Création de la partie de l'entête pour le numéro du paquet
            numeroPaquet = "Numero:"+index;
            numeroPaquet = remplissage(numeroPaquet, 16);

            //Création de la partie de l'entête pour le denier du paquet
            dernierPaquet = "Dernier:"+index;
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

    private void lireTrame(byte[] message) {
        byte[] fichier;

        //Vérifie si la transmission est de type Envoi pour le paquet
        if(verificationEnvoie(message)) {
            //Vérifie si le numéro du Paquet est la suite des numéros de paquets recus
            if(verificationNumeroPaquet(message)) {
                paquets.add(message);

                //Vérifie s'il s'agit du dernier numéro de Paquet
                if(verificationDernier(message)) {
                    fichier = creationFichier();
                    String nomFichier = new String(Arrays.copyOfRange(paquets.get(0), 51, paquets.get(0).length),
                            StandardCharsets.UTF_8);

                    //send to application serveur
                    setNext(application);
                    nextCouche.handle(nomFichier, fichier);
                } else {
                    fichier = accuserReception(message);

                    setNext(liaison);
                    nextCouche.handle("ENVOIE", fichier);//send to liaison serveur
                }
            } else {
                //Vérifie s'il s'agit du premier paquet
                if(numeroPaquet(message) == 0) {
                    fichier = accuserReceptionErreur(message);

                    setNext(liaison);
                    nextCouche.handle("ENVOI", fichier);//send to liaison serveur
                } else {
                    fichier = accuserReceptionErreur(message);

                    setNext(liaison);
                    nextCouche.handle("ENVOI", fichier);//send to liaison serveur
                }
            }
        //Vérifie si le paquet est un accusé de reception
        } else if(verificationReception(message)) {
            fichier = paquets.get(numeroPaquet(message)+1);

            setNext(liaison);
            nextCouche.handle("ENVOI", fichier);//send to liaison client
        //Le cas où le paquet est perdu
        } else {
            try {
                fichier = retransmission(message);

                setNext(liaison);
                nextCouche.handle("ENVOI", fichier);//send to liaison client
            } catch (TransmissionErrorException e) {
                setNext(application);
                nextCouche.handle("PaquetPerdu", e.getMessage().getBytes());//send to application client
            }
        }
    }

    private String remplissage(String chaine, int quantite) {
        while(chaine.getBytes().length < quantite) {
            chaine += "-";
        }

        return chaine;
    }

    private int numeroPaquet(byte[] message) {
        String numPaquet = new String(Arrays.copyOfRange(message, 7, 16), StandardCharsets.UTF_8);

        try {
            numPaquet = numPaquet.substring(0, numPaquet.indexOf('-'));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Integer.parseInt(numPaquet);
    }

    private boolean verificationEnvoie(byte[] message) {
        boolean verif = false;
        String trans = new String(Arrays.copyOfRange(message, 33, 39), StandardCharsets.UTF_8);

        if(trans.equals("Envoie")) {
            verif = true;
        }

        return verif;
    }

    private boolean verificationReception(byte[] message) {
        boolean verif = false;
        String trans = new String(Arrays.copyOfRange(message, 33, 37), StandardCharsets.UTF_8);

        if(trans.equals("Recu")) {
            verif = true;
        }

        return verif;
    }

    private boolean verificationNumeroPaquet(byte[] message) {
        boolean verif = false;

        if(paquets.size() > 1) {
            int numPaquet = numeroPaquet(paquets.get(paquets.size()-1));
            int numPaquetNext = numeroPaquet(message);

            if(numPaquet + 1 == numPaquetNext) {
                verif = true;
            }
        }  else {
            int numPaquet = numeroPaquet(message);

            if(numPaquet == 0) {
                verif = true;
            }
        }

        return verif;
    }

    private byte[] retransmission(byte[] message) throws TransmissionErrorException {
        int numeroPaquet = numeroPaquet(message);
        String transmission = new String(Arrays.copyOfRange(message, 40, 41), StandardCharsets.UTF_8);
        int nbEnvoie;
        byte[] paquet;

        paquet = paquets.get(numeroPaquet);

        nbEnvoie = Integer.parseInt(transmission)+1;
        if(nbEnvoie < 4) {
            paquet[42] = (byte) String.valueOf(nbEnvoie).charAt(0);
        } else {
            throw new TransmissionErrorException("Paquet #"+numeroPaquet+" perdu");
        }

        return paquet;
    }

    private boolean verificationDernier(byte[] message) {
        boolean verif = false;

        int numPaquet = numeroPaquet(message);
        int dernier = numeroPaquet(paquets.get(paquets.size()-1));

        if(numPaquet == dernier) {
            verif = true;
        }

        return verif;
    }

    private byte[] accuserReception(byte[] message) {
        byte[] paquet = new byte[51];
        int position = 0;
        String recu = "Recu----";

        for(int index = 0; index < 51; index++) {
            paquet[index] = message[index];
            if(index >= 33 && index <= 40) {
                paquet[position] = (byte) recu.charAt(position);
                position++;
            }
        }

        return paquet;
    }

    private byte[] accuserReceptionErreur(byte[] message) {
        byte[] paquet = new byte[51];
        String trans = new String(Arrays.copyOfRange(message, 40, 41), StandardCharsets.UTF_8);
        String erreur = "Erreur:"+trans;
        String numeroPaquet = "Numero:"+(numeroPaquet(message)-1);
        numeroPaquet = remplissage(numeroPaquet, 16);
        int positionNumero = 0, positionTrans = 0;

        for(int index = 0; index < 51; index++) {
            paquet[index] = message[index];

            if(paquets.size() > 1 && index < 16) {
                paquet[index] = (byte) numeroPaquet.charAt(positionNumero);
                positionNumero++;
            }

            if(index >= 33 && index <= 40) {
                paquet[index] = (byte) erreur.charAt(positionTrans);
                positionTrans++;
            }
        }

        return paquet;
    }

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
