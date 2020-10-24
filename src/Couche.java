public interface Couche {
    public void setNext(Couche couche);
    public void handle(String typeReseau, byte[] message);
}
