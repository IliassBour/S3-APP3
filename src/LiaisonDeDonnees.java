import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Liaison de données est la couche Liaison De Données pour le client et le serveur
 * @author Iliass Bouraba et Pedro Maria Scoccimarro
 * @version 1.0
 */
public class LiaisonDeDonnees extends Thread implements Couche {
    private Couche prochain;
    private DatagramSocket socket;
    private String adresseIP;
    private String identity;
    private int portSocket = 25678;
    private long paquetsRecus = 0;
    private long paquetsTransmis = 0;
    private long paquetsTransmisPerdus = 0;
    private long paquetsRecusErreurCRC = 0;

    public LiaisonDeDonnees(){ }

    /**
    * Initialise la prochaine couche utilisé
    * @param couche est la prochaine couche de la chaîne de responsabilité
     * @param id est l'identifiant de l'entité qui crée la couhe. (Client ou Serveur)
    */
    public LiaisonDeDonnees(Couche couche, String id){
        this.prochain = couche;
        this.identity = id;
    }

    /**
     * Initialise la prochiane couche de cette couche
     * @param couche est la couche suivante
     */
    public void SetNext(Couche couche){
        this.prochain = couche;
    }

    /**
     * Gére les données reçus en paramètre selon le type de requête envoyé
     * @param typeRequest correspond à la requête
     * @param message correspond aux données reçus
     */
    public void Handle(String typeRequete, byte[] message) {    //TypeRequete = {"Envoi", "Recu", "Adresse"}

        //Ouvrir socket vide
        try {
            this.socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        System.out.println("Liaison de Données requête: "+typeRequete);
        try{
            //Requête
            switch (typeRequete){
                //Envoi d'un message
                case "Envoi":
                    Envoi(message);
                    break;

                //Reception d'un message
                case "Recu":
                    Recu();
                    break;

                //Reception de l'adresse IP
                case "Adresse":
                    SetAdresseIP(message);
                    this.prochain.Handle("LireFichier", null);
                    break;

                case "PaquetPerdu":
                    paquetsTransmisPerdus++;
                    Envoi(message);
                    break;

                case "ProchainFichierServeur":
                    ProchainFichier();
                    break;

                case "TestErreurCRC":
                    TestErreurCRC(message);
                    break;

                case "TestRecuErreur":
                    TestRecuErreur();
                    break;
            }
        }
        catch (IOException ioe){
            ioe.printStackTrace();
        }
    }

    /**
     * Ajoute la valeur CRC à la fin du message et l'envoi au travers du socket
     * @param message correspond aux données reçus
     */
    public void Envoi(byte[] message) throws IOException{
        //Calcul CRC du message
        CRC32 crc = new CRC32();
        crc.reset();
        crc.update(message);

        //Conversion CRC long => byte[]
        byte[] crcBytes = new BigInteger(Long.toBinaryString(crc.getValue()), 2).toByteArray();
        System.out.println("crcBytes: "+new String(crcBytes));
        System.out.println("crcBytes length: "+crcBytes.length);

        //Filler
        if(crcBytes.length > 4){
            crcBytes = new byte[]{crcBytes[1],crcBytes[2],crcBytes[3],crcBytes[4]};
        }
        else if(crcBytes.length < 4){
            switch (crcBytes.length){
                case 3:
                    crcBytes = new byte[]{0,crcBytes[0],crcBytes[1],crcBytes[2]};
                    break;

                case 2:
                    crcBytes = new byte[]{0,0,crcBytes[0],crcBytes[1]};
                    break;

                case 1:
                    crcBytes = new byte[]{0,0,0,crcBytes[0]};
                    break;

                case 0:
                    crcBytes = new byte[]{0,0,0,0};
                    break;
            }
        }

        //Ajout array de byte CRC au message
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        try{
            outputStream.write(crcBytes);
            outputStream.write(message);
        }
        catch(Exception e){
            e.printStackTrace();
        }
        byte[] messageToSend = outputStream.toByteArray();

        if(messageToSend.length > 255){
            System.out.println("ERREUR TAILLE");
            System.out.println("Message.length: "+message.length);
            System.out.println("CRCBytes.lenght: "+crcBytes.length);
        }

        //Envoyer messageToSend à travers des Sockets
        InetAddress address = InetAddress.getByName(this.adresseIP); //Addresse IP

        DatagramPacket packet = new DatagramPacket(messageToSend, messageToSend.length, address, this.portSocket);
        this.socket.send(packet);
        Log("Envoi");
        this.paquetsTransmis++;

        //Écouter pour réponse
        Handle("Recu", null);
    }

    /**
     * Recoit les données au travers du socket et fait la vérification CRC
     */
    public void Recu() throws IOException{
        byte[] messageRecu = new byte[256];
        DatagramPacket packet = new DatagramPacket(messageRecu, messageRecu.length);

        //Ouvrir socket
        this.socket = new DatagramSocket(this.portSocket);

        System.out.println("Wait");
        this.socket.receive(packet); //Attend le reception d'un message
        System.out.println("Received");
        this.socket.close(); //Fermer Socket
        this.paquetsRecus++;

        byte[] message = packet.getData();

        if(verificationVide(message)) {
            prochain.Handle("LireFichier", null);
        } else {
            //Création du CRC de vérification à partir du message
            int finMessage = 0;
            for (int i = 4; i < 256; i++) {
                if (message[i] == 0) {
                    System.out.println("Message["+i+"]: "+new String(new byte[]{message[i]}));
                    finMessage = i-1;
                    break;
                }
            }

            //Créer message pour crcVérif
            byte[] crcVerifMessage = Arrays.copyOfRange(message, 4, finMessage+1);
            System.out.println("MESSAGE RECU: "+new String(crcVerifMessage));
            System.out.println("Message length: "+crcVerifMessage.length);

            CRC32 crcVerif = new CRC32();
            crcVerif.reset();
            crcVerif.update(crcVerifMessage);

            //Prise du CRC inclut dans le message
            byte[] messageCRCBytes = {message[0], message[1], message[2], message[3]};
            //Long messageCRCValue = new BigInteger(messageCRCBytes).longValue();
            long messageCRCValue = 0;
            for (int i = 0; i < messageCRCBytes.length; i++) {
                messageCRCValue = (messageCRCValue << 8) + (messageCRCBytes[i] & 0xff);
            }

            //Erreur CRC
            if (/*false*/messageCRCValue != crcVerif.getValue()) {
                this.paquetsRecusErreurCRC++;
                Log("ErreurCRC");
                System.out.println("Erreur CRC");

                //Send Error to Transport
                //Retrait du CRC du message
                byte[] messageToPass = Arrays.copyOfRange(message, 4, finMessage+1);

                //Envoi du message dans Transport
                this.prochain.Handle("ErreurCRC", messageToPass);

            }
            //Réussite CRC
            else {
                Log("Recu");
                System.out.println("Réussite CRC");

                //Retrait du CRC du message
                byte[] messageToPass = Arrays.copyOfRange(message, 4, finMessage+1);

                //Envoi du message dans Transport
                this.prochain.Handle("Recu", messageToPass);
            }
        }
    }

    /**
     * Initialise l'adresse IP utilisée pour la communication
     * @param message L'adresse IP à se connecter à.
     */
    public void SetAdresseIP(byte[] message){
        this.adresseIP = new String(message);
    }

    /**
     * Écrit l'action comises dans un log
     * @param action correspond à l'action effectué
     */
    public void Log(String action) throws IOException{
        File file = new File("liaisonDeDonnees.txt");
        FileWriter fileW = new FileWriter(file, true);

        if(file.exists()){
            BufferedWriter writer = new BufferedWriter(fileW);

            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            String log;
            switch (action){
                case "Debut":
                    log = "---DÉBUT DE LA TRANSMISSION---\n\n";
                    writer.write(log);
                    break;

                case "Envoi":
                    log = timestamp+"  -  ID: "+ this.identity +" ACTION: Envoi d'un paquet\n";
                    writer.write(log);
                    break;

                case "Recu":
                    log = timestamp+"  -  ID: "+ this.identity +" ACTION: Reception d'un paquet\n";
                    writer.write(log);
                    break;

                case "ErreurCRC":
                    log = timestamp+"  -  ID: "+ this.identity +" ACTION: Reception d'un paquet avec erreur CRC\n";
                    writer.write(log);
                    break;

                case "Stats":
                    log = "\n\n--STATISTIQUES--\n\n"
                        + "Paquets recus: "+this.paquetsRecus+"\n"
                        + "Paquets erreur CRC:"+this.paquetsRecusErreurCRC+" ("+ String.format("%.2f", ((double)this.paquetsRecusErreurCRC/(double)this.paquetsRecus)*100)+"%)\n"
                        + "Paquets transmis: "+this.paquetsTransmis+"\n"
                        + "Paquets perdus: "+this.paquetsTransmisPerdus+" ("+ String.format("%.2f", ((double)this.paquetsTransmisPerdus/(double)this.paquetsTransmis)*100)+"%)\n\n";
                    writer.write(log);
                    break;

                case "Fin":
                    log = "---FIN DE LA TRANSMISSION---\n\n\n";
                    writer.write(log);
                    //Reset des variables
                    this.paquetsRecus = 0;
                    this.paquetsRecusErreurCRC = 0;
                    this.paquetsTransmis = 0;
                    this.paquetsRecusErreurCRC = 0;
                    break;
            }
            writer.close();
        }
    }

    /**
     * Fin du transfert du fichier, demande l'envoi d'un nouveau fichier.
     */
    private void ProchainFichier() throws IOException {
        InetAddress address = InetAddress.getByName(this.adresseIP);
        byte[] messageVide = new byte[256];
        DatagramPacket packet = new DatagramPacket(messageVide, messageVide.length, address, this.portSocket);
        this.socket.send(packet);
        this.paquetsTransmis++;
        Log("Envoi");
        Log("Stats");
        Log("Fin");

        //Écouter pour réponse
        Handle("Recu", null);
    }

    /**
     * Vérifie si le message ne contient aucune information
     * @param message message à vérifier
     */
    private boolean verificationVide(byte[] message) {
        boolean verif = true;

        for(int index = 0; index < message.length; index++) {
            if(message[index] != 0) {
                verif = false;
                break;
            }
        }

        return verif;
    }

    /**
     * Fonction de test qui ajoute une valeur CRC éronné
     * @param message message à envoyé
     */
    private void TestErreurCRC(byte[] message) throws IOException{
        byte[] fauxCRC = {40,20,30,10};

        //Ajout array de byte CRC au message
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        try{
            outputStream.write(fauxCRC);
            outputStream.write(message);
        }
        catch(Exception e){
            e.printStackTrace();
        }
        byte[] messageToSend = outputStream.toByteArray();

        //Envoi dans le Socket
        InetAddress address = InetAddress.getByName(this.adresseIP); //Addresse IP
        DatagramPacket packet = new DatagramPacket(messageToSend, messageToSend.length, address, this.portSocket);
        this.socket.send(packet);

        //Écouter pour réponse
        Handle("TestRecuErreur", null);
    }

    /**
     * Fonction de test qui recoit l'erreur et la transmet à la couche Transport.
     */
    private void TestRecuErreur() throws IOException{
        byte[] messageRecu = new byte[256];
        DatagramPacket packet = new DatagramPacket(messageRecu, messageRecu.length);

        //Ouvrir socket
        this.socket = new DatagramSocket(this.portSocket);

        System.out.println("Wait");
        this.socket.receive(packet); //Attend le reception d'un message
        System.out.println("Received");
        this.socket.close(); //Fermer Socket
        this.paquetsRecus++;

        byte[] message = packet.getData();

        if(verificationVide(message)) {
            prochain.Handle("LireFichier", null);
        } else {
            //Création du CRC de vérification à partir du message
            int finMessage = 0;
            for (int i = 4; i < 256; i++) {
                if (message[i] == 0) {
                    System.out.println("Message["+i+"]: "+new String(new byte[]{message[i]}));
                    finMessage = i-1;
                    break;
                }
            }
            System.out.println("finMessage: "+finMessage);

            System.out.print(String.format("%8s", Integer.toBinaryString(message[0] & 0xFF)).replace(' ', '0'));
            System.out.print(' ');
            System.out.print(String.format("%8s", Integer.toBinaryString(message[1] & 0xFF)).replace(' ', '0'));
            System.out.print(' ');
            System.out.print(String.format("%8s", Integer.toBinaryString(message[2] & 0xFF)).replace(' ', '0'));
            System.out.print(' ');
            System.out.println(String.format("%8s", Integer.toBinaryString(message[3] & 0xFF)).replace(' ', '0'));

            //Créer message pour crcVérif
            byte[] crcVerifMessage = Arrays.copyOfRange(message, 4, finMessage+1);
            System.out.println("MESSAGE RECU: "+new String(crcVerifMessage));
            System.out.println("Message length: "+crcVerifMessage.length);

            CRC32 crcVerif = new CRC32();
            crcVerif.reset();
            crcVerif.update(crcVerifMessage);

            //Prise du CRC inclut dans le message
            byte[] messageCRCBytes = {message[0], message[1], message[2], message[3]};
            //Long messageCRCValue = new BigInteger(messageCRCBytes).longValue();
            long messageCRCValue = 0;
            for (int i = 0; i < messageCRCBytes.length; i++) {
                messageCRCValue = (messageCRCValue << 8) + (messageCRCBytes[i] & 0xff);
            }

            System.out.println("CRC recu Bytes: "+new String(messageCRCBytes));
            System.out.println("CRC recu Value: " + crcVerif.getValue());

            //Erreur CRC
            if (/*false*/messageCRCValue != crcVerif.getValue()) {
                this.paquetsRecusErreurCRC++;
                Log("ErreurCRC");
                System.out.println("Erreur CRC");
                System.out.println("messageCRCValue: " + messageCRCValue);
                System.out.println("crcVerif value: " + crcVerif.getValue());

                //Send Error to Transport
                //Retrait du CRC du message
                byte[] messageToPass = Arrays.copyOfRange(message, 4, finMessage+1);

                //Envoi du message dans Transport
                this.prochain.Handle("TestErreurCRC", messageToPass);

            }
            //Réussite CRC
            else {
                Log("Recu");
                System.out.println("Réussite CRC");

                //Retrait du CRC du message
                byte[] messageToPass = Arrays.copyOfRange(message, 4, finMessage+1);

                //Envoi du message dans Transport
                this.prochain.Handle("TestErreurCRC", messageToPass);
            }
        }
    }

    /**
     * Démarre le thread
     */
    public void run(){
        System.out.println("Run");
        Handle("Recu",null);
    }
}
