public interface Couche {
    public void setNext(Couche couche);
    public void handle(String typeRequest, byte[] message);
}
