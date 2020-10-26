import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.sql.Timestamp;
import java.util.Arrays;
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
            }
        }
        catch (IOException ioe){
            ioe.printStackTrace();
        }
    }

    public void Envoi(byte[] message) throws IOException{
        //Calcul CRC du message
        CRC32 crc = new CRC32();
        crc.update(message);
        //Conversion CRC long => byte[]
        byte[] crcBytes = new BigInteger(Long.toBinaryString(crc.getValue()), 2).toByteArray();

        //Ajout array de byte CRC au message
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        try{
            outputStream.write(message);
            outputStream.write(crcBytes);
        }
        catch(Exception e){
            //Erreur
        }
        byte[] messageToSend = outputStream.toByteArray();

        //Envoyer messageToSend à travers des Sockets
        InetAddress address = InetAddress.getByName(this.adresseIP); //Addresse IP

        System.out.print(String.format("%8s", Integer.toBinaryString(messageToSend[messageToSend.length-4] & 0xFF)).replace(' ', '0'));
        System.out.print(' ');
        System.out.print(String.format("%8s", Integer.toBinaryString(messageToSend[messageToSend.length-3] & 0xFF)).replace(' ', '0'));
        System.out.print(' ');
        System.out.print(String.format("%8s", Integer.toBinaryString(messageToSend[messageToSend.length-2] & 0xFF)).replace(' ', '0'));
        System.out.print(' ');
        System.out.println(String.format("%8s", Integer.toBinaryString(messageToSend[messageToSend.length-1] & 0xFF)).replace(' ', '0'));


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
        System.out.println("Wait");
        socket.receive(packet); //Attend le reception d'un message
        System.out.println("Received");
        this.socket.close();
        this.paquetsRecus++;

        byte[] message = packet.getData();

        if(verificationVide(message)) {
            prochain.Handle("LireFichier", null);
        } else {
            //Création du CRC de vérification à partir du message
            int finCRC = 0;
            for (int i = 255; i > 0; i--) {
                if (message[i] != 0) {
                    finCRC = i;
                    break;
                }
            }

            System.out.println(new String(message));

            System.out.println("FinCRC: " + finCRC);

            System.out.print(String.format("%8s", Integer.toBinaryString(message[finCRC - 3] & 0xFF)).replace(' ', '0'));
            System.out.print(' ');
            System.out.print(String.format("%8s", Integer.toBinaryString(message[finCRC - 2] & 0xFF)).replace(' ', '0'));
            System.out.print(' ');
            System.out.print(String.format("%8s", Integer.toBinaryString(message[finCRC - 1] & 0xFF)).replace(' ', '0'));
            System.out.print(' ');
            System.out.println(String.format("%8s", Integer.toBinaryString(message[finCRC] & 0xFF)).replace(' ', '0'));

            System.out.println("Message length: " + message.length);

            CRC32 crcVerif = new CRC32();
            crcVerif.update(message, 0, message.length - (message.length - finCRC) - 4);

            //Prise du CRC inclut dans le message
            byte[] messageCRCBytes = {message[finCRC - 3], message[finCRC - 2], message[finCRC - 1], message[finCRC]};
            Long messageCRCValue = new BigInteger(messageCRCBytes).longValue();

            //Erreur CRC
            if (false/*messageCRCValue != crcVerif.getValue()*/) {
                this.paquetsRecusErreurCRC++;
                Log("ErreurCRC");
                System.out.println("Erreur");
                System.out.println("messageCRCValue: " + messageCRCValue);
                System.out.println("crcVerif value: " + crcVerif.getValue());

                //Send Error to Transport
                //Retrait du CRC du message
                byte[] messageToPass = Arrays.copyOfRange(message, 0, message.length - 4);

                //Envoi du message dans Transport
                this.prochain.Handle("ErreurCRC", messageToPass);

            }
            //Réussite CRC
            else {
                Log("Recu");
                System.out.println("Réussite");

                //Retrait du CRC du message
                byte[] messageToPass = Arrays.copyOfRange(message, 0, message.length - 4);

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

        if(file.exists()){
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));

            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            String log;
            switch (action){
                case "Envoi":
                    log = timestamp+"  -  ACTION: Envoi d'un paquet\n";
                    writer.write(log);
                    break;

                case "Recu":
                    log = timestamp+"  -  ACTION: Recu d'un paquet\n";
                    writer.write(log);
                    break;

                case "ErreurCRC":
                    log = timestamp+"  -  ACTION: Recu d'un paquet avec erreur CRC\n";
                    writer.write(log);
                    break;

                case "Stats":
                    log = "\n\n\n\n---STATISTIQUES---\n\n"
                        + "Paquets recus: "+this.paquetsRecus+"\n"
                        + "Paquets erreur CRC:"+this.paquetsRecusErreurCRC+" ("+(this.paquetsRecus/this.paquetsRecusErreurCRC)*100+"%)\n"
                        + "Paquets transmis: "+this.paquetsTransmis+"\n"
                        + "Paquets perdus: "+this.paquetsTransmisPerdus+" ("+(this.paquetsTransmis/this.paquetsTransmisPerdus)*100+"%)\n";
                    writer.write(log);
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

    public void run(){
        System.out.println("Run");
        Handle("Recu",null);
    }
}
