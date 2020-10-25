public class TransmissionErrorException extends Exception {
    public TransmissionErrorException(String message){
        super("TransmissionErrorException: "+ message);
    }
}