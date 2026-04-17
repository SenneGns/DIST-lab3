package namingserver.ciscos.distlab3.service;
import org.springframework.stereotype.Service;

@Service
public class HashService {

    private static final long MAX = 2147483647L;
    private static final long MIN = -2147483647L;
    private static final int NEW_MAX = 32768;

    public int hash(String input) {
        long raw = input.hashCode();

        double scaled = (raw + MAX) * ((double) NEW_MAX / (MAX + Math.abs(MIN)));

        return (int) Math.round(scaled);
    }
}