package agents.ciscos.distlab6;

import java.io.Serializable;

public class SystemFileInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    public String fileName;
    public int ownerId;
    public String downloadLocation;
    public boolean locked;

    public SystemFileInfo() {
    }

    public SystemFileInfo(String fileName, int ownerId, String downloadLocation, boolean locked) {
        this.fileName = fileName;
        this.ownerId = ownerId;
        this.downloadLocation = downloadLocation;
        this.locked = locked;
    }

    @Override
    public String toString() {
        return "SystemFileInfo{" +
                "fileName='" + fileName + '\'' +
                ", ownerId=" + ownerId +
                ", downloadLocation='" + downloadLocation + '\'' +
                ", locked=" + locked +
                '}';
    }
}