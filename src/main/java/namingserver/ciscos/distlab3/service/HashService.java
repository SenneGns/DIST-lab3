package namingserver.ciscos.distlab3.service;
import org.springframework.stereotype.Service;

@Service
public class HashService {

    private static final long MAX = 2147483647L; // L prevents overflow (because now it gets treated as long in calc)
    private static final long MIN = -2147483647L;
    private static final int NEW_MAX = 32768; // Modified max number for hashing

    public int hash(String input) {
        long raw = input.hashCode();

        double scaled = (raw + MAX) * ((double) NEW_MAX / (MAX + Math.abs(MIN))); // Now it's between 0 and 32768

        return (int) Math.round(scaled); // Afronden
    }
}