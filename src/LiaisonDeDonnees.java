import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.zip.CRC32;

public class LiaisonDeDonnees extends Thread implements Couche {
    private Couche prochain;
    private DatagramSocket socket;
    private String adresseIP;
    private int portSocket = 25678;
    private long paquetsRecus = 0;
    private long paquetsTransmis = 0;
    private long paquetsTransmisPerdus = 0;
    private long paquetsRecusErreurCRC = 0;

    //private byte[] polynome = {1,0,0,0,0,0,1,0,0,1,1,0,0,0,0,0,1,0,0,0,1,1,1,0,1,0,0,1,1,0,1,1,1};

    public LiaisonDeDonnees(){ }

    public LiaisonDeDonnees(Couche couche){
        this.prochain = couche;
    }

    public void SetNext(Couche couche){
        this.prochain = couche;
    }

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
            }
        }
        catch (IOException ioe){
            ioe.printStackTrace();
        }
    }

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
        /*
        System.out.println("MESSAGE ENVOYE: "+new String(message));
        System.out.println("Message length: "+message.length);

        System.out.println("CRC envoi Bytes: "+new String(crcBytes));
        System.out.println("CRC envoi Value: "+crc.getValue());


        System.out.print(String.format("%8s", Integer.toBinaryString(messageToSend[messageToSend.length-4] & 0xFF)).replace(' ', '0'));
        System.out.print(' ');
        System.out.print(String.format("%8s", Integer.toBinaryString(messageToSend[messageToSend.length-3] & 0xFF)).replace(' ', '0'));
        System.out.print(' ');
        System.out.print(String.format("%8s", Integer.toBinaryString(messageToSend[messageToSend.length-2] & 0xFF)).replace(' ', '0'));
        System.out.print(' ');
        System.out.println(String.format("%8s", Integer.toBinaryString(messageToSend[messageToSend.length-1] & 0xFF)).replace(' ', '0'));
        */

        DatagramPacket packet = new DatagramPacket(messageToSend, messageToSend.length, address, this.portSocket);
        this.socket.send(packet);
        Log("Envoi");
        this.paquetsTransmis++;

        //Écouter pour réponse
        Handle("Recu", null);
    }

    public void Recu() throws IOException{

        //Ouvrir socket
        this.socket = new DatagramSocket(this.portSocket);

        byte[] messageRecu = new byte[256];
        DatagramPacket packet = new DatagramPacket(messageRecu, messageRecu.length);
        //.out.println("Wait");
        this.socket.receive(packet); //Attend le reception d'un message
        //System.out.println("Received");
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

    public void SetAdresseIP(byte[] message){
        this.adresseIP = new String(message);
    }

    public void Log(String action) throws IOException{
        File file = new File("liaisonDeDonnees.txt");
        FileWriter fileW = new FileWriter(file, true);

        if(file.exists()){
            BufferedWriter writer = new BufferedWriter(fileW);

            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            String log;
            switch (action){
                case "Envoi":
                    log = timestamp+"  -  ACTION: Envoi d'un paquet\n";
                    writer.write(log);
                    break;

                case "Recu":
                    log = timestamp+"  -  ACTION: Reception d'un paquet\n";
                    writer.write(log);
                    break;

                case "ErreurCRC":
                    log = timestamp+"  -  ACTION: Reception d'un paquet avec erreur CRC\n";
                    writer.write(log);
                    break;

                case "Stats":
                    log = "\n\n---STATISTIQUES---\n\n"
                        + "Paquets recus: "+this.paquetsRecus+"\n"
                        + "Paquets erreur CRC:"+this.paquetsRecusErreurCRC+" ("+(this.paquetsRecusErreurCRC/this.paquetsRecus)*100+"%)\n"
                        + "Paquets transmis: "+this.paquetsTransmis+"\n"
                        + "Paquets perdus: "+this.paquetsTransmisPerdus+" ("+(this.paquetsTransmisPerdus/this.paquetsTransmis)*100+"%)\n\n\n\n";
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

    private void ProchainFichier() throws IOException {
        InetAddress address = InetAddress.getByName(this.adresseIP);
        byte[] messageVide = new byte[256];
        DatagramPacket packet = new DatagramPacket(messageVide, messageVide.length, address, this.portSocket);
        this.socket.send(packet);
        Log("Envoi");
        Log("Stats");
        this.paquetsTransmis++;

        //Écouter pour réponse
        Handle("Recu", null);
    }

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
        Handle("Recu", null);
    }

    public void run(){
        System.out.println("Run");
        Handle("Recu",null);
    }
}
