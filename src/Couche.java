/**
 * Couche est un interface qui indique les méthodes de bases pour chaque Couche du réseau Client et Serveur
 * @author Iliass Bouraba et Pedro Maria Scoccimarro
 * @version 1.0
 */
public interface Couche{
    /**
     * Initialise la prochaine couche utilisé
     * @param couche est la prochaine couche de la chaîne de responsabilité
     */
    public void SetNext(Couche couche);

    /**
     * Gére les données reçus en paramètre selon le type de requête envoyé
     * @param typeRequest correspond à la requête
     * @param message correspond aux données reçus
     */
    public void Handle(String typeRequest, byte[] message);
}
