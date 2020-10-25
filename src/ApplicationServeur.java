public class ApplicationServeur implements Couche {
    private Couche nextCouche;
    @Override
    public void setNext(Couche couche) {
        nextCouche = couche;
    }

    @Override
    public void handle(String typeRequest, byte[] message) {

    }
}
