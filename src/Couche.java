public interface Couche {
    public void SetNext(Couche couche);
    public void Handle(String typeRequete, byte[] message);
}
