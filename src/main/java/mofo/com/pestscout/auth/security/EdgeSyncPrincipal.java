package mofo.com.pestscout.auth.security;

public record EdgeSyncPrincipal(String companyNumber, String edgeNodeId) {
    public String email() {
        return "edge-sync@" + companyNumber;
    }
}
