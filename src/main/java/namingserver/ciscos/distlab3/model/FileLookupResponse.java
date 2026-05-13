package namingserver.ciscos.distlab3.model;

public class FileLookupResponse {

    private String fileName;
    private int fileHash;
    private int ownerNodeId;
    private String ownerIpAddress;

    public FileLookupResponse() {
    }

    public FileLookupResponse(String fileName, int fileHash, int ownerNodeId, String ownerIpAddress) {
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.ownerNodeId = ownerNodeId; // de hash van de owner node
        this.ownerIpAddress = ownerIpAddress;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getFileHash() {
        return fileHash;
    }

    public void setFileHash(int fileHash) {
        this.fileHash = fileHash;
    }

    public int getOwnerNodeId() {
        return ownerNodeId;
    }

    public void setOwnerNodeId(int ownerNodeId) {
        this.ownerNodeId = ownerNodeId;
    }

    public String getOwnerIpAddress() {
        return ownerIpAddress;
    }

    public void setOwnerIpAddress(String ownerIpAddress) {
        this.ownerIpAddress = ownerIpAddress;
    }
}