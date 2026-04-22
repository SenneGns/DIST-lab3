package discovery.ciscos.distlab4.service;

public class RingState {
    private int currentID;
    private int previousID;
    private int nextID;

    public RingState(int currentID) {
        this.currentID = currentID;
        this.previousID = currentID;
        this.nextID = currentID;
    }

    public int getCurrentID()  { return currentID; }
    public int getPreviousID() { return previousID; }
    public int getNextID()     { return nextID; }

    public void setPreviousID(int id) { previousID = id; }
    public void setNextID(int id)     { nextID = id; }

    public String handleNewNode(int hash) {
        int cur  = currentID;
        int prev = previousID;
        int next = nextID;

        if (cur < hash && hash < next) {
            nextID = hash;
            return "NEXT";
        }

        if (prev < hash && hash < cur){
            previousID= hash;
            return "PREV";
        }

        if (next <= cur) { // next wrapped around (next < cur means ring wraps)
            if (hash > cur || hash < next) {
                setNextID(hash);
                return "NEXT";
            }
        }
        if (prev >= cur) {
            if (hash < cur || hash > prev) {
                setPreviousID(hash);
                return "PREV";
            }
        }
        return null;
    }

}
