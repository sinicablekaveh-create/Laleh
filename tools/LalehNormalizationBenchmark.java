import com.sinicable.telegramelectric.WordBank;

/** Synthetic local benchmark; timings are observations, never a pass/fail threshold. */
public class LalehNormalizationBenchmark {
    public static void main(String[] args) {
        String[] values = {"  تَأسیسات\u200cكابل تهران  ", "INDUSTRIAL   PLC", "برق\n ساختمان"};
        int checksum = 0;
        for (int i = 0; i < 5000; i++) checksum += WordBank.normalize(values[i % 3]).length();
        for (int round = 0; round < 3; round++) {
            long started = System.nanoTime();
            for (int i = 0; i < 20000; i++) checksum += WordBank.normalize(values[i % 3]).length();
            System.out.println("round=" + round + " milliseconds=" + (System.nanoTime() - started) / 1_000_000.0);
        }
        System.out.println("checksum=" + checksum);
    }
}
