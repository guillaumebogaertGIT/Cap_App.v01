import java.net.HttpURLConnection;
import java.net.URI;

/** HTTP readiness probe without an extra curl package in the runtime image. */
public final class Healthcheck {
    public static void main(String[] args) {
        try {
            var connection=(HttpURLConnection) URI.create("http://127.0.0.1:8081/api/health").toURL().openConnection();
            connection.setConnectTimeout(2000); connection.setReadTimeout(2000);
            int status=connection.getResponseCode(); connection.disconnect();
            System.exit(status==200 ? 0 : 1);
        } catch (Exception ignored) { System.exit(1); }
    }
}
