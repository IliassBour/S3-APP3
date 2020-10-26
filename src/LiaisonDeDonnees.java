import java.io.*;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.zip.CRC32;

public class LiaisonDeDonnees implements Couche {
    private Couche prochain;
    private DatagramSocket socket;
    private String adresseIP;
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
        try{
            //Ouvrir socket
            this.socket = new DatagramSocket();

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
                    break;

                case "PaquetPerdu":
                    paquetsTransmisPerdus++;
                    Envoi(message);
                    break;
            }
        }
        catch (IOException ioe){/*Erreur*/}

        //Fermer socket
        this.socket.close();
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
        DatagramPacket packet = new DatagramPacket(messageToSend, messageToSend.length, address, 26000);
        this.socket.send(packet);
        Log("Envoi");
        this.paquetsTransmis++;
    }

    public void Recu() throws IOException{

        byte[] messageRecu = new byte[256];
        DatagramPacket packet = new DatagramPacket(messageRecu, messageRecu.length);
        socket.receive(packet);
        this.paquetsRecus++;

        //Création du CRC de vérification à partir du message
        byte[] message = packet.getData();

        CRC32 crcVerif = new CRC32();
        crcVerif.update(message, 0, message.length-4);

        //Prise du CRC inclut dans le message
        byte[] messageCRCBytes = {message[message.length-4], message[message.length-3], message[message.length-2], message[message.length-1]};
        Long messageCRCValue = new BigInteger(messageCRCBytes).longValue();

        //Erreur CRC
        if(messageCRCValue != crcVerif.getValue()){
            this.paquetsRecusErreurCRC++;
            Log("ErreurCRC");
            System.out.println("Erreur");
            System.out.println("messageCRCValue: "+messageCRCValue);
            System.out.println("crcVerif value: "+crcVerif.getValue());

            //Send Error to Transport
            //Retrait du CRC du message
            byte[] messageToPass = Arrays.copyOfRange(message, 0, message.length-4);

            //Envoi du message dans Transport
            this.prochain.Handle("ErreurCRC", messageToPass);

        }
        //Réussite CRC
        else{
            Log("Recu");
            System.out.println("Réussite");

            //Retrait du CRC du message
            byte[] messageToPass = Arrays.copyOfRange(message, 0, message.length-4);

            //Envoi du message dans Transport
            this.prochain.Handle("Recu", messageToPass);
        }
    }

    public void SetAdresseIP(byte[] message){
        this.adresseIP = new String(message);
    }

    public void Log(String action) throws IOException{
        File file = new File("liasonDeDonnes.log");

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
}
